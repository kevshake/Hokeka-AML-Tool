package com.hokeka.edge;

import com.hokeka.edge.EdgeRuleInterpreter.Action;
import com.hokeka.edge.EdgeRuleInterpreter.Decision;
import com.hokeka.edge.activation.AuthorizationGate;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Executes the real JNI boundary against a built {@code libedge_engine}: verified IR in through
 * {@code loadVerifiedIr}, decisions out through the Rust interpreter.
 *
 * <p>Runs only under {@code mvn test -Pnative-core} (which points {@code java.library.path} at the
 * built core); the default build runs the Java fallback, matching how the container ships until CI
 * stages the {@code .so}.
 */
class EdgeEngineNativeIT {

    private static final byte[] IR = """
        { "version": 41, "psp_id": 7, "rules": [
          { "id": 7, "name": "large amount", "description": "amount over threshold",
            "condition": { "type": "cmp", "field": "amount", "op": "GT", "value": 100 },
            "action": "BLOCK", "score": 90, "priority": 1 } ] }""".getBytes(StandardCharsets.UTF_8);

    @BeforeAll
    static void requireNativeCore() {
        Assumptions.assumeTrue(new EdgeEngine(AuthorizationGate.alwaysAuthorized()).nativeAvailable(),
                "libedge_engine is not on java.library.path — run with -Pnative-core");
    }

    @Test
    void verifiedIrIsPublishedIntoTheNativeArenaAndEvaluatedThere() {
        EdgeEngine engine = new EdgeEngine(AuthorizationGate.alwaysAuthorized());

        assertEquals(41, engine.loadVerifiedBundle(IR));
        assertEquals(EdgeEngine.EVALUATOR_NATIVE, engine.activeEvaluator());
        // Both implementations accepted the same IR bytes — that is what makes the standby usable.
        assertTrue(engine.standbyReady(), "the Rust core and the Java interpreter must agree on the IR");
        assertFalse(engine.nativeDegraded());

        Decision hit = engine.evaluate(Map.of("amount", 5000));
        assertEquals(Action.BLOCK, hit.action());
        assertEquals(90, hit.score());
        assertEquals(List.of(7L), hit.triggeredRuleIds());

        Decision miss = engine.evaluate(Map.of("amount", 5));
        assertEquals(Action.ALLOW, miss.action());
        assertTrue(miss.triggeredRuleIds().isEmpty());
    }

    /**
     * Cross-implementation parity: the shared IR v2 vectors evaluated by the real JNI build and by
     * the Java interpreter must give identical decisions (and the recorded ones).
     */
    @Test
    void nativeCoreAndJavaInterpreterAgreeOnTheSharedVectors() throws Exception {
        com.fasterxml.jackson.databind.JsonNode file = EdgeRuleIrParityTest.vectors();
        byte[] bundle = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(file.get("bundle"));
        EdgeEngine engine = new EdgeEngine(AuthorizationGate.alwaysAuthorized());
        engine.loadVerifiedBundle(bundle);
        assertEquals(EdgeEngine.EVALUATOR_NATIVE, engine.activeEvaluator());
        EdgeRuleInterpreter java = new EdgeRuleInterpreter();
        java.loadBundle(bundle);
        for (com.fasterxml.jackson.databind.JsonNode c : file.get("cases")) {
            Map<String, Object> features = EdgeRuleIrParityTest.features(c);
            EdgeEngine.Outcome nativeOutcome = engine.evaluateWithProvenance(features);
            assertEquals(EdgeEngine.CONTRACT_EVALUATOR_NATIVE, nativeOutcome.evaluator(), c.get("name").asText());
            Decision n = nativeOutcome.decision();
            Decision j = java.evaluate(features);
            String name = c.get("name").asText();
            assertEquals(j.action(), n.action(), name);
            assertEquals(j.score(), n.score(), name);
            assertEquals(j.triggeredRuleIds(), n.triggeredRuleIds(), name);
            assertEquals(c.get("expect").get("action").asText(), n.action().name(), name);
        }
    }

    /**
     * The compiled seed catalog (BACKEND EdgeRuleCompiler output) loads in the native core and
     * gives the Java interpreter's decision on derived-feature bags of every typology.
     */
    @Test
    void nativeCoreAgreesWithJavaOnTheCompiledTypologyCatalog() throws Exception {
        byte[] bundle = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("..", "edge-engine", "test-vectors",
                "typology_catalog_bundle.json"));
        EdgeEngine engine = new EdgeEngine(AuthorizationGate.alwaysAuthorized());
        engine.loadVerifiedBundle(bundle);
        assertEquals(EdgeEngine.EVALUATOR_NATIVE, engine.activeEvaluator());
        EdgeRuleInterpreter java = new EdgeRuleInterpreter();
        java.loadBundle(bundle);
        List<Map<String, Object>> bags = List.of(
                Map.of("pan_txn_count_1m", 9L, "pan_txn_count_24h", 12L, "amount", 10.0, "txn_direction", "OUTBOUND"),
                Map.of("amount", 8500.0, "pan_txn_count_24h", 5L, "txn_direction", "INBOUND"),
                Map.of("zscore_amount_vs_pan_history", 4.2, "is_first_transaction", false),
                Map.of("country_code", "ir", "pan_txn_count_24h", 6L),
                Map.of("pan_hash", "PAN-BLOCKED", "device_key", "x"),
                Map.of("reference_lc", "casino deposit", "anonymous_payment_screening_hit", true),
                Map.of("days_since_last_activity", 400L, "round_value_share_pct", 80.0, "unique_senders", 12L),
                Map.of());
        for (Map<String, Object> bag : bags) {
            Decision n = engine.evaluate(bag);
            Decision j = java.evaluate(bag);
            assertEquals(j.action(), n.action(), bag.toString());
            assertEquals(j.score(), n.score(), bag.toString());
            assertEquals(j.triggeredRuleIds(), n.triggeredRuleIds(), bag.toString());
        }
    }

    @Test
    void malformedIrIsRejectedAndTheRunningBundleSurvives() {
        EdgeEngine engine = new EdgeEngine(AuthorizationGate.alwaysAuthorized());
        engine.loadVerifiedBundle(IR);

        // Pre-native structural validation rejects invalid JSON before JNI is reached.
        assertThrows(IllegalArgumentException.class,
                () -> engine.loadVerifiedBundle("{ not valid IR".getBytes(StandardCharsets.UTF_8)));

        assertEquals(41, engine.activeVersion());
        assertEquals(Action.BLOCK, engine.evaluate(Map.of("amount", 5000)).action());
    }

    @Test
    void theNativeCoreFailsClosedForAnUnauthorizedNode() {
        EdgeEngine engine = new EdgeEngine(new AuthorizationGate() {
            @Override
            public boolean authorized() {
                return false;
            }

            @Override
            public String state() {
                return STATE_UNAUTHORIZED;
            }

            @Override
            public String reason() {
                return "awaiting approval";
            }
        });
        engine.loadVerifiedBundle(IR);

        assertEquals(Action.HOLD, engine.evaluate(Map.of("amount", 5000)).action());
    }
}
