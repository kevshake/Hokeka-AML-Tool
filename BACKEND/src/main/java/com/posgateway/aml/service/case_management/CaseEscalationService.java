package com.posgateway.aml.service.case_management;

import com.posgateway.aml.entity.User;
import com.posgateway.aml.entity.compliance.ComplianceCase;
import com.posgateway.aml.entity.compliance.EscalationRule;
import com.posgateway.aml.model.CasePriority;
import com.posgateway.aml.model.CaseStatus;
import com.posgateway.aml.model.UserRole;
import com.posgateway.aml.repository.ComplianceCaseRepository;
import com.posgateway.aml.repository.EscalationRuleRepository;
import com.posgateway.aml.repository.UserRepository;
import com.posgateway.aml.repository.TransactionRepository;
import com.posgateway.aml.service.risk.CustomerRiskProfilingService;
import com.posgateway.aml.service.CaseWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Case Escalation Service
 * Handles automatic and manual case escalation
 */
@Service
public class CaseEscalationService {

    private static final Logger logger = LoggerFactory.getLogger(CaseEscalationService.class);

    private final ComplianceCaseRepository caseRepository;
    private final TransactionRepository transactionRepository;
    private final CustomerRiskProfilingService riskProfilingService;
    private final EscalationRuleRepository escalationRuleRepository;
    private final UserRepository userRepository;
    private final CaseWorkflowService caseWorkflowService;
    private final CaseActivityService caseActivityService;

    @Value("${escalation.auto.enabled:true}")
    private Boolean autoEscalationEnabled;

    @Value("${escalation.auto.risk-score-threshold:0.8}")
    private Double riskScoreThreshold;

    @Value("${escalation.auto.amount-threshold:100000}")
    private BigDecimal amountThreshold;

    @Autowired
    public CaseEscalationService(ComplianceCaseRepository caseRepository,
            TransactionRepository transactionRepository,
            CustomerRiskProfilingService riskProfilingService,
            EscalationRuleRepository escalationRuleRepository,
            UserRepository userRepository,
            CaseWorkflowService caseWorkflowService,
            CaseActivityService caseActivityService) {
        this.caseRepository = caseRepository;
        this.transactionRepository = transactionRepository;
        this.riskProfilingService = riskProfilingService;
        this.escalationRuleRepository = escalationRuleRepository;
        this.userRepository = userRepository;
        this.caseWorkflowService = caseWorkflowService;
        this.caseActivityService = caseActivityService;
    }

    /**
     * Escalate a case manually
     */
    @Transactional
    public void escalateCase(Long caseId, String reason, Long escalatedBy) {
        ComplianceCase complianceCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Case not found"));

        User escalationTarget = determineEscalationTarget(complianceCase);

        caseWorkflowService.escalateCase(
                caseId,
                escalationTarget.getId(),
                reason,
                userRepository.findById(escalatedBy).orElseThrow());

        caseActivityService.logEscalation(
                caseId,
                escalationTarget.getUsername(),
                reason,
                escalatedBy);

        logger.info("Case {} escalated to {} by user {}",
                complianceCase.getCaseReference(),
                escalationTarget.getUsername(),
                escalatedBy);
    }

    /**
     * Check and perform automatic escalation for a case
     */
    @Transactional
    public void checkAutomaticEscalation(ComplianceCase complianceCase) {
        if (!autoEscalationEnabled || complianceCase.getEscalated()) {
            return;
        }

        List<EscalationRule> rules = escalationRuleRepository.findByEnabledTrue();

        for (EscalationRule rule : rules) {
            if (matchesEscalationRule(complianceCase, rule)) {
                String reason = buildEscalationReason(complianceCase, rule);
                escalateCase(complianceCase.getId(), reason, null); // System escalation
                logger.info("Case {} automatically escalated based on rule: {}",
                        complianceCase.getCaseReference(), rule.getRuleName());
                break; // Only escalate once
            }
        }
    }

