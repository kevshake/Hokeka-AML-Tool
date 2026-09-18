package com.posgateway.aml.service.rules;

import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.entity.rules.RuleExecutionLog;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.rules.RuleEvaluationResult;
import com.posgateway.aml.rules.TransactionFact;
import com.posgateway.aml.service.feature.FeatureStoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RulesExecutionServiceTest {

    @Mock private RuleDefinitionRepository ruleRepository;
    @Mock private DroolsRulesService droolsRulesService;
    @Mock private SpelRuleExecutor spelRuleExecutor;
    @Mock private RuleEffectivenessService effectivenessService;
    @Mock private FeatureStoreService featureStoreService;
    @Mock private com.posgateway.aml.compliance.RegulatoryComplianceService regulatoryComplianceService;
    @Mock private TransactionFact fact;

    @Test
    void recordsEverySpelEvaluationForEffectivenessMetrics() {
        RuleDefinition rule = new RuleDefinition();
        rule.setId(42L);
        rule.setName("High Value");
        rule.setRuleType("SPEL");
        rule.setAction("ALERT");
        rule.setScore(25);
        when(ruleRepository.findByEnabledTrueAndPspIdOrderByPriorityDesc(7L)).thenReturn(List.of(rule));
        when(droolsRulesService.evaluate(100L, Map.of("pspId", 7L), 0.1))
                .thenReturn(new RuleEvaluationResult(
                        100L, "ALLOW", List.of(), List.of(), false, false, 8, 1));
        stubAllowCompliance();
        when(spelRuleExecutor.evaluate(eq(rule), eq(fact), eq(Map.of("pspId", 7L)))).thenReturn(true);

        RulesExecutionService service = new RulesExecutionService(
                ruleRepository, droolsRulesService, spelRuleExecutor, effectivenessService,
                featureStoreService, regulatoryComplianceService);
        RuleEvaluationResult result = service.evaluateTransaction(100L, fact, Map.of("pspId", 7L), 0.1);

        assertEquals(List.of("High Value"), result.getTriggeredRules());
        verify(effectivenessService).recordExecution(
                eq(42L), eq(7L), eq("100"), anyLong(), eq(RuleExecutionLog.Result.MATCH));
    }

    @Test
    void appliesCashCtrDecisionWithoutDatabaseDroolsSeed() {
        when(ruleRepository.findByEnabledTrueAndPspIdOrderByPriorityDesc(7L)).thenReturn(List.of());
        when(droolsRulesService.evaluate(101L, Map.of("pspId", 7L), 0.2))
                .thenReturn(new RuleEvaluationResult(
                        101L, "ALLOW", List.of(), List.of(), false, false, 8, 1));
        com.posgateway.aml.compliance.RegulatoryComplianceService.ComplianceDecision compliance =
                new com.posgateway.aml.compliance.RegulatoryComplianceService.ComplianceDecision();
        compliance.setDecision("ALLOW");
        compliance.setCtrRequired(true);
        compliance.addReason("KENYA_CASH_TRANSACTION_REPORT", "Cash threshold met");
        compliance.putEvidence("ctrEvaluationStatus", "REPORTABLE");
        when(regulatoryComplianceService.evaluateCompliance(
                any(), any(), any(), anyBoolean(), anyLong(), anyDouble(), anyBoolean(), any()))
                .thenReturn(compliance);

        RulesExecutionService service = new RulesExecutionService(
                ruleRepository, droolsRulesService, spelRuleExecutor, effectivenessService,
                featureStoreService, regulatoryComplianceService);
        RuleEvaluationResult result = service.evaluateTransaction(
                101L, fact, Map.of("pspId", 7L), 0.2);

        assertTrue(result.isCtrRequired());
        assertTrue(result.getTriggeredRules().contains("KENYA_CASH_TRANSACTION_REPORT"));
        assertEquals("REPORTABLE", result.getRegulatoryEvidence().get("ctrEvaluationStatus"));
    }

    /**
     * A PSP that deliberately disables its entire rule set must NOT have the global system defaults
     * silently re-armed underneath it — that is the opposite of what the operator asked for. The
     * fallback exists only for a tenant that has no rules provisioned at all.
     */
    @Test
    void deliberatelyDisablingEveryRuleDoesNotReArmTheGlobalDefaults() {
        when(ruleRepository.findByEnabledTrueAndPspIdOrderByPriorityDesc(7L)).thenReturn(List.of());
        when(ruleRepository.existsByPspId(7L)).thenReturn(true); // provisioned, but all disabled
        when(droolsRulesService.evaluate(anyLong(), any(), anyDouble()))
                .thenReturn(new RuleEvaluationResult(102L, "ALLOW", List.of(), List.of(), false, false, 0, 1));

        com.posgateway.aml.compliance.RegulatoryComplianceService.ComplianceDecision allow =
                new com.posgateway.aml.compliance.RegulatoryComplianceService.ComplianceDecision();
        allow.setDecision("ALLOW");
        when(regulatoryComplianceService.evaluateCompliance(
                any(), any(), any(), anyBoolean(), anyLong(), anyDouble(), anyBoolean(), any()))
                .thenReturn(allow);

        RulesExecutionService service = new RulesExecutionService(
                ruleRepository, droolsRulesService, spelRuleExecutor, effectivenessService,
                featureStoreService, regulatoryComplianceService);
        service.evaluateTransaction(102L, fact, Map.of("pspId", 7L), 0.2);

        verify(ruleRepository, org.mockito.Mockito.never())
                .findByEnabledTrueAndPspIdIsNullOrderByPriorityDesc();
    }

    /** A freshly onboarded PSP with no rules at all still gets the defaults, so it is never unprotected. */
    @Test
    void unprovisionedPspStillFallsBackToTheGlobalDefaults() {
        when(ruleRepository.findByEnabledTrueAndPspIdOrderByPriorityDesc(9L)).thenReturn(List.of());
        when(ruleRepository.existsByPspId(9L)).thenReturn(false); // never provisioned
        when(ruleRepository.findByEnabledTrueAndPspIdIsNullOrderByPriorityDesc()).thenReturn(List.of());
        when(droolsRulesService.evaluate(anyLong(), any(), anyDouble()))
                .thenReturn(new RuleEvaluationResult(103L, "ALLOW", List.of(), List.of(), false, false, 0, 1));

        com.posgateway.aml.compliance.RegulatoryComplianceService.ComplianceDecision allow =
                new com.posgateway.aml.compliance.RegulatoryComplianceService.ComplianceDecision();
        allow.setDecision("ALLOW");
        when(regulatoryComplianceService.evaluateCompliance(
                any(), any(), any(), anyBoolean(), anyLong(), anyDouble(), anyBoolean(), any()))
                .thenReturn(allow);

        RulesExecutionService service = new RulesExecutionService(
                ruleRepository, droolsRulesService, spelRuleExecutor, effectivenessService,
                featureStoreService, regulatoryComplianceService);
        service.evaluateTransaction(103L, fact, Map.of("pspId", 9L), 0.2);

        verify(ruleRepository).findByEnabledTrueAndPspIdIsNullOrderByPriorityDesc();
    }

    @Test
    void ruleEvaluationFailureForcesHold() {
        RuleDefinition rule = new RuleDefinition();
        rule.setId(88L);
        rule.setName("Broken expression");
        rule.setRuleType("SPEL");
        rule.setAction("ALLOW");
        when(ruleRepository.findByEnabledTrueAndPspIdIsNullOrderByPriorityDesc()).thenReturn(List.of(rule));
        when(droolsRulesService.evaluate(102L, Map.of(), 0.1))
                .thenReturn(new RuleEvaluationResult(
                        102L, "ALLOW", List.of(), List.of(), false, false, 1, 1));
        stubAllowCompliance();
        when(spelRuleExecutor.evaluate(rule, fact, Map.of()))
                .thenThrow(new SpelRuleExecutor.RuleEvaluationException("invalid expression"));

        RulesExecutionService service = new RulesExecutionService(
                ruleRepository, droolsRulesService, spelRuleExecutor, effectivenessService,
                featureStoreService, regulatoryComplianceService);
        RuleEvaluationResult result = service.evaluateTransaction(102L, fact, Map.of(), 0.1);

        assertEquals("HOLD", result.getDecision());
        assertTrue(result.getTriggeredRules().contains("RULE_EVALUATION_ERROR:Broken expression"));
        verify(effectivenessService).recordExecution(
                eq(88L), eq(null), eq("102"), anyLong(), eq(RuleExecutionLog.Result.ERROR));
    }

    private void stubAllowCompliance() {
        com.posgateway.aml.compliance.RegulatoryComplianceService.ComplianceDecision compliance =
                new com.posgateway.aml.compliance.RegulatoryComplianceService.ComplianceDecision();
        compliance.setDecision("ALLOW");
        when(regulatoryComplianceService.evaluateCompliance(
                any(), any(), any(), anyBoolean(), anyLong(), anyDouble(), anyBoolean(), any()))
                .thenReturn(compliance);
    }
}
