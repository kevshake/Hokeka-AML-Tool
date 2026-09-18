package com.hokeka.edge;

import com.hokeka.edge.EdgeRuleInterpreter.Action;
import com.hokeka.edge.EdgeRuleInterpreter.Decision;
import com.hokeka.edge.activation.AuthorizationGate;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The edge host must be fully functional even without the native core (the container ships that way
 * until CI stages libedge_engine.so) and must fail closed before it is authorized.
 */
class EdgeEngineTest {

    private static final String BUNDLE = """
        { "version": 37, "psp_id": 42, "rules": [
          { "id": 100, "name": "VPN hold",
            "condition": { "type": "cmp", "field": "ip_vpn_or_proxy", "op": "EQ", "value": true },
            "action": "HOLD", "score": 25, "priority": 5 } ] }""";

    private static final AuthorizationGate UNAUTHORIZED = new AuthorizationGate() {
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
            return "awaiting operator approval";
        }
    };

    @Test
    void evaluatesViaFallbackWhenNativeAbsent() {
        EdgeEngine engine = new EdgeEngine(AuthorizationGate.alwaysAuthorized());
        assertEquals(37, engine.loadPlainBundle(BUNDLE.getBytes(StandardCharsets.UTF_8)));

        Decision hit = engine.evaluate(Map.of("ip_vpn_or_proxy", true, "amount", 9000));
        assertEquals(Action.HOLD, hit.action());
        assertEquals(25, hit.score());
        assertEquals(java.util.List.of(100L), hit.triggeredRuleIds());

        Decision miss = engine.evaluate(Map.of("ip_vpn_or_proxy", false));
        assertEquals(Action.ALLOW, miss.action());
    }

    @Test
    void failsClosedWithNoBundle() {
        EdgeEngine engine = new EdgeEngine(AuthorizationGate.alwaysAuthorized());
        assertEquals(Action.HOLD, engine.evaluate(Map.of("amount", 5)).action());
        // Native is not present in the test JVM → engine transparently uses the interpreter.
        assertTrue(!engine.nativeAvailable());
    }

    @Test
    void holdsWhileUnauthorizedEvenWithABundleLoaded() {
        EdgeEngine engine = new EdgeEngine(UNAUTHORIZED);
        engine.loadPlainBundle(BUNDLE.getBytes(StandardCharsets.UTF_8));

        Decision decision = engine.evaluate(Map.of("ip_vpn_or_proxy", false));
        assertEquals(Action.HOLD, decision.action(), "an unauthorized node must never ALLOW");
        assertEquals(AuthorizationGate.STATE_UNAUTHORIZED, engine.authorizationState());
        assertTrue(decision.reasons().get(0).contains("not authorized"));
    }

    @Test
    void reportsTheRunningBundleHashForAttestation() {
        EdgeEngine engine = new EdgeEngine(AuthorizationGate.alwaysAuthorized());
        engine.loadPlainBundle(BUNDLE.getBytes(StandardCharsets.UTF_8));
        assertTrue(engine.activeBundleHash().startsWith("sha256:"));
        assertEquals(71, engine.activeBundleHash().length());
    }
}
