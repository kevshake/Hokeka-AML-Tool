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
import java.util.Comparator;
import java.util.List;

@Service
public class WorkflowAutomationService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorkflowAutomationService.class);

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
     * Automate approval for low-risk merchants.
     */
    @Async
    @Transactional
    public void autoApproveLowRisk(Merchant merchant, RiskLevel riskLevel) {
        if (riskLevel == RiskLevel.LOW) {
            log.info("Auto-approving low-risk merchant: {}", merchant.getMerchantId());

            merchant.setStatus("ACTIVE");
            merchant.setUpdatedAt(LocalDateTime.now());
            merchantRepository.save(merchant);

            List<ComplianceCase> merchantCases = complianceCaseRepository.findByMerchantId(merchant.getMerchantId());
            LocalDateTime now = LocalDateTime.now();
            for (ComplianceCase c : merchantCases) {
                if (c.getStatus() == CaseStatus.NEW
                        || c.getStatus() == CaseStatus.IN_PROGRESS
                        || c.getStatus() == CaseStatus.ASSIGNED) {
                    c.setStatus(CaseStatus.CLOSED_CLEARED);
                    c.setResolution("Auto-approved: Low Risk merchant");
                    c.setResolvedAt(now);
                    c.setUpdatedAt(now);
                    complianceCaseRepository.save(c);
                    log.info("Closed case {} for auto-approved merchant {}", c.getId(), merchant.getMerchantId());
                }
            }

            notificationService.sendEmail(merchant.getContactEmail(), "Welcome to POS Gateway",
                    "Your account has been auto-approved.");
        }
        log.info("Auto-approving low-risk merchant: {}", merchant.getMerchantId());

        merchant.setStatus("ACTIVE");
        merchant.setUpdatedAt(LocalDateTime.now());
        merchantRepository.save(merchant);

        // Close any open onboarding compliance cases for this merchant. The
        // ComplianceCase entity exposes findByMerchantId — case_reference for
        // onboarding cases is "ONBOARD-{merchantId}" so we filter on either.
        List<ComplianceCase> cases = complianceCaseRepository.findByMerchantId(merchant.getMerchantId());
        int closed = 0;
        for (ComplianceCase c : cases) {
            com.posgateway.aml.model.CaseStatus s = c.getStatus();
            boolean isOpen = s != com.posgateway.aml.model.CaseStatus.CLOSED_CLEARED
                    && s != com.posgateway.aml.model.CaseStatus.CLOSED_SAR_FILED
                    && s != com.posgateway.aml.model.CaseStatus.CLOSED_BLOCKED
                    && s != com.posgateway.aml.model.CaseStatus.CLOSED_REJECTED;
            boolean isOnboarding = c.getCaseReference() != null
                    && c.getCaseReference().startsWith("ONBOARD-");
            if (isOpen && isOnboarding) {
                c.setStatus(com.posgateway.aml.model.CaseStatus.CLOSED_CLEARED);
                c.setResolution("Auto-approved — low risk");
                c.setResolvedAt(LocalDateTime.now());
                c.setUpdatedAt(LocalDateTime.now());
                complianceCaseRepository.save(c);
                closed++;
            }
        }
        if (closed > 0) {
            log.info("Closed {} onboarding case(s) for merchant {}", closed, merchant.getMerchantId());
        }

        notificationService.sendEmail(merchant.getContactEmail(), "Welcome to POS Gateway",
                "Your account has been auto-approved.");
    }

    /**
     * Auto-assign cases to the least-loaded user in the role required by priority.
     */
    @Async
    @Transactional
    public void autoAssignCase(ComplianceCase complianceCase) {
        if (complianceCase.getStatus() == CaseStatus.NEW && complianceCase.getAssignedTo() == null) {
            UserRole targetRole = determineAssigneeRole(complianceCase);
            var assignedUser = caseAssignmentService.assignCaseByWorkload(complianceCase, targetRole);

            if (assignedUser != null) {
                log.info("Auto-assigned case {} to {}", complianceCase.getId(), assignedUser.getUsername());
                notificationService.sendSystemAlert(assignedUser.getUsername(),
                        "New Case Assigned: " + complianceCase.getId());
            } else {
                log.warn("No eligible users available to auto-assign case {} for role {}",
                        complianceCase.getId(), targetRole);
                notificationService.sendSystemAlert("compliance-team",
                        "New Case Awaiting Assignment: " + complianceCase.getId());
            }
        }
        String roleName = isHighPriority(complianceCase) ? "COMPLIANCE_OFFICER" : "INVESTIGATOR";
        List<User> candidates = userRepository.findByRole_NameAndEnabled(roleName, true);
        if (candidates.isEmpty()) {
            // Fallback: anyone enabled in either role
            candidates = userRepository.findByRole_NameAndEnabled("INVESTIGATOR", true);
            if (candidates.isEmpty()) {
                candidates = userRepository.findByRole_NameAndEnabled("COMPLIANCE_OFFICER", true);
            }
        }
        if (candidates.isEmpty()) {
            log.warn("Auto-assignment: no eligible users in role={} for case {}", roleName, complianceCase.getId());
            return;
        }
        List<com.posgateway.aml.model.CaseStatus> openStatuses = List.of(
                com.posgateway.aml.model.CaseStatus.NEW,
                com.posgateway.aml.model.CaseStatus.ASSIGNED,
                com.posgateway.aml.model.CaseStatus.IN_PROGRESS,
                com.posgateway.aml.model.CaseStatus.PENDING_INFO,
                com.posgateway.aml.model.CaseStatus.PENDING_REVIEW,
                com.posgateway.aml.model.CaseStatus.ESCALATED,
                com.posgateway.aml.model.CaseStatus.REOPENED);
        User assignee = candidates.stream()
                .min(Comparator.comparingLong(u ->
                        complianceCaseRepository.countByAssignedTo_IdAndStatusIn(u.getId(), openStatuses)))
                .orElse(candidates.get(0));
        complianceCase.setAssignedTo(assignee);
        complianceCase.setStatus(com.posgateway.aml.model.CaseStatus.ASSIGNED);
        complianceCase.setUpdatedAt(LocalDateTime.now());
        complianceCaseRepository.save(complianceCase);
        log.info("Auto-assigned case {} to {} ({}+open caseload)",
                complianceCase.getId(), assignee.getUsername(), roleName);
        notificationService.sendSystemAlert(assignee.getUsername(),
                "Case " + complianceCase.getId() + " auto-assigned to you");
    }

    private boolean isHighPriority(ComplianceCase c) {
        CasePriority p = c.getPriority();
        return p == CasePriority.CRITICAL || p == CasePriority.HIGH;
    }

    private UserRole determineAssigneeRole(ComplianceCase c) {
        if (c.getPriority() == CasePriority.CRITICAL || c.getPriority() == CasePriority.HIGH) {
            return UserRole.SENIOR_ANALYST;
        }
        return UserRole.ANALYST;
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
            if (!Boolean.TRUE.equals(c.getEscalated())) {
                log.warn("Escalating overdue case: {}", c.getId());

                c.setEscalated(true);
                c.setStatus(CaseStatus.ESCALATED);
                c.setPriority(CasePriority.CRITICAL);
                c.setUpdatedAt(now);
                complianceCaseRepository.save(c);

                notificationService.sendAlert("Case " + c.getId() + " escalated due to breach of SLA", "HIGH");
            }
        }
    }
}
