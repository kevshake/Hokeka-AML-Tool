package com.hokeka.edge.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.crypto.HokekaSecureEnvelope;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;

/**
 * The replay envelope of channel contract §4, wrapped inside HSE-1. Both directions share it: the
 * poller opens control-plane bundles with it, the shipper seals metrics with it.
 *
 * <pre>
 * { "nonce": "&lt;22-char base64url&gt;", "issuedAt": "2026-07-20T10:15:30Z",
 *   "edgeId": "acme-eu-1", "payload": { ... } }
 * </pre>
 *
 * <p>The {@code context} string is bound into the HKDF info and the AEAD AAD, so an envelope sealed
 * for one purpose can never be opened as another.
 */
public final class SealedEnvelopeCodec {

    public static final String CTX_RULE_BUNDLE = "hokeka.rules.bundle";
    public static final String CTX_METRICS_REPORT = "hokeka.metrics.report";
    public static final String CTX_ACTIVATION = "hokeka.edge.activation";

    private final ObjectMapper mapper;
    private final HokekaSecureEnvelope hse = new HokekaSecureEnvelope();
    private final ReplayGuard replayGuard;

    public SealedEnvelopeCodec(ObjectMapper mapper, ReplayGuard replayGuard) {
        this.mapper = mapper;
        this.replayGuard = replayGuard;
    }

    public ReplayGuard replayGuard() {
        return replayGuard;
    }

    /** Wrap {@code payload} in a fresh replay envelope and HSE-1 seal it for the control plane. */
    public byte[] seal(Object payload, String edgeId, byte[] peerX25519PubRaw,
                       PrivateKey ownEd25519Priv, String context, Instant now) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("nonce", ReplayGuard.newNonce());
            root.put("issuedAt", now.toString());
            root.put("edgeId", edgeId);
            root.set("payload", mapper.valueToTree(payload));
            return hse.seal(mapper.writeValueAsBytes(root), peerX25519PubRaw, ownEd25519Priv,
                    context.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("failed to seal " + context + " envelope", e);
        }
    }

    /**
     * HSE-1 open + replay validation. Returns the inner {@code payload} node.
     *
     * @throws SecurityException on a bad signature, failed AEAD tag, malformed framing, a replayed
     *                           nonce, excessive clock skew, or an edge-id mismatch
     */
    public JsonNode open(byte[] envelope, PrivateKey ownX25519Priv, PublicKey senderEd25519Pub,
                         String context, String expectedEdgeId, Instant now) {
        byte[] plaintext = hse.open(envelope, ownX25519Priv, senderEd25519Pub,
                context.getBytes(StandardCharsets.UTF_8));
        JsonNode root;
        try {
            root = mapper.readTree(plaintext);
        } catch (Exception e) {
            throw new SecurityException("replay envelope is not valid JSON", e);
        }
        if (!root.isObject() || !root.hasNonNull("payload")) {
            throw new SecurityException("replay envelope has no payload");
        }
        String edgeId = root.path("edgeId").asText("");
        if (expectedEdgeId != null && !expectedEdgeId.equals(edgeId)) {
            throw new SecurityException("replay envelope addressed to edge '" + edgeId
                    + "', this node is '" + expectedEdgeId + "'");
        }
        Instant issuedAt;
        try {
            issuedAt = Instant.parse(root.path("issuedAt").asText(""));
        } catch (Exception e) {
            throw new SecurityException("replay envelope has an unparseable issuedAt", e);
        }
        replayGuard.accept(root.path("nonce").asText(null), issuedAt, now);
        return root.get("payload");
    }

    /** Same as {@link #open} but returns the payload re-serialised, ready for the rule interpreter. */
    public byte[] openPayloadBytes(byte[] envelope, PrivateKey ownX25519Priv, PublicKey senderEd25519Pub,
                                   String context, String expectedEdgeId, Instant now) {
        JsonNode payload = open(envelope, ownX25519Priv, senderEd25519Pub, context, expectedEdgeId, now);
        try {
            return mapper.writeValueAsBytes(payload);
        } catch (Exception e) {
            throw new SecurityException("cannot serialise the opened payload", e);
        }
    }
}
