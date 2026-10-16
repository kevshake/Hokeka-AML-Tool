package com.posgateway.aml.controller.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import com.posgateway.aml.config.edge.EdgeProperties;
import com.posgateway.aml.contracts.ContractFixtures;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.edge.EdgeNodeStatus;
import com.posgateway.aml.service.edge.EdgeEnrollmentService;
import com.posgateway.aml.service.edge.EdgeIdentityResolver;
import com.posgateway.aml.service.edge.EdgeNodeAuthorizer;
import com.posgateway.aml.service.edge.EdgeReplayGuard;
import com.posgateway.aml.service.edge.records.DecisionRecordIngestService;
import com.posgateway.aml.service.edge.records.DecisionRecordStore;
import com.posgateway.aml.service.residency.EdgeConfigDocumentBuilder;
import com.posgateway.aml.service.residency.EdgeConfigService;
import com.posgateway.aml.service.residency.EdgeConfigVersionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.XECPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /edge/config and POST /edge/assessments (v2) through MockMvc with real HSE-1 crypto: the Edge
 * side opens / seals exactly as an Edge would, and payloads are checked against the contract schemas.
 */
class EdgeResidencyControllerTest {

    private static final String EDGE_ID = "acme-eu-1";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HokekaSecureEnvelope envelope = new HokekaSecureEnvelope();
    private MockMvc mockMvc;
    private EdgeControlPlaneKeys controlPlaneKeys;
    private EdgeNode node;
    private PrivateKey edgeX25519Priv;
    private PrivateKey edgeEd25519Priv;
    private DecisionRecordStore store;
    private final AtomicReference<ObjectNode> content = new AtomicReference<>();
    private long version = 14;

