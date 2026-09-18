package com.posgateway.aml.service.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.rules.TransactionFact;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule engine evaluates untrusted operator/LLM-authored SpEL. It MUST block the authenticated-RCE
 * path while still evaluating the real seeded rule shapes (property access, instance methods, inline
 * lists, map indexing).
 */
class SpelRuleExecutorSandboxTest {

    private final SpelRuleExecutor executor = new SpelRuleExecutor(new ObjectMapper());

    private TransactionFact fact(BigDecimal amount, boolean highRisk, long panCount1h) {
        return new TransactionFact(1L, "M1", amount, "USD", "US", null, "WEB", "hash",
                0.0, 0.0, 0L, 0.0, 0L, panCount1h, 0L, false, highRisk, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    private RuleDefinition rule(String expr, String paramsJson) {
        RuleDefinition r = new RuleDefinition();
        r.setName("test-rule");
        r.setRuleExpression(expr);
        r.setParameters(paramsJson);
        return r;
    }

    // ---- The security property: no RCE, no reflection, no constructors ----

    @Test
    void blocksRuntimeExecTypeReference() {
        RuleDefinition r = rule("T(java.lang.Runtime).getRuntime().exec('calc') != null", null);
        assertThrows(SpelRuleExecutor.RuleEvaluationException.class,
                () -> executor.evaluate(r, fact(BigDecimal.TEN, false, 0)));
    }

    @Test
    void blocksReflectionViaGetClass() {
        RuleDefinition r = rule("#tx.getClass().getName() != null", null);
        assertThrows(SpelRuleExecutor.RuleEvaluationException.class,
                () -> executor.evaluate(r, fact(BigDecimal.TEN, false, 0)));
    }

    @Test
    void blocksConstructorInvocation() {
        RuleDefinition r = rule("new java.lang.ProcessBuilder('sh').start() != null", null);
        assertThrows(SpelRuleExecutor.RuleEvaluationException.class,
                () -> executor.evaluate(r, fact(BigDecimal.TEN, false, 0)));
    }

    @Test
    void validateExpressionRejectsUnsafeAndAcceptsSafe() {
        assertThrows(SpelRuleExecutor.RuleEvaluationException.class,
                () -> executor.validateExpression("T(java.lang.System).exit(0)"));
        // A safe expression must validate without throwing.
        executor.validateExpression("#tx.isHighRiskCountry() && #features['x'] == true");
    }

    // ---- The functional property: real seeded rule shapes still evaluate ----

    @Test
    void instanceMethodOnTxEvaluates() {
        RuleDefinition r = rule("#tx.isHighRiskCountry()", null);
        assertTrue(executor.evaluate(r, fact(BigDecimal.TEN, true, 0)));
        assertFalse(executor.evaluate(r, fact(BigDecimal.TEN, false, 0)));
    }

    @Test
    void amountDoubleValueThresholdEvaluates() {
        // The V212-rewritten R-2 shape (no T(BigDecimal)).
        RuleDefinition r = rule(
                "#tx.amount != null && #tx.amount.doubleValue() >= (#params['threshold_amount'] ?: 1000000)", null);
        assertTrue(executor.evaluate(r, fact(new BigDecimal("2000000"), false, 0)));
        assertFalse(executor.evaluate(r, fact(new BigDecimal("500"), false, 0)));
    }

    @Test
    void inlineListContainsEvaluates() {
        // The V212-rewritten R-CB-4 shape (inline SpEL list instead of T(java.util.Arrays)).
        RuleDefinition r = rule(
                "#features['dispute_reason_category'] == 'fraud' || {'10.4','10.5'}.contains(#features['dispute_reason_code'])",
                null);
        TransactionFact f = fact(BigDecimal.TEN, false, 0);
        assertTrue(executor.evaluate(r, f, Map.of("dispute_reason_code", "10.4")));
        assertFalse(executor.evaluate(r, f, Map.of("dispute_reason_code", "99.9")));
        assertTrue(executor.evaluate(r, f, Map.of("dispute_reason_category", "fraud")));
    }

    /**
     * The V213-rewritten R-6 shape: "High-Risk Currency Transaction" must actually evaluate the
     * transaction CURRENCY against its own `high_risk_currencies` parameter. It previously ran
     * `#tx.isHighRiskCountry()` — a byte-identical duplicate of R-14 that never read its parameter.
     * Also confirms a List `.contains(...)` call survives the sandbox.
     */
    @Test
    void highRiskCurrencyRuleEvaluatesTheCurrencyParameter() {
        RuleDefinition r = rule(
                "#params['high_risk_currencies'] != null && #params['high_risk_currencies'].contains(#tx.currency)",
                "{\"high_risk_currencies\": [\"IRR\", \"KPW\"]}");

        TransactionFact risky = new TransactionFact(1L, "M1", BigDecimal.TEN, "KPW", "US", null, "WEB",
                "hash", 0.0, 0.0, 0L, 0.0, 0L, 0L, 0L, false, false, 0.0, 0.0, 0.0, 0.0, 0.0);
        TransactionFact ordinary = new TransactionFact(1L, "M1", BigDecimal.TEN, "USD", "US", null, "WEB",
                "hash", 0.0, 0.0, 0L, 0.0, 0L, 0L, 0L, false, false, 0.0, 0.0, 0.0, 0.0, 0.0);

        assertTrue(executor.evaluate(r, risky));
        assertFalse(executor.evaluate(r, ordinary));

        // A PSP that never populated the list must not have the rule mis-fire.
        RuleDefinition unconfigured = rule(
                "#params['high_risk_currencies'] != null && #params['high_risk_currencies'].contains(#tx.currency)",
                "{}");
        assertFalse(executor.evaluate(unconfigured, risky));
    }

    @Test
    void featureBooleanAndParamElvisEvaluate() {
        RuleDefinition r = rule("#features['sanctions_hit'] == true", null);
        assertTrue(executor.evaluate(r, fact(BigDecimal.TEN, false, 0), Map.of("sanctions_hit", true)));

        RuleDefinition velocity = rule("#tx.panTxnCount1h >= (#params['max_transactions'] ?: 10)", "{\"max_transactions\": 5}");
        assertTrue(executor.evaluate(velocity, fact(BigDecimal.TEN, false, 6)));
        assertFalse(executor.evaluate(velocity, fact(BigDecimal.TEN, false, 3)));
    }
}
