package com.posgateway.aml.controller.edge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import com.posgateway.aml.config.edge.EdgeProperties;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.entity.edge.EdgeMetricsRecord;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.edge.EdgeNodeStatus;
import com.posgateway.aml.repository.edge.EdgeMetricsRecordRepository;
import com.posgateway.aml.service.edge.EdgeBundleDistributionService;
import com.posgateway.aml.service.edge.EdgeEnrollmentException;
import com.posgateway.aml.service.edge.EdgeEnrollmentService;
import com.posgateway.aml.service.edge.EdgeIdentityResolver;
import com.posgateway.aml.service.edge.EdgeMetricsIngestService;
import com.posgateway.aml.service.edge.EdgeReplayGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.XECPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for the machine-to-machine edge endpoints (contract §5), driven through MockMvc with
 * real HSE-1 crypto — sealed payloads are produced exactly as an edge would produce them.
 *
 * <p>Covers the fail-closed table of contract §2: no verified client certificate, a spoofed header
 * from an untrusted remote, and a revoked edge all get {@code 403} with <b>no bundle bytes</b>.
 */
class EdgeDistributionControllerTest {

    private static final String EDGE_ID = "acme-eu-1";
    private static final byte[] BUNDLE_BYTES = "SEALED-BUNDLE".getBytes(StandardCharsets.UTF_8);

    private MockMvc mockMvc;
    private EdgeEnrollmentService enrollmentService;
    private EdgeBundleDistributionService distributionService;
    private EdgeMetricsRecordRepository metricsRepository;
    private EdgeControlPlaneKeys controlPlaneKeys;

    private EdgeNode node;
    private PrivateKey edgeEd25519Priv;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HokekaSecureEnvelope envelope = new HokekaSecureEnvelope();

