package com.posgateway.aml.service.reporting;

import com.posgateway.aml.dto.reporting.RegulatorySubmissionDTO;
import com.posgateway.aml.dto.reporting.RegulatorySubmissionRequest;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.reporting.*;
import com.posgateway.aml.repository.reporting.RegulatorySubmissionRepository;
import com.posgateway.aml.repository.reporting.ReportExecutionRepository;
import com.posgateway.aml.repository.reporting.ReportRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final ReportRepository reportRepository;
    private final ReportExecutionRepository reportExecutionRepository;
    private final UserRepository userRepository;
    private final PspIsolationService pspIsolationService;

    // Regulator-specific configurations
    private static final String REGULATOR_FINCEN = "FINCEN";
    private static final String REGULATOR_FCA = "FCA";
    private static final String REGULATOR_OFAC = "OFAC";
    private static final String REGULATOR_FIU = "FIU";

    public RegulatorySubmissionService(RegulatorySubmissionRepository submissionRepository,
                                       ReportRepository reportRepository,
                                       ReportExecutionRepository reportExecutionRepository,
                                       UserRepository userRepository,
                                       PspIsolationService pspIsolationService) {
        this.submissionRepository = submissionRepository;
        this.reportRepository = reportRepository;
        this.reportExecutionRepository = reportExecutionRepository;
        this.userRepository = userRepository;
        this.pspIsolationService = pspIsolationService;
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
     * Submit to FinCEN (Financial Crimes Enforcement Network)
     */
    @Transactional
    public RegulatorySubmissionDTO submitToFinCEN(Long reportId, Long userId, Long pspId) {
        logger.info("Submitting to FinCEN - report: {}, user: {}", reportId, userId);
        
        // Prepare submission if not exists
        RegulatorySubmission submission = findOrCreateSubmission(reportId, REGULATOR_FINCEN, userId, pspId);
        
        // Validate submission can be filed
        if (!submission.canFile()) {
            throw new IllegalStateException("Submission cannot be filed. Current status: " + submission.getStatus());
        }
        
        // Perform FinCEN-specific submission logic
        // In production, this would integrate with FinCEN's BSA E-Filing System
        String fincenReference = submitToFinCENApi(submission);
        
        // Update submission
        submission.setStatus(SubmissionStatus.FILED);
        submission.setFiledBy(userRepository.findById(userId).orElse(null));
        submission.setFiledAt(LocalDateTime.now());
        submission.setRegulatorReference(fincenReference);
        submission.setFilingReceipt(generateFilingReceipt(submission, REGULATOR_FINCEN));
        
        RegulatorySubmission saved = submissionRepository.save(submission);
        logger.info("FinCEN submission completed: {}", saved.getSubmissionReference());
        
        return convertToDTO(saved);
    }

    /**
     * Submit to FCA (Financial Conduct Authority - UK)
     */
    @Transactional
    public RegulatorySubmissionDTO submitToFCA(Long reportId, Long userId, Long pspId) {
        logger.info("Submitting to FCA - report: {}, user: {}", reportId, userId);
        
        RegulatorySubmission submission = findOrCreateSubmission(reportId, REGULATOR_FCA, userId, pspId);
        
        if (!submission.canFile()) {
            throw new IllegalStateException("Submission cannot be filed. Current status: " + submission.getStatus());
        }
        
        // FCA-specific submission
        String fcaReference = submitToFCAApi(submission);
        
        submission.setStatus(SubmissionStatus.FILED);
        submission.setFiledBy(userRepository.findById(userId).orElse(null));
        submission.setFiledAt(LocalDateTime.now());
        submission.setRegulatorReference(fcaReference);
        submission.setFilingReceipt(generateFilingReceipt(submission, REGULATOR_FCA));
        
        RegulatorySubmission saved = submissionRepository.save(submission);
        logger.info("FCA submission completed: {}", saved.getSubmissionReference());
        
        return convertToDTO(saved);
    }

    /**
     * Submit to OFAC (Office of Foreign Assets Control)
     */
    @Transactional
    public RegulatorySubmissionDTO submitToOFAC(Long reportId, Long userId, Long pspId) {
        logger.info("Submitting to OFAC - report: {}, user: {}", reportId, userId);
        
        RegulatorySubmission submission = findOrCreateSubmission(reportId, REGULATOR_OFAC, userId, pspId);
        
        if (!submission.canFile()) {
            throw new IllegalStateException("Submission cannot be filed. Current status: " + submission.getStatus());
        }
        
        // OFAC-specific submission
        String ofacReference = submitToOFACApi(submission);
        
        submission.setStatus(SubmissionStatus.FILED);
        submission.setFiledBy(userRepository.findById(userId).orElse(null));
        submission.setFiledAt(LocalDateTime.now());
        submission.setRegulatorReference(ofacReference);
        submission.setFilingReceipt(generateFilingReceipt(submission, REGULATOR_OFAC));
        
        RegulatorySubmission saved = submissionRepository.save(submission);
        logger.info("OFAC submission completed: {}", saved.getSubmissionReference());
        
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

    private String submitToFinCENApi(RegulatorySubmission submission) {
        // In production: Integrate with FinCEN BSA E-Filing API
        // This is a mock implementation
        logger.info("Mock FinCEN API submission for: {}", submission.getSubmissionReference());
        return "FINCEN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    private String submitToFCAApi(RegulatorySubmission submission) {
        // In production: Integrate with FCA RegData API
        logger.info("Mock FCA API submission for: {}", submission.getSubmissionReference());
        return "FCA-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    private String submitToOFACApi(RegulatorySubmission submission) {
        // In production: Integrate with OFAC reporting system
        logger.info("Mock OFAC API submission for: {}", submission.getSubmissionReference());
        return "OFAC-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
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
