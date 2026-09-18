package com.hokeka.edge;

import com.hokeka.edge.EdgeRuleInterpreter.Action;
import com.hokeka.edge.EdgeRuleInterpreter.Decision;
import com.hokeka.edge.activation.AuthorizationGate;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The verified IR must reach the native arena when the core is loaded — evaluation is the only
 * per-transaction path, so silently running it on the Java interpreter would waste the Rust core.
 */
class EdgeEngineNativeRoutingTest {

    private static final byte[] IR = """
        { "version": 41, "psp_id": 7, "rules": [
          { "id": 7, "name": "structuring",
            "condition": { "type": "cmp", "field": "amount", "op": "GT", "value": 100 },
            "action": "BLOCK", "score": 90, "priority": 1 } ] }""".getBytes(StandardCharsets.UTF_8);

    @Test
    void publishesTheVerifiedIrThroughTheNativeCoreAndEvaluatesThere() {
        FakeNativeCore core = new FakeNativeCore(true, 41);
        EdgeEngine engine = new EdgeEngine(AuthorizationGate.alwaysAuthorized(), core);

        assertEquals(41, engine.loadVerifiedBundle(IR));
        assertEquals(1, core.publishedIr().size(), "the IR must be handed to loadVerifiedIr");
        assertTrue(core.publishedIr().get(0).contains("psp_id"),
                "the native core must receive the unwrapped IR, not the §4 replay envelope");
        assertEquals(EdgeEngine.EVALUATOR_NATIVE, engine.activeEvaluator());

        Decision decision = engine.evaluate(Map.of("amount", 5000));
        assertEquals(1, core.evaluateCalls(), "evaluation must run on the native kernel");
        assertEquals(Action.BLOCK, decision.action());
        assertEquals(java.util.List.of(7L), decision.triggeredRuleIds());
    }

    @Test
    void aRejectedPublishKeepsThePreviousBundleServing() {
        FakeNativeCore core = new FakeNativeCore(true, 41);
        EdgeEngine engine = new EdgeEngine(AuthorizationGate.alwaysAuthorized(), core);
        engine.loadVerifiedBundle(IR);
        String hashBefore = engine.activeBundleHash();

        core.rejectNextPublish();
        // Structurally valid (passes the pre-native-call schema check the same way IR does) but a
        // different version, so this exercises the FakeNativeCore's forced -1 rejection path
        // specifically — not the JSON/schema pre-check, which now runs before native is ever
        // reached and would otherwise short-circuit before this scenario is exercised at all.
        byte[] structurallyValidButRejectedByNative = """
            { "version": 42, "psp_id": 7, "rules": [
              { "id": 8, "name": "another-rule",
                "condition": { "type": "cmp", "field": "amount", "op": "GT", "value": 200 },
                "action": "ALERT", "score": 10 } ] }""".getBytes(StandardCharsets.UTF_8);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> engine.loadVerifiedBundle(structurallyValidButRejectedByNative));

