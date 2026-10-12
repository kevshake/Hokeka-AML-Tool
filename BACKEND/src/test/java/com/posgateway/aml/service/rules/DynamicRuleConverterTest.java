package com.posgateway.aml.service.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicRuleConverterTest {

    private final DynamicRuleConverter converter = new DynamicRuleConverter();

    @Test
    void visualBuilderGroupsBecomeRealConditionsRatherThanAMatchAllRule() {
        String drl = converter.convertJsonToDrl("Visual high value", """
                {"groups":[{"logic":"AND","conditions":[
                  {"field":"amount","operator":">=","value":"10000"},
                  {"field":"country","operator":"==","value":"KP"}
                ]}]}
                """);

        assertTrue(drl.contains("amount >= new BigDecimal(\"10000\")"), drl);
        assertTrue(drl.contains("countryCode == \"KP\""), drl);
        assertFalse(drl.contains("this != null"), drl);
    }
}
