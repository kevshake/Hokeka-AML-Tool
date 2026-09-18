package com.posgateway.aml.service.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import com.posgateway.aml.config.edge.EdgeProperties;
import com.posgateway.aml.edge.EdgeBundleService;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.edge.host.EdgeRuleInterpreter;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.edge.EdgeNodeStatus;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end proof of the rules-down half of the channel: authored {@code rule_json} → edge Rule IR
 * → HSE-1 sealed bundle → opened at the edge → evaluated by the reference interpreter with the
 * decision the central rule intended.
 */
class EdgeRuleCompilerTest {

    private EdgeBundleService bundleService;
    private EdgeRuleCompiler compiler;

    @BeforeEach
    void setUp() {
        bundleService = new EdgeBundleService();
        compiler = new EdgeRuleCompiler(bundleService);
    }

    @Test
    void compilesNestedAllAnyNotGroupsOntoTheIr() {
        RuleDefinition rule = ruleDefinition(1L, "High value from a risky country", "BLOCK", 80, 10,
                """
                {"conditions":{"all":[
                   {"field":"amount","operator":"GREATER_THAN","value":10000},
                   {"any":[
                      {"field":"country_code","operator":"IN","value":["IR","KP","SY"]},
                      {"field":"ip_vpn_or_proxy","operator":"EQUALS","value":true}
                   ]},
                   {"not":{"field":"channel","operator":"EQUALS","value":"BRANCH"}}
                ]}}
                """);

        EdgeRuleCompiler.CompilationResult result = compiler.compile(List.of(rule));

        assertEquals(1, result.rules().size());
        assertTrue(result.skipped().isEmpty());
        EdgeBundleService.CompiledRule compiled = result.rules().get(0);
        assertEquals("BLOCK", compiled.action());
        assertEquals(80, compiled.score());
        assertEquals("all", compiled.condition().get("type").asText());

        // The interpreter is the semantics oracle: same IR, same decision as the Rust edge kernel.
        EdgeRuleInterpreter interpreter = new EdgeRuleInterpreter();
        interpreter.loadBundle(bundleService.buildBundleJson(result.version(), 42L, result.rules()));

        assertEquals(EdgeRuleInterpreter.Action.BLOCK, interpreter.evaluate(Map.of(
                "amount", 25000, "country_code", "KP", "channel", "ECOM")).action());
        assertEquals(EdgeRuleInterpreter.Action.BLOCK, interpreter.evaluate(Map.of(
                "amount", 25000, "country_code", "GB", "ip_vpn_or_proxy", true,
                "channel", "ECOM")).action());
        // Below the threshold — no rule fires.
        assertEquals(EdgeRuleInterpreter.Action.ALLOW, interpreter.evaluate(Map.of(
                "amount", 100, "country_code", "KP", "channel", "ECOM")).action());
        // The NOT arm excludes branch traffic.
        assertEquals(EdgeRuleInterpreter.Action.ALLOW, interpreter.evaluate(Map.of(
                "amount", 25000, "country_code", "KP", "channel", "BRANCH")).action());
    }

    @Test
    void anUncompilableRuleIsOmittedRatherThanEmittedPermissively() {
        RuleDefinition drlOnly = ruleDefinition(2L, "Drools only", "HOLD", 10, 1, null);
        RuleDefinition unknownField = ruleDefinition(3L, "Unknown field", "HOLD", 10, 1,
                "{\"conditions\":{\"field\":\"astrology_sign\",\"operator\":\"EQUALS\",\"value\":\"leo\"}}");
        RuleDefinition good = ruleDefinition(4L, "MCC block", "BLOCK", 50, 5,
                "{\"conditions\":{\"field\":\"mcc\",\"operator\":\"EQUALS\",\"value\":\"7995\"}}");

        EdgeRuleCompiler.CompilationResult result =
                compiler.compile(List.of(drlOnly, unknownField, good));

        assertEquals(1, result.rules().size());
        assertEquals(4L, result.rules().get(0).id());
        assertEquals(List.of("Drools only", "Unknown field"), result.skipped());
    }

    @Test
    void theVersionChangesWhenTheRuleSetChanges() {
        RuleDefinition a = ruleDefinition(1L, "A", "ALERT", 5, 1,
                "{\"conditions\":{\"field\":\"mcc\",\"operator\":\"EQUALS\",\"value\":\"5411\"}}");
        RuleDefinition b = ruleDefinition(2L, "B", "ALERT", 5, 1,
                "{\"conditions\":{\"field\":\"mcc\",\"operator\":\"EQUALS\",\"value\":\"5812\"}}");

        long one = compiler.compile(List.of(a)).version();
        long two = compiler.compile(List.of(a, b)).version();
        assertTrue(two > one, "adding a rule must advance the version (" + one + " -> " + two + ")");
    }

    @Test
    void nonIrActionsAreMappedOntoTheEdgeActionSet() {
        RuleDefinition review = ruleDefinition(5L, "Review", "REVIEW", 10, 1,
                "{\"conditions\":{\"field\":\"mcc\",\"operator\":\"EQUALS\",\"value\":\"5411\"}}");
        assertEquals("ALERT", compiler.compile(List.of(review)).rules().get(0).action());

        RuleDefinition blockTxn = ruleDefinition(6L, "Block", "BLOCK_TRANSACTION", 10, 1,
                "{\"conditions\":{\"field\":\"mcc\",\"operator\":\"EQUALS\",\"value\":\"5411\"}}");
        assertEquals("BLOCK", compiler.compile(List.of(blockTxn)).rules().get(0).action());
    }

