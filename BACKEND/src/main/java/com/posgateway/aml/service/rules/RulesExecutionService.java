package com.posgateway.aml.service.rules;

import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.rules.RuleEvaluationResult;
import com.posgateway.aml.rules.TransactionFact;
import com.posgateway.aml.service.feature.FeatureStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    @Autowired
    public RulesExecutionService(RuleDefinitionRepository ruleRepository,
                                 DroolsRulesService droolsService,
                                 SpelRuleExecutor spelExecutor,
                                 RuleEffectivenessService effectivenessService,
                                 FeatureStoreService featureStore) {
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

        List<RuleDefinition> allRules = ruleRepository.findByEnabledTrueOrderByPriorityDesc();
        List<String> triggeredRules = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        double scoreAdjustment = 0.0;
        String decision = "ALLOW";
        boolean sarRequired = false;
        boolean ctrRequired = false;
        boolean droolsEvaluated = false;

        for (RuleDefinition rule : allRules) {
            boolean triggered = false;

            if ("DROOLS_DRL".equals(rule.getRuleType())) {
                if (!droolsEvaluated) {
                    RuleEvaluationResult droolsResult = droolsService.evaluate(txnId, features, mlScore);
                    triggeredRules.addAll(droolsResult.getTriggeredRules());
                    reasons.addAll(droolsResult.getReasons());
                    decision = strongestDecision(decision, droolsResult.getDecision());
                    sarRequired = sarRequired || droolsResult.isSarRequired();
                    ctrRequired = ctrRequired || droolsResult.isCtrRequired();
                    scoreAdjustment += droolsResult.getTriggeredRules().size() * 10.0;
                    droolsEvaluated = true;
                }
            } else if ("SPEL".equals(rule.getRuleType())) {
                triggered = spelExecutor.evaluate(rule, fact);
            }

            if (triggered) {
                triggeredRules.add(rule.getName());
                reasons.add(rule.getDescription() != null ? rule.getDescription() : rule.getName());
                scoreAdjustment += rule.getScore() != null ? rule.getScore() : 10;
                decision = strongestDecision(decision, rule.getAction());
                logger.info("Rule triggered: {}", rule.getName());
            }
        }

        featureStore.storeRiskScore("txn:" + txnId, scoreAdjustment, "rule_based");

        return new RuleEvaluationResult(
                txnId,
                decision,
                reasons,
                triggeredRules.stream().distinct().collect(Collectors.toList()),
                sarRequired,
                ctrRequired,
                allRules.size(),
                System.currentTimeMillis() - startedAt);
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
        if (("ALERT".equals(normalized) || "FLAG".equals(normalized)) && "ALLOW".equals(current)) {
            return "REVIEW";
        }
        return current;
    }
}
