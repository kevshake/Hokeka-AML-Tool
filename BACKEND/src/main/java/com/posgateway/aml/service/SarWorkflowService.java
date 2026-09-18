package com.posgateway.aml.service;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.model.Permission;
import com.posgateway.aml.model.SarStatus;

import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
import com.posgateway.aml.service.compliance.ComplianceCalendarService;
import com.posgateway.aml.service.compliance.RegulatoryDeadlineService;
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

    @Autowired
    public SarWorkflowService(SuspiciousActivityReportRepository sarRepository,
            PermissionService permissionService,
            AuditLogService auditLogService,
            RegulatoryDeadlineService regulatoryDeadlineService,
            ComplianceCalendarService complianceCalendarService) {
        this.sarRepository = sarRepository;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
        this.regulatoryDeadlineService = regulatoryDeadlineService;
        this.complianceCalendarService = complianceCalendarService;
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
        LocalDateTime suspicionAroseAt = sar.getSuspicionAroseAt() != null
                ? sar.getSuspicionAroseAt()
                : now;
        if (suspicionAroseAt.isAfter(now.plusMinutes(1))) {
            throw new IllegalArgumentException("Suspicion arose timestamp cannot be in the future");
        }
        sar.setSuspicionAroseAt(suspicionAroseAt);
        if (sar.getSuspicionTimestampSource() == null || sar.getSuspicionTimestampSource().isBlank()) {
            sar.setSuspicionTimestampSource(sar.getSuspicionAroseAt().equals(now)
                    ? "REPORT_CREATED" : "OPERATOR_RECORDED");
        }
        RegulatoryDeadlineService.DeadlineCalculation deadline = regulatoryDeadlineService.calculate(
                RegulatoryDeadlineService.SAR_STR, sar.getJurisdiction(), pspId, suspicionAroseAt);
        sar.setFilingDeadline(deadline.deadline());
        sar.setDeadlinePolicyId(deadline.policyId());
        sar.setDeadlinePolicyCode(deadline.policyCode());
        sar.setDeadlineCalculatedAt(now);
        sar.setDeadlineWarningAt(deadline.deadline().minusHours(deadline.warningHours()));

        logger.info("Created new SAR draft: {}", sar.getSarReference());
        SuspiciousActivityReport saved = sarRepository.save(sar);
        complianceCalendarService.upsertSourceDeadline(
                "SAR_FILING", saved.getFilingDeadline(),
                saved.getSarReference() + " filing deadline (" + saved.getDeadlinePolicyCode() + ")",
                saved.getJurisdiction(), saved.getPspId(), "SAR", saved.getId());
        auditLogService.logAction(creator, "CREATE_SAR", "SAR", String.valueOf(saved.getId()), null, saved, null, null);
        return saved;
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
     * Mark SAR as Filed
     */
    @Transactional
    public SuspiciousActivityReport markAsFiled(Long sarId, String filingReference, User filer) {
        if (!permissionService.hasPermission(filer.getRole(), Permission.FILE_SAR)) {
            throw new SecurityException("User does not have permission to file SARs");
        }

        SuspiciousActivityReport sar = sarRepository.findById(sarId)
                .orElseThrow(() -> new IllegalArgumentException("SAR not found"));
        assertPspAccess(sar, filer);

        if (sar.getStatus() != SarStatus.APPROVED) {
            throw new IllegalStateException("SAR must be APPROVED before filing");
        }

        SuspiciousActivityReport before = new SuspiciousActivityReport();
        before.setId(sar.getId());
        before.setStatus(sar.getStatus());

        sar.setStatus(SarStatus.FILED);
        sar.setFiledBy(filer);
        sar.setFiledAt(LocalDateTime.now());
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
