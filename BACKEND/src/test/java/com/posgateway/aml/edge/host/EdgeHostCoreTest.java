package com.posgateway.aml.edge.host;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.EdgeBundleService;
import com.posgateway.aml.edge.EdgeMetricsReport;
import com.posgateway.aml.edge.host.EdgeRuleInterpreter.Action;
import com.posgateway.aml.edge.host.EdgeRuleInterpreter.Decision;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdgeHostCoreTest {

    private final EdgeBundleService svc = new EdgeBundleService();

    // ── Rule interpreter — must agree with the Rust rule-core crate ───────────────────────────────

    @Test
    void interpreterMatchesRustSemantics() {
        // VPN-hold + a combined AND/OR/NOT rule (same shapes as the Rust unit tests).
        ObjectNode vpn = svc.cmp("ip_vpn_or_proxy", "EQ", true);
        ObjectNode combo = svc.all(
                svc.cmp("amount", "GT", 1000),
                svc.any(
                        svc.cmp("mcc", "IN", "5411,5812,7995"),
                        svc.cmp("ip_vpn_or_proxy", "EQ", true)),
                svc.not(svc.cmp("allowlisted", "EQ", true)));

        byte[] bundleJson = svc.buildBundleJson(1, 42, List.of(
                new EdgeBundleService.CompiledRule(100, "VPN hold", null, vpn, "HOLD", 25, 5),
                new EdgeBundleService.CompiledRule(7, "Combo", "combo hit", combo, "BLOCK", 40, 9)));

        EdgeRuleInterpreter engine = new EdgeRuleInterpreter();
        assertEquals(1, engine.loadBundle(bundleJson));

        // Both rules fire → strongest action BLOCK, score 25+40.
        Decision d = engine.evaluate(Map.of(
                "ip_vpn_or_proxy", true,
                "amount", 2000,
                "mcc", "5812",
                "allowlisted", false));
        assertEquals(Action.BLOCK, d.action());
        assertEquals(65, d.score());
        assertEquals(List.of(100L, 7L), d.triggeredRuleIds());

        // Combo misses (mcc not in set, not vpn) → only nothing fires here.
        Decision miss = engine.evaluate(Map.of(
                "ip_vpn_or_proxy", false,
                "amount", 2000,
                "mcc", "1234",
                "allowlisted", false));
        assertEquals(Action.ALLOW, miss.action());
        assertTrue(miss.triggeredRuleIds().isEmpty());
    }

    @Test
    void interpreterFailsClosedWithNoBundle() {
        EdgeRuleInterpreter engine = new EdgeRuleInterpreter();
        Decision d = engine.evaluate(Map.of("amount", 5));
        assertEquals(Action.HOLD, d.action());
    }

    // ── Metrics aggregator — counts only, no PII ─────────────────────────────────────────────────

    @Test
    void aggregatorProducesAggregateOnlyReport() {
        EdgeMetricsAggregator agg = new EdgeMetricsAggregator("psp-42", "edge-1", 1024, 1000L);
        agg.record(new Decision(Action.ALLOW, 0, List.of(), List.of()), 120.0);
        agg.record(new Decision(Action.BLOCK, 50, List.of(100L), List.of("vpn")), 340.0);
        agg.record(new Decision(Action.HOLD, 25, List.of(100L, 7L), List.of("x")), 210.0);

        EdgeMetricsReport r = agg.snapshotAndReset(37, "hash-abc", "HEALTHY", 2000L);
        assertEquals(3, r.totalEvaluated());
        assertEquals(1, r.allowed());
        assertEquals(1, r.blocked());
        assertEquals(1, r.held());
        assertEquals(3, r.totalDecisions());
        assertEquals(2L, r.ruleHitCounts().get(100L)); // rule 100 fired twice
        assertEquals(1L, r.ruleHitCounts().get(7L));
        assertTrue(r.p99LatencyMicros() >= r.p50LatencyMicros());
        assertEquals(37, r.ruleBundleVersion());

        // Report carries only aggregates — its serialized form contains no PAN/customer/amount fields.
        String json = assertDoesNotThrowSerialize(r);
        for (String forbidden : new String[]{"pan", "customer", "amount", "cardNumber", "ipAddress"}) {
            assertTrue(json.toLowerCase().indexOf(forbidden) < 0, "metrics must not contain '" + forbidden + "'");
        }
    }

    private String assertDoesNotThrowSerialize(EdgeMetricsReport r) {
        try {
            return new ObjectMapper().writeValueAsString(r);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    // ── Offline token validator ──────────────────────────────────────────────────────────────────

    @Test
    void validatesCentralIssuedTokenOffline() throws Exception {
        KeyPair cp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        EdgeTokenValidator validator = new EdgeTokenValidator(cp.getPublic(), "hokeka-control-plane", "edge-1");

        long now = 1_000_000L;
        String token = issueToken(cp, "hokeka-control-plane", "psp-api-node-9", "edge-1", now + 300, now);

        var claims = validator.validate(token, Instant.ofEpochSecond(now + 10));
        assertEquals("psp-api-node-9", claims.subject());
    }

    @Test
    void rejectsExpiredWrongSignerAndTamperedTokens() throws Exception {
        KeyPair cp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair attacker = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        EdgeTokenValidator validator = new EdgeTokenValidator(cp.getPublic(), "hokeka-control-plane", "edge-1");
        long now = 1_000_000L;

        String expired = issueToken(cp, "hokeka-control-plane", "n", "edge-1", now - 500, now - 800);
        assertThrows(SecurityException.class, () -> validator.validate(expired, Instant.ofEpochSecond(now)));

        String wrongSigner = issueToken(attacker, "hokeka-control-plane", "n", "edge-1", now + 300, now);
        assertThrows(SecurityException.class, () -> validator.validate(wrongSigner, Instant.ofEpochSecond(now)));

        String wrongAudience = issueToken(cp, "hokeka-control-plane", "n", "some-other-edge", now + 300, now);
        assertThrows(SecurityException.class, () -> validator.validate(wrongAudience, Instant.ofEpochSecond(now)));

        String good = issueToken(cp, "hokeka-control-plane", "n", "edge-1", now + 300, now);
        String tampered = good.substring(0, 5) + (good.charAt(5) == 'A' ? 'B' : 'A') + good.substring(6);
        assertThrows(SecurityException.class, () -> validator.validate(tampered, Instant.ofEpochSecond(now)));
    }

    /** Control-plane side: sign claims → compact token (base64url(claims).base64url(sig)). */
    private static String issueToken(KeyPair cp, String iss, String sub, String aud, long exp, long iat)
            throws Exception {
        String claims = String.format(
                "{\"iss\":\"%s\",\"sub\":\"%s\",\"aud\":\"%s\",\"exp\":%d,\"iat\":%d}", iss, sub, aud, exp, iat);
        String claimsSeg = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(cp.getPrivate());
        s.update(claimsSeg.getBytes(StandardCharsets.US_ASCII));
        String sig = Base64.getUrlEncoder().withoutPadding().encodeToString(s.sign());
        return claimsSeg + "." + sig;
    }
}
