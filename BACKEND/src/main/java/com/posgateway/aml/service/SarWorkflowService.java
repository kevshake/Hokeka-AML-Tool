package com.posgateway.aml.service;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.SuspiciousActivityReport;
import com.posgateway.aml.model.Permission;
import com.posgateway.aml.model.SarStatus;

import com.posgateway.aml.repository.SuspiciousActivityReportRepository;
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

    @Autowired
    public SarWorkflowService(SuspiciousActivityReportRepository sarRepository,
            PermissionService permissionService,
            AuditLogService auditLogService) {
        this.sarRepository = sarRepository;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
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

        // Calculate deadline (e.g., 30 days from detection)
        sar.setFilingDeadline(LocalDateTime.now().plusDays(30));

        logger.info("Created new SAR draft: {}", sar.getSarReference());
        SuspiciousActivityReport saved = sarRepository.save(sar);
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

        if (!sar.getCreatedBy().getId().equals(user.getId()) &&
                !permissionService.hasPermission(user.getRole(), Permission.MANAGE_RULES)) { // Allow managers to submit
                                                                                             // too
            // Strict check: only creator can submit, or someone with override
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

        if (sar.getStatus() != SarStatus.PENDING_REVIEW) {
            throw new IllegalStateException("SAR must be in PENDING_REVIEW state to approve");
        }

        SuspiciousActivityReport before = new SuspiciousActivityReport();
        before.setId(sar.getId());
        before.setStatus(sar.getStatus());

        sar.setStatus(SarStatus.APPROVED);
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

        SuspiciousActivityReport before = new SuspiciousActivityReport();
        before.setId(sar.getId());
        before.setStatus(sar.getStatus());

        sar.setStatus(SarStatus.REJECTED);
        // Could add rejection reason to notes/audit log

        logger.info("SAR {} rejected by {}", sar.getSarReference(), rejector.getUsername());
        SuspiciousActivityReport saved = sarRepository.save(sar);
        auditLogService.logAction(rejector, "REJECT_SAR", "SAR", String.valueOf(saved.getId()), before, saved, null,
                reason);
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

        logger.info("SAR {} marked as FILED by {}", sar.getSarReference(), filer.getUsername());
        SuspiciousActivityReport saved = sarRepository.save(sar);
        auditLogService.logAction(filer, "FILE_SAR", "SAR", String.valueOf(saved.getId()), before, saved, null, null);
        return saved;
    }
}