    @BeforeEach
    void setUp() throws Exception {
        EdgeProperties properties = new EdgeProperties();
        controlPlaneKeys = new EdgeControlPlaneKeys(properties);

        enrollmentService = mock(EdgeEnrollmentService.class);
        distributionService = mock(EdgeBundleDistributionService.class);
        metricsRepository = mock(EdgeMetricsRecordRepository.class);
        when(metricsRepository.save(any(EdgeMetricsRecord.class))).thenAnswer(i -> i.getArgument(0));

        KeyPair edgeX = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        KeyPair edgeEd = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        edgeEd25519Priv = edgeEd.getPrivate();

        node = new EdgeNode();
        node.setId(7L);
        node.setPspId(42L);
        node.setEdgeId(EDGE_ID);
        node.setDisplayName("Acme EU 1");
        node.setStatus(EdgeNodeStatus.ACTIVE);
        node.setEdgeX25519PublicKey(Base64.getEncoder().encodeToString(
                HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) edgeX.getPublic())));
        node.setEdgeEd25519PublicKey(Base64.getEncoder().encodeToString(
                EdgeControlPlaneKeys.rawEd25519Public((EdECPublicKey) edgeEd.getPublic())));

        when(enrollmentService.findAuthorized(EDGE_ID)).thenReturn(Optional.of(node));
        when(distributionService.currentVersion(42L)).thenReturn(37L);
        when(distributionService.sealFor(node)).thenReturn(
                new EdgeBundleDistributionService.SealedBundle(37L, BUNDLE_BYTES, 3, List.of()));

        EdgeDistributionController controller = new EdgeDistributionController(
                new EdgeIdentityResolver(properties),
                enrollmentService,
                distributionService,
                new EdgeMetricsIngestService(metricsRepository),
                new EdgeReplayGuard(),
                controlPlaneKeys,
                properties,
                envelope);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ── GET /edge/bundle ─────────────────────────────────────────────────────────────────────────

    @Test
    void bundleIsRefusedWithoutAVerifiedClientCertificate() throws Exception {
        byte[] body = mockMvc.perform(get("/edge/bundle"))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsByteArray();

        assertEquals(0, body.length, "no bundle bytes may leak to an unauthenticated caller");
        verify(distributionService, never()).sealFor(any());
    }

    @Test
    void aBareEdgeIdHeaderIsNeverSufficientOnItsOwn() throws Exception {
        mockMvc.perform(get("/edge/bundle").header(EdgeIdentityResolver.EDGE_ID_HEADER, EDGE_ID))
                .andExpect(status().isForbidden());
        verify(distributionService, never()).sealFor(any());
    }

    @Test
    void forwardedCertificateHeadersFromAnUntrustedRemoteAreIgnored() throws Exception {
        mockMvc.perform(edgeRequest(get("/edge/bundle")).with(request -> {
                    request.setRemoteAddr("203.0.113.9"); // not on edge.mtls.trusted-proxies
                    return request;
                }))
                .andExpect(status().isForbidden());
        verify(distributionService, never()).sealFor(any());
    }

    @Test
    void anActiveEdgeIsServedTheSealedBundleWithAVersionEtag() throws Exception {
        var result = mockMvc.perform(edgeRequest(get("/edge/bundle")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("ETag", "\"37\""))
                .andExpect(header().string("X-Edge-Bundle-Version", "37"))
                .andReturn();

        assertArrayEquals(BUNDLE_BYTES, result.getResponse().getContentAsByteArray());
        verify(enrollmentService).recordBundleDelivery(7L, 37L, null);
    }

    @Test
    void ifNoneMatchOnTheCurrentVersionYields304AndNoSealingWork() throws Exception {
        mockMvc.perform(edgeRequest(get("/edge/bundle")).header("If-None-Match", "\"37\""))
                .andExpect(status().isNotModified());

        verify(distributionService, never()).sealFor(any());
        verify(enrollmentService).recordSeen(7L, null);
    }

    @Test
    void ifNoneMatchOnAStaleVersionStillServesTheBundle() throws Exception {
        mockMvc.perform(edgeRequest(get("/edge/bundle")).header("If-None-Match", "\"36\""))
                .andExpect(status().isOk());
    }

    @Test
    void aRevokedEdgeGetsForbiddenAndNoBundleBytes() throws Exception {
        when(enrollmentService.findAuthorized(EDGE_ID)).thenReturn(Optional.empty());

        byte[] body = mockMvc.perform(edgeRequest(get("/edge/bundle")))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsByteArray();

        assertEquals(0, body.length);
        verify(distributionService, never()).sealFor(any());
    }

    @Test
    void anEdgeIdHeaderThatContradictsTheCertificateIsRefused() throws Exception {
        mockMvc.perform(edgeRequest(get("/edge/bundle"))
                        .header(EdgeIdentityResolver.EDGE_ID_HEADER, "someone-else"))
                .andExpect(status().isForbidden());
        verify(distributionService, never()).sealFor(any());
    }

    // ── POST /edge/metrics ───────────────────────────────────────────────────────────────────────

    @Test
    void aSealedAggregateMetricsUploadIsAcceptedAndPersisted() throws Exception {
        byte[] sealed = sealMetrics(aggregatePayload(), EDGE_ID, Instant.now(), newNonce());

        mockMvc.perform(edgeRequest(post("/edge/metrics"))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(sealed))
                .andExpect(status().isNoContent());

        verify(metricsRepository).save(any(EdgeMetricsRecord.class));
        verify(enrollmentService).recordSeen(anyLong(), any());
    }

    @Test
    void aMetricsPayloadCarryingPerTransactionFieldsIsRejected() throws Exception {
        ObjectNode payload = aggregatePayload();
        payload.put("panHash", "3f2b9c");

        mockMvc.perform(edgeRequest(post("/edge/metrics"))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(sealMetrics(payload, EDGE_ID, Instant.now(), newNonce())))
                .andExpect(status().isForbidden());

        verify(metricsRepository, never()).save(any());
    }

    @Test
    void aReplayedEnvelopeIsRejected() throws Exception {
        byte[] sealed = sealMetrics(aggregatePayload(), EDGE_ID, Instant.now(), newNonce());

        mockMvc.perform(edgeRequest(post("/edge/metrics"))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(sealed))
                .andExpect(status().isNoContent());
        mockMvc.perform(edgeRequest(post("/edge/metrics"))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(sealed))
                .andExpect(status().isForbidden());
    }

    @Test
    void anEnvelopeOutsideTheSkewWindowIsRejected() throws Exception {
        byte[] sealed = sealMetrics(aggregatePayload(), EDGE_ID,
                Instant.now().minusSeconds(EdgeReplayGuard.MAX_SKEW.toSeconds() + 60), newNonce());

        mockMvc.perform(edgeRequest(post("/edge/metrics"))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(sealed))
                .andExpect(status().isForbidden());
        verify(metricsRepository, never()).save(any());
    }

    @Test
    void anEnvelopeSealedUnderTheBundleContextCannotBeOpenedAsMetrics() throws Exception {
        byte[] wrongContext = envelope.seal(
                replayEnvelope(aggregatePayload(), EDGE_ID, Instant.now(), newNonce()),
                Base64.getDecoder().decode(controlPlaneKeys.ingestPublicKeyBase64()),
                edgeEd25519Priv,
                EdgeBundleDistributionService.BUNDLE_CONTEXT.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(edgeRequest(post("/edge/metrics"))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(wrongContext))
                // HSE-1 binds the context into the HKDF info and the AEAD AAD, so opening under a
                // different context fails the tag check — a security rejection, not a parse error.
                .andExpect(status().isForbidden());
        verify(metricsRepository, never()).save(any());
    }

    @Test
    void metricsFromAnUnauthorisedEdgeAreRefusedWithoutDecrypting() throws Exception {
        when(enrollmentService.findAuthorized(EDGE_ID)).thenReturn(Optional.empty());

        mockMvc.perform(edgeRequest(post("/edge/metrics"))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(sealMetrics(aggregatePayload(), EDGE_ID, Instant.now(), newNonce())))
                .andExpect(status().isForbidden());
        verify(metricsRepository, never()).save(any());
    }

    // ── POST /edge/enroll ────────────────────────────────────────────────────────────────────────

    @Test
    void enrollmentReturns202AndTheKeysTheEdgeMustPin() throws Exception {
        when(enrollmentService.activate(any(), any(), any(), any(), any(), any(), any())).thenReturn(node);

        mockMvc.perform(post("/edge/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        // Byte-for-byte the shape edge-host ActivationService.EnrollmentRequest emits.
                        .content("{\"edgeId\":\"acme-eu-1\",\"pspId\":\"42\","
                                + "\"enrollmentCode\":\"code\","
                                + "\"x25519PublicKey\":\"" + node.getEdgeX25519PublicKey() + "\","
                                + "\"ed25519PublicKey\":\"" + node.getEdgeEd25519PublicKey() + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.edgeId").value(EDGE_ID))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.controlPlaneSigningKey")
                        .value(controlPlaneKeys.signingPublicKeyBase64()))
                .andExpect(jsonPath("$.controlPlaneIngestKey")
                        .value(controlPlaneKeys.ingestPublicKeyBase64()))
                .andExpect(jsonPath("$.bundleContext").value("hokeka.rules.bundle"))
                .andExpect(jsonPath("$.metricsContext").value("hokeka.metrics.report"));
    }

    @Test
    void theAssertedPspIdIsForwardedToTheServiceForVerification() throws Exception {
        when(enrollmentService.activate(any(), any(), any(), any(), any(), any(), any())).thenReturn(node);

        mockMvc.perform(post("/edge/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"edgeId\":\"acme-eu-1\",\"pspId\":\"99\","
                                + "\"enrollmentCode\":\"code\","
                                + "\"x25519PublicKey\":\"" + node.getEdgeX25519PublicKey() + "\","
                                + "\"ed25519PublicKey\":\"" + node.getEdgeEd25519PublicKey() + "\"}"))
                .andExpect(status().isAccepted());

        // The controller must not silently drop the tenant assertion — the service is the
        // enforcement point and needs the value to compare.
        verify(enrollmentService).activate("code", "acme-eu-1", "99",
                node.getEdgeX25519PublicKey(), node.getEdgeEd25519PublicKey(), null, null);
    }

    @Test
    void aPspMismatchSurfacesAs403NamingBothValues() throws Exception {
        when(enrollmentService.activate(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(EdgeEnrollmentException.forbidden(
                        "PSP mismatch: this enrollment code belongs to PSP 42, but the edge is "
                                + "configured with pspId '99'. Set the edge host's PSP id to 42 and "
                                + "retry — the enrollment code has not been used."));

        mockMvc.perform(post("/edge/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"edgeId\":\"acme-eu-1\",\"pspId\":\"99\","
                                + "\"enrollmentCode\":\"code\","
                                + "\"x25519PublicKey\":\"" + node.getEdgeX25519PublicKey() + "\","
                                + "\"ed25519PublicKey\":\"" + node.getEdgeEd25519PublicKey() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("99")))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("42")));
    }

    @Test
    void anInvalidEnrollmentCodeIsRefusedWithTheServicesStatus() throws Exception {
        when(enrollmentService.activate(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(EdgeEnrollmentException.forbidden("invalid or already-used enrollment code"));

        mockMvc.perform(post("/edge/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentCode\":\"bad\",\"edgeId\":\"acme-eu-1\"}"))
                .andExpect(status().isForbidden());
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /** Adds the forwarded client-certificate headers nginx sets after a successful mTLS handshake. */
    private static MockHttpServletRequestBuilder edgeRequest(MockHttpServletRequestBuilder builder) {
        return builder
                .header("X-SSL-Client-Verify", "SUCCESS")
                .header("X-SSL-Client-S-DN", "CN=" + EDGE_ID + ",OU=edge,O=Acme");
    }

    private ObjectNode aggregatePayload() {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("pspId", "42");
        payload.put("edgeId", EDGE_ID);
        payload.put("windowStartEpochMs", System.currentTimeMillis() - 60_000);
        payload.put("windowEndEpochMs", System.currentTimeMillis());
        payload.put("ruleBundleVersion", 37L);
        payload.put("ruleBundleHash", "abc123");
        payload.put("totalEvaluated", 1000L);
        payload.put("allowed", 900L);
        payload.put("alerted", 60L);
        payload.put("held", 30L);
        payload.put("blocked", 10L);
        payload.putObject("ruleHitCounts").put("100", 12L);
        payload.put("p50LatencyMicros", 120.0);
        payload.put("p95LatencyMicros", 480.0);
        payload.put("p99LatencyMicros", 910.0);
        payload.put("engineHealth", "OK");
        return payload;
    }

    private byte[] replayEnvelope(ObjectNode payload, String edgeId, Instant issuedAt, String nonce)
            throws Exception {
        ObjectNode env = mapper.createObjectNode();
        env.put("nonce", nonce);
        env.put("issuedAt", issuedAt.toString());
        env.put("edgeId", edgeId);
        env.set("payload", payload);
        return mapper.writeValueAsBytes(env);
    }

    private byte[] sealMetrics(ObjectNode payload, String edgeId, Instant issuedAt, String nonce)
            throws Exception {
        return envelope.seal(
                replayEnvelope(payload, edgeId, issuedAt, nonce),
                Base64.getDecoder().decode(controlPlaneKeys.ingestPublicKeyBase64()),
                edgeEd25519Priv,
                EdgeMetricsIngestService.METRICS_CONTEXT.getBytes(StandardCharsets.UTF_8));
    }

    private static String newNonce() {
        byte[] raw = new byte[16];
        new SecureRandom().nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
