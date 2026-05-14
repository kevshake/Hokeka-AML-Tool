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
     * Compute a composite case risk score in the range [0, 100].
     *
     * <ul>
     *   <li><b>Alert base (0–50):</b> average of all linked {@link com.posgateway.aml.entity.compliance.CaseAlert#getScore()}
     *       values (which are in [0,1]) multiplied by 50.</li>
     *   <li><b>Transaction amount factor (0–30):</b> log10(totalAmountCents + 1) scaled so
     *       that 10 000 USD (1 000 000 cents) maps to ≈30. Formula:
     *       {@code min(30, log10(totalCents + 1) / log10(1_000_001) * 30)}.</li>
     *   <li><b>Entity risk tier (0–20):</b> derived from the merchant's risk level stored in
     *       {@link com.posgateway.aml.service.risk.CustomerRiskProfilingService}:
     *       CRITICAL→20, HIGH→15, MEDIUM→8, LOW→3, unknown→0.</li>
     * </ul>
     */
    private Double getCaseRiskScore(ComplianceCase complianceCase) {
        // --- 1. Alert base score ---
        double alertBase = 0.0;
        java.util.List<com.posgateway.aml.entity.compliance.CaseAlert> alerts = complianceCase.getAlerts();
        if (alerts != null && !alerts.isEmpty()) {
            double sum = alerts.stream()
                    .map(com.posgateway.aml.entity.compliance.CaseAlert::getScore)
                    .filter(s -> s != null)
                    .mapToDouble(Double::doubleValue)
                    .sum();
            long withScore = alerts.stream()
                    .filter(a -> a.getScore() != null)
                    .count();
            if (withScore > 0) {
                alertBase = (sum / withScore) * 50.0; // average normalised to [0,1], scaled to [0,50]
            }
        }

        // --- 2. Transaction amount factor ---
        double amountFactor = 0.0;
        if (complianceCase.getMerchantId() != null) {
            Long totalCents = transactionRepository.sumAmountByMerchantInTimeWindow(
                    complianceCase.getMerchantId().toString(),
                    java.time.LocalDateTime.now().minusDays(30),
                    java.time.LocalDateTime.now());
            if (totalCents != null && totalCents > 0) {
                // log10(1_000_001) ≈ 6 → 10 000 USD maps to 30
                amountFactor = Math.min(30.0, Math.log10(totalCents + 1.0) / Math.log10(1_000_001.0) * 30.0);
            }
        }

        // --- 3. Entity risk tier ---
        double entityRisk = 0.0;
        if (complianceCase.getMerchantId() != null) {
            try {
                String riskLevel = riskProfilingService
                        .calculateRiskRating(complianceCase.getMerchantId().toString())
                        .getRiskLevel();
                if (riskLevel != null) {
                    entityRisk = switch (riskLevel.toUpperCase()) {
                        case "CRITICAL" -> 20.0;
                        case "HIGH"     -> 15.0;
                        case "MEDIUM"   -> 8.0;
                        case "LOW"      -> 3.0;
                        default         -> 0.0;
                    };
                }
            } catch (Exception ignored) {
                // leave entityRisk at 0 if risk profiling is unavailable
            }
        }

        double raw = alertBase + amountFactor + entityRisk;
        return Math.max(0.0, Math.min(100.0, raw));
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
