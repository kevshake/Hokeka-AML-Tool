package com.posgateway.aml.service;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.model.CasePriority;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.model.Permission;

import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Case Workflow Service
 * Manages the lifecycle and transitions of compliance cases
 */
@Service
@SuppressWarnings("null") // Repository methods return Optional, saved entities are non-null
public class CaseWorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(CaseWorkflowService.class);

    private final ComplianceCaseRepository caseRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;

    @Autowired
    public CaseWorkflowService(ComplianceCaseRepository caseRepository,
            UserRepository userRepository,
            PermissionService permissionService,
            AuditLogService auditLogService) {
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
    }

    private static final DateTimeFormatter CASE_REF_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Create a new compliance case
     */
    @Transactional
    public ComplianceCase createCase(String caseReference, String description, CasePriority priority, User creator) {
        if (!permissionService.hasPermission(creator.getRole(), Permission.CREATE_CASES)) {
            throw new SecurityException("User does not have permission to create cases");
        }

        String ref = (caseReference != null && !caseReference.isBlank())
                ? caseReference
                : "CASE-" + LocalDateTime.now().format(CASE_REF_FMT);

        ComplianceCase newCase = ComplianceCase.builder()
                .caseReference(ref)
                .description(description)
                .status(CaseStatus.NEW)
                .priority(priority != null ? priority : CasePriority.MEDIUM)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .daysOpen(0)
                .build();

        // Calculate SLA deadline based on priority
        newCase.setSlaDeadline(calculateSlaDeadline(newCase.getPriority()));

        logger.info("Created new compliance case: {}", ref);
        ComplianceCase saved = caseRepository.save(newCase);
        auditLogService.logAction(creator, "CREATE_CASE", "CASE", String.valueOf(saved.getId()), null, saved, null,
                null);
        return saved;
    }

    /**
     * Assign a case to a user
     */
    @Transactional
    public ComplianceCase assignCase(Long caseId, Long assigneeId, User assigner) {
        if (!permissionService.hasPermission(assigner.getRole(), Permission.ASSIGN_CASES)) {
            throw new SecurityException("User does not have permission to assign cases");
        }

        java.util.Objects.requireNonNull(caseId, "Case ID cannot be null");
        ComplianceCase complianceCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found"));

        java.util.Objects.requireNonNull(assigneeId, "Assignee ID cannot be null");
        User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new IllegalArgumentException("Assignee user not found"));

        ComplianceCase before = ComplianceCase.builder()
                .id(complianceCase.getId())
                .assignedTo(complianceCase.getAssignedTo())
                .status(complianceCase.getStatus())
                .build();

        complianceCase.setAssignedTo(assignee);
        complianceCase.setAssignedBy(assigner.getId());
        complianceCase.setAssignedAt(LocalDateTime.now());

        // Update status if it was NEW
        if (complianceCase.getStatus() == CaseStatus.NEW) {
            complianceCase.setStatus(CaseStatus.ASSIGNED);
        }

        logger.info("Assigned case {} to user {}", complianceCase.getCaseReference(), assignee.getUsername());
        ComplianceCase saved = caseRepository.save(complianceCase);
        auditLogService.logAction(assigner, "ASSIGN_CASE", "CASE", String.valueOf(saved.getId()), before, saved, null,
                null);
        return saved;
    }

    /**
     * Update case status
     */
    @Transactional
    public ComplianceCase updateStatus(Long caseId, CaseStatus newStatus, User user) {
        ComplianceCase complianceCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found"));

        // Validate transition
        validateStatusTransition(complianceCase.getStatus(), newStatus);

        // Check permissions based on transition
        checkStatusTransitionPermissions(user, newStatus);

        ComplianceCase before = ComplianceCase.builder()
                .id(complianceCase.getId())
                .status(complianceCase.getStatus())
                .build();

        complianceCase.setStatus(newStatus);

        // Handle specific status logic
        if (newStatus == CaseStatus.CLOSED_CLEARED ||
                newStatus == CaseStatus.CLOSED_SAR_FILED ||
                newStatus == CaseStatus.CLOSED_BLOCKED) {
            complianceCase.setResolvedAt(LocalDateTime.now());
            complianceCase.setResolvedBy(user.getId());
        }

        logger.info("Updated case {} status to {}", complianceCase.getCaseReference(), newStatus);
        ComplianceCase saved = caseRepository.save(complianceCase);
        auditLogService.logAction(user, "UPDATE_CASE_STATUS", "CASE", String.valueOf(saved.getId()), before, saved,
                null, null);
        return saved;
    }

    /**
     * Escalate a case
     */
    @Transactional
    public ComplianceCase escalateCase(Long caseId, Long escalatedToUserId, String reason, User user) {
        if (!permissionService.hasPermission(user.getRole(), Permission.ESCALATE_CASES)) {
            throw new SecurityException("User does not have permission to escalate cases");
        }

        ComplianceCase complianceCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found"));

        ComplianceCase before = ComplianceCase.builder()
                .id(complianceCase.getId())
                .status(complianceCase.getStatus())
                .escalated(complianceCase.getEscalated())
                .escalatedTo(complianceCase.getEscalatedTo())
                .build();

        complianceCase.setEscalated(true);
        complianceCase.setEscalatedTo(escalatedToUserId);
        complianceCase.setEscalationReason(reason);
        complianceCase.setEscalatedAt(LocalDateTime.now());
        complianceCase.setStatus(CaseStatus.ESCALATED);

        logger.info("Escalated case {} to user {}", complianceCase.getCaseReference(), escalatedToUserId);
        ComplianceCase saved = caseRepository.save(complianceCase);
        auditLogService.logAction(user, "ESCALATE_CASE", "CASE", String.valueOf(saved.getId()), before, saved, null,
                reason);
        return saved;
    }

    private LocalDateTime calculateSlaDeadline(CasePriority priority) {
        LocalDateTime now = LocalDateTime.now();
        switch (priority) {
            case CRITICAL:
                return now.plusHours(4);
            case HIGH:
                return now.plusHours(24);
            case MEDIUM:
                return now.plusDays(3);
            case LOW:
                return now.plusDays(7);
            default:
                return now.plusDays(5);
        }
    }

    private void validateStatusTransition(CaseStatus current, CaseStatus next) {
        // Basic state machine validation
        if (current == next)
            return;

        if (isClosed(current) && next != CaseStatus.REOPENED) {
            throw new IllegalStateException("Cannot transition from closed state unless reopening");
        }

        // Add more complex validation rules as needed
    }

    private boolean isClosed(CaseStatus status) {
        return status == CaseStatus.CLOSED_CLEARED ||
                status == CaseStatus.CLOSED_SAR_FILED ||
                status == CaseStatus.CLOSED_BLOCKED;
    }

    private void checkStatusTransitionPermissions(User user, CaseStatus next) {
        if (isClosed(next)) {
            if (!permissionService.hasPermission(user.getRole(), Permission.CLOSE_CASES)) {
                throw new SecurityException("User does not have permission to close cases");
            }
        }

        if (next == CaseStatus.REOPENED) {
            if (!permissionService.hasPermission(user.getRole(), Permission.REOPEN_CASES)) {
                throw new SecurityException("User does not have permission to reopen cases");
            }
        }
    }
}