    @BeforeEach
    void setUp() throws Exception {
        EdgeProperties properties = new EdgeProperties();
        controlPlaneKeys = new EdgeControlPlaneKeys(properties);
        KeyPair edgeX = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        KeyPair edgeEd = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        edgeX25519Priv = edgeX.getPrivate();
        edgeEd25519Priv = edgeEd.getPrivate();

        node = new EdgeNode();
        node.setId(7L);
        node.setPspId(12L);
        node.setEdgeId(EDGE_ID);
        node.setStatus(EdgeNodeStatus.ACTIVE);
        node.setEdgeX25519PublicKey(Base64.getEncoder().encodeToString(
                HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) edgeX.getPublic())));
        node.setEdgeEd25519PublicKey(Base64.getEncoder().encodeToString(
                EdgeControlPlaneKeys.rawEd25519Public((EdECPublicKey) edgeEd.getPublic())));
        EdgeEnrollmentService enrollment = mock(EdgeEnrollmentService.class);
        when(enrollment.findAuthorized(EDGE_ID)).thenReturn(Optional.of(node));

        // Content = the contract's default config fixture minus its header members.
        ObjectNode fixture = (ObjectNode) ContractFixtures.fixture("edge-config--default.json");
        fixture.remove(Set.of("configVersion", "issuedAt"));
        content.set(fixture);
        EdgeConfigDocumentBuilder builder = mock(EdgeConfigDocumentBuilder.class);
        when(builder.buildContent(any(), any())).thenAnswer(i -> content.get().deepCopy());
        EdgeConfigVersionStore versions = mock(EdgeConfigVersionStore.class);
        AtomicReference<String> lastHash = new AtomicReference<>();
        when(versions.ensureVersion(anyLong(), anyString())).thenAnswer(i -> {
            String hash = i.getArgument(1);
            boolean minted = !hash.equals(lastHash.getAndSet(hash));
            if (minted) {
                version++;
            }
            return new EdgeConfigVersionStore.VersionResult(version, minted);
        });
        EdgeConfigService configService = new EdgeConfigService(builder, versions, controlPlaneKeys, envelope,
                mock(ApplicationEventPublisher.class));

        store = mock(DecisionRecordStore.class);
        when(store.store(any(), any(), anyString())).thenReturn(DecisionRecordStore.Outcome.ACCEPTED);

        EdgeResidencyController controller = new EdgeResidencyController(
                new EdgeNodeAuthorizer(new EdgeIdentityResolver(properties), enrollment),
                configService,
                new DecisionRecordIngestService(store, siteResolver("acme-eu")),
                new EdgeReplayGuard(),
                controlPlaneKeys,
                properties,
                envelope);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ── GET /edge/config ─────────────────────────────────────────────────────────────────────────

    @Test
    void configIsRefusedWithoutAVerifiedClientCertificate() throws Exception {
        MvcResult result = mockMvc.perform(get("/edge/config")).andExpect(status().isForbidden()).andReturn();
        assertEquals(0, result.getResponse().getContentAsByteArray().length);
        mockMvc.perform(get("/edge/config").header(EdgeIdentityResolver.EDGE_ID_HEADER, EDGE_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void configIsSealedToTheNodeAndConformsToTheSchema() throws Exception {
        MvcResult result = mockMvc.perform(edgeRequest(get("/edge/config")))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"15\""))
                .andExpect(header().string("X-Edge-Config-Version", "15"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        byte[] sealed = result.getResponse().getContentAsByteArray();

        byte[] plaintext = envelope.open(sealed, edgeX25519Priv, controlPlaneKeys.signingPublicKey(),
                EdgeConfigService.CONFIG_CONTEXT.getBytes(StandardCharsets.UTF_8));
        JsonNode replay = mapper.readTree(plaintext);
        assertEquals(EDGE_ID, replay.get("edgeId").asText(), "addressed to this node");
        assertEquals(22, replay.get("nonce").asText().length());
        JsonNode doc = replay.get("payload");
        assertEquals(15, doc.get("configVersion").asLong());
        assertEquals(12, doc.get("pspId").asLong());
        Set<String> errors = ContractFixtures.validate("edge-config.schema.json", doc);
        assertTrue(errors.isEmpty(), "sealed document conforms: " + errors);
        Iterator<String> first = doc.fieldNames();
        assertEquals("v", first.next());
        assertEquals("configVersion", first.next());

        // A bundle-context opener must not open a config envelope.
        assertThrows(SecurityException.class, () -> envelope.open(sealed, edgeX25519Priv,
                controlPlaneKeys.signingPublicKey(), "hokeka.rules.bundle".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void ifNoneMatchOnTheCurrentVersionAnswers304InEveryEtagForm() throws Exception {
        mockMvc.perform(edgeRequest(get("/edge/config"))).andExpect(status().isOk());
        for (String etag : new String[]{"\"15\"", "15", "W/\"15\"", "\"9\", \"15\""}) {
            MvcResult notModified = mockMvc.perform(edgeRequest(get("/edge/config")).header("If-None-Match", etag))
                    .andExpect(status().isNotModified())
                    .andExpect(header().string("ETag", "\"15\""))
                    .andReturn();
            assertEquals(0, notModified.getResponse().getContentAsByteArray().length, etag);
        }
        mockMvc.perform(edgeRequest(get("/edge/config")).header("If-None-Match", "\"14\""))
                .andExpect(status().isOk());
    }

    @Test
    void aChangedInputGetsANewEtag() throws Exception {
        mockMvc.perform(edgeRequest(get("/edge/config"))).andExpect(header().string("ETag", "\"15\""));
        ObjectNode changed = content.get().deepCopy();
        ((ObjectNode) changed.get("supportAccess")).put("enabled", true);
        content.set(changed);
        mockMvc.perform(edgeRequest(get("/edge/config")).header("If-None-Match", "\"15\""))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"16\""));
    }

    // ── POST /edge/assessments (v2) ──────────────────────────────────────────────────────────────

    @Test
    void sealedContractBatchIsAcknowledged() throws Exception {
        JsonNode batch = ContractFixtures.fixture("decision-record-batch-v2--two-records.json");
        MvcResult result = mockMvc.perform(edgeRequest(post("/edge/assessments"))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(sealBatch(batch, DecisionRecordIngestService.DECISION_RECORDS_CONTEXT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(2))
                .andExpect(jsonPath("$.duplicates").value(0))
                .andReturn();
        JsonNode ack = mapper.readTree(result.getResponse().getContentAsString());
        Set<String> errors = ContractFixtures.validate("decision-record-ack-v2.schema.json", ack);
        assertTrue(errors.isEmpty(), "ack conforms: " + errors);
    }

    @Test
    void forbiddenFieldsAreRejectedPerRecordInsideASealedBatch() throws Exception {
        ObjectNode batch = (ObjectNode) ContractFixtures.fixture("decision-record-batch-v2--two-records.json");
        ((ObjectNode) batch.get("records").get(0)).put("pan_hash", "sha256:abc");
        mockMvc.perform(edgeRequest(post("/edge/assessments"))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(sealBatch(batch, DecisionRecordIngestService.DECISION_RECORDS_CONTEXT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.rejected[0].code").value("FORBIDDEN_FIELD"))
                .andExpect(jsonPath("$.rejected[0].edgeAssessmentId").value("3f0c9a2e-7d41-4b8e-9a51-0c2f6b1d7e44"));
    }

    @Test
    void wrongContextOrReplayIs400AndNothingIsStored() throws Exception {
        JsonNode batch = ContractFixtures.fixture("decision-record-batch-v2--two-records.json");
        mockMvc.perform(edgeRequest(post("/edge/assessments"))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(sealBatch(batch, "hokeka.metrics.report")))
                .andExpect(status().isBadRequest());

        byte[] once = sealBatch(batch, DecisionRecordIngestService.DECISION_RECORDS_CONTEXT);
        mockMvc.perform(edgeRequest(post("/edge/assessments"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(once)).andExpect(status().isOk());
        mockMvc.perform(edgeRequest(post("/edge/assessments"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(once)).andExpect(status().isBadRequest());
        verify(store, org.mockito.Mockito.times(2)).store(any(), any(), anyString());
    }

    @Test
    void aV1BodyInsideTheSealIs400() throws Exception {
        ObjectNode notV2 = mapper.createObjectNode();
        notV2.put("edgeAssessmentId", "3f0c9a2e-7d41-4b8e-9a51-0c2f6b1d7e44");
        notV2.put("decision", "ALERT");
        mockMvc.perform(edgeRequest(post("/edge/assessments"))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(sealBatch(notV2, DecisionRecordIngestService.DECISION_RECORDS_CONTEXT)))
                .andExpect(status().isBadRequest());
        verify(store, never()).store(any(), any(), anyString());
    }

    @Test
    void oversizeAndUnauthenticatedUploadsAreRefused() throws Exception {
        mockMvc.perform(edgeRequest(post("/edge/assessments"))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[262_145]))
                .andExpect(status().isPayloadTooLarge());
        mockMvc.perform(post("/edge/assessments")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[]{1, 2, 3}))
                .andExpect(status().isForbidden());
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /** A site resolver that places every node in {@code site}. */
    private static com.posgateway.aml.service.edge.records.EdgeSiteResolver siteResolver(String site) {
        com.posgateway.aml.service.edge.records.EdgeSiteResolver resolver =
                mock(com.posgateway.aml.service.edge.records.EdgeSiteResolver.class);
        when(resolver.siteOf(any())).thenReturn(site);
        return resolver;
    }

    /** nginx's forwarded client-certificate headers after a successful mTLS handshake. */
    private static MockHttpServletRequestBuilder edgeRequest(MockHttpServletRequestBuilder builder) {
        return builder
                .header("X-SSL-Client-Verify", "SUCCESS")
                .header("X-SSL-Client-S-DN", "CN=" + EDGE_ID + ",OU=edge,O=Acme");
    }

    /** Wraps {@code payload} in a fresh replay envelope and seals it as the Edge's outbox shipper does. */
    private byte[] sealBatch(JsonNode payload, String context) throws Exception {
        ObjectNode env = mapper.createObjectNode();
        env.put("nonce", EdgeReplayGuard.newNonce());
        env.put("issuedAt", Instant.now().toString());
        env.put("edgeId", EDGE_ID);
        env.set("payload", payload);
        return envelope.seal(mapper.writeValueAsBytes(env),
                Base64.getDecoder().decode(controlPlaneKeys.ingestPublicKeyBase64()),
                edgeEd25519Priv,
                context.getBytes(StandardCharsets.UTF_8));
    }
}
