package com.posgateway.aml.controller.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import com.posgateway.aml.config.edge.EdgeProperties;
import com.posgateway.aml.contracts.ContractFixtures;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.entity.edge.EdgeNodeStatus;
import com.posgateway.aml.entity.sanctions.SanctionsList;
import com.posgateway.aml.service.edge.EdgeEnrollmentService;
import com.posgateway.aml.service.edge.EdgeIdentityResolver;
import com.posgateway.aml.service.edge.EdgeNodeAuthorizer;
import com.posgateway.aml.service.residency.CanonicalJson;
import com.posgateway.aml.service.residency.EdgeSanctionsListService;
import com.posgateway.aml.service.residency.EdgeSanctionsSnapshotService;
import com.posgateway.aml.service.residency.EdgeSanctionsSnapshotStore;
import com.posgateway.aml.service.residency.InMemorySanctionsSnapshotStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.XECPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R10b: GET /edge/sanctions/list and its chunks through MockMvc with real HSE-1 crypto. The test
 * opens each response as an Edge would (pinned Control Plane key, its own X25519 key, the
 * contract context), validates it against the contract schemas, and re-derives listHash and every
 * chunk hash from the bytes it received.
 */
class EdgeSanctionsListControllerTest {

    private static final String EDGE_ID = "acme-eu-1";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HokekaSecureEnvelope envelope = new HokekaSecureEnvelope();
    private MockMvc mockMvc;
    private EdgeControlPlaneKeys controlPlaneKeys;
    private PrivateKey edgeX25519Priv;
    private InMemorySanctionsSnapshotStore store;
    private EdgeSanctionsSnapshotService snapshots;
    private EdgeNode node;
    private EdgeEnrollmentService enrollment;

