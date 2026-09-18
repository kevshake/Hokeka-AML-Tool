package com.posgateway.aml.service.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import com.posgateway.aml.config.edge.EdgeProperties;
import com.posgateway.aml.edge.EdgeBundleService;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.edge.EdgeNodeStatus;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.entity.rules.RuleDefinition;
import com.posgateway.aml.repository.PspRepository;
import com.posgateway.aml.repository.edge.EdgeNodeRepository;
import com.posgateway.aml.repository.rules.RuleDefinitionRepository;
import com.posgateway.aml.service.security.PspIsolationService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.XECPublicKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wire-compatibility with the PSP edge host ({@code edge-host}, {@code com.hokeka.edge}).
 *
 * <p>The two modules are built independently, so nothing in the compiler stops them drifting apart.
 * These tests re-implement the edge side's exact codecs — {@code EdgeKeys.encodePublic} (base64
 * X.509 SPKI) and {@code SealedEnvelopeCodec.openPayloadBytes} (HSE-1 open, then contract §4 replay
 * envelope, then {@code payload}) — and assert the control plane speaks that dialect. A regression
 * here would otherwise surface in the field as an opaque decrypt failure.
 */
class EdgeWireCompatibilityTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void enrollmentAcceptsTheSpkiEncodedPublicKeysTheEdgeHostSends() throws Exception {
        Fixture f = new Fixture();
        KeyPair edgeX = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        KeyPair edgeEd = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();

        // EdgeKeys.encodePublic(key) == base64(key.getEncoded()) == base64 X.509 SPKI DER (44 B).
        String spkiX = Base64.getEncoder().encodeToString(edgeX.getPublic().getEncoded());
        String spkiEd = Base64.getEncoder().encodeToString(edgeEd.getPublic().getEncoded());
        assertEquals(44, Base64.getDecoder().decode(spkiX).length);
        assertEquals(44, Base64.getDecoder().decode(spkiEd).length);

        EdgeNode node = f.activate("acme-eu-1", spkiX, spkiEd);

