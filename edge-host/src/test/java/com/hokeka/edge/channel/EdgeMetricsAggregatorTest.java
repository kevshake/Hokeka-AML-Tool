package com.hokeka.edge.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.EdgeRuleInterpreter.Action;
import com.hokeka.edge.EdgeRuleInterpreter.Decision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Contract §6: what goes up is counts, never transactions. */
class EdgeMetricsAggregatorTest {

    /** The complete, exhaustive allow-list the control plane accepts. */
    private static final Set<String> ALLOWED_FIELDS = new TreeSet<>(List.of(
            "pspId", "edgeId", "windowStartEpochMs", "windowEndEpochMs",
            "ruleBundleVersion", "ruleBundleHash",
            "totalEvaluated", "allowed", "alerted", "held", "blocked",
            "ruleHitCounts", "p50LatencyMicros", "p95LatencyMicros", "p99LatencyMicros",
            "engineHealth"));

    @Test
    void aggregatesDecisionsAndResetsTheWindow() {
        EdgeMetricsAggregator aggregator = new EdgeMetricsAggregator("acme", "acme-eu-1", 64, 1_000L);
        aggregator.record(new Decision(Action.ALLOW, 0, List.of(), List.of()), 40.0);
        aggregator.record(new Decision(Action.HOLD, 25, List.of(100L), List.of("vpn")), 80.0);
        aggregator.record(new Decision(Action.BLOCK, 90, List.of(100L, 101L), List.of("sanctions")), 120.0);

        EdgeMetricsReport report = aggregator.snapshotAndReset(41L, "sha256:beef", "OK", 61_000L);
        assertEquals(3, report.totalEvaluated());
        assertEquals(3, report.totalDecisions());
        assertEquals(1, report.allowed());
        assertEquals(1, report.held());
        assertEquals(1, report.blocked());
        assertEquals(2L, report.ruleHitCounts().get(100L));
        assertEquals(1L, report.ruleHitCounts().get(101L));
        assertEquals(1_000L, report.windowStartEpochMs());

        EdgeMetricsReport empty = aggregator.snapshotAndReset(41L, "sha256:beef", "OK", 121_000L);
        assertEquals(0, empty.totalEvaluated());
        assertEquals(61_000L, empty.windowStartEpochMs());
        assertEquals(0.0, empty.p99LatencyMicros());
    }

    @Test
    void serialisedReportCarriesOnlyAllowListedAggregateFields() throws Exception {
        EdgeMetricsAggregator aggregator = new EdgeMetricsAggregator("acme", "acme-eu-1", 64, 0L);
        aggregator.record(new Decision(Action.BLOCK, 90, List.of(100L), List.of("sanctions hit")), 55.0);

        String json = new ObjectMapper().writeValueAsString(
                aggregator.snapshotAndReset(41L, "sha256:beef", "OK", 60_000L));
        JsonNode node = new ObjectMapper().readTree(json);

        Set<String> fields = new TreeSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        assertEquals(ALLOWED_FIELDS, fields, "the wire shape must be exactly the aggregate allow-list");
        for (String forbidden : List.of("pan", "card", "customer", "amount", "merchant", "ip", "email")) {
            assertFalse(fields.stream().anyMatch(f -> f.toLowerCase().contains(forbidden)),
                    "aggregate report must not carry a '" + forbidden + "' field: " + fields);
        }
    }
}
