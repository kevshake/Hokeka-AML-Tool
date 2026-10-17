package com.hokeka.edge.sanctions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hokeka.edge.activation.ActivationService;
import com.hokeka.edge.activation.EdgeIdentity;
import com.hokeka.edge.channel.ControlPlaneProperties;
import com.hokeka.edge.channel.SealedEnvelopeCodec;
import com.hokeka.edge.channel.SecureChannel;
import com.hokeka.edge.contract.ContractSchemas;
import com.hokeka.edge.crypto.EdgeKeys;
import com.hokeka.edge.residency.EdgeConfigDocument;
import com.hokeka.edge.residency.EdgeConfigStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.security.PublicKey;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fetches the sanctions/PEP list the Edge config names (contract section 6.4) and makes it the list
 * in force. Driven by the config poll cycle and, early, by every newly accepted config document.
 *
 * <p>Only {@code GET} requests without a body leave the node; nothing about the node's transactions
 * is sent. Each document is opened like the config (HSE-1 signature against the pinned Control Plane
 * key, AEAD under its context, replay check, {@code edgeId} = this node), validated against its
 * schema, and cross-checked: the manifest's listHash must equal the config's {@code sanctions.listHash}
 * and be re-derivable from its chunk lines; every chunk must carry that listHash, its index, and an
 * uncompressed SHA-256 and entry count matching the manifest line. Verified chunks are stored on the
 * site ledger as they arrive, so an interrupted download resumes and a second node of the site reuses
 * them. The list is swapped in only after {@link SanctionsListStore#activate} re-verified the whole
 * stored list; until then the previous list stays in force.
 */
public class SanctionsListPoller {

    private static final Logger log = LoggerFactory.getLogger(SanctionsListPoller.class);

    /** Manifest endpoint. */
    public static final String LIST_PATH = "/api/v1/edge/sanctions/list";
    /** HSE-1 context of the manifest. */
    public static final String CTX_LIST = "hokeka.sanctions.list";
    /** HSE-1 context of a chunk. */
    public static final String CTX_CHUNK = "hokeka.sanctions.list.chunk";
    /** Manifest schema. */
    public static final String MANIFEST_SCHEMA = "sanctions-list-manifest.schema.json";
    /** Chunk schema. */
    public static final String CHUNK_SCHEMA = "sanctions-list-chunk.schema.json";

    /** Outcome of one {@link #poll()}. */
    public enum Outcome {
        /** Channel off, node not authorised or no pinned key. */
        SKIPPED,
        /** No config yet, or the Control Plane has published no list. */
        NO_LIST_PUBLISHED,
        /** The list the config names is already in force. */
        UP_TO_DATE,
        /** A new list was fetched, verified and is now in force. */
        ACTIVATED,
        /** Fetch or verification failed; the previous list stays in force. */
        FAILED
    }

    private final ControlPlaneProperties properties;
    private final SecureChannel channel;
    private final SealedEnvelopeCodec codec;
    private final ActivationService activation;
    private final EdgeConfigStore configStore;
    private final SanctionsListStore store;
    private final ContractSchemas schemas;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final AtomicBoolean soonScheduled = new AtomicBoolean();
    private volatile Instant lastSuccess;
    private volatile String lastError = "";

    /** Wires the poller. */
    public SanctionsListPoller(ControlPlaneProperties properties, SecureChannel channel, SealedEnvelopeCodec codec,
                               ActivationService activation, EdgeConfigStore configStore, SanctionsListStore store,
                               ContractSchemas schemas, ObjectMapper mapper, Clock clock) {
        this.properties = properties;
        this.channel = channel;
        this.codec = codec;
        this.activation = activation;
        this.configStore = configStore;
        this.store = store;
        this.schemas = schemas;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Last time a poll ended UP_TO_DATE or ACTIVATED. */
    public Instant lastSuccess() {
        return lastSuccess;
    }

    /** Log-safe reason of the last failure ({@code ""} after a success). */
    public String lastError() {
        return lastError;
    }

    /** Schedules one poll on a virtual thread right away; bursts collapse into one. */
    public void pollSoon() {
        if (soonScheduled.compareAndSet(false, true)) {
            Thread.ofVirtual().name("sanctions-list-poll").start(() -> {
                soonScheduled.set(false);
                poll();
            });
        }
    }

    /** One poll. Never throws: the caller is a scheduler and the node must keep serving. */
    public synchronized Outcome poll() {
        if (!properties.isEnabled() || !activation.authorized()) {
            return Outcome.SKIPPED;
        }
        String signingKey = properties.getSigningPublicKey();
        if (signingKey == null || signingKey.isBlank()) {
            lastError = "CONTROLPLANE_ED25519_PUBLIC_KEY is not set";
            return Outcome.SKIPPED;
        }
        Optional<EdgeConfigDocument> config = configStore.current();
        if (config.isEmpty() || !config.get().sanctionsListPublished()) {
            return Outcome.NO_LIST_PUBLISHED;
        }
        String target = config.get().sanctionsListHash();
        if (target.equals(store.currentHash())) {
            lastSuccess = clock.instant();
            lastError = "";
            return Outcome.UP_TO_DATE;
        }
        try {
            EdgeIdentity identity = activation.loadOrCreateIdentity();
            PublicKey cpSigning = EdgeKeys.decodeEd25519Public(signingKey);
            RestClient client = channel.mutualTlsClient();
            JsonNode manifest = fetch(client, LIST_PATH, CTX_LIST, MANIFEST_SCHEMA, identity, cpSigning);
            SanctionsListCodec.verifyManifest(manifest);
            if (!target.equals(manifest.get("listHash").asText())) {
                return fail("the Control Plane serves a different list than the config names (will retry)");
            }
            store.saveManifest(manifest);
            int fetched = 0;
            for (JsonNode line : manifest.get("chunks")) {
                int index = line.get("index").asInt();
                String sha = line.get("sha256").asText();
                if (store.hasChunk(target, index, sha)) {
                    continue;
                }
                JsonNode chunk = fetch(client, LIST_PATH + "/" + target + "/chunks/" + index, CTX_CHUNK, CHUNK_SCHEMA,
                        identity, cpSigning);
                byte[] gzip = verifyChunk(chunk, target, line);
                store.saveChunk(target, index, sha, line.get("entryCount").asInt(), gzip);
                fetched++;
            }
            SanctionsListStore.Loaded loaded = store.activate(target, clock.instant());
            log.info("Sanctions list {} in force: {} entries ({} chunk(s) fetched, {} reused)", loaded.listVersion(),
                    loaded.entryCount(), fetched, manifest.get("chunks").size() - fetched);
            lastSuccess = clock.instant();
            lastError = "";
            return Outcome.ACTIVATED;
        } catch (RestClientResponseException e) {
            return fail("control plane returned HTTP " + e.getStatusCode().value());
        } catch (SanctionsListCodec.InvalidListException e) {
            return fail("list refused: " + e.getMessage());
        } catch (SecurityException e) {
            return fail("list envelope refused (" + e.getClass().getSimpleName() + ")");
        } catch (SQLException e) {
            return fail("ledger: " + com.hokeka.edge.ledger.EdgeLedger.safeMessage(e));
        } catch (Exception e) {
            return fail(e.getClass().getSimpleName());
        }
    }

    /** GETs one sealed document, opens it under {@code context} and validates it against {@code schema}. */
    private JsonNode fetch(RestClient client, String path, String context, String schema, EdgeIdentity identity,
                           PublicKey cpSigning) throws SanctionsListCodec.InvalidListException {
        ResponseEntity<byte[]> response = client.get().uri(path)
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .header("X-Hokeka-Edge-Id", identity.edgeId())
                .retrieve().toEntity(byte[].class);
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            throw new SanctionsListCodec.InvalidListException("empty response body");
        }
        JsonNode doc = codec.open(body, activation.x25519Private(), cpSigning, context, identity.edgeId(),
                clock.instant());
        List<String> violations = schemas.validate(schema, doc);
        if (!violations.isEmpty()) {
            throw new SanctionsListCodec.InvalidListException("document fails " + schema);
        }
        return doc;
    }

    /**
     * Checks a schema-valid chunk against its manifest line and returns its gzip bytes; the
     * uncompressed SHA-256, the entry count and every entry are verified by
     * {@link SanctionsListCodec#parseChunk}.
     */
    byte[] verifyChunk(JsonNode chunk, String listHash, JsonNode line) throws SanctionsListCodec.InvalidListException {
        if (!listHash.equals(chunk.get("listHash").asText()) || chunk.get("index").asInt() != line.get("index").asInt()
                || !line.get("sha256").asText().equals(chunk.get("sha256").asText())
                || chunk.get("entryCount").asInt() != line.get("entryCount").asInt()) {
            throw new SanctionsListCodec.InvalidListException("chunk does not match its manifest line");
        }
        byte[] gzip;
        try {
            gzip = Base64.getDecoder().decode(chunk.get("data").asText());
        } catch (IllegalArgumentException e) {
            throw new SanctionsListCodec.InvalidListException("chunk data is not base64");
        }
        SanctionsListCodec.parseChunk(SanctionsListCodec.gunzip(gzip), line.get("sha256").asText(),
                line.get("entryCount").asInt(), mapper);
        return gzip;
    }

    /** Records a failure (log-safe) and returns FAILED. */
    private Outcome fail(String reason) {
        lastError = reason;
        store.recordError(reason);
        log.warn("Sanctions list fetch failed ({}) — the previous list stays in force", reason);
        return Outcome.FAILED;
    }
}
