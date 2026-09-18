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

    @Test
    void malformedIrIsRejectedAndTheRunningBundleSurvives() {
        EdgeEngine engine = new EdgeEngine(AuthorizationGate.alwaysAuthorized());
        engine.loadVerifiedBundle(IR);

        assertThrows(IllegalStateException.class,
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
