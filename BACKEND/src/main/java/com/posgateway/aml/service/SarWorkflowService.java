package com.posgateway.aml.service;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.model.Permission;
import com.posgateway.aml.model.SarStatus;

import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.service.compliance.ComplianceCalendarService;
import com.posgateway.aml.service.compliance.RegulatoryDeadlineService;
import com.posgateway.aml.entity.filing.LegalHoldWorkflow;
import com.posgateway.aml.model.TransactionReference;
import com.posgateway.aml.service.legalhold.LegalHoldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * SAR Workflow Service
 * Manages the lifecycle of Suspicious Activity Reports
 */
@Service
@SuppressWarnings("null") // Repository methods return Optional, saved entities are non-null
public class SarWorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(SarWorkflowService.class);

    private final SuspiciousActivityReportRepository sarRepository;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;
    private final RegulatoryDeadlineService regulatoryDeadlineService;
    private final ComplianceCalendarService complianceCalendarService;
    private final com.posgateway.aml.service.compliance.ComplianceEntityLinkService complianceEntityLinkService;
    /** Legal holds on the Edge for the transactions of a drafted SAR (R9). */
    private final LegalHoldService legalHoldService;

    @Autowired
    public SarWorkflowService(SuspiciousActivityReportRepository sarRepository,
            PermissionService permissionService,
            AuditLogService auditLogService,
            RegulatoryDeadlineService regulatoryDeadlineService,
            ComplianceCalendarService complianceCalendarService,
            com.posgateway.aml.service.compliance.ComplianceEntityLinkService complianceEntityLinkService,
            LegalHoldService legalHoldService) {
        this.sarRepository = sarRepository;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
        this.regulatoryDeadlineService = regulatoryDeadlineService;
        this.complianceCalendarService = complianceCalendarService;
        this.complianceEntityLinkService = complianceEntityLinkService;
        this.legalHoldService = legalHoldService;
    }

    private static final DateTimeFormatter SAR_REF_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Create a new SAR draft
     */
    @Transactional
    public SuspiciousActivityReport createSarDraft(SuspiciousActivityReport sar, User creator) {
        if (!permissionService.hasPermission(creator.getRole(), Permission.CREATE_SAR)) {
            throw new SecurityException("User does not have permission to create SARs");
        }

        if (sar.getSarReference() == null || sar.getSarReference().isBlank()) {
            sar.setSarReference("SAR-" + LocalDateTime.now().format(SAR_REF_FMT));
        }

        sar.setCreatedBy(creator);
        sar.setStatus(SarStatus.DRAFT);

        Long pspId = userPspId(creator);
        sar.setPspId(pspId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime caseSuspicion = sar.getComplianceCase() != null
                ? sar.getComplianceCase().getSuspicionFormedAt()
                : null;
        LocalDateTime suspicionAroseAt = sar.getSuspicionAroseAt() != null
                ? sar.getSuspicionAroseAt()
                : (caseSuspicion != null ? caseSuspicion : now);
        if (suspicionAroseAt.isAfter(now.plusMinutes(1))) {
            throw new IllegalArgumentException("Suspicion arose timestamp cannot be in the future");
        }
        sar.setSuspicionAroseAt(suspicionAroseAt);
        if (sar.getSuspicionTimestampSource() == null || sar.getSuspicionTimestampSource().isBlank()) {
            if (sar.getSuspicionAroseAt().equals(now)) {
                sar.setSuspicionTimestampSource("REPORT_CREATED");
            } else if (caseSuspicion != null && sar.getSuspicionAroseAt().equals(caseSuspicion)) {
                sar.setSuspicionTimestampSource("CASE_SUSPICION_FORMED");
            } else {
                sar.setSuspicionTimestampSource("OPERATOR_RECORDED");
            }
        }
        RegulatoryDeadlineService.DeadlineCalculation deadline = regulatoryDeadlineService.calculate(
                RegulatoryDeadlineService.SAR_STR, sar.getJurisdiction(), pspId, suspicionAroseAt);
        sar.setFilingDeadline(deadline.deadline());
        sar.setDeadlinePolicyId(deadline.policyId());
        sar.setDeadlinePolicyCode(deadline.policyCode());
        sar.setDeadlineCalculatedAt(now);
        sar.setDeadlineWarningAt(deadline.deadline().minusHours(deadline.warningHours()));

        // The draft references the case's transactions by ID only (plan conflict 7); their details
        // are rendered from the Edge when viewed and stored only in the filed document (R9).
        if (sar.getComplianceCase() != null && sar.getTransactionRefs().isEmpty()) {
            sar.setTransactionReferences(
                    complianceEntityLinkService.caseTransactionReferences(sar.getComplianceCase(), pspId));
        }

        logger.info("Created new SAR draft: {}", sar.getSarReference());
        SuspiciousActivityReport saved = sarRepository.save(sar);
        complianceCalendarService.upsertSourceDeadline(
                "SAR_FILING", saved.getFilingDeadline(),
                saved.getSarReference() + " filing deadline (" + saved.getDeadlinePolicyCode() + ")",
                saved.getJurisdiction(), saved.getPspId(), "SAR", saved.getId());
        auditLogService.logAction(creator, "CREATE_SAR", "SAR", String.valueOf(saved.getId()), null, saved, null, null);
        if (saved.getComplianceCase() != null) {
            complianceEntityLinkService.linkSarToCaseAlerts(saved.getId(), saved.getComplianceCase().getId(), null);
        }
        placeSarHolds(saved, creator);
        return saved;
    }

    /**
     * Residency contract 7.10 / 8.3: a drafted SAR places a legal hold (reasonCode SAR) on the Edge for
     * the decision records it references, so the PSP's Edge Ledger keeps them past retention. The
     * hold rows are written in this transaction and the notifies go out after it commits.
     */
    private void placeSarHolds(SuspiciousActivityReport sar, User actor) {
        if (sar.getPspId() == null || sar.getPspId() <= 0) {
            return;
        }
        java.util.List<java.util.UUID> ids = sar.getTransactionReferences().stream()
                .map(TransactionReference::edgeAssessmentId).filter(java.util.Objects::nonNull).toList();
        legalHoldService.ensureCoverage(sar.getPspId(), LegalHoldWorkflow.Type.SAR, sar.getId(), "SAR",
                sar.getSarReference(), ids, actor != null && actor.getId() != null ? "u-" + actor.getId() : null);
    }

    /**
     * Submit SAR for review
     */
    @Transactional
    public SuspiciousActivityReport submitForReview(Long sarId, User user) {
        SuspiciousActivityReport sar = sarRepository.findById(sarId)
                .orElseThrow(() -> new IllegalArgumentException("SAR not found"));
        assertPspAccess(sar, user);
        if (!permissionService.hasPermission(user.getRole(), Permission.CREATE_SAR)) {
            throw new SecurityException("User does not have permission to submit SARs");
        }
        if (sar.getCreatedBy() == null || !sar.getCreatedBy().getId().equals(user.getId())) {
            throw new SecurityException("Only the SAR creator may submit it for review");
        }
        if (sar.getStatus() != SarStatus.DRAFT && sar.getStatus() != SarStatus.REJECTED) {
            throw new IllegalStateException("Only DRAFT or REJECTED SARs may be submitted for review");
        }

        SuspiciousActivityReport before = new SuspiciousActivityReport();
        before.setId(sar.getId());
        before.setStatus(sar.getStatus());

        sar.setStatus(SarStatus.PENDING_REVIEW);
        logger.info("Submitted SAR {} for review", sar.getSarReference());
        SuspiciousActivityReport saved = sarRepository.save(sar);
        auditLogService.logAction(user, "SUBMIT_SAR", "SAR", String.valueOf(saved.getId()), before, saved, null, null);
        return saved;
    }

    /**
     * Approve SAR (Compliance Officer / MLRO)
     */
    @Transactional
    public SuspiciousActivityReport approveSar(Long sarId, User approver) {
        if (!permissionService.hasPermission(approver.getRole(), Permission.APPROVE_SAR)) {
            throw new SecurityException("User does not have permission to approve SARs");
        }

        SuspiciousActivityReport sar = sarRepository.findById(sarId)
                .orElseThrow(() -> new IllegalArgumentException("SAR not found"));
        assertPspAccess(sar, approver);

        if (sar.getStatus() != SarStatus.PENDING_REVIEW) {
            throw new IllegalStateException("SAR must be in PENDING_REVIEW state to approve");
        }
        if (sar.getCreatedBy() != null && sar.getCreatedBy().getId().equals(approver.getId())) {
            throw new SecurityException("SAR creator cannot approve their own report");
        }

        SuspiciousActivityReport before = new SuspiciousActivityReport();
        before.setId(sar.getId());
        before.setStatus(sar.getStatus());

        sar.setStatus(SarStatus.APPROVED);
        sar.setReviewedBy(approver);
        sar.setReviewedAt(LocalDateTime.now());
        sar.setReviewNotes("Approved for regulatory filing");
        sar.setApprovedBy(approver);
        sar.setApprovedAt(LocalDateTime.now());

        logger.info("SAR {} approved by {}", sar.getSarReference(), approver.getUsername());
        SuspiciousActivityReport saved = sarRepository.save(sar);
        auditLogService.logAction(approver, "APPROVE_SAR", "SAR", String.valueOf(saved.getId()), before, saved, null,
                null);
        return saved;
    }

    /**
     * Reject SAR
     */
    @Transactional
    public SuspiciousActivityReport rejectSar(Long sarId, User rejector, String reason) {
        if (!permissionService.hasPermission(rejector.getRole(), Permission.REJECT_SAR)) {
            throw new SecurityException("User does not have permission to reject SARs");
        }

        SuspiciousActivityReport sar = sarRepository.findById(sarId)
                .orElseThrow(() -> new IllegalArgumentException("SAR not found"));
        assertPspAccess(sar, rejector);
        if (sar.getStatus() != SarStatus.PENDING_REVIEW) {
            throw new IllegalStateException("SAR must be in PENDING_REVIEW state to reject");
        }
        if (sar.getCreatedBy() != null && sar.getCreatedBy().getId().equals(rejector.getId())) {
            throw new SecurityException("SAR creator cannot reject their own report");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A rejection reason is required");
        }

        SuspiciousActivityReport before = new SuspiciousActivityReport();
        before.setId(sar.getId());
        before.setStatus(sar.getStatus());

        sar.setStatus(SarStatus.REJECTED);
        sar.setReviewedBy(rejector);
        sar.setReviewedAt(LocalDateTime.now());
        sar.setReviewNotes(reason.trim());

        logger.info("SAR {} rejected by {}", sar.getSarReference(), rejector.getUsername());
        SuspiciousActivityReport saved = sarRepository.save(sar);
        auditLogService.logAction(rejector, "REJECT_SAR", "SAR", String.valueOf(saved.getId()), before, saved, null,
                reason.trim());
        return saved;
    }

    /**
     * Records the filing of an APPROVED SAR (maker-checker already done: the approver was not the
     * creator). Called only by the R9 filing service, inside the transaction that stores the filed
     * document and with the SAR row locked, so a SAR never becomes FILED without its filed document.
     *
     * @param sar             the locked SAR
     * @param filingReference the regulator's reference, if known at filing time
     * @param filedAt         the filing time (the filed document's too)
     */
    @Transactional
    public SuspiciousActivityReport recordFiled(SuspiciousActivityReport sar, String filingReference, User filer,
            LocalDateTime filedAt) {
        if (!permissionService.hasPermission(filer.getRole(), Permission.FILE_SAR)) {
            throw new SecurityException("User does not have permission to file SARs");
        }
        assertPspAccess(sar, filer);

        if (sar.getStatus() != SarStatus.APPROVED) {
            throw new IllegalStateException("SAR must be APPROVED before filing");
        }

        SuspiciousActivityReport before = new SuspiciousActivityReport();
        before.setId(sar.getId());
        before.setStatus(sar.getStatus());

        sar.setStatus(SarStatus.FILED);
        sar.setFiledBy(filer);
        sar.setFiledAt(filedAt);
        sar.setFilingReferenceNumber(filingReference);
        sar.setDeadlineBreached(sar.getFilingDeadline() != null
                && sar.getFiledAt().isAfter(sar.getFilingDeadline()));

        logger.info("SAR {} marked as FILED by {}", sar.getSarReference(), filer.getUsername());
        SuspiciousActivityReport saved = sarRepository.save(sar);
        complianceCalendarService.completeSourceDeadline("SAR", saved.getId());
        auditLogService.logAction(filer, "FILE_SAR", "SAR", String.valueOf(saved.getId()), before, saved, null, null);
        return saved;
    }

    private void assertPspAccess(SuspiciousActivityReport sar, User user) {
        Long userPspId = userPspId(user);
        if (userPspId == null) {
            String role = user.getRole() != null ? user.getRole().getName() : null;
            if (role != null && ("SUPER_ADMIN".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role))) {
                return;
            }
            if (sar.getPspId() == null) return;
            throw new SecurityException("User has no PSP access to this SAR");
        }
        if (sar.getPspId() == null || !userPspId.equals(sar.getPspId())) {
            throw new SecurityException("SAR belongs to a different PSP");
        }
    }

    private Long userPspId(User user) {
        return user != null && user.getPsp() != null ? user.getPsp().getPspId() : null;
    }
}