    /**
     * Check if case matches escalation rule
     */
    private boolean matchesEscalationRule(ComplianceCase complianceCase, EscalationRule rule) {
        // Check priority
        if (rule.getMinPriority() != null) {
            if (getPriorityOrdinal(complianceCase.getPriority()) < getPriorityOrdinal(rule.getMinPriority())) {
                return false;
            }
        }

        // Check risk score (if available)
        if (rule.getMinRiskScore() != null) {
            Double riskScore = getCaseRiskScore(complianceCase);
            if (riskScore == null || riskScore < rule.getMinRiskScore()) {
                return false;
            }
        }

        // Check days open
        if (rule.getDaysOpen() != null) {
            if (complianceCase.getDaysOpen() == null || complianceCase.getDaysOpen() < rule.getDaysOpen()) {
                return false;
            }
        }

        // Check amount (if available)
        if (rule.getMinAmount() != null) {
            BigDecimal totalAmount = getCaseTotalAmount(complianceCase);
            if (totalAmount == null || totalAmount.compareTo(rule.getMinAmount()) < 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Determine escalation target based on hierarchy
     */
    private User determineEscalationTarget(ComplianceCase complianceCase) {
        // Check if case has matching escalation rule
        EscalationRule matchingRule = findMatchingEscalationRule(complianceCase);

        if (matchingRule != null && matchingRule.getEscalateToUserId() != null) {
            return userRepository.findById(matchingRule.getEscalateToUserId())
                    .orElseThrow(() -> new IllegalStateException("Escalation target user not found"));
        }

        // Default escalation hierarchy
        UserRole targetRole = determineEscalationRole(complianceCase);
        List<User> availableUsers = userRepository.findByRole_NameAndEnabled(targetRole.name(), true);

        if (availableUsers.isEmpty()) {
            // Escalate to next level
            targetRole = getNextEscalationRole(targetRole);
            availableUsers = userRepository.findByRole_NameAndEnabled(targetRole.name(), true);
        }

        if (availableUsers.isEmpty()) {
            throw new IllegalStateException("No escalation target available");
        }

        // Assign to user with lowest workload
        return availableUsers.stream()
                .min(Comparator.comparing(this::getCurrentWorkload))
                .orElseThrow();
    }

    /**
     * Determine escalation role based on current assignee
     */
    private UserRole determineEscalationRole(ComplianceCase complianceCase) {
        User currentAssignee = complianceCase.getAssignedTo();

        if (currentAssignee == null) {
            return UserRole.COMPLIANCE_OFFICER;
        }

        // Get role from user's role entity
        String roleName = currentAssignee.getRole().getName().toUpperCase();

        // Escalation hierarchy: ANALYST -> COMPLIANCE_OFFICER -> MLRO
        if (roleName.contains("ANALYST")) {
            return UserRole.COMPLIANCE_OFFICER;
        } else if (roleName.contains("COMPLIANCE")) {
            return UserRole.MLRO;
        } else {
            return UserRole.COMPLIANCE_OFFICER;
        }
    }

    /**
     * Get next escalation role
     */
    private UserRole getNextEscalationRole(UserRole currentRole) {
        return switch (currentRole) {
            case ANALYST -> UserRole.COMPLIANCE_OFFICER;
            case COMPLIANCE_OFFICER -> UserRole.MLRO;
            default -> UserRole.MLRO; // Already at highest level
        };
    }

    /**
     * Find matching escalation rule
     */
    private EscalationRule findMatchingEscalationRule(ComplianceCase complianceCase) {
        List<EscalationRule> rules = escalationRuleRepository.findByEnabledTrue();
        return rules.stream()
                .filter(rule -> matchesEscalationRule(complianceCase, rule))
                .findFirst()
                .orElse(null);
    }

    /**
     * Build escalation reason
     */
    private String buildEscalationReason(ComplianceCase complianceCase, EscalationRule rule) {
        if (rule.getReasonTemplate() != null) {
            return rule.getReasonTemplate()
                    .replace("{priority}", complianceCase.getPriority().name())
                    .replace("{daysOpen}", String.valueOf(complianceCase.getDaysOpen()));
        }

        StringBuilder reason = new StringBuilder("Automatic escalation: ");
        if (complianceCase.getPriority() == CasePriority.CRITICAL) {
            reason.append("Case marked as CRITICAL priority");
        } else if (complianceCase.getDaysOpen() != null && complianceCase.getDaysOpen() >= 7) {
            reason.append("Case open for ").append(complianceCase.getDaysOpen()).append(" days");
        } else {
            reason.append("Based on escalation rule: ").append(rule.getRuleName());
        }

        return reason.toString();
    }

    /**
     * Get case risk score (placeholder - implement based on your risk scoring
     * system)
     */
    private Double getCaseRiskScore(ComplianceCase complianceCase) {
        // Implement risk score calculation
        if (complianceCase.getMerchantId() == null)
            return 0.0;
        return riskProfilingService.calculateRiskRating(complianceCase.getMerchantId().toString()).getRiskScore();
    }

    private java.math.BigDecimal getCaseTotalAmount(ComplianceCase complianceCase) {
        // Sum of all related transaction amounts
        if (complianceCase.getMerchantId() == null)
            return java.math.BigDecimal.ZERO;

        // For simplicity, we get sum of last 30 days transactions for this merchant
        Long sumCents = transactionRepository.sumAmountByMerchantInTimeWindow(
                complianceCase.getMerchantId() != null ? complianceCase.getMerchantId().toString() : null,
                java.time.LocalDateTime.now().minusDays(30),
                java.time.LocalDateTime.now());

        return java.math.BigDecimal.valueOf(sumCents).divide(java.math.BigDecimal.valueOf(100));
    }

    /**
     * Get current workload for a user
     */
    private int getCurrentWorkload(User user) {
        return (int) caseRepository.countByAssignedTo_IdAndStatusIn(
                user.getId(),
                List.of(CaseStatus.ASSIGNED, CaseStatus.IN_PROGRESS, CaseStatus.PENDING_REVIEW));
    }

    /**
     * Get priority ordinal for comparison
     */
    private int getPriorityOrdinal(CasePriority priority) {
        return switch (priority) {
            case CRITICAL -> 4;
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
    }

    /**
     * Scheduled task to check pending escalations
     */
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    @Transactional
    public void checkPendingEscalations() {
        if (!autoEscalationEnabled) {
            return;
        }

        List<CaseStatus> openStatuses = List.of(
                CaseStatus.NEW,
                CaseStatus.ASSIGNED,
                CaseStatus.IN_PROGRESS);

        List<ComplianceCase> casesToCheck = caseRepository.findByStatusIn(openStatuses);
        int escalated = 0;

        for (ComplianceCase complianceCase : casesToCheck) {
            if (!complianceCase.getEscalated()) {
                checkAutomaticEscalation(complianceCase);
                escalated++;
            }
        }

        if (escalated > 0) {
            logger.info("Checked {} cases for automatic escalation", escalated);
        }
    }
}
