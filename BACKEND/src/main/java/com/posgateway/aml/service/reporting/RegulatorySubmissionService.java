package com.posgateway.aml.service.reporting;

import com.posgateway.aml.client.regulator.FcaSubmissionClient;
import com.posgateway.aml.client.regulator.FincenSubmissionClient;
import com.posgateway.aml.client.regulator.OfacSubmissionClient;
import com.posgateway.aml.client.regulator.RegulatorSubmissionClient;
import com.posgateway.aml.client.regulator.RegulatorSubmissionDisabledException;
import com.posgateway.aml.client.regulator.SubmissionResult;
import com.posgateway.aml.dto.reporting.RegulatorySubmissionDTO;
import com.posgateway.aml.dto.reporting.RegulatorySubmissionRequest;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.reporting.*;
import com.posgateway.aml.repository.reporting.RegulatorySubmissionAttemptRepository;
import com.posgateway.aml.repository.reporting.RegulatorySubmissionRepository;
import com.posgateway.aml.repository.reporting.ReportExecutionRepository;
import com.posgateway.aml.repository.reporting.ReportRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Regulatory Submission Service
 * Handles preparation, submission, and tracking of regulatory filings
 */
@Service
public class RegulatorySubmissionService {

    private static final Logger logger = LoggerFactory.getLogger(RegulatorySubmissionService.class);

    private final RegulatorySubmissionRepository submissionRepository;
    private final RegulatorySubmissionAttemptRepository submissionAttemptRepository;
    private final ReportRepository reportRepository;
    private final ReportExecutionRepository reportExecutionRepository;
    private final UserRepository userRepository;
    private final PspIsolationService pspIsolationService;

    /** Hard cap on the request body bytes we log; longer payloads are truncated. */
    private static final int REQUEST_BODY_MAX_BYTES = 64 * 1024;
    private static final String TRUNCATED_MARKER = "\n__TRUNCATED__";

    // Optional regulator-side adapters — present only when the matching
    // `regulators.<name>.enabled` flag is true. We tolerate absence at boot
    // so deploys without all three credential sets still come up.
    private final FincenSubmissionClient fincenClient;
    private final FcaSubmissionClient fcaClient;
    private final OfacSubmissionClient ofacClient;

    // Regulator-specific configurations
    private static final String REGULATOR_FINCEN = "FINCEN";
    private static final String REGULATOR_FCA = "FCA";
    private static final String REGULATOR_OFAC = "OFAC";
    private static final String REGULATOR_FIU = "FIU";

    public RegulatorySubmissionService(RegulatorySubmissionRepository submissionRepository,
                                       RegulatorySubmissionAttemptRepository submissionAttemptRepository,
                                       ReportRepository reportRepository,
                                       ReportExecutionRepository reportExecutionRepository,
                                       UserRepository userRepository,
                                       PspIsolationService pspIsolationService,
                                       @Autowired(required = false) FincenSubmissionClient fincenClient,
                                       @Autowired(required = false) FcaSubmissionClient fcaClient,
                                       @Autowired(required = false) OfacSubmissionClient ofacClient) {
        this.submissionRepository = submissionRepository;
        this.submissionAttemptRepository = submissionAttemptRepository;
        this.reportRepository = reportRepository;
        this.reportExecutionRepository = reportExecutionRepository;
        this.userRepository = userRepository;
        this.pspIsolationService = pspIsolationService;
        this.fincenClient = fincenClient;
        this.fcaClient = fcaClient;
        this.ofacClient = ofacClient;
    }

