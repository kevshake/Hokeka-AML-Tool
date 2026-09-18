package com.posgateway.aml.service.reporting;

import com.posgateway.aml.client.regulator.FcaSubmissionClient;
import com.posgateway.aml.client.regulator.FincenSubmissionClient;
import com.posgateway.aml.client.regulator.FrcSubmissionClient;
import com.posgateway.aml.client.regulator.RegulatorSubmissionClient;
import com.posgateway.aml.client.regulator.RegulatorSubmissionDisabledException;
import com.posgateway.aml.client.regulator.RegulatorTransportException;
import com.posgateway.aml.client.regulator.SubmissionResult;
import com.posgateway.aml.dto.reporting.RegulatorySubmissionDTO;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.reporting.*;
import com.posgateway.aml.repository.reporting.RegulatorySubmissionAttemptRepository;
import com.posgateway.aml.repository.reporting.RegulatorySubmissionRepository;
import com.posgateway.aml.repository.reporting.ReportExecutionRepository;
import com.posgateway.aml.repository.reporting.ReportResultRepository;
import com.posgateway.aml.repository.reporting.ReportRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.compliance.FrcReportingService;
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
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
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
    private final ReportResultRepository reportResultRepository;
    private final UserRepository userRepository;
    private final PspIsolationService pspIsolationService;
    private final FrcReportingService frcReportingService;

    /** Hard cap on the request body bytes we log; longer payloads are truncated. */
    private static final int REQUEST_BODY_MAX_BYTES = 64 * 1024;
    private static final String TRUNCATED_MARKER = "\n__TRUNCATED__";

    // Optional regulator-side adapters — present only when the matching
    // `regulators.<name>.enabled` flag is true. We tolerate absence at boot
    // so deployments without every regulator credential set still come up.
    private final FincenSubmissionClient fincenClient;
    private final FcaSubmissionClient fcaClient;
    private final FrcSubmissionClient frcClient;

    // Regulator-specific configurations
    private static final String REGULATOR_FINCEN = "FINCEN";
    private static final String REGULATOR_FCA = "FCA";
    private static final String REGULATOR_FRC = "FRC";

    public RegulatorySubmissionService(RegulatorySubmissionRepository submissionRepository,
                                       RegulatorySubmissionAttemptRepository submissionAttemptRepository,
                                       ReportRepository reportRepository,
                                       ReportExecutionRepository reportExecutionRepository,
                                       ReportResultRepository reportResultRepository,
                                       UserRepository userRepository,
                                       PspIsolationService pspIsolationService,
                                       FrcReportingService frcReportingService,
                                       @Autowired(required = false) FincenSubmissionClient fincenClient,
                                       @Autowired(required = false) FcaSubmissionClient fcaClient,
                                       @Autowired(required = false) FrcSubmissionClient frcClient) {
        this.submissionRepository = submissionRepository;
        this.submissionAttemptRepository = submissionAttemptRepository;
        this.reportRepository = reportRepository;
        this.reportExecutionRepository = reportExecutionRepository;
        this.reportResultRepository = reportResultRepository;
        this.userRepository = userRepository;
        this.pspIsolationService = pspIsolationService;
        this.frcReportingService = frcReportingService;
        this.fincenClient = fincenClient;
        this.fcaClient = fcaClient;
        this.frcClient = frcClient;
    }

    /**
     * Prepare a new regulatory submission
     */
    @Transactional
    public RegulatorySubmissionDTO prepareSubmission(Long reportId, String regulatoryBody, Long userId, Long pspId) {
        logger.info("Preparing submission for report: {}, regulator: {}, user: {}", reportId, regulatoryBody, userId);

        Report report = reportRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
        if (!Boolean.TRUE.equals(report.getEnabled())) {
            throw new IllegalStateException("Disabled report cannot be prepared for filing");
        }

        Long effectivePspId = pspIsolationService.sanitizePspId(pspId);
        String regulatorCode = normalizeRegulatoryBody(regulatoryBody);
        ReportExecution execution = reportExecutionRepository.findLatestForSubmission(
                        reportId, effectivePspId, ExecutionStatus.COMPLETED, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "A completed report execution is required before preparing a regulatory filing"));
        return prepareSubmission(report, execution, regulatoryBody, userId, effectivePspId);
    }

    /**
     * Prepare a filing from one immutable completed execution.
     */
    @Transactional
    public RegulatorySubmissionDTO prepareSubmissionForExecution(
            Long executionId, String regulatoryBody, Long userId, Long pspId) {
        ReportExecution execution = reportExecutionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Report execution not found: " + executionId));
        if (execution.getReport() == null) {
            throw new IllegalStateException("Report execution has no report definition");
        }
        Long effectivePspId = pspIsolationService.sanitizePspId(pspId);
        if (execution.getPspId() != null && !Objects.equals(execution.getPspId(), effectivePspId)) {
            throw new SecurityException("Report execution is outside the current PSP scope");
        }
        return prepareSubmission(
                execution.getReport(), execution, regulatoryBody, userId, effectivePspId);
    }

    private RegulatorySubmissionDTO prepareSubmission(
            Report report,
            ReportExecution execution,
            String regulatoryBody,
            Long userId,
            Long effectivePspId) {
        if (!Boolean.TRUE.equals(report.getEnabled())) {
            throw new IllegalStateException("Disabled report cannot be prepared for filing");
        }
        if (execution.getStatus() != ExecutionStatus.COMPLETED
                || execution.getCompletedAt() == null) {
            throw new IllegalStateException(
                    "Only a completed report execution can be prepared for filing");
        }

        String regulatorCode = normalizeRegulatoryBody(regulatoryBody);
        Optional<RegulatorySubmission> existing = submissionRepository
                .findFirstByExecutionIdAndPspIdAndRegulatorCodeAndAmendedSubmissionIsNullOrderByCreatedAtDesc(
                        execution.getId(), effectivePspId, regulatorCode);
        if (existing.isPresent()) {
            return convertToDTO(existing.get());
        }

        LocalDateTime periodStartTimestamp = execution.getDateFrom() != null
                ? execution.getDateFrom() : execution.getCreatedAt();
        LocalDateTime periodEndTimestamp = execution.getDateTo() != null
                ? execution.getDateTo() : execution.getCompletedAt();
        if (periodStartTimestamp == null || periodEndTimestamp == null) {
            throw new IllegalStateException("Completed execution has no reproducible filing period");
        }
        if (periodEndTimestamp.isBefore(periodStartTimestamp)) {
            throw new IllegalStateException("Execution filing period ends before it starts");
        }

        RegulatorySubmission submission = new RegulatorySubmission();
        submission.setSubmissionReference(generateSubmissionReference(regulatorCode));
        submission.setReport(report);
        submission.setExecutionId(execution.getId());
        submission.setRegulatorCode(regulatorCode);
        submission.setSubmissionType(determineSubmissionType(report, regulatorCode));
        submission.setJurisdiction(determineJurisdiction(regulatorCode));
        submission.setStatus(SubmissionStatus.DRAFT);
        submission.setPspId(effectivePspId);
        LocalDate periodStart = periodStartTimestamp.toLocalDate();
        LocalDate periodEnd = periodEndTimestamp.toLocalDate();
        submission.setFilingPeriodStart(periodStart);
        submission.setFilingPeriodEnd(periodEnd);
        submission.setFilingDeadline(calculateFilingDeadline(regulatorCode, report, periodEnd));
        Map<String, Object> evidence = buildSubmissionEvidence(execution);
        Map<String, String> attachments = buildAttachmentPaths(execution);
        if (REGULATOR_FRC.equals(regulatorCode)) {
            Path xmlArtifact = generateFrcArtifact(report, execution, submission.getSubmissionReference());
            attachments.put("xml", xmlArtifact.toString());
            evidence.put("xmlArtifactSha256", sha256(xmlArtifact));
            evidence.put("xmlArtifactBytes", fileSize(xmlArtifact));
            evidence.put("filingChannel", "FRC_GOAML_XML");
        }
        submission.setSubmittedData(evidence);
        submission.setAttachmentPaths(attachments);

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
     * Submit an approved Kenya filing to the FRC goAML transport.
     */
    @Transactional
    public RegulatorySubmissionDTO submitToFRC(Long reportId, Long userId, Long pspId) {
        logger.info("Submitting to FRC - report: {}, user: {}", reportId, userId);
        RegulatorySubmission submission = findOrCreateSubmission(reportId, REGULATOR_FRC, userId, pspId);
        return dispatchSubmission(submission, frcClient, REGULATOR_FRC, userId);
    }

    /**
     * File one exact approved submission. Report-based methods remain for
     * backwards-compatible clients, but new UI flows use this deterministic path.
     */
    @Transactional
    public RegulatorySubmissionDTO fileSubmission(Long submissionId, Long userId) {
        RegulatorySubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Submission not found: " + submissionId));
        pspIsolationService.validatePspAccess(submission.getPspId());
        String regulatorCode = normalizeRegulatoryBody(submission.getRegulatorCode());
        return dispatchSubmission(
                submission, clientFor(regulatorCode), regulatorCode, userId);
    }

    /**
     * OFAC maintains sanctions programs; it is not a SAR/CTR filing destination.
     */
    @Transactional
    public RegulatorySubmissionDTO submitToOFAC(Long reportId, Long userId, Long pspId) {
        throw new IllegalArgumentException(
                "OFAC is a sanctions-list authority and does not accept SAR/CTR filings");
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
        if (submission.getStatus() == SubmissionStatus.FILED
                && submission.getRegulatorReference() != null
                && !submission.getRegulatorReference().isBlank()) {
            logger.info("{} submission {} already filed (ref={}), returning existing record",
                    regulatorCode, submission.getSubmissionReference(), submission.getRegulatorReference());
            return convertToDTO(submission);
        }
        if (!submission.canFile()) {
            throw new IllegalStateException("Submission cannot be filed. Current status: " + submission.getStatus());
        }

        if (client == null) {
            recordAttempt(submission, regulatorCode,
                    String.valueOf(submission.getSubmittedData()), null, null,
                    "CLIENT_NOT_CONFIGURED", regulatorCode + " client not configured");
            return parkPending(submission, regulatorCode,
                    regulatorCode + " client not configured (regulators." + regulatorCode.toLowerCase() + ".enabled=false)");
        }

        try {
            SubmissionResult result = client.submit(submission);
            if (result == null) {
                throw new RegulatorTransportException(
                        regulatorCode + " returned no submission result",
                        null, "EMPTY_RESPONSE", null);
            }
            if (result.regulator() != null
                    && !regulatorCode.equalsIgnoreCase(result.regulator())) {
                throw new IllegalStateException("Regulator response identity mismatch");
            }
            String upstreamStatus = normalizeUpstreamStatus(result.status());
            if (isRejectedStatus(upstreamStatus)) {
                submission.setStatus(SubmissionStatus.REJECTED);
                submission.setFiledBy(null);
                submission.setFiledAt(null);
                submission.setRegulatorReference(blankToNull(result.submissionId()));
                submission.setFilingReceipt(null);
                submission.setRejectionReason(
                        regulatorCode + " rejected the filing with status " + upstreamStatus);
                RegulatorySubmission rejected = submissionRepository.save(submission);
                recordAttempt(rejected, regulatorCode,
                        String.valueOf(rejected.getSubmittedData()),
                        result.responseBody(),
                        result.httpStatus(),
                        upstreamStatus,
                        rejected.getRejectionReason());
                return convertToDTO(rejected);
            }
            if (!isAcceptedStatus(upstreamStatus)) {
                throw new RegulatorTransportException(
                        regulatorCode + " returned an unrecognized filing status: " + upstreamStatus,
                        result.httpStatus(),
                        upstreamStatus,
                        result.responseBody());
            }
            if (result.submissionId() == null || result.submissionId().isBlank()) {
                throw new RegulatorTransportException(
                        regulatorCode + " accepted the filing but returned no regulator reference",
                        result.httpStatus(),
                        upstreamStatus,
                        result.responseBody());
            }
            submission.setStatus(SubmissionStatus.FILED);
            submission.setFiledBy(userRepository.findById(userId).orElse(null));
            submission.setFiledAt(LocalDateTime.now());
            submission.setRegulatorReference(result.submissionId());
            submission.setFilingReceipt(generateFilingReceipt(
                    submission, regulatorCode, upstreamStatus));
            submission.setRejectionReason(null);
            RegulatorySubmission saved = submissionRepository.save(submission);
            recordAttempt(saved, regulatorCode,
                    String.valueOf(saved.getSubmittedData()),
                    result.responseBody() != null ? result.responseBody()
                            : "regulatorRef=" + result.submissionId() + " status=" + result.status(),
                    result.httpStatus() != null ? result.httpStatus() : 200,
                    upstreamStatus,
                    null);
            logger.info("{} submission completed: localRef={} regulatorRef={} status={}",
                    regulatorCode, saved.getSubmissionReference(), result.submissionId(), upstreamStatus);
            return convertToDTO(saved);
        } catch (RegulatorSubmissionDisabledException e) {
            recordAttempt(submission, regulatorCode,
                    String.valueOf(submission.getSubmittedData()), e.getMessage(), null,
                    "TRANSPORT_DISABLED", e.getMessage());
            return parkPending(submission, regulatorCode, e.getMessage());
        } catch (RegulatorTransportException e) {
            recordAttempt(submission, regulatorCode,
                    String.valueOf(submission.getSubmittedData()),
                    e.getResponseBody(),
                    e.getHttpStatus(),
                    e.getRegulatorStatus(),
                    e.getMessage());
            return parkPending(submission, regulatorCode,
                    "Transmission was not accepted and requires review or retry: " + e.getMessage());
        } catch (RuntimeException e) {
            recordAttempt(submission, regulatorCode,
                    String.valueOf(submission.getSubmittedData()), e.getMessage(), 500,
                    "TRANSMISSION_FAILED", e.getMessage());
            return parkPending(submission, regulatorCode,
                    "Transmission failed and requires retry: " + e.getMessage());
        }
    }

    /** Append the regulator request/response evidence in the filing transaction. */
    private void recordAttempt(RegulatorySubmission submission,
                               String regulatorCode,
                               String requestBody,
                               String responseBody,
                               Integer httpStatus,
                               String regulatorStatus,
                               String errorOrNull) {
        int nextAttempt = submissionAttemptRepository
                .findTopBySubmissionIdAndRegulatorOrderByAttemptNoDesc(submission.getId(), regulatorCode)
                .map(a -> a.getAttemptNo() == null ? 1 : a.getAttemptNo() + 1)
                .orElse(1);

        RegulatorySubmissionAttempt attempt = new RegulatorySubmissionAttempt();
        attempt.setSubmissionId(submission.getId());
        attempt.setRegulator(regulatorCode);
        attempt.setIdempotencyKey(transportIdempotencyKey(submission));
        attempt.setRequestBody(truncate(requestBody));
        attempt.setResponseBody(truncate(responseBody));
        attempt.setRequestSha256(sha256(requestBody));
        attempt.setResponseSha256(sha256(responseBody));
        attempt.setHttpStatus(httpStatus);
        attempt.setRegulatorStatus(regulatorStatus);
        attempt.setErrorMessage(errorOrNull);
        attempt.setAttemptNo(nextAttempt);
        submissionAttemptRepository.save(attempt);
        if (errorOrNull != null) {
            logger.debug("Logged regulatory submission attempt {} for {} (error: {})",
                    nextAttempt, regulatorCode, errorOrNull);
        }
    }

    private static String truncate(String body) {
        if (body == null) return null;
        if (body.length() <= REQUEST_BODY_MAX_BYTES) return body;
        return body.substring(0, REQUEST_BODY_MAX_BYTES) + TRUNCATED_MARKER;
    }

    private static String transportIdempotencyKey(RegulatorySubmission submission) {
        String identity = submission.getId() + ":" + submission.getSubmissionReference() + ":v1";
        return UUID.nameUUIDFromBytes(
                identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }

    private static String normalizeUpstreamStatus(String status) {
        if (status == null || status.isBlank()) return "UNKNOWN";
        return status.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    private static boolean isAcceptedStatus(String status) {
        return Set.of(
                "ACCEPTED",
                "ACKNOWLEDGED",
                "FILED",
                "PROCESSING",
                "QUEUED",
                "RECEIVED",
                "SUBMITTED",
                "SUCCESS",
                "SUCCESSFUL",
                "VALIDATED",
                "PENDING_VALIDATION").contains(status);
    }

    private static boolean isRejectedStatus(String status) {
        return Set.of(
                "DECLINED",
                "ERROR",
                "FAILED",
                "INVALID",
                "REJECTED",
                "VALIDATION_ERROR",
                "VALIDATION_FAILED").contains(status);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private RegulatorySubmissionDTO parkPending(RegulatorySubmission submission, String regulatorCode, String reason) {
        logger.warn("{} submission {} parked SUBMISSION_PENDING: {}",
                regulatorCode, submission.getSubmissionReference(), reason);
        submission.setStatus(SubmissionStatus.SUBMISSION_PENDING);
        submission.setRejectionReason(reason);
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
        
        // Return persisted status and regulator receipt. No upstream status is
        // invented when a regulator has not provisioned a status-query contract.
        
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
                if (user != null && submission.getPreparedBy() != null
                        && Objects.equals(user.getId(), submission.getPreparedBy().getId())) {
                    throw new IllegalStateException(
                            "The preparer cannot approve the same regulatory filing");
                }
                submission.setReviewedBy(user);
                submission.setReviewedAt(LocalDateTime.now());
                submission.setApprovedBy(user);
                submission.setApprovedAt(LocalDateTime.now());
                break;
            case FILED:
                throw new IllegalStateException(
                        "FILED can only be set by a successful regulator transport response");
            case REJECTED:
                if (!List.of(
                        SubmissionStatus.DRAFT,
                        SubmissionStatus.PENDING_REVIEW,
                        SubmissionStatus.APPROVED,
                        SubmissionStatus.SUBMISSION_PENDING).contains(submission.getStatus())) {
                    throw new IllegalStateException(
                            "Submission cannot be rejected from " + submission.getStatus());
                }
                submission.setRejectionReason("Rejected by user: " + (user != null ? user.getFullName() : "Unknown"));
                break;
            default:
                break;
        }
        
        submission.setStatus(newStatus);
        RegulatorySubmission saved = submissionRepository.save(submission);
        
        return convertToDTO(saved);
    }

    @Transactional
    public RegulatorySubmissionDTO rejectSubmission(
            Long submissionId, String reason, Long userId) {
        RegulatorySubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Submission not found: " + submissionId));
        pspIsolationService.validatePspAccess(submission.getPspId());
        if (!List.of(
                SubmissionStatus.DRAFT,
                SubmissionStatus.PENDING_REVIEW,
                SubmissionStatus.APPROVED,
                SubmissionStatus.SUBMISSION_PENDING).contains(submission.getStatus())) {
            throw new IllegalStateException(
                    "Submission cannot be rejected from " + submission.getStatus());
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A rejection reason is required");
        }
        User user = userRepository.findById(userId).orElse(null);
        submission.setStatus(SubmissionStatus.REJECTED);
        submission.setReviewedBy(user);
        submission.setReviewedAt(LocalDateTime.now());
        submission.setRejectionReason(reason.trim());
        return convertToDTO(submissionRepository.save(submission));
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
        amended.setExecutionId(original.getExecutionId());
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
        Map<String, Object> amendedEvidence = original.getSubmittedData() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(original.getSubmittedData());
        Map<String, String> amendedAttachments = original.getAttachmentPaths() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(original.getAttachmentPaths());
        if (REGULATOR_FRC.equals(original.getRegulatorCode())) {
            ReportExecution execution = reportExecutionRepository.findById(original.getExecutionId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Original filing execution no longer exists"));
            Path xmlArtifact = generateFrcArtifact(
                    original.getReport(), execution, amended.getSubmissionReference());
            amendedAttachments.put("xml", xmlArtifact.toString());
            amendedEvidence.put("xmlArtifactSha256", sha256(xmlArtifact));
            amendedEvidence.put("xmlArtifactBytes", fileSize(xmlArtifact));
        }
        amended.setSubmittedData(amendedEvidence);
        amended.setAttachmentPaths(amendedAttachments);
        
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

    private String normalizeRegulatoryBody(String regulatoryBody) {
        if (regulatoryBody == null || regulatoryBody.isBlank()) {
            throw new IllegalArgumentException("Regulatory body is required");
        }
        String normalized = regulatoryBody.trim().toUpperCase();
        if ("FIU".equals(normalized) || "KENYA_FRC".equals(normalized)) {
            return REGULATOR_FRC;
        }
        if (!List.of(REGULATOR_FINCEN, REGULATOR_FCA, REGULATOR_FRC).contains(normalized)) {
            throw new IllegalArgumentException("Unsupported filing destination: " + regulatoryBody);
        }
        return normalized;
    }

    private RegulatorSubmissionClient clientFor(String regulatorCode) {
        return switch (regulatorCode) {
            case REGULATOR_FINCEN -> fincenClient;
            case REGULATOR_FCA -> fcaClient;
            case REGULATOR_FRC -> frcClient;
            default -> throw new IllegalArgumentException(
                    "Unsupported filing destination: " + regulatorCode);
        };
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
            case REGULATOR_FINCEN -> "US";
            case REGULATOR_FCA -> "UK";
            case REGULATOR_FRC -> "KE";
            default -> "UNKNOWN";
        };
    }

    private LocalDate calculateFilingDeadline(String regulatoryBody, Report report, LocalDate periodEnd) {
        String reportCode = report.getReportCode() != null
                ? report.getReportCode().toUpperCase() : "";
        return switch (regulatoryBody.toUpperCase()) {
            case REGULATOR_FRC -> {
                if (reportCode.contains("CTR")) {
                    int days = Math.floorMod(
                            DayOfWeek.FRIDAY.getValue() - periodEnd.getDayOfWeek().getValue(), 7);
                    yield periodEnd.plusDays(days);
                }
                if (reportCode.contains("STR") || reportCode.contains("SAR")) {
                    yield periodEnd.plusDays(2);
                }
                yield periodEnd.plusDays(30);
            }
            case REGULATOR_FINCEN -> periodEnd.plusDays(30);
            case REGULATOR_FCA -> periodEnd.plusDays(45);
            default -> periodEnd.plusDays(30);
        };
    }

    private RegulatorySubmission findOrCreateSubmission(Long reportId, String regulator, Long userId, Long pspId) {
        Long effectivePspId = pspIsolationService.sanitizePspId(pspId);
        Optional<RegulatorySubmission> existing =
                submissionRepository.findFirstByReportIdAndPspIdAndRegulatorCodeOrderByCreatedAtDesc(
                        reportId, effectivePspId, regulator);
        if (existing.isPresent()) {
            return existing.get();
        }

        RegulatorySubmissionDTO dto = prepareSubmission(reportId, regulator, userId, effectivePspId);
        return submissionRepository.findById(dto.getId()).orElseThrow();
    }

    private Map<String, Object> buildSubmissionEvidence(ReportExecution execution) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("reportExecutionId", execution.getId());
        evidence.put("executionReference", execution.getExecutionId());
        evidence.put("executionCompletedAt", execution.getCompletedAt());
        evidence.put("recordCount", execution.getTotalRecords());
        evidence.put("parameters", execution.getParameters());
        evidence.put("filtersApplied", execution.getFiltersApplied());
        evidence.put("sourceContext", execution.getSourceContext());
        evidence.put("calculationSummary", execution.getCalculationSummary());
        evidence.put("recordLinks", execution.getRecordLinks());
        if (execution.getFilePath() != null && !execution.getFilePath().isBlank()) {
            Path artifact = Path.of(execution.getFilePath()).toAbsolutePath().normalize();
            evidence.put("sourceArtifact", artifact.toString());
            if (Files.isRegularFile(artifact) && Files.isReadable(artifact)) {
                evidence.put("sourceArtifactSha256", sha256(artifact));
                evidence.put("sourceArtifactBytes", fileSize(artifact));
            }
        }
        return evidence;
    }

    private Map<String, String> buildAttachmentPaths(ReportExecution execution) {
        Map<String, String> attachments = new LinkedHashMap<>();
        if (execution.getFilePath() != null && !execution.getFilePath().isBlank()) {
            attachments.put("primary",
                    Path.of(execution.getFilePath()).toAbsolutePath().normalize().toString());
        }
        return attachments;
    }

    private Path generateFrcArtifact(
            Report report,
            ReportExecution execution,
            String submissionReference) {
        String reportCode = report.getReportCode() != null
                ? report.getReportCode().toUpperCase() : "";
        List<com.posgateway.aml.entity.reporting.ReportResult> resultRows =
                reportResultRepository.findByExecutionIdOrderByRowNumberAsc(execution.getId());
        if (resultRows.isEmpty()) {
            throw new IllegalStateException(
                    "Completed execution has no immutable result rows; rerun the report before filing");
        }
        if (execution.getTotalRecords() != null
                && execution.getTotalRecords() != resultRows.size()) {
            throw new IllegalStateException(
                    "Stored result-row count does not match the completed execution");
        }

        Set<Long> transactionIds = new LinkedHashSet<>();
        List<Map<String, Object>> immutableRows = new java.util.ArrayList<>(resultRows.size());
        for (com.posgateway.aml.entity.reporting.ReportResult row : resultRows) {
            Long transactionId = transactionId(row.getData());
            if (transactionId == null) {
                throw new IllegalStateException(
                        "FRC filing row " + row.getRowNumber() + " has no transaction identifier");
            }
            if (!transactionIds.add(transactionId)) {
                throw new IllegalStateException(
                        "FRC filing contains duplicate transaction " + transactionId);
            }
            Long rowPspId = longValue(row.getData(), "psp_id", "pspId");
            if (execution.getPspId() != null && rowPspId != null
                    && !Objects.equals(execution.getPspId(), rowPspId)) {
                throw new SecurityException(
                        "Completed report contains a row outside its PSP scope");
            }
            immutableRows.add(new LinkedHashMap<>(row.getData()));
        }
        String xml = frcReportingService.generateTransactionReportFromRows(
                reportCode, submissionReference, immutableRows);
        try {
            Path directory = Path.of(System.getProperty("java.io.tmpdir"), "reports", "submissions")
                    .toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path artifact = directory.resolve(submissionReference + ".xml").normalize();
            if (!artifact.startsWith(directory)) {
                throw new IllegalStateException("Invalid FRC artifact path");
            }
            Files.writeString(artifact, xml);
            return artifact;
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Unable to persist FRC goAML XML artifact", failure);
        }
    }

    private Long transactionId(Map<String, Object> row) {
        if (row == null) return null;
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String normalized = entry.getKey() == null ? ""
                    : entry.getKey().replaceAll("[^A-Za-z0-9]", "").toLowerCase();
            if (!List.of("txnid", "transactionid").contains(normalized)
                    || entry.getValue() == null) {
                continue;
            }
            try {
                if (entry.getValue() instanceof Number number) {
                    return number.longValue();
                }
                return Long.valueOf(String.valueOf(entry.getValue()));
            } catch (NumberFormatException failure) {
                throw new IllegalStateException(
                        "Invalid transaction identifier in immutable report row", failure);
            }
        }
        return null;
    }

    private Long longValue(Map<String, Object> row, String... keys) {
        if (row == null) return null;
        for (String key : keys) {
            Object value = row.get(key);
            if (value == null) continue;
            try {
                return value instanceof Number number
                        ? number.longValue() : Long.valueOf(String.valueOf(value));
            } catch (NumberFormatException failure) {
                throw new IllegalStateException(
                        "Invalid numeric value in immutable report row: " + key, failure);
            }
        }
        return null;
    }

    private boolean matchesFrcReport(
            String reportCode,
            com.posgateway.aml.entity.TransactionEntity transaction) {
        if (transaction == null || !transaction.isCashTransaction()) return false;
        if (reportCode.contains("005") || reportCode.contains("STRUCTUR")) {
            return transaction.isSarRequired()
                    && transaction.getTriggeredRules() != null
                    && transaction.getTriggeredRules().contains("CASH_STRUCTURING");
        }
        if (reportCode.contains("CTR")) {
            return transaction.isCtrRequired();
        }
        return false;
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to hash report artifact", failure);
        }
    }

    private static String sha256(String value) {
        if (value == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to hash submission evidence", failure);
        }
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Unable to read report artifact size", failure);
        }
    }

    private String generateSubmissionReference(String regulatoryBody) {
        String prefix = regulatoryBody.substring(0, 3);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuid = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return prefix + "-" + timestamp + "-" + uuid;
    }

    private String generateFilingReceipt(
            RegulatorySubmission submission,
            String regulator,
            String upstreamStatus) {
        StringBuilder receipt = new StringBuilder();
        receipt.append("FILING RECEIPT\n");
        receipt.append("==============\n\n");
        receipt.append("Submission Reference: ").append(submission.getSubmissionReference()).append("\n");
        receipt.append("Regulator Reference: ").append(submission.getRegulatorReference()).append("\n");
        receipt.append("Regulator: ").append(regulator).append("\n");
        receipt.append("Filing Period: ").append(submission.getFilingPeriodStart()).append(" to ").append(submission.getFilingPeriodEnd()).append("\n");
        receipt.append("Filed At: ").append(submission.getFiledAt()).append("\n");
        receipt.append("Platform Status: ").append(submission.getStatus()).append("\n");
        receipt.append("Regulator Status: ").append(upstreamStatus).append("\n");
        receipt.append("\nThis receipt records the regulator transport acknowledgement.");
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
