package com.posgateway.aml.service.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.entity.rules.RuleDefinition;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZscoreAmountRuleTest {

    private static final String EXPRESSION =
            "#features['zscore_amount_vs_pan_history'] != null && "
                    + "#features['zscore_amount_vs_pan_history'] >= (#params['z'] ?: 3)";

    private final SpelRuleExecutor executor = new SpelRuleExecutor(new ObjectMapper());

    @Test
    void zScoreOfThreePointFiveFires() {
        assertTrue(executor.evaluate(rule(), null, Map.of("zscore_amount_vs_pan_history", 3.5)));
    }

    @Test
    void zScoreOfOneDoesNotFire() {
        assertFalse(executor.evaluate(rule(), null, Map.of("zscore_amount_vs_pan_history", 1.0)));
    }

    private static RuleDefinition rule() {
        RuleDefinition rule = new RuleDefinition();
        rule.setName("PAN amount z-score");
        rule.setRuleType("SPEL");
        rule.setRuleExpression(EXPRESSION);
        rule.setParameters("{\"z\": 3}");
        rule.setAction("HOLD");
        return rule;
    }
}