    /**
     * Prepare a new regulatory submission
     */
    @Transactional
    public RegulatorySubmissionDTO prepareSubmission(Long reportId, String regulatoryBody, Long userId, Long pspId) {
        logger.info("Preparing submission for report: {}, regulator: {}, user: {}", reportId, regulatoryBody, userId);
        
        // Find report
        Report report = reportRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
        
        // Sanitize PSP ID
        Long effectivePspId = pspIsolationService.sanitizePspId(pspId);
        
        // Validate regulatory body
        validateRegulatoryBody(regulatoryBody);
        
        // Create submission
        RegulatorySubmission submission = new RegulatorySubmission();
        submission.setSubmissionReference(generateSubmissionReference(regulatoryBody));
        submission.setReport(report);
        submission.setRegulatorCode(regulatoryBody.toUpperCase());
        submission.setSubmissionType(determineSubmissionType(report, regulatoryBody));
        submission.setJurisdiction(determineJurisdiction(regulatoryBody));
        submission.setStatus(SubmissionStatus.DRAFT);
        submission.setPspId(effectivePspId);
        
        // Set filing period (default to previous month)
        LocalDate now = LocalDate.now();
        submission.setFilingPeriodStart(now.minusMonths(1).withDayOfMonth(1));
        submission.setFilingPeriodEnd(now.withDayOfMonth(1).minusDays(1));
        submission.setFilingDeadline(calculateFilingDeadline(regulatoryBody, submission.getFilingPeriodEnd()));
        
        User user = userRepository.findById(userId).orElse(null);
        submission.setPreparedBy(user);
        submission.setPreparedAt(LocalDateTime.now());
        
        RegulatorySubmission saved = submissionRepository.save(submission);
        logger.info("Submission prepared: {}", saved.getSubmissionReference());
        
        return convertToDTO(saved);
    }

    /**
     * Submit to FinCEN (Financial Crimes Enforcement Network).
     * Uses {@link FincenSubmissionClient} when {@code regulators.fincen.enabled=true};
     * otherwise the submission is parked in {@link SubmissionStatus#SUBMISSION_PENDING}.
     */
    @Transactional
    public RegulatorySubmissionDTO submitToFinCEN(Long reportId, Long userId, Long pspId) {
        logger.info("Submitting to FinCEN - report: {}, user: {}", reportId, userId);
        RegulatorySubmission submission = findOrCreateSubmission(reportId, REGULATOR_FINCEN, userId, pspId);
        return dispatchSubmission(submission, fincenClient, REGULATOR_FINCEN, userId);
    }

    /**
     * Submit to FCA (Financial Conduct Authority - UK).
     */
    @Transactional
    public RegulatorySubmissionDTO submitToFCA(Long reportId, Long userId, Long pspId) {
        logger.info("Submitting to FCA - report: {}, user: {}", reportId, userId);
        RegulatorySubmission submission = findOrCreateSubmission(reportId, REGULATOR_FCA, userId, pspId);
        return dispatchSubmission(submission, fcaClient, REGULATOR_FCA, userId);
    }

    /**
     * Submit to OFAC. Note: OFAC has no SAR-submit endpoint; the adapter validates
     * the SDN feed freshness and returns a NO_OP result when the bean is enabled.
     */
    @Transactional
    public RegulatorySubmissionDTO submitToOFAC(Long reportId, Long userId, Long pspId) {
        logger.info("Submitting to OFAC - report: {}, user: {}", reportId, userId);
        RegulatorySubmission submission = findOrCreateSubmission(reportId, REGULATOR_OFAC, userId, pspId);
        return dispatchSubmission(submission, ofacClient, REGULATOR_OFAC, userId);
    }

