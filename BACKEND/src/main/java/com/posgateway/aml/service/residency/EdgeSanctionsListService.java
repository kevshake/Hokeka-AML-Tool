package com.posgateway.aml.service.residency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.service.edge.EdgeReplayGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Serves the published sanctions/PEP list snapshot to Edge Nodes (contract section 6.4):
 * the manifest ({@code GET /api/v1/edge/sanctions/list}, context {@value #LIST_CONTEXT}) and its
 * chunks ({@code GET /api/v1/edge/sanctions/list/{listHash}/chunks/{index}}, context
 * {@value #CHUNK_CONTEXT}), each in the channel replay envelope, HSE-1 sealed to the node's pinned
 * X25519 key and signed with the Control Plane Ed25519 key — the keys and construction of the Edge
 * config, so no new key material exists.
 */
@Service
public class EdgeSanctionsListService {

    /** Contract 3.2 — HSE-1 context of the list manifest. */
    public static final String LIST_CONTEXT = "hokeka.sanctions.list";
    /** Contract 3.2 — HSE-1 context of one list chunk. */
    public static final String CHUNK_CONTEXT = "hokeka.sanctions.list.chunk";

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    private final EdgeSanctionsSnapshotStore store;
    private final EdgeControlPlaneKeys controlPlaneKeys;
    private final HokekaSecureEnvelope envelope;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public EdgeSanctionsListService(EdgeSanctionsSnapshotStore store, EdgeControlPlaneKeys controlPlaneKeys,
                                    HokekaSecureEnvelope envelope) {
        this(store, controlPlaneKeys, envelope, Clock.systemUTC());
    }

    /** Test constructor with an explicit clock. */
    public EdgeSanctionsListService(EdgeSanctionsSnapshotStore store, EdgeControlPlaneKeys controlPlaneKeys,
                                    HokekaSecureEnvelope envelope, Clock clock) {
        this.store = store;
        this.controlPlaneKeys = controlPlaneKeys;
        this.envelope = envelope;
        this.clock = clock;
    }

    /**
     * The manifest of the newest READY snapshot.
     *
     * @param listHash the snapshot hash (the ETag)
     * @param document {@code sanctions-list-manifest.schema.json} document
     */
    public record Manifest(String listHash, ObjectNode document) {
    }

    /** The newest published snapshot's manifest, or empty when none has been published. */
    public Optional<Manifest> latestManifest() {
        return store.latestReady().map(this::manifest);
    }

    /** Builds the manifest document of a snapshot from its chunk rows. */
    Manifest manifest(EdgeSanctionsSnapshotStore.Snapshot snapshot) {
        List<EdgeSanctionsSnapshotStore.ChunkMeta> chunks = store.chunks(snapshot.id());
        ObjectNode doc = mapper.createObjectNode();
        doc.put("v", 1);
        doc.put("listVersion", snapshot.listVersion());
        doc.put("listHash", snapshot.listHash());
        doc.put("publishedAt", snapshot.publishedAt().truncatedTo(ChronoUnit.MILLIS).toString());
        doc.put("entryCount", snapshot.entryCount());
        doc.put("format", EdgeSanctionsSnapshotService.FORMAT);
        ArrayNode items = doc.putArray("chunks");
        for (EdgeSanctionsSnapshotStore.ChunkMeta chunk : chunks) {
            ObjectNode item = items.addObject();
            item.put("index", chunk.index());
            item.put("sha256", chunk.sha256());
            item.put("entryCount", chunk.entryCount());
            item.put("bytes", chunk.bytes());
        }
        return new Manifest(snapshot.listHash(), doc);
    }

    /**
     * One chunk document ({@code sanctions-list-chunk.schema.json}) of a READY snapshot, or empty when
     * the hash is not (or no longer) published or the index is out of range.
     */
    public Optional<ObjectNode> chunk(String listHash, int index) {
        if (listHash == null || !SHA256_HEX.matcher(listHash).matches() || index < 0 || index > 4095) {
            return Optional.empty();
        }
        return store.chunk(listHash, index).map(content -> {
            ObjectNode doc = mapper.createObjectNode();
            doc.put("v", 1);
            doc.put("listHash", content.listHash());
            doc.put("index", content.index());
            doc.put("sha256", content.sha256());
            doc.put("entryCount", content.entryCount());
            doc.put("encoding", "gzip+base64");
            doc.put("data", Base64.getEncoder().encodeToString(content.gzip()));
            return doc;
        });
    }

    /** Seals a manifest for {@code node} (context {@value #LIST_CONTEXT}). */
    public byte[] sealManifest(EdgeNode node, Manifest manifest) {
        return seal(node, manifest.document(), LIST_CONTEXT);
    }

    /** Seals a chunk document for {@code node} (context {@value #CHUNK_CONTEXT}). */
    public byte[] sealChunk(EdgeNode node, ObjectNode chunk) {
        return seal(node, chunk, CHUNK_CONTEXT);
    }

    /**
     * Replay envelope {@code {nonce, issuedAt, edgeId, payload}} (channel contract section 4), then
     * HSE-1 to the node's pinned X25519 key under {@code context}.
     */
    private byte[] seal(EdgeNode node, ObjectNode payload, String context) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("nonce", EdgeReplayGuard.newNonce());
            root.put("issuedAt", clock.instant().toString());
            root.put("edgeId", node.getEdgeId());
            root.set("payload", payload);
            byte[] edgeX25519Pub = Base64.getDecoder().decode(node.getEdgeX25519PublicKey());
            return envelope.seal(mapper.writeValueAsBytes(root), edgeX25519Pub, controlPlaneKeys.signingPrivateKey(),
                    context.getBytes(StandardCharsets.UTF_8));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("failed to build the sanctions list replay envelope", e);
        }
    }
}