    @BeforeEach
    void setUp() throws Exception {
        EdgeProperties properties = new EdgeProperties();
        controlPlaneKeys = new EdgeControlPlaneKeys(properties);
        KeyPair edgeX = KeyPairGenerator.getInstance("X25519").generateKeyPair();
        KeyPair edgeEd = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        edgeX25519Priv = edgeX.getPrivate();
        node = new EdgeNode();
        node.setId(7L);
        node.setPspId(12L);
        node.setEdgeId(EDGE_ID);
        node.setSiteId("acme-eu");
        node.setStatus(EdgeNodeStatus.ACTIVE);
        node.setEdgeX25519PublicKey(Base64.getEncoder().encodeToString(
                HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) edgeX.getPublic())));
        node.setEdgeEd25519PublicKey(Base64.getEncoder().encodeToString(
                EdgeControlPlaneKeys.rawEd25519Public((EdECPublicKey) edgeEd.getPublic())));
        enrollment = mock(EdgeEnrollmentService.class);
        when(enrollment.findAuthorized(EDGE_ID)).thenReturn(Optional.of(node));

        store = new InMemorySanctionsSnapshotStore();
        snapshots = new EdgeSanctionsSnapshotService(store, mock(ApplicationEventPublisher.class), true);
        EdgeSanctionsListService lists = new EdgeSanctionsListService(store, controlPlaneKeys, envelope);
        EdgeSanctionsListController controller = new EdgeSanctionsListController(
                new EdgeNodeAuthorizer(new EdgeIdentityResolver(properties), enrollment), lists);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    /** Publishes a snapshot of {@code count} entities. */
    private EdgeSanctionsSnapshotStore.Snapshot publish(int count, String version) {
        EdgeSanctionsSnapshotService.Builder builder = snapshots.begin();
        for (int i = 0; i < count; i++) {
            Map<String, Object> e = new HashMap<>();
            e.put("entityId", "NK-" + version + "-" + i);
            e.put("name", "Listed Person " + i);
            e.put("aliases", List.of("Alias " + i));
            e.put("type", "PERSON");
            e.put("listName", "us_ofac_sdn");
            builder.add(e);
        }
        SanctionsList row = new SanctionsList();
        row.setListId(1);
        row.setVersion(version);
        return builder.finish(row).orElseThrow();
    }

    /** Opens a sealed response as the Edge does and returns the replay envelope's payload. */
    private JsonNode open(byte[] sealed, String context) throws Exception {
        JsonNode replay = mapper.readTree(envelope.open(sealed, edgeX25519Priv, controlPlaneKeys.signingPublicKey(),
                context.getBytes(StandardCharsets.UTF_8)));
        assertEquals(EDGE_ID, replay.get("edgeId").asText(), "addressed to this node");
        assertEquals(22, replay.get("nonce").asText().length());
        return replay.get("payload");
    }

    @Test
    void theListIsRefusedWithoutAVerifiedClientCertificate() throws Exception {
        publish(3, "v1");
        MvcResult result = mockMvc.perform(get("/edge/sanctions/list")).andExpect(status().isForbidden()).andReturn();
        assertEquals(0, result.getResponse().getContentAsByteArray().length);
        mockMvc.perform(get("/edge/sanctions/list").header(EdgeIdentityResolver.EDGE_ID_HEADER, EDGE_ID))
                .andExpect(status().isForbidden());
        String hash = store.latestReady().orElseThrow().listHash();
        mockMvc.perform(get("/edge/sanctions/list/" + hash + "/chunks/0")).andExpect(status().isForbidden());
        // A node that is not ACTIVE is not authorised (EdgeEnrollmentService.findAuthorized is empty).
        when(enrollment.findAuthorized(EDGE_ID)).thenReturn(Optional.empty());
        mockMvc.perform(edgeRequest(get("/edge/sanctions/list"))).andExpect(status().isForbidden());
        mockMvc.perform(edgeRequest(get("/edge/sanctions/list/" + hash + "/chunks/0"))).andExpect(status().isForbidden());
    }

    @Test
    void beforeAnyPublishTheListIs404() throws Exception {
        mockMvc.perform(edgeRequest(get("/edge/sanctions/list"))).andExpect(status().isNotFound());
    }

    @Test
    void manifestAndChunksAreSealedSchemaValidAndHashConsistent() throws Exception {
        EdgeSanctionsSnapshotStore.Snapshot snapshot = publish(7_500, "20261017020000-abc");
        MvcResult result = mockMvc.perform(edgeRequest(get("/edge/sanctions/list")))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + snapshot.listHash() + "\""))
                .andExpect(header().string("X-Sanctions-List-Version", "20261017020000-abc"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        byte[] sealed = result.getResponse().getContentAsByteArray();
        JsonNode manifest = open(sealed, EdgeSanctionsListService.LIST_CONTEXT);
        Set<String> errors = ContractFixtures.validate("sanctions-list-manifest.schema.json", manifest);
        assertTrue(errors.isEmpty(), "manifest conforms: " + errors);
        assertEquals(7_500, manifest.get("entryCount").asInt());
        assertEquals(2, manifest.get("chunks").size());
        // A config-context opener must not open the list envelope (context binding).
        assertThrows(SecurityException.class, () -> envelope.open(sealed, edgeX25519Priv,
                controlPlaneKeys.signingPublicKey(), "hokeka.edge.config".getBytes(StandardCharsets.UTF_8)));

        List<String> lines = new ArrayList<>();
        int entries = 0;
        for (JsonNode item : manifest.get("chunks")) {
            int index = item.get("index").asInt();
            byte[] sealedChunk = mockMvc.perform(edgeRequest(get("/edge/sanctions/list/" + snapshot.listHash()
                            + "/chunks/" + index)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("ETag", "\"" + item.get("sha256").asText() + "\""))
                    .andReturn().getResponse().getContentAsByteArray();
            JsonNode chunk = open(sealedChunk, EdgeSanctionsListService.CHUNK_CONTEXT);
            Set<String> chunkErrors = ContractFixtures.validate("sanctions-list-chunk.schema.json", chunk);
            assertTrue(chunkErrors.isEmpty(), "chunk conforms: " + chunkErrors);
            assertEquals(snapshot.listHash(), chunk.get("listHash").asText());
            byte[] raw;
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(
                    Base64.getDecoder().decode(chunk.get("data").asText())))) {
                raw = in.readAllBytes();
            }
            assertEquals(item.get("sha256").asText(), CanonicalJson.sha256Hex(raw));
            entries += new String(raw, StandardCharsets.UTF_8).split("\n").length;
            lines.add(index + ":" + item.get("sha256").asText() + ":" + item.get("entryCount").asInt());
        }
        assertEquals(7_500, entries);
        assertEquals(snapshot.listHash(), EdgeSanctionsSnapshotService.listHash(lines),
                "the Edge can re-derive listHash from the manifest");
    }

    @Test
    void ifNoneMatchOnTheCurrentHashAnswers304AndANewPublishChangesTheEtag() throws Exception {
        EdgeSanctionsSnapshotStore.Snapshot first = publish(2, "v1");
        for (String etag : new String[]{"\"" + first.listHash() + "\"", first.listHash(), "W/\"" + first.listHash() + "\""}) {
            MvcResult notModified = mockMvc.perform(edgeRequest(get("/edge/sanctions/list")).header("If-None-Match", etag))
                    .andExpect(status().isNotModified()).andReturn();
            assertEquals(0, notModified.getResponse().getContentAsByteArray().length);
        }
        Thread.sleep(5);
        EdgeSanctionsSnapshotStore.Snapshot second = publish(3, "v2");
        mockMvc.perform(edgeRequest(get("/edge/sanctions/list")).header("If-None-Match", "\"" + first.listHash() + "\""))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + second.listHash() + "\""));
        // The previous snapshot stays fetchable for a node mid-download; an unknown hash is 404.
        mockMvc.perform(edgeRequest(get("/edge/sanctions/list/" + first.listHash() + "/chunks/0")))
                .andExpect(status().isOk());
        mockMvc.perform(edgeRequest(get("/edge/sanctions/list/" + "0".repeat(64) + "/chunks/0")))
                .andExpect(status().isNotFound());
        mockMvc.perform(edgeRequest(get("/edge/sanctions/list/" + second.listHash() + "/chunks/1")))
                .andExpect(status().isNotFound());
        mockMvc.perform(edgeRequest(get("/edge/sanctions/list/" + second.listHash() + "/chunks/x")))
                .andExpect(status().isNotFound());
    }

    /** nginx's forwarded client-certificate headers after a successful mTLS handshake. */
    private static MockHttpServletRequestBuilder edgeRequest(MockHttpServletRequestBuilder builder) {
        return builder
                .header("X-SSL-Client-Verify", "SUCCESS")
                .header("X-SSL-Client-S-DN", "CN=" + EDGE_ID + ",OU=edge,O=Acme");
    }
}
