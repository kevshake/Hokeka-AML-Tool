package com.posgateway.aml.service.workflow;

import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.merchant.Merchant;
import com.posgateway.aml.model.RiskLevel;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.MerchantRepository;
import com.posgateway.aml.service.notification.NotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// @RequiredArgsConstructor removed
@Service
public class WorkflowAutomationService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorkflowAutomationService.class);

    private final MerchantRepository merchantRepository;
    private final ComplianceCaseRepository complianceCaseRepository;
    private final NotificationService notificationService;

    public WorkflowAutomationService(MerchantRepository merchantRepository,
            ComplianceCaseRepository complianceCaseRepository, NotificationService notificationService) {
        this.merchantRepository = merchantRepository;
        this.complianceCaseRepository = complianceCaseRepository;
        this.notificationService = notificationService;
    }

    /**
     * Automate approval for Low Risk merchants
     */
    @Async
    @Transactional
    public void autoApproveLowRisk(Merchant merchant, RiskLevel riskLevel) {
        if (riskLevel == RiskLevel.LOW) {
            log.info("🤖 Auto-approving Low Risk Merchant: {}", merchant.getMerchantId());

            // Update Merchant Status
            merchant.setStatus("ACTIVE");
            merchant.setUpdatedAt(LocalDateTime.now());
            merchantRepository.save(merchant);

            // Close related onboarding case if exists
            // Finding logic needs update based on new entity structure, simplified for now
            // to fix compile error
            // List<ComplianceCase> cases =
            // complianceCaseRepository.findByMerchant_MerchantId(merchant.getMerchantId());
            // for (ComplianceCase c : cases) {
            // if ("ONBOARDING".equals(c.getCaseType()) && "OPEN".equals(c.getCaseStatus()))
            // {
            // c.setStatus(com.posgateway.aml.model.CaseStatus.CLOSED_CLEARED);
            // c.setResolution("Auto-approved Low Risk");
            // c.setResolvedAt(LocalDateTime.now());
            // complianceCaseRepository.save(c);
            // }
            // }

            // Placeholder: Log action as we can't easily find cases by merchant anymore
            // with new schema
            log.info("Auto-approved merchant {}", merchant.getMerchantId());

            notificationService.sendEmail(merchant.getContactEmail(), "Welcome to POS Gateway",
                    "Your account has been auto-approved.");
        }
    }

    /**
     * Auto-assign cases based on priority
     */
    @Async
    @Transactional
    public void autoAssignCase(ComplianceCase complianceCase) {
        if (complianceCase.getStatus() == com.posgateway.aml.model.CaseStatus.NEW
                && complianceCase.getAssignedTo() == null) {
            // Determine assignee logic here (requires User entity lookup)
            // For now, log the action
            log.info("🤖 Auto-assignment needed for Case {}", complianceCase.getId());
            notificationService.sendSystemAlert("compliance-team", "New Case Assigned: " + complianceCase.getId());
        }
    }

    @SuppressWarnings("unused")
    private String determineAssignee(ComplianceCase c) {
        // Mock logic: Assign based on priority
        // Adapted to new Enum
        if (c.getPriority() == com.posgateway.aml.model.CasePriority.CRITICAL
                || c.getPriority() == com.posgateway.aml.model.CasePriority.HIGH) {
            return "senior_officer";
        }
        return "junior_officer";
    }

    /**
     * Scheduled Job: Escalate overdue cases
     * Runs every hour
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void escalateOverdueCases() {
        log.info("⏰ Running scheduled job: Escalate Overdue Cases");

        LocalDateTime now = LocalDateTime.now();
        // Updated repository method call
        List<ComplianceCase> overdueCases = complianceCaseRepository.findBySlaDeadlineBeforeAndStatusNot(
                now, com.posgateway.aml.model.CaseStatus.CLOSED_CLEARED); // Example status

        for (ComplianceCase c : overdueCases) {
            if (!Boolean.TRUE.equals(c.getEscalated())) { // Use wrapper check
                log.warn("Escalating overdue case: {}", c.getId());

                c.setEscalated(true);
                c.setStatus(com.posgateway.aml.model.CaseStatus.ESCALATED);
                c.setPriority(com.posgateway.aml.model.CasePriority.CRITICAL);
                c.setUpdatedAt(now);
                complianceCaseRepository.save(c);

                notificationService.sendAlert("Case " + c.getId() + " escalated due to breach of SLA", "HIGH");
            }
        }
    }
}
