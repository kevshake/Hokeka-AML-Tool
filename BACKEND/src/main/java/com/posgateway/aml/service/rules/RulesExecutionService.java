package com.posgateway.aml.service.rules;

import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.entity.rules.RuleExecutionLog;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.rules.RuleEvaluationResult;
import com.posgateway.aml.rules.TransactionFact;
import com.posgateway.aml.service.feature.AerospikeFeatureStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Unified Rules Execution Service.
 * Orchestrates execution of Drools, SpEL, and other rule types.
 * Integrated with Aerospike Feature Store for velocity and risk features.
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
     * Evaluate ALL enabled rules against a transaction.
     */
    public RuleEvaluationResult evaluateTransaction(Long txnId, TransactionFact fact, Map<String, Object> features, Double mlScore) {
        logger.info("Evaluating rules for transaction: {}", txnId);

        // Store velocity features
        featureStore.incrementCounter("txn_velocity:" + txnId, "count", 1);
        featureStore.storeFeature("txn:" + txnId, "amount", fact.getAmount());

        List<RuleDefinition> allRules = ruleRepository.findByEnabledTrueOrderByPriorityDesc();
        List<String> triggeredRules = new ArrayList<>();
        double scoreAdjustment = 0.0;

        for (RuleDefinition rule : allRules) {
            boolean triggered = false;

            if ("DROOLS_DRL".equals(rule.getRuleType())) {
                triggered = droolsService.evaluate(rule, fact);
            } else if ("SPEL".equals(rule.getRuleType())) {
                triggered = spelExecutor.evaluate(rule, fact);
            }

            if (triggered) {
                triggeredRules.add(rule.getName());
                scoreAdjustment += rule.getScore() != null ? rule.getScore() : 10;
                logger.info("Rule triggered: {}", rule.getName());
            }
        }

        // Store risk score
        featureStore.storeRiskScore("txn:" + txnId, scoreAdjustment, "rule_based");

        return new RuleEvaluationResult(triggeredRules, scoreAdjustment, mlScore);
    }
}