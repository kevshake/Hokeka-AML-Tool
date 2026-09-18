package com.posgateway.aml.service.rules;

import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.entity.rules.RuleExecutionLog;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.rules.RuleEvaluationResult;
import com.posgateway.aml.rules.TransactionFact;
import com.posgateway.aml.service.feature.FeatureStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Unified Rules Execution Service.
 * Orchestrates execution of Drools, SpEL, and other rule types.
 * Integrated with Redis-backed feature storage for velocity and risk features.
 */
@Service
public class RulesExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(RulesExecutionService.class);

    private final RuleDefinitionRepository ruleRepository;
    private final DroolsRulesService droolsService;
    private final SpelRuleExecutor spelExecutor;
    private final RuleEffectivenessService effectivenessService;
    private final FeatureStoreService featureStore;
    private final com.posgateway.aml.compliance.RegulatoryComplianceService regulatoryComplianceService;

    @Autowired
    public RulesExecutionService(RuleDefinitionRepository ruleRepository,
                                 DroolsRulesService droolsService,
                                 SpelRuleExecutor spelExecutor,
                                 RuleEffectivenessService effectivenessService,
                                 FeatureStoreService featureStore,
                                 com.posgateway.aml.compliance.RegulatoryComplianceService regulatoryComplianceService) {
        this.ruleRepository = ruleRepository;
        this.droolsService = droolsService;
        this.spelExecutor = spelExecutor;
        this.effectivenessService = effectivenessService;
        this.featureStore = featureStore;
        this.regulatoryComplianceService = regulatoryComplianceService;
    }

    /**
     * Convenience overload used by RiskScoringService — builds a TransactionFact from the features map.
     */
    public RuleEvaluationResult evaluateRules(Long txnId, Map<String, Object> features, double mlScore) {
        TransactionFact fact = droolsService.buildTransactionFactPublic(txnId, features, mlScore);
        return evaluateTransaction(txnId, fact, features, mlScore);
    }

    /**
     * Evaluate ALL enabled rules against a transaction.
     */
    public RuleEvaluationResult evaluateTransaction(Long txnId, TransactionFact fact,
                                                    Map<String, Object> features, Double mlScore) {
        logger.info("Evaluating rules for transaction: {}", txnId);
        long startedAt = System.currentTimeMillis();

        // Store velocity features (null-safe)
        featureStore.incrementCounter("txn_velocity:" + txnId, "count", 1);
        if (fact != null) {
            featureStore.storeFeature("txn:" + txnId, "amount", fact.getAmount());
        }

        // Evaluate the transaction against ITS PSP's own rule set (that PSP's editable copies of the
        // defaults plus its custom rules), not every PSP's rules. A PSP that has not been provisioned
        // with copies yet falls back to the global system defaults so it is never left unprotected.
        Long evaluationPspId = resolvePspId(features);
        List<RuleDefinition> allRules;
        if (evaluationPspId != null) {
            allRules = ruleRepository.findByEnabledTrueAndPspIdOrderByPriorityDesc(evaluationPspId);
            // "No ENABLED rules" is ambiguous, so distinguish the two causes before falling back.
            // Falling back on emptiness alone meant a PSP that deliberately disabled its whole rule
            // set silently had the global system defaults re-armed underneath it — the opposite of
            // what the operator asked for. Only fall back when the PSP has NO rules provisioned at
            // all (a fresh tenant, which must never be left unprotected).
            if (allRules.isEmpty() && !ruleRepository.existsByPspId(evaluationPspId)) {
                allRules = ruleRepository.findByEnabledTrueAndPspIdIsNullOrderByPriorityDesc();
            }
        } else {
            allRules = ruleRepository.findByEnabledTrueAndPspIdIsNullOrderByPriorityDesc();
        }
        List<String> triggeredRules = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        double scoreAdjustment = 0.0;
        String decision = "ALLOW";
        boolean sarRequired = false;
        boolean ctrRequired = false;
        RuleEvaluationResult droolsResult = droolsService.evaluate(txnId, features, mlScore);
        Set<String> droolsTriggeredRules = new HashSet<>(droolsResult.getTriggeredRules());
        triggeredRules.addAll(droolsResult.getTriggeredRules());
        reasons.addAll(droolsResult.getReasons());
        decision = strongestDecision(decision, droolsResult.getDecision());
        sarRequired = droolsResult.isSarRequired();
        ctrRequired = droolsResult.isCtrRequired();
        scoreAdjustment += droolsResult.getTriggeredRules().size() * 10.0;
        int regulatoryChecks = fact != null ? 1 : 0;
        Map<String, Object> regulatoryEvidence = new LinkedHashMap<>();
        if (fact != null) {
            com.posgateway.aml.compliance.RegulatoryComplianceService.ComplianceDecision compliance =
                    regulatoryComplianceService.evaluateCompliance(
                            fact.getAmount(),
                            fact.getCurrency(),
                            fact.getCountryCode(),
                            false,
                            fact.getPanTxnCount24h(),
                            fact.getMlScore(),
                            fact.isCashTransaction(),
                            fact.getTxnTime());
            decision = strongestDecision(decision, compliance.getDecision());
            sarRequired = sarRequired || compliance.isStrRequired();
            ctrRequired = ctrRequired || compliance.isCtrRequired();
            regulatoryEvidence.putAll(compliance.getEvidence());
            for (Map.Entry<String, String> reason : compliance.getReasons().entrySet()) {
                triggeredRules.add(reason.getKey());
                reasons.add(reason.getValue());
            }
            if (compliance.isStrRequired()) scoreAdjustment += 20.0;
            if ("HOLD".equals(compliance.getDecision())) scoreAdjustment += 10.0;
        }
        for (RuleDefinition rule : allRules) {
            boolean triggered = false;
            boolean evaluationFailed = false;
            RuleExecutionLog.Result executionResult = RuleExecutionLog.Result.NO_MATCH;
            long ruleStartedAt = System.nanoTime();

            try {
                if ("DROOLS_DRL".equals(rule.getRuleType())) {
                    triggered = droolsTriggeredRules.contains(rule.getName());
                } else if ("SPEL".equals(rule.getRuleType())) {
                    triggered = spelExecutor.evaluate(rule, fact, features);
                }
                executionResult = triggered ? RuleExecutionLog.Result.MATCH : RuleExecutionLog.Result.NO_MATCH;
            } catch (RuntimeException e) {
                evaluationFailed = true;
                executionResult = RuleExecutionLog.Result.ERROR;
                logger.error("Rule evaluation failed for rule {} (id={}): {}", rule.getName(), rule.getId(), e.getMessage());
            } finally {
                long executionTimeMicros = Math.max(1L, (System.nanoTime() - ruleStartedAt) / 1_000L);
                effectivenessService.recordExecution(
                        rule.getId(), evaluationPspId, String.valueOf(txnId), executionTimeMicros, executionResult);
            }

            if (evaluationFailed) {
                decision = strongestDecision(decision, "HOLD");
                triggeredRules.add("RULE_EVALUATION_ERROR:" + rule.getName());
                reasons.add("Rule '" + rule.getName()
                        + "' could not be evaluated; manual review is required");
                scoreAdjustment += 20.0;
                continue;
            }

            if (triggered) {
                triggeredRules.add(rule.getName());
                reasons.add(rule.getDescription() != null ? rule.getDescription() : rule.getName());
                scoreAdjustment += rule.getScore() != null ? rule.getScore() : 10;
                String normalizedAction = normalizeRuleAction(rule.getAction());
                if (normalizedAction == null) {
                    decision = strongestDecision(decision, "HOLD");
                    triggeredRules.add("RULE_ACTION_ERROR:" + rule.getName());
                    reasons.add("Rule '" + rule.getName()
                            + "' has an unsupported action; manual review is required");
                    scoreAdjustment += 20.0;
                } else {
                    decision = strongestDecision(decision, normalizedAction);
                }
                logger.info("Rule triggered: {}", rule.getName());
            }
        }

        featureStore.storeRiskScore("txn:" + txnId, scoreAdjustment, "rule_based");

        RuleEvaluationResult result = new RuleEvaluationResult(
                txnId,
                decision,
                reasons,
                triggeredRules.stream().distinct().collect(Collectors.toList()),
                sarRequired,
                ctrRequired,
                allRules.size() + droolsResult.getRulesExecuted() + regulatoryChecks,
                System.currentTimeMillis() - startedAt,
                regulatoryEvidence);
        // Carry the summed score_impact so it reaches the decision layer instead of being discarded.
        result.setScoreImpact(scoreAdjustment);
        return result;
    }

    private Long resolvePspId(Map<String, Object> features) {
        if (features == null) return null;
        Object value = features.get("pspId");
        if (value == null) value = features.get("psp_id");
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String stringValue) {
            try {
                return Long.valueOf(stringValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String strongestDecision(String current, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return current;
        }
        String normalized = candidate.toUpperCase();
        if ("BLOCK".equals(normalized) || "SUSPEND".equals(normalized)) {
            return "BLOCK";
        }
        if ("HOLD".equals(normalized) && !"BLOCK".equals(current)) {
            return "HOLD";
        }
        if (("ALERT".equals(normalized) || "FLAG".equals(normalized) || "REVIEW".equals(normalized))
                && "ALLOW".equals(current)) {
            return "REVIEW";
        }
        return current;
    }

    private String normalizeRuleAction(String action) {
        if (action == null || action.isBlank()) return null;
        return switch (action.trim().toUpperCase()) {
            case "ALLOW" -> "ALLOW";
            case "ALERT", "FLAG", "REVIEW" -> "REVIEW";
            case "HOLD" -> "HOLD";
            case "BLOCK", "SUSPEND" -> "BLOCK";
            default -> null;
        };
    }
}
