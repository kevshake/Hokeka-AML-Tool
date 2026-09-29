package com.posgateway.aml.service.workflow;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.CasePriority;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.model.RiskLevel;
import com.posgateway.aml.model.UserRole;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.service.case_management.CaseAssignmentService;
import com.posgateway.aml.service.notification.NotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WorkflowAutomationService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorkflowAutomationService.class);

    private static final List<UserRole> ASSIGNMENT_CHAIN = List.of(
            UserRole.ANALYST, UserRole.COMPLIANCE_OFFICER, UserRole.MLRO);

    private final MerchantRepository merchantRepository;
    private final ComplianceCaseRepository complianceCaseRepository;
    private final CaseAssignmentService caseAssignmentService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public WorkflowAutomationService(MerchantRepository merchantRepository,
            ComplianceCaseRepository complianceCaseRepository,
            CaseAssignmentService caseAssignmentService,
            NotificationService notificationService,
            UserRepository userRepository) {
        this.merchantRepository = merchantRepository;
        this.complianceCaseRepository = complianceCaseRepository;
        this.caseAssignmentService = caseAssignmentService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    /**
     * Activate and welcome only LOW-risk merchants. APPROVE + non-LOW is a no-op.
     * Only ONBOARD-* cases are closed; investigation cases stay open.
     */
    @Async
    @Transactional
    public void autoApproveLowRisk(Merchant merchant, RiskLevel riskLevel) {
        if (riskLevel != RiskLevel.LOW) {
            return;
        }
        log.info("Auto-approving low-risk merchant: {}", merchant.getMerchantId());

        merchant.setStatus("ACTIVE");
        merchant.setUpdatedAt(LocalDateTime.now());
        merchantRepository.save(merchant);

        List<ComplianceCase> cases = complianceCaseRepository.findByMerchantId(merchant.getMerchantId());
        int closed = 0;
        LocalDateTime now = LocalDateTime.now();
        for (ComplianceCase c : cases) {
            if (!isOpen(c) || !isOnboardingCase(c)) {
                continue;
            }
            c.setStatus(CaseStatus.CLOSED_CLEARED);
            c.setResolution("Auto-approved: Low Risk merchant");
            c.setResolvedAt(now);
            c.setUpdatedAt(now);
            complianceCaseRepository.save(c);
            closed++;
        }
        if (closed > 0) {
            log.info("Closed {} onboarding case(s) for merchant {}", closed, merchant.getMerchantId());
        }

        notificationService.sendEmail(merchant.getContactEmail(), "Welcome to POS Gateway",
                "Your account has been auto-approved.");
    }

    /**
     * Auto-assign an unassigned NEW case once, using CaseAssignmentService workload
     * balancing and the ANALYST → COMPLIANCE_OFFICER → MLRO role policy.
     */
    @Async
    @Transactional
    public void autoAssignCase(ComplianceCase complianceCase) {
        if (complianceCase.getAssignedTo() != null) {
            return;
        }
        if (complianceCase.getStatus() != CaseStatus.NEW) {
            return;
        }

        UserRole startRole = determineAssigneeRole(complianceCase);
        User assignedUser = assignAlongHierarchy(complianceCase, startRole);
        if (assignedUser != null) {
            log.info("Auto-assigned case {} to {}", complianceCase.getId(), assignedUser.getUsername());
            notificationService.sendSystemAlert(assignedUser.getUsername(),
                    "New Case Assigned: " + complianceCase.getId());
        } else {
            log.warn("No eligible users available to auto-assign case {} starting at role {}",
                    complianceCase.getId(), startRole);
            notificationService.sendSystemAlert("compliance-team",
                    "New Case Awaiting Assignment: " + complianceCase.getId());
        }
    }

    private User assignAlongHierarchy(ComplianceCase complianceCase, UserRole startRole) {
        int start = ASSIGNMENT_CHAIN.indexOf(startRole);
        if (start < 0) {
            start = 0;
        }
        for (int i = start; i < ASSIGNMENT_CHAIN.size(); i++) {
            User assigned = caseAssignmentService.assignCaseByWorkload(complianceCase, ASSIGNMENT_CHAIN.get(i));
            if (assigned != null) {
                return assigned;
            }
        }
        return null;
    }

    private UserRole determineAssigneeRole(ComplianceCase c) {
        if (c.getPriority() == CasePriority.CRITICAL || c.getPriority() == CasePriority.HIGH) {
            return UserRole.COMPLIANCE_OFFICER;
        }
        return UserRole.ANALYST;
    }

    private static boolean isOpen(ComplianceCase c) {
        CaseStatus s = c.getStatus();
        return s != CaseStatus.CLOSED_CLEARED
                && s != CaseStatus.CLOSED_SAR_FILED
                && s != CaseStatus.CLOSED_BLOCKED
                && s != CaseStatus.CLOSED_REJECTED;
    }

    private static boolean isOnboardingCase(ComplianceCase c) {
        return c.getCaseReference() != null && c.getCaseReference().startsWith("ONBOARD-");
    }

    /**
     * Scheduled Job: Escalate overdue cases. Runs every hour.
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void escalateOverdueCases() {
        log.info("Running scheduled job: Escalate Overdue Cases");

        LocalDateTime now = LocalDateTime.now();
        List<ComplianceCase> overdueCases = complianceCaseRepository.findBySlaDeadlineBeforeAndStatusNot(
                now, CaseStatus.CLOSED_CLEARED);

        for (ComplianceCase c : overdueCases) {
            if (Boolean.TRUE.equals(c.getEscalated()) || !isOpen(c)) {
                continue;
            }
            log.warn("Escalating overdue case: {}", c.getId());

            User assignedUser = c.getId() == null ? null : assignAlongHierarchy(c, UserRole.COMPLIANCE_OFFICER);
            ComplianceCase persisted = c.getId() == null
                    ? c
                    : complianceCaseRepository.findById(c.getId()).orElse(c);
            persisted.setEscalated(true);
            persisted.setStatus(CaseStatus.ESCALATED);
            persisted.setPriority(CasePriority.CRITICAL);
            persisted.setEscalatedAt(now);
            persisted.setEscalationReason("SLA deadline breached");
            if (assignedUser != null) {
                persisted.setEscalatedTo(assignedUser.getId());
            }
            persisted.setUpdatedAt(now);
            if (isMlro(assignedUser) && persisted.getMlroNotifiedAt() == null) {
                persisted.setMlroNotifiedAt(now);
            }
            complianceCaseRepository.save(persisted);

            notificationService.sendAlert("Case " + persisted.getId() + " escalated due to breach of SLA", "HIGH");
            if (assignedUser != null) {
                notificationService.sendSystemAlert(assignedUser.getUsername(),
                        "SLA Escalation: " + persisted.getId());
            }
        }
    }

    private static boolean isMlro(User user) {
        return user != null && user.getRole() != null
                && "MLRO".equalsIgnoreCase(user.getRole().getName());
    }
}