    /**
     * Common submission dispatch:
     * <ol>
     *   <li>Verify the submission is APPROVED ({@link RegulatorySubmission#canFile()}).</li>
     *   <li>If a regulator-side reference is already present, treat the call as a
     *       no-op replay (idempotency) and return the existing record.</li>
     *   <li>Invoke the wired client; on success persist FILED + receipt.</li>
     *   <li>On {@link RegulatorSubmissionDisabledException}, mark the submission
     *       {@link SubmissionStatus#SUBMISSION_PENDING} with a rejection reason so
     *       it can be re-driven once the regulator is enabled.</li>
     * </ol>
     */
    private RegulatorySubmissionDTO dispatchSubmission(RegulatorySubmission submission,
                                                       RegulatorSubmissionClient client,
                                                       String regulatorCode,
                                                       Long userId) {
        if (!submission.canFile()) {
            throw new IllegalStateException("Submission cannot be filed. Current status: " + submission.getStatus());
        }

        // Idempotent retry: if a previous attempt already returned a regulator ref, don't re-submit.
        if (submission.getStatus() == SubmissionStatus.FILED && submission.getRegulatorReference() != null) {
            logger.info("{} submission {} already filed (ref={}), returning existing record",
                    regulatorCode, submission.getSubmissionReference(), submission.getRegulatorReference());
            return convertToDTO(submission);
        }

        if (client == null) {
            recordAttempt(submission, regulatorCode,
                    String.valueOf(submission.getSubmittedData()), null, null,
                    regulatorCode + " client not configured");
            return parkPending(submission, regulatorCode,
                    regulatorCode + " client not configured (regulators." + regulatorCode.toLowerCase() + ".enabled=false)");
        }

        try {
            SubmissionResult result = client.submit(submission);
            submission.setStatus(SubmissionStatus.FILED);
            submission.setFiledBy(userRepository.findById(userId).orElse(null));
            submission.setFiledAt(LocalDateTime.now());
            submission.setRegulatorReference(result.submissionId());
            submission.setFilingReceipt(generateFilingReceipt(submission, regulatorCode));
            RegulatorySubmission saved = submissionRepository.save(submission);
            recordAttempt(saved, regulatorCode,
                    String.valueOf(saved.getSubmittedData()),
                    "regulatorRef=" + result.submissionId() + " status=" + result.status(),
                    200,
                    null);
            logger.info("{} submission completed: localRef={} regulatorRef={} status={}",
                    regulatorCode, saved.getSubmissionReference(), result.submissionId(), result.status());
            return convertToDTO(saved);
        } catch (RegulatorSubmissionDisabledException e) {
            recordAttempt(submission, regulatorCode,
                    String.valueOf(submission.getSubmittedData()), e.getMessage(), null, e.getMessage());
            return parkPending(submission, regulatorCode, e.getMessage());
        } catch (RuntimeException e) {
            recordAttempt(submission, regulatorCode,
                    String.valueOf(submission.getSubmittedData()), e.getMessage(), 500, e.getMessage());
            throw e;
        }
    }

    /**
     * Append a row to {@code regulatory_submission_attempts}. Best-effort; a
     * failure to log MUST NOT break the dispatch path so we swallow the
     * persistence exception with a warning.
     */
    private void recordAttempt(RegulatorySubmission submission,
                               String regulatorCode,
                               String requestBody,
                               String responseBody,
                               Integer httpStatus,
                               String errorOrNull) {
        try {
            int nextAttempt = submissionAttemptRepository
                    .findTopBySubmissionIdAndRegulatorOrderByAttemptNoDesc(submission.getId(), regulatorCode)
                    .map(a -> a.getAttemptNo() == null ? 1 : a.getAttemptNo() + 1)
                    .orElse(1);

            RegulatorySubmissionAttempt attempt = new RegulatorySubmissionAttempt();
            attempt.setSubmissionId(submission.getId());
            attempt.setRegulator(regulatorCode);
            attempt.setIdempotencyKey(submission.getSubmissionReference() + "#" + nextAttempt);
            attempt.setRequestBody(truncate(requestBody));
            attempt.setResponseBody(truncate(responseBody));
            attempt.setHttpStatus(httpStatus);
            attempt.setAttemptNo(nextAttempt);
            submissionAttemptRepository.save(attempt);
            if (errorOrNull != null) {
                logger.debug("Logged regulatory submission attempt {} for {} (error: {})",
                        nextAttempt, regulatorCode, errorOrNull);
            }
        } catch (Exception ex) {
            logger.warn("Failed to log regulatory_submission_attempts row for submission={} regulator={}: {}",
                    submission != null ? submission.getId() : null, regulatorCode, ex.getMessage());
        }
    }

    private static String truncate(String body) {
        if (body == null) return null;
        if (body.length() <= REQUEST_BODY_MAX_BYTES) return body;
        return body.substring(0, REQUEST_BODY_MAX_BYTES) + TRUNCATED_MARKER;
    }

    private RegulatorySubmissionDTO parkPending(RegulatorySubmission submission, String regulatorCode, String reason) {
        logger.warn("{} submission {} parked SUBMISSION_PENDING: {}",
                regulatorCode, submission.getSubmissionReference(), reason);
        submission.setStatus(SubmissionStatus.SUBMISSION_PENDING);
        submission.setRejectionReason("Pending regulator enablement: " + reason);
        RegulatorySubmission saved = submissionRepository.save(submission);
        return convertToDTO(saved);
    }

    /**
     * Track submission status
     */
    @Transactional(readOnly = true)
    public RegulatorySubmissionDTO trackSubmissionStatus(String submissionId) {
        logger.debug("Tracking submission: {}", submissionId);
        
        RegulatorySubmission submission = submissionRepository.findBySubmissionReference(submissionId)
            .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
        
        // Validate PSP access
        pspIsolationService.validatePspAccess(submission.getPspId());
        
        // In production, query regulator API for latest status
        // For now, return current status
        
        return convertToDTO(submission);
    }

