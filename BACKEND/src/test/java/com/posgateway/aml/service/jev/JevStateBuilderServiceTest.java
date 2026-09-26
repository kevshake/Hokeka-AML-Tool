package com.posgateway.aml.service.jev;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JevStateBuilderServiceTest {

    private final JevStateBuilderService service = new JevStateBuilderService();

    @Test
    void stripsForbiddenKeysFromBuiltState() {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("score", 0.62);
        features.put("pan_hash", "abc");
        features.put("terminal_id", "t-1");
        features.put("description", "Customer John sent funds");
        features.put("matches", java.util.List.of("John Doe:INDIVIDUAL"));

        JevDecisionContext ctx = JevDecisionContext.builder(JevEngineType.ALERT_TRIAGE)
                .merchantId(42L)
                .baselineDecision("ALERT")
                .features(features)
                .build();

        Map<String, Object> state = service.buildState(JevDecisionPoint.DP1_TM_ALERT_TRIAGE, ctx);
        String serialized = state.toString();
        assertFalse(serialized.contains("abc"));
        assertFalse(serialized.contains("t-1"));
        assertFalse(serialized.contains("John"));
        assertFalse(serialized.contains("pan_hash"));
        assertTrue(state.containsKey("alert"));
        assertTrue(state.containsKey("subject"));
    }

    @Test
    void dp2StateContainsMatchFeaturesOnly() {
        Map<String, Object> features = Map.of(
                "similarity_score", 0.91,
                "match_type", "NAME_MATCH",
                "screened_entity_type", "ORGANIZATION",
                "screened_name", "Should Not Appear");
        JevDecisionContext ctx = JevDecisionContext.builder(JevEngineType.SANCTIONS_DISAMBIGUATION)
                .features(features)
                .build();
        Map<String, Object> state = service.buildState(JevDecisionPoint.DP2_SCREENING_MATCH, ctx);
        assertFalse(state.toString().contains("Should Not Appear"));
        assertTrue(state.containsKey("match_features"));
    }
}
