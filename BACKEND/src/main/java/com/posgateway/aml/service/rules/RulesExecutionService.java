package com.posgateway.aml.service.rules;

import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.rules.RuleEvaluationResult;
import com.posgateway.aml.rules.TransactionFact;
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
 */
@Service
public class RulesExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(RulesExecutionService.class);

    private final RuleDefinitionRepository ruleRepository;
    private final DroolsRulesService droolsService;
    private final SpelRuleExecutor spelExecutor;

    @Autowired
    public RulesExecutionService(RuleDefinitionRepository ruleRepository,
                                 DroolsRulesService droolsService,
                                 SpelRuleExecutor spelExecutor) {
        this.ruleRepository = ruleRepository;
        this.droolsService = droolsService;
        this.spelExecutor = spelExecutor;
    }

    /**
     * Evaluate ALL enabled rules against a transaction.
     *
     * @param txnId Transaction ID
     * @param features Transaction Features Map
     * @param mlScore Current ML Score (can be modified by rules)
     * @return Execution result including triggered rules and score adjustments
     */
    public RuleEvaluationResult evaluateRules(Long txnId, Map<String, Object> features, Double mlScore) {
        long startTime = System.currentTimeMillis();

        // 1. Convert to TransactionFact (Common Data Model)
        TransactionFact fact = droolsService.buildTransactionFactPublic(txnId, features, mlScore);

        // 2. Load Enabled Rules (In production, cache these)
        // Optimization: Use a cached service for rules list
        List<RuleDefinition> allRules = ruleRepository.findByEnabledTrueOrderByPriorityDesc();

        int rulesExecuted = 0;
        List<String> triggeredRuleNames = new ArrayList<>();
        List<String> reasons = new ArrayList<>();

        // 3. Execute Rules
        for (RuleDefinition rule : allRules) {
            boolean matched = false;

            if ("SPEL".equals(rule.getRuleType())) {
                matched = spelExecutor.evaluate(rule, fact);
            } else if ("DROOLS_DRL".equals(rule.getRuleType())) {
                // Drools rules are typically executed in batch via DroolsService,
                // but if defined individually, we might handle them differently.
                // For now, we assume global Drools execution handles the "DROOLS_DRL" types together
                // or we skip if DroolsService runs them separately.
                // CURRENT STRATEGY: DroolsService runs *all* DRLs in one session.
                // So we skip individual execution here to avoid double counting, unless we change strategy.
                continue; 
            }

            if (matched) {
                // Apply Action logic
                applyRuleOutcome(rule, fact);
                triggeredRuleNames.add(rule.getName());
                reasons.add("Rule Matched: " + rule.getName());
                rulesExecuted++;
            }
        }

        // 4. Run Legacy/Batch Drools Engine (if not replaced entirely)
        // This executes any DRL files (static or dynamic loaded into KieContainer)
        // Note: Ideally, we migrate completely to one flow, but for now we combine them.
        RuleEvaluationResult droolsResult = droolsService.evaluate(txnId, features, mlScore);
        
        // Merge results
        fact.getTriggeredRules().addAll(droolsResult.getTriggeredRules());
        fact.getReasons().addAll(droolsResult.getReasons());
        // Merge decisions (simplistic: BLOCK overrides ALL)
        if ("BLOCK".equals(droolsResult.getDecision())) {
            fact.setDecision("BLOCK");
        }
        
        // 5. Build Final Result
        long duration = System.currentTimeMillis() - startTime;
        return new RuleEvaluationResult(
                txnId,
                fact.getDecision(),
                new ArrayList<>(fact.getReasons()),
                new ArrayList<>(fact.getTriggeredRules()),
                fact.isSarRequired(),
                fact.isCtrRequired(),
                rulesExecuted + droolsResult.getRulesExecuted(),
                duration
        );
    }

    private void applyRuleOutcome(RuleDefinition rule, TransactionFact fact) {
        // Apply Action
        if (rule.getAction() != null) {
            switch (rule.getAction()) {
                case "BLOCK":
                    fact.setDecision("BLOCK");
                    break;
                case "HOLD":
                    if (!"BLOCK".equals(fact.getDecision())) {
                        fact.setDecision("HOLD");
                    }
                    break;
                case "ALERT":
                    // Just log/tag, don't change decision unless it's ALLOW
                    break;
            }
        }

        // Apply Score Impact (if we had a setRiskScore method on Fact, or we return it)
        // Since TransactionFact is a wrapper, we might need to track score adjustments separately
        // For now, we assume the Fact modification is sufficient for Decision, 
        // but for Score, we might need to return an adjusted score.
        // NOTE: TransactionFact doesn't hold mutable 'currentScore' typically, it holds input score.
        // We will assume 'RiskScoringService' handles the final score aggregation if we return a Structure.
    }
}
