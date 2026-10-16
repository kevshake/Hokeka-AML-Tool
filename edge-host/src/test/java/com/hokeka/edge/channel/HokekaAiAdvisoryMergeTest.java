package com.hokeka.edge.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.EdgeRuleInterpreter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression (sim run 2026-10-16): with no AI key the edge reported "Hokeka AI recommendation:
 * ESCALATE" on every HOLD, because the Control Plane's fallback placeholder was merged as if an AI
 * had reviewed the transaction; the merge also dropped the triggered rule version ids.
 */
class HokekaAiAdvisoryMergeTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final EdgeRuleInterpreter.Decision baseline = new EdgeRuleInterpreter.Decision(
            EdgeRuleInterpreter.Action.HOLD, 70, List.of(11L), List.of("rule 11"), List.of(301L));

    @Test
    void fallbackAdvisoryIsNotPresentedAsAnAiRecommendation() throws Exception {
        var node = mapper.readTree("{\"aiRecommendation\":\"ESCALATE\",\"aiApplied\":false,\"fallback\":true,"
                + "\"reasons\":[\"inline mode off\"]}");
        EdgeRuleInterpreter.Decision merged = HokekaAiDecisionClient.mergeAdvisory(baseline, node);
        assertThat(merged).isSameAs(baseline);
        assertThat(merged.reasons()).noneMatch(r -> r.contains("AI recommendation"));
    }

    @Test
    void appliedAdvisoryAddsReasonsButKeepsActionScoreRulesAndVersions() throws Exception {
        var node = mapper.readTree("{\"aiRecommendation\":\"BLOCK\",\"confidence\":0.9,\"aiApplied\":true,"
                + "\"fallback\":false,\"reasons\":[\"velocity anomaly\"]}");
        EdgeRuleInterpreter.Decision merged = HokekaAiDecisionClient.mergeAdvisory(baseline, node);
        assertThat(merged.action()).isEqualTo(EdgeRuleInterpreter.Action.HOLD);
        assertThat(merged.score()).isEqualTo(70);
        assertThat(merged.triggeredRuleIds()).containsExactly(11L);
        assertThat(merged.triggeredRuleVersionIds()).containsExactly(301L);
        assertThat(merged.reasons()).contains("rule 11", "Hokeka AI recommendation: BLOCK (confidence=0.9)",
                "velocity anomaly");
    }
}