    /**
     * Get submission by ID
     */
    @Transactional(readOnly = true)
    public RegulatorySubmissionDTO getSubmissionById(Long id) {
        RegulatorySubmission submission = submissionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + id));
        
        pspIsolationService.validatePspAccess(submission.getPspId());
        
        return convertToDTO(submission);
    }

    /**
     * List submissions with filters
     */
    @Transactional(readOnly = true)
    public Page<RegulatorySubmissionDTO> listSubmissions(Long pspId, SubmissionStatus status, String regulatorCode, 
                                                           LocalDate deadlineFrom, LocalDate deadlineTo, 
                                                           int page, int size, String sortBy, String sortDirection) {
        logger.debug("Listing submissions for psp: {}, status: {}, regulator: {}", pspId, status, regulatorCode);
        
        // Sanitize PSP ID
        Long effectivePspId = pspIsolationService.sanitizePspId(pspId);
        
        Sort sort = Sort.by(sortDirection.equalsIgnoreCase("DESC") ? Sort.Direction.DESC : Sort.Direction.ASC, 
                             sortBy != null ? sortBy : "createdAt");
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<RegulatorySubmission> submissions = submissionRepository.findByFilters(
            effectivePspId, status, regulatorCode, deadlineFrom, deadlineTo, pageable);
        
        return submissions.map(this::convertToDTO);
    }

    /**
     * Update submission status
     */
    @Transactional
    public RegulatorySubmissionDTO updateSubmissionStatus(Long submissionId, SubmissionStatus newStatus, Long userId) {
        logger.info("Updating submission {} status to {}", submissionId, newStatus);
        
        RegulatorySubmission submission = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
        
        pspIsolationService.validatePspAccess(submission.getPspId());
        
        User user = userRepository.findById(userId).orElse(null);
        
        // Handle status transitions
        switch (newStatus) {
            case PENDING_REVIEW:
                if (submission.getStatus() != SubmissionStatus.DRAFT) {
                    throw new IllegalStateException("Can only move to PENDING_REVIEW from DRAFT");
                }
                break;
            case APPROVED:
                if (submission.getStatus() != SubmissionStatus.PENDING_REVIEW) {
                    throw new IllegalStateException("Can only approve from PENDING_REVIEW");
                }
                submission.setApprovedBy(user);
                submission.setApprovedAt(LocalDateTime.now());
                break;
            case FILED:
                if (!submission.canFile()) {
                    throw new IllegalStateException("Submission must be APPROVED before filing");
                }
                submission.setFiledBy(user);
                submission.setFiledAt(LocalDateTime.now());
                break;
            case REJECTED:
                submission.setRejectionReason("Rejected by user: " + (user != null ? user.getFullName() : "Unknown"));
                break;
            default:
                break;
        }
        
        submission.setStatus(newStatus);
        RegulatorySubmission saved = submissionRepository.save(submission);
        
        return convertToDTO(saved);
    }

    /**
     * Create amended submission
     */
    @Transactional
    public RegulatorySubmissionDTO amendSubmission(Long originalSubmissionId, String amendmentReason, Long userId) {
        logger.info("Creating amended submission for: {}", originalSubmissionId);
        
        RegulatorySubmission original = submissionRepository.findById(originalSubmissionId)
            .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + originalSubmissionId));
        
        pspIsolationService.validatePspAccess(original.getPspId());
        
        if (original.getStatus() != SubmissionStatus.FILED) {
            throw new IllegalStateException("Can only amend filed submissions");
        }
        
        // Create new submission as amendment
        RegulatorySubmission amended = new RegulatorySubmission();
        amended.setSubmissionReference(generateSubmissionReference(original.getRegulatorCode()));
        amended.setReport(original.getReport());
        amended.setRegulatorCode(original.getRegulatorCode());
        amended.setSubmissionType(original.getSubmissionType() + "_AMENDED");
        amended.setJurisdiction(original.getJurisdiction());
        amended.setFilingPeriodStart(original.getFilingPeriodStart());
        amended.setFilingPeriodEnd(original.getFilingPeriodEnd());
        amended.setFilingDeadline(original.getFilingDeadline());
        amended.setStatus(SubmissionStatus.DRAFT);
        amended.setAmendedSubmission(original);
        amended.setAmendmentReason(amendmentReason);
        amended.setPspId(original.getPspId());
        
        User user = userRepository.findById(userId).orElse(null);
        amended.setPreparedBy(user);
        amended.setPreparedAt(LocalDateTime.now());
        
        // Mark original as amended
        original.setStatus(SubmissionStatus.AMENDED);
        submissionRepository.save(original);
        
        RegulatorySubmission saved = submissionRepository.save(amended);
        logger.info("Amended submission created: {}", saved.getSubmissionReference());
        
        return convertToDTO(saved);
    }

    /**
     * Get overdue submissions
     */
    @Transactional(readOnly = true)
    public List<RegulatorySubmissionDTO> getOverdueSubmissions() {
        logger.debug("Getting overdue submissions");
        
        List<RegulatorySubmission> overdue = submissionRepository.findOverdueSubmissions();
        
        return overdue.stream()
            .filter(s -> {
                try {
                    pspIsolationService.validatePspAccess(s.getPspId());
                    return true;
                } catch (SecurityException e) {
                    return false;
                }
            })
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get at-risk submissions (deadline approaching)
     */
    @Transactional(readOnly = true)
    public List<RegulatorySubmissionDTO> getAtRiskSubmissions(int daysThreshold) {
        logger.debug("Getting at-risk submissions within {} days", daysThreshold);
        
        LocalDate threshold = LocalDate.now().plusDays(daysThreshold);
        List<RegulatorySubmission> atRisk = submissionRepository.findAtRiskSubmissions(threshold);
        
        return atRisk.stream()
            .filter(s -> {
                try {
                    pspIsolationService.validatePspAccess(s.getPspId());
                    return true;
                } catch (SecurityException e) {
                    return false;
                }
            })
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Delete a submission (only draft submissions)
     */
    @Transactional
    public void deleteSubmission(Long submissionId) {
        logger.info("Deleting submission: {}", submissionId);
        
        RegulatorySubmission submission = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
        
        pspIsolationService.validatePspAccess(submission.getPspId());
        
        if (submission.getStatus() != SubmissionStatus.DRAFT) {
            throw new IllegalStateException("Only draft submissions can be deleted");
        }
        
        submissionRepository.delete(submission);
        logger.info("Submission deleted: {}", submissionId);
    }

    // Helper methods

    private void validateRegulatoryBody(String regulatoryBody) {
        List<String> validBodies = List.of(REGULATOR_FINCEN, REGULATOR_FCA, REGULATOR_OFAC, REGULATOR_FIU);
        if (!validBodies.contains(regulatoryBody.toUpperCase())) {
            throw new IllegalArgumentException("Invalid regulatory body: " + regulatoryBody);
        }
    }

    private String determineSubmissionType(Report report, String regulatoryBody) {
        // Map report category to submission type based on regulator
        if (report.getReportCategory() != null) {
            return report.getReportCategory().name() + "_" + regulatoryBody;
        }
        return "GENERAL_" + regulatoryBody;
    }

    private String determineJurisdiction(String regulatoryBody) {
        return switch (regulatoryBody.toUpperCase()) {
            case REGULATOR_FINCEN, REGULATOR_OFAC -> "US";
            case REGULATOR_FCA -> "UK";
            default -> "UNKNOWN";
        };
    }

    private LocalDate calculateFilingDeadline(String regulatoryBody, LocalDate periodEnd) {
        // Default deadlines by regulator
        return switch (regulatoryBody.toUpperCase()) {
            case REGULATOR_FINCEN -> periodEnd.plusDays(30); // 30 days after period end
            case REGULATOR_FCA -> periodEnd.plusDays(45);    // 45 days after period end
            case REGULATOR_OFAC -> periodEnd.plusDays(10);   // 10 days for OFAC
            default -> periodEnd.plusDays(30);
        };
    }

    private RegulatorySubmission findOrCreateSubmission(Long reportId, String regulator, Long userId, Long pspId) {
        // Look for existing draft submission
        Optional<RegulatorySubmission> existing = submissionRepository.findByFilters(
            pspId, SubmissionStatus.DRAFT, regulator, null, null, PageRequest.of(0, 1))
            .getContent().stream()
            .filter(s -> s.getReport().getId().equals(reportId))
            .findFirst();
        
        if (existing.isPresent()) {
            return existing.get();
        }
        
        // Create new submission
        RegulatorySubmissionDTO dto = prepareSubmission(reportId, regulator, userId, pspId);
        return submissionRepository.findById(dto.getId()).orElseThrow();
    }

    private String generateSubmissionReference(String regulatoryBody) {
        String prefix = regulatoryBody.substring(0, 3);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuid = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return prefix + "-" + timestamp + "-" + uuid;
    }

    private String generateFilingReceipt(RegulatorySubmission submission, String regulator) {
        StringBuilder receipt = new StringBuilder();
        receipt.append("FILING RECEIPT\n");
        receipt.append("==============\n\n");
        receipt.append("Submission Reference: ").append(submission.getSubmissionReference()).append("\n");
        receipt.append("Regulator Reference: ").append(submission.getRegulatorReference()).append("\n");
        receipt.append("Regulator: ").append(regulator).append("\n");
        receipt.append("Filing Period: ").append(submission.getFilingPeriodStart()).append(" to ").append(submission.getFilingPeriodEnd()).append("\n");
        receipt.append("Filed At: ").append(submission.getFiledAt()).append("\n");
        receipt.append("Status: ").append(submission.getStatus()).append("\n");
        receipt.append("\nThis receipt confirms your filing has been received.");
        return receipt.toString();
    }

    private RegulatorySubmissionDTO convertToDTO(RegulatorySubmission submission) {
        RegulatorySubmissionDTO dto = new RegulatorySubmissionDTO();
        dto.setId(submission.getId());
        dto.setSubmissionReference(submission.getSubmissionReference());
        dto.setReportId(submission.getReport() != null ? submission.getReport().getId() : null);
        dto.setReportName(submission.getReport() != null ? submission.getReport().getReportName() : null);
        dto.setReportCode(submission.getReport() != null ? submission.getReport().getReportCode() : null);
        dto.setExecutionId(submission.getExecutionId());
        dto.setRegulatorCode(submission.getRegulatorCode());
        dto.setSubmissionType(submission.getSubmissionType());
        dto.setJurisdiction(submission.getJurisdiction());
        dto.setFilingPeriodStart(submission.getFilingPeriodStart());
        dto.setFilingPeriodEnd(submission.getFilingPeriodEnd());
        dto.setFilingDeadline(submission.getFilingDeadline());
        dto.setStatus(submission.getStatus());
        
        if (submission.getPreparedBy() != null) {
            dto.setPreparedBy(submission.getPreparedBy().getId());
            dto.setPreparedByName(submission.getPreparedBy().getFullName());
        }
        dto.setPreparedAt(submission.getPreparedAt());
        
        if (submission.getReviewedBy() != null) {
            dto.setReviewedBy(submission.getReviewedBy().getId());
            dto.setReviewedByName(submission.getReviewedBy().getFullName());
        }
        dto.setReviewedAt(submission.getReviewedAt());
        
        if (submission.getApprovedBy() != null) {
            dto.setApprovedBy(submission.getApprovedBy().getId());
            dto.setApprovedByName(submission.getApprovedBy().getFullName());
        }
        dto.setApprovedAt(submission.getApprovedAt());
        
        if (submission.getFiledBy() != null) {
            dto.setFiledBy(submission.getFiledBy().getId());
            dto.setFiledByName(submission.getFiledBy().getFullName());
        }
        dto.setFiledAt(submission.getFiledAt());
        
        dto.setRegulatorReference(submission.getRegulatorReference());
        dto.setFilingReceipt(submission.getFilingReceipt());
        dto.setRejectionReason(submission.getRejectionReason());
        dto.setAmendedSubmissionId(submission.getAmendedSubmission() != null ? submission.getAmendedSubmission().getId() : null);
        dto.setAmendmentReason(submission.getAmendmentReason());
        dto.setSubmittedData(submission.getSubmittedData());
        dto.setAttachmentPaths(submission.getAttachmentPaths());
        dto.setPspId(submission.getPspId());
        dto.setIsLateFiling(submission.isLateFiling());
        dto.setDaysUntilDeadline(submission.getDaysUntilDeadline());
        dto.setCreatedAt(submission.getCreatedAt());
        dto.setUpdatedAt(submission.getUpdatedAt());
        
        return dto;
    }
}
