package com.posgateway.aml.service.residency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.service.edge.EdgeReplayGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;

/**
 * Issues, versions and seals the per-PSP Edge config document (contract section 6).
 *
 * <p>{@link #current(Long)} builds the content from cloud state, derives its canonical hash and
 * resolves (or mints) the {@code configVersion} for that content, so the ETag of
 * {@code GET /api/v1/edge/config} changes exactly when an input changes. {@link #seal} wraps the
 * document in the channel replay envelope and HSE-1 seals it to the node's pinned X25519 key,
 * signed with the Control Plane Ed25519 key, under context {@value #CONFIG_CONTEXT} — the same
 * keys and construction as the rule bundle, so no new key material exists.
 */
@Service
public class EdgeConfigService {

    private static final Logger log = LoggerFactory.getLogger(EdgeConfigService.class);

    /** Contract 3.2 — HSE-1 context of the Edge config document. */
    public static final String CONFIG_CONTEXT = "hokeka.edge.config";

    private final EdgeConfigDocumentBuilder builder;
    private final EdgeConfigVersionStore versions;
    private final EdgeControlPlaneKeys controlPlaneKeys;
    private final HokekaSecureEnvelope envelope;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public EdgeConfigService(EdgeConfigDocumentBuilder builder,
                             EdgeConfigVersionStore versions,
                             EdgeControlPlaneKeys controlPlaneKeys,
                             HokekaSecureEnvelope envelope,
                             ApplicationEventPublisher events) {
        this(builder, versions, controlPlaneKeys, envelope, events, Clock.systemUTC());
    }

    /** Test constructor with an explicit clock. */
    public EdgeConfigService(EdgeConfigDocumentBuilder builder,
                             EdgeConfigVersionStore versions,
                             EdgeControlPlaneKeys controlPlaneKeys,
                             HokekaSecureEnvelope envelope,
                             ApplicationEventPublisher events,
                             Clock clock) {
        this.builder = builder;
        this.versions = versions;
        this.controlPlaneKeys = controlPlaneKeys;
        this.envelope = envelope;
        this.events = events;
        this.clock = clock;
    }

    /** The issued document of a PSP: its version and full JSON (v, configVersion, pspId, issuedAt, sections). */
    public record IssuedConfig(long version, ObjectNode document) {}

    /** A document sealed for one node. */
    public record SealedConfig(long version, byte[] sealed) {}

    /**
     * Builds the PSP's current document and its version. Mints a new version (and publishes
     * {@link EdgeConfigUpdatedEvent}) when the content differs from the last issued one.
     */
    @Transactional
    public IssuedConfig current(Long pspId) {
        Instant now = clock.instant();
        ObjectNode content = builder.buildContent(pspId, now);
        String hash = CanonicalJson.sha256Hex(content);
        EdgeConfigVersionStore.VersionResult version = versions.ensureVersion(pspId, hash);
        if (version.minted()) {
            log.info("Issued Edge config version {} for PSP {}", version.version(), pspId);
            events.publishEvent(new EdgeConfigUpdatedEvent(pspId, version.version()));
        }
        return new IssuedConfig(version.version(), assemble(content, version.version(), now));
    }

    /**
     * Seals an issued document for {@code node}: replay envelope {@code {nonce, issuedAt, edgeId,
     * payload}}, then HSE-1 to the node's pinned X25519 key with context {@value #CONFIG_CONTEXT}.
     * The node must already be authorised (ACTIVE, keys pinned).
     */
    public SealedConfig seal(EdgeNode node, IssuedConfig issued) {
        if (!issued.document().path("pspId").isIntegralNumber()
                || issued.document().get("pspId").asLong() != node.getPspId()) {
            throw new IllegalStateException("refusing to seal another PSP's Edge config for edge " + node.getEdgeId());
        }
        byte[] plaintext = wrapInReplayEnvelope(issued.document(), node.getEdgeId());
        byte[] edgeX25519Pub = Base64.getDecoder().decode(node.getEdgeX25519PublicKey());
        byte[] sealed = envelope.seal(plaintext, edgeX25519Pub, controlPlaneKeys.signingPrivateKey(),
                CONFIG_CONTEXT.getBytes(StandardCharsets.UTF_8));
        return new SealedConfig(issued.version(), sealed);
    }

    /**
     * Re-derives the version after a settings change has committed, so connected Edges are told
     * about the change promptly rather than at their next poll. Runs in its own transaction because
     * the publishing transaction has already committed. A failure here is logged and heals on the
     * next poll, which recomputes the version from the same inputs.
     */
    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSettingsChanged(PspDataSettingsChangedEvent event) {
        try {
            current(event.pspId());
        } catch (RuntimeException e) {
            log.warn("Could not re-issue the Edge config for PSP {} after {} changed: {}",
                    event.pspId(), event.reason(), e.toString());
        }
    }

    /** Puts the header members first, then the content sections, as the schema lists them. */
    private static ObjectNode assemble(ObjectNode content, long version, Instant now) {
        ObjectNode doc = JsonNodeFactory.instance.objectNode();
        doc.put("v", 1);
        doc.put("configVersion", version);
        doc.set("pspId", content.get("pspId"));
        doc.put("issuedAt", now.truncatedTo(ChronoUnit.MILLIS).toString());
        Iterator<Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> fields = content.fields();
        while (fields.hasNext()) {
            Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> field = fields.next();
            if (!doc.has(field.getKey())) {
                doc.set(field.getKey(), field.getValue());
            }
        }
        return doc;
    }

    /** {@code {"nonce":…,"issuedAt":…,"edgeId":…,"payload":<document>}} — channel contract §4. */
    private byte[] wrapInReplayEnvelope(ObjectNode document, String edgeId) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("nonce", EdgeReplayGuard.newNonce());
            root.put("issuedAt", clock.instant().toString());
            root.put("edgeId", edgeId);
            root.set("payload", document);
            return mapper.writeValueAsBytes(root);
        } catch (Exception e) {
            throw new IllegalStateException("failed to build the Edge config replay envelope", e);
        }
    }
}