    @Test
    void theSealedBundleOpensOnlyWithTheEdgesPinnedKeyAndTheBundleContext() throws Exception {
        RuleDefinitionRepository repository = mock(RuleDefinitionRepository.class);
        RuleDefinition rule = ruleDefinition(1L, "VPN hold", "HOLD", 25, 5,
                "{\"conditions\":{\"field\":\"ip_vpn_or_proxy\",\"operator\":\"EQUALS\",\"value\":true}}");
        when(repository.findByEnabledTrueAndPspIdOrderByPriorityDesc(anyLong()))
                .thenReturn(List.of(rule));

        EdgeControlPlaneKeys keys = new EdgeControlPlaneKeys(new EdgeProperties());
        EdgeBundleDistributionService distribution = new EdgeBundleDistributionService(
                repository, compiler, bundleService, keys, new HokekaSecureEnvelope());

        KeyPair edgeX = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        EdgeNode node = new EdgeNode();
        node.setId(1L);
        node.setPspId(42L);
        node.setEdgeId("acme-eu-1");
        node.setStatus(EdgeNodeStatus.ACTIVE);
        node.setEdgeX25519PublicKey(Base64.getEncoder().encodeToString(
                HokekaSecureEnvelope.rawFromX25519Public(
                        (java.security.interfaces.XECPublicKey) edgeX.getPublic())));

        EdgeBundleDistributionService.SealedBundle sealed = distribution.sealFor(node);

        byte[] plaintext = new HokekaSecureEnvelope().open(sealed.sealed(), edgeX.getPrivate(),
                keys.signingPublicKey(),
                EdgeBundleDistributionService.BUNDLE_CONTEXT.getBytes(StandardCharsets.UTF_8));

        // Contract §4: the plaintext is the replay envelope, and the rule IR is its `payload` —
        // this is exactly what the edge host's SealedEnvelopeCodec.openPayloadBytes expects.
        JsonNode replayEnvelope = new ObjectMapper().readTree(plaintext);
        assertEquals("acme-eu-1", replayEnvelope.get("edgeId").asText());
        assertEquals(22, replayEnvelope.get("nonce").asText().length(),
                "22-char base64url over 16 random bytes");
        Instant.parse(replayEnvelope.get("issuedAt").asText()); // must be ISO-8601
        JsonNode ir = replayEnvelope.get("payload");
        assertTrue(ir.isObject() && ir.has("rules"));

        EdgeRuleInterpreter interpreter = new EdgeRuleInterpreter();
        assertEquals(sealed.version(),
                interpreter.loadBundle(new ObjectMapper().writeValueAsBytes(ir)));
        assertEquals(EdgeRuleInterpreter.Action.HOLD,
                interpreter.evaluate(Map.of("ip_vpn_or_proxy", true)).action());

        // A different edge's key cannot open it.
        KeyPair otherEdge = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        org.junit.jupiter.api.Assertions.assertThrows(SecurityException.class,
                () -> new HokekaSecureEnvelope().open(sealed.sealed(), otherEdge.getPrivate(),
                        keys.signingPublicKey(),
                        EdgeBundleDistributionService.BUNDLE_CONTEXT.getBytes(StandardCharsets.UTF_8)));

        // Nor can the correct edge open it under the metrics context.
        org.junit.jupiter.api.Assertions.assertThrows(SecurityException.class,
                () -> new HokekaSecureEnvelope().open(sealed.sealed(), edgeX.getPrivate(),
                        keys.signingPublicKey(),
                        EdgeMetricsIngestService.METRICS_CONTEXT.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void refusesToSealAZeroRuleBundleBecauseItWouldAllowEverything() throws Exception {
        RuleDefinitionRepository repository = mock(RuleDefinitionRepository.class);
        // A rule with no structured rule_json cannot be compiled to IR, so it is skipped — which is
        // exactly how the whole seeded catalogue silently produces an EMPTY (fully permissive) bundle.
        RuleDefinition uncompilable = ruleDefinition(1L, "SpEL only", "HOLD", 25, 5, null);
        when(repository.findByEnabledTrueAndPspIdOrderByPriorityDesc(anyLong()))
                .thenReturn(List.of(uncompilable));

        EdgeControlPlaneKeys keys = new EdgeControlPlaneKeys(new EdgeProperties());
        EdgeBundleDistributionService distribution = new EdgeBundleDistributionService(
                repository, compiler, bundleService, keys, new HokekaSecureEnvelope());

        KeyPair edgeX = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        EdgeNode node = new EdgeNode();
        node.setId(1L);
        node.setPspId(42L);
        node.setEdgeId("acme-eu-1");
        node.setStatus(EdgeNodeStatus.ACTIVE);
        node.setEdgeX25519PublicKey(Base64.getEncoder().encodeToString(
                HokekaSecureEnvelope.rawFromX25519Public(
                        (java.security.interfaces.XECPublicKey) edgeX.getPublic())));

        org.junit.jupiter.api.Assertions.assertThrows(
                EdgeBundleDistributionService.EmptyBundleException.class,
                () -> distribution.sealFor(node),
                "an empty rule set must never be sealed and served — the edge would allow all traffic");
    }

    private static RuleDefinition ruleDefinition(long id, String name, String action, int score,
                                                 int priority, String ruleJson) {
        RuleDefinition r = new RuleDefinition();
        r.setId(id);
        r.setName(name);
        r.setDescription(name);
        r.setAction(action);
        r.setScore(score);
        r.setPriority(priority);
        r.setRuleJson(ruleJson);
        r.setRuleType(ruleJson == null ? "DROOLS_DRL" : "SPEL");
        r.setEnabled(true);
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        return r;
    }
}