        assertTrue(e.getMessage().contains("still serving the previous bundle"), e.getMessage());
        assertEquals(41, engine.activeVersion(), "a -1 must never advance the active version");
        assertEquals(hashBefore, engine.activeBundleHash());
        assertEquals(EdgeEngine.EVALUATOR_NATIVE, engine.activeEvaluator());
    }

    @Test
    void aMalformedBundleIsRejectedBeforeEverReachingTheNativeCore() {
        // The pre-native structural check exists specifically so error quality does not depend on
        // which evaluator is active (found live: the native core's own rejection message named no
        // field or rule, only "malformed"). Proven here at the EdgeEngine level, not just inside
        // EdgeRuleInterpreter's own unit tests: native must never even be called.
        FakeNativeCore core = new FakeNativeCore(true, 41);
        EdgeEngine engine = new EdgeEngine(AuthorizationGate.alwaysAuthorized(), core);

        byte[] missingPspId = """
            { "version": 1, "rules": [
              { "id": 1, "name": "r",
                "condition": { "type": "cmp", "field": "amount", "op": "GT", "value": 1 },
                "action": "BLOCK" } ] }""".getBytes(StandardCharsets.UTF_8);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> engine.loadVerifiedBundle(missingPspId));

        assertTrue(e.getMessage().contains("psp_id"), e.getMessage());
        assertEquals(0, core.publishedIr().size(),
                "native must never be called for a bundle that fails the pre-check");
    }

    @Test
    void fallsBackToTheInterpreterOnlyWhenTheCoreIsAbsent() {
        FakeNativeCore core = new FakeNativeCore(false, 41);
        EdgeEngine engine = new EdgeEngine(AuthorizationGate.alwaysAuthorized(), core);

        assertEquals(41, engine.loadVerifiedBundle(IR));
        assertTrue(core.publishedIr().isEmpty(), "nothing may be pushed at an unavailable core");
        assertEquals(EdgeEngine.EVALUATOR_INTERPRETER, engine.activeEvaluator());

        assertEquals(Action.BLOCK, engine.evaluate(Map.of("amount", 5000)).action());
        assertEquals(0, core.evaluateCalls());
    }

    @Test
    void aNativeFaultDegradesToTheWarmStandbyRatherThanHoldingAllTraffic() {
        FakeNativeCore core = new FakeNativeCore(true, 41);
        EdgeEngine engine = new EdgeEngine(AuthorizationGate.alwaysAuthorized(), core);
        engine.loadVerifiedBundle(IR);
        assertTrue(engine.standbyReady(), "the verified IR must be mirrored into the interpreter");

        core.faultOnEvaluate();
        Decision decision = engine.evaluate(Map.of("amount", 5000));

        // The decision is still CORRECT — declining the PSP's whole live traffic would be worse.
        assertEquals(Action.BLOCK, decision.action());
        assertEquals(90, decision.score());
        assertEquals(java.util.List.of(7L), decision.triggeredRuleIds());

        assertTrue(engine.nativeDegraded(), "the degradation must be visible, not silent");
        assertEquals(EdgeEngine.EVALUATOR_INTERPRETER, engine.activeEvaluator());
        assertTrue(engine.nativeDegradedReason().contains("simulated native kernel fault"),
                engine.nativeDegradedReason());
        assertEquals(41, engine.activeVersion(), "the bundle is unchanged; only the evaluator moved");
    }

    @Test
    void theDegradationLatchesInsteadOfFlappingPerTransaction() {
        FakeNativeCore core = new FakeNativeCore(true, 41);
        EdgeEngine engine = new EdgeEngine(AuthorizationGate.alwaysAuthorized(), core);
        engine.loadVerifiedBundle(IR);
        core.faultOnEvaluate();

        for (int i = 0; i < 25; i++) {
            assertEquals(Action.BLOCK, engine.evaluate(Map.of("amount", 5000)).action());
        }
        assertEquals(1, core.evaluateCalls(), "the faulting kernel must be tried once, not per transaction");
    }

    @Test
    void aFreshBundlePublishReleasesTheDegradationLatch() {
        FakeNativeCore core = new FakeNativeCore(true, 41);
        EdgeEngine engine = new EdgeEngine(AuthorizationGate.alwaysAuthorized(), core);
        engine.loadVerifiedBundle(IR);
        core.faultOnEvaluate();
        engine.evaluate(Map.of("amount", 5000));
        assertTrue(engine.nativeDegraded());

        core.publishVersion(42);
        engine.loadVerifiedBundle(IR);

        assertFalse(engine.nativeDegraded(), "a fresh arena earns the native path another attempt");
        assertEquals(EdgeEngine.EVALUATOR_NATIVE, engine.activeEvaluator());
        assertEquals("", engine.nativeDegradedReason());
    }

    @Test
    void aNodeThatNeverLoadedABundleStillHolds() {
        FakeNativeCore core = new FakeNativeCore(true, 41);
        EdgeEngine engine = new EdgeEngine(AuthorizationGate.alwaysAuthorized(), core);

        // No bundle was ever verified: the standby is empty and HOLD is the only safe answer.
        assertFalse(engine.standbyReady());
        assertEquals(Action.HOLD, engine.evaluate(Map.of("amount", 5000)).action());

        // Same after a rejected publish — nothing was swapped, so nothing can decide.
        core.rejectNextPublish();
        assertThrows(IllegalStateException.class, () -> engine.loadVerifiedBundle(IR));
        assertFalse(engine.standbyReady());
        assertEquals(Action.HOLD, engine.evaluate(Map.of("amount", 5000)).action());
    }

    @Test
    void malformedIrOnTheInterpreterPathAlsoKeepsThePreviousBundle() {
        FakeNativeCore core = new FakeNativeCore(false, 41);
        EdgeEngine engine = new EdgeEngine(AuthorizationGate.alwaysAuthorized(), core);
        engine.loadVerifiedBundle(IR);

        assertThrows(IllegalArgumentException.class,
                () -> engine.loadVerifiedBundle("{ not json".getBytes(StandardCharsets.UTF_8)));
        assertEquals(41, engine.activeVersion());
        assertEquals(Action.BLOCK, engine.evaluate(Map.of("amount", 5000)).action());
    }
}
