package com.posgateway.aml.service.rules;

import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.rules.RuleEvaluationResult;
import com.posgateway.aml.rules.TransactionFact;
import com.posgateway.aml.service.feature.AerospikeFeatureStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Unified Rules Execution Service.
 * Orchestrates SpEL rules per-rule; delegates DROOLS rules to DroolsRulesService.
 */
@Service
public class RulesExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(RulesExecutionService.class);

    private final RuleDefinitionRepository ruleRepository;
    private final DroolsRulesService droolsService;
    private final SpelRuleExecutor spelExecutor;
    private final RuleEffectivenessService effectivenessService;
    private final AerospikeFeatureStore featureStore;

    @Autowired
    public RulesExecutionService(RuleDefinitionRepository ruleRepository,
                                 DroolsRulesService droolsService,
                                 SpelRuleExecutor spelExecutor,
                                 RuleEffectivenessService effectivenessService,
                                 AerospikeFeatureStore featureStore) {
        this.ruleRepository = ruleRepository;
        this.droolsService = droolsService;
        this.spelExecutor = spelExecutor;
        this.effectivenessService = effectivenessService;
        this.featureStore = featureStore;
    }

    /**
     * Convenience overload used by RiskScoringService — builds a TransactionFact from the features map.
     */
    public RuleEvaluationResult evaluateRules(Long txnId, Map<String, Object> features, double mlScore) {
        TransactionFact fact = buildFact(txnId, features, mlScore);
        return evaluateTransaction(txnId, fact, features, mlScore);
    }

    /**
     * Evaluate ALL enabled rules against a transaction.
     */
    public RuleEvaluationResult evaluateTransaction(Long txnId, TransactionFact fact,
                                                    Map<String, Object> features, Double mlScore) {
        logger.info("Evaluating rules for transaction: {}", txnId);
        long start = System.currentTimeMillis();

        // Store velocity features (null-safe)
        featureStore.incrementCounter("txn_velocity:" + txnId, "count", 1);
        if (fact != null) {
            featureStore.storeFeature("txn:" + txnId, "amount", fact.getAmount());
        }

        List<RuleDefinition> allRules = ruleRepository.findByEnabledTrueOrderByPriorityDesc();
        List<String> triggeredRules = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        double scoreAdjustment = 0.0;
        int rulesExecuted = 0;
        boolean sarRequired = false;
        boolean ctrRequired = false;

        // DROOLS rules: delegate entire evaluation to DroolsRulesService
        boolean hasDropped = allRules.stream().anyMatch(r -> "DROOLS_DRL".equals(r.getRuleType()));
        if (hasDropped) {
            try {
                RuleEvaluationResult droolsResult = droolsService.evaluate(txnId, features, mlScore);
                if (droolsResult != null) {
                    if (droolsResult.getTriggeredRules() != null) {
                        triggeredRules.addAll(droolsResult.getTriggeredRules());
                    }
                    if (droolsResult.getReasons() != null) {
                        reasons.addAll(droolsResult.getReasons());
                    }
                    sarRequired = droolsResult.isSarRequired();
                    ctrRequired = droolsResult.isCtrRequired();
                    rulesExecuted += droolsResult.getRulesExecuted();
                }
            } catch (Exception e) {
                logger.warn("Drools evaluation failed for txn {}: {}", txnId, e.getMessage());
            }
        }

        // SPEL rules: evaluate per-rule
        TransactionFact evalFact = fact != null ? fact : buildFact(txnId, features, mlScore != null ? mlScore : 0.0);
        for (RuleDefinition rule : allRules) {
            if (!"SPEL".equals(rule.getRuleType())) continue;
            rulesExecuted++;
            try {
                boolean triggered = spelExecutor.evaluate(rule, evalFact);
                if (triggered) {
                    triggeredRules.add(rule.getName());
                    reasons.add(rule.getDescription() != null ? rule.getDescription() : rule.getName());
                    scoreAdjustment += rule.getScore() != null ? rule.getScore() : 10;
                    if ("SUSPEND".equals(rule.getAction())) sarRequired = true;
                    logger.info("Rule triggered: {}", rule.getName());
                }
            } catch (Exception e) {
                logger.debug("SPEL rule {} evaluation error: {}", rule.getName(), e.getMessage());
            }
        }

        featureStore.storeRiskScore("txn:" + txnId, scoreAdjustment, "rule_based");

        String decision = sarRequired ? "BLOCK" : (scoreAdjustment > 70 ? "REVIEW" : "APPROVE");
        long evaluationTimeMs = System.currentTimeMillis() - start;

        return new RuleEvaluationResult(txnId, decision, reasons, triggeredRules,
                sarRequired, ctrRequired, rulesExecuted, evaluationTimeMs);
    }

    private TransactionFact buildFact(Long txnId, Map<String, Object> features, double mlScore) {
        Object amtRaw = features.get("amount");
        BigDecimal amount = BigDecimal.ZERO;
        if (amtRaw instanceof BigDecimal) {
            amount = (BigDecimal) amtRaw;
        } else if (amtRaw instanceof Number) {
            amount = new BigDecimal(amtRaw.toString());
        }
        return new TransactionFact(
                txnId,
                (String) features.getOrDefault("merchant_id", "UNKNOWN"),
                amount,
                (String) features.getOrDefault("currency", "USD"),
                (String) features.getOrDefault("country_code", "UNK"),
                LocalDateTime.now(),
                (String) features.getOrDefault("channel", "POS"),
                (String) features.get("pan_hash"),
                mlScore,
                toDouble(features.get("pageRank")),
                toLong(features.get("communityId")),
                toDouble(features.get("betweenness")),
                toLong(features.get("connectionCount")),
                toLong(features.get("pan_txn_count_1h")),
                toDouble(features.get("pan_txn_amount_sum_24h")),
                toDouble(features.get("merchant_txn_amount_sum_24h")),
                toDouble(features.get("krs_score")),
                toDouble(features.get("cra_score")),
                toDouble(features.get("trs_score")));
    }

    private double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0.0; }
    }

    private long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; }
    }
}