        // Canonicalised to the raw 32-byte form HSE-1 consumes directly.
        assertArrayEquals(HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) edgeX.getPublic()),
                Base64.getDecoder().decode(node.getEdgeX25519PublicKey()));
        assertArrayEquals(EdgeControlPlaneKeys.rawEd25519Public((EdECPublicKey) edgeEd.getPublic()),
                Base64.getDecoder().decode(node.getEdgeEd25519PublicKey()));
    }

    @Test
    void theSameKeyInSpkiAndRawFormIsTreatedAsOneAndTheSamePinnedKey() throws Exception {
        Fixture f = new Fixture();
        KeyPair edgeX = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        KeyPair edgeEd = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String rawX = Base64.getEncoder().encodeToString(
                HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) edgeX.getPublic()));
        String rawEd = Base64.getEncoder().encodeToString(
                EdgeControlPlaneKeys.rawEd25519Public((EdECPublicKey) edgeEd.getPublic()));

        EdgeNode viaSpki = f.activate("acme-eu-1",
                Base64.getEncoder().encodeToString(edgeX.getPublic().getEncoded()),
                Base64.getEncoder().encodeToString(edgeEd.getPublic().getEncoded()));

        assertEquals(rawX, viaSpki.getEdgeX25519PublicKey());
        assertEquals(rawEd, viaSpki.getEdgeEd25519PublicKey());
    }

    @Test
    void theSealedBundleOpensExactlyAsTheEdgeHostCodecOpensIt() throws Exception {
        RuleDefinitionRepository rules = mock(RuleDefinitionRepository.class);
        when(rules.findByEnabledTrueAndPspIdOrderByPriorityDesc(anyLong())).thenReturn(List.of(rule()));

        EdgeBundleService bundleService = new EdgeBundleService();
        EdgeControlPlaneKeys keys = new EdgeControlPlaneKeys(new EdgeProperties());
        EdgeBundleDistributionService distribution = new EdgeBundleDistributionService(
                rules, new EdgeRuleCompiler(bundleService), bundleService, keys,
                new HokekaSecureEnvelope());

        KeyPair edgeX = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        EdgeNode node = new EdgeNode();
        node.setId(1L);
        node.setPspId(42L);
        node.setEdgeId("acme-eu-1");
        node.setStatus(EdgeNodeStatus.ACTIVE);
        node.setEdgeX25519PublicKey(Base64.getEncoder().encodeToString(
                HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) edgeX.getPublic())));

        byte[] sealed = distribution.sealFor(node).sealed();

        // --- edge-host SealedEnvelopeCodec.openPayloadBytes, step for step -----------------------
        // 1) the edge pins the control-plane signing key from base64 SPKI *or* raw 32 bytes.
        PublicKey pinned = decodeEd25519PublicLikeTheEdgeDoes(keys.signingPublicKeyBase64());
        // 2) HSE-1 open under the rule-bundle context.
        byte[] plaintext = new HokekaSecureEnvelope().open(sealed, edgeX.getPrivate(), pinned,
                "hokeka.rules.bundle".getBytes(StandardCharsets.UTF_8));
        // 3) contract §4 replay envelope: nonce / issuedAt / edgeId / payload.
        JsonNode root = mapper.readTree(plaintext);
        assertTrue(root.isObject() && root.hasNonNull("payload"), "envelope must carry a payload");
        assertEquals("acme-eu-1", root.path("edgeId").asText());
        assertEquals(22, root.path("nonce").asText().length());
        Instant.parse(root.path("issuedAt").asText());
        // 4) the payload is the rule IR the interpreter loads.
        JsonNode ir = root.get("payload");
        assertEquals(42, ir.get("psp_id").asInt());
        assertEquals(1, ir.get("rules").size());
    }

    @Test
    void theMetricsAllowListMatchesTheEdgeHostReportRecordExactly() {
        // Field-for-field with edge-host channel/EdgeMetricsReport.
        EdgeMetricsIngestService service = new EdgeMetricsIngestService(
                mock(com.posgateway.aml.repository.edge.EdgeMetricsRecordRepository.class));

        EdgeNode node = new EdgeNode();
        node.setId(1L);
        node.setPspId(42L);
        node.setEdgeId("acme-eu-1");

        var payload = mapper.createObjectNode();
        payload.put("pspId", "42");
        payload.put("edgeId", "acme-eu-1");
        payload.put("windowStartEpochMs", 1L);
        payload.put("windowEndEpochMs", 2L);
        payload.put("ruleBundleVersion", 37L);
        payload.put("ruleBundleHash", "hash");
        payload.put("totalEvaluated", 10L);
        payload.put("allowed", 7L);
        payload.put("alerted", 1L);
        payload.put("held", 1L);
        payload.put("blocked", 1L);
        payload.putObject("ruleHitCounts").put("100", 3L);
        payload.put("p50LatencyMicros", 1.0);
        payload.put("p95LatencyMicros", 2.0);
        payload.put("p99LatencyMicros", 3.0);
        payload.put("engineHealth", "OK");

        var report = service.validate(node, payload);
        assertEquals(10L, report.totalEvaluated());
        assertEquals(Map.of(100L, 3L), report.ruleHitCounts());
        assertEquals(37L, report.ruleBundleVersion());
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /** Mirror of {@code EdgeKeys.decodeEd25519Public}: base64 SPKI DER or raw 32 bytes. */
    private static PublicKey decodeEd25519PublicLikeTheEdgeDoes(String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        return bytes.length == 32
                ? EdgeControlPlaneKeys.ed25519PublicFromRaw(bytes)
                : rebuildFromSpki(bytes);
    }

    private static PublicKey rebuildFromSpki(byte[] der) {
        try {
            return java.security.KeyFactory.getInstance("Ed25519")
                    .generatePublic(new java.security.spec.X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static RuleDefinition rule() {
        RuleDefinition r = new RuleDefinition();
        r.setId(100L);
        r.setName("VPN hold");
        r.setAction("HOLD");
        r.setScore(25);
        r.setPriority(5);
        r.setRuleJson("{\"conditions\":{\"field\":\"ip_vpn_or_proxy\",\"operator\":\"EQUALS\","
                + "\"value\":true}}");
        r.setEnabled(true);
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        return r;
    }

    /** Minimal in-memory enrollment fixture that gets a node all the way to ACTIVE. */
    private static final class Fixture {
        private final Map<Long, EdgeNode> table = new HashMap<>();
        private final AtomicLong ids = new AtomicLong();
        private final EdgeEnrollmentService service;

        Fixture() {
            EdgeNodeRepository repo = mock(EdgeNodeRepository.class);
            PspRepository psps = mock(PspRepository.class);
            PspIsolationService isolation = mock(PspIsolationService.class);

            Psp psp = new Psp();
            psp.setPspId(42L);
            psp.setStatus("ACTIVE");
            when(psps.findById(42L)).thenReturn(Optional.of(psp));

            when(repo.save(any(EdgeNode.class))).thenAnswer(i -> {
                EdgeNode n = i.getArgument(0);
                if (n.getId() == null) {
                    n.setId(ids.incrementAndGet());
                }
                table.put(n.getId(), n);
                return n;
            });
            when(repo.findById(anyLong()))
                    .thenAnswer(i -> Optional.ofNullable(table.get(i.<Long>getArgument(0))));
            when(repo.findByEnrollmentCodeHash(any())).thenAnswer(i -> {
                String hash = i.getArgument(0);
                return table.values().stream()
                        .filter(n -> hash.equals(n.getEnrollmentCodeHash())).findFirst();
            });
            when(repo.findByEdgeId(any())).thenAnswer(i -> {
                String edgeId = i.getArgument(0);
                return table.values().stream()
                        .filter(n -> edgeId.equals(n.getEdgeId())).findFirst();
            });

            service = new EdgeEnrollmentService(repo, psps, isolation);
        }

        EdgeNode activate(String edgeId, String x25519, String ed25519) {
            var requested = service.requestNode(42L, "Acme EU 1", "psp-admin");
            service.approve(requested.node().getId(), "platform-admin");
            return service.activate(requested.rawEnrollmentCode(), edgeId, "42", x25519,
                    ed25519, "edge01", "1.0.0");
        }
    }
}
