package com.hokeka.edge.tunnel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.channel.ReplayGuard;
import com.hokeka.edge.contract.ContractSchemas;
import com.hokeka.edge.crypto.HokekaSecureEnvelope;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Edge side of the tunnel frame format (EDGE_DATA_RESIDENCY_CONTRACT.md §7.3, §7.4): builds and
 * parses the outer JSON frame, seals Edge→cloud frames and opens cloud→Edge frames with HSE-1 under
 * the per-type context {@code hokeka.tunnel.<type>} (§3.2), runs the replay check (§3.4) and
 * verifies the frame binding. Wire-compatible with the Control Plane's {@code TunnelFrameCodec}.
 *
 * <p>Edge→cloud frames are sealed to the pinned Control Plane X25519 ("ingest") key and signed with
 * this node's Ed25519 key; cloud→Edge frames are opened with this node's X25519 key and verified
 * against the pinned Control Plane Ed25519 signing key (§3.1, §3.3).
 */
public final class TunnelFrameCodec {

    /** §7.1 WebSocket subprotocol. */
    public static final String SUBPROTOCOL = "hokeka-tunnel.v1";
    /** §7.1 maximum encoded frame size. */
    public static final int MAX_FRAME_BYTES = 1_048_576;
    /** §3.2 context prefix; the frame type is appended. */
    public static final String CONTEXT_PREFIX = "hokeka.tunnel.";

    /** Every frame type of §7.3. */
    public static final Set<String> FRAME_TYPES = Set.of("hello", "hello.ack", "request", "response", "chunk",
            "cancel", "credit", "notify", "error", "ping", "pong");
    /** Unsealed frame types (§7.3). */
    public static final Set<String> UNSEALED = Set.of("ping", "pong");

    /** Body schema per sealed frame type (§7.3 table). */
    public static final Map<String, String> BODY_SCHEMA = Map.of(
            "hello", "tunnel/hello.schema.json",
            "hello.ack", "tunnel/hello-ack.schema.json",
            "request", "tunnel/request.schema.json",
            "response", "tunnel/response.schema.json",
            "chunk", "tunnel/response.schema.json",
            "cancel", "tunnel/cancel.schema.json",
            "credit", "tunnel/credit.schema.json",
            "notify", "tunnel/notify.schema.json",
            "error", "tunnel/error.schema.json");

    private static final String FRAME_SCHEMA = "tunnel/frame.schema.json";

    /**
     * A cloud frame that passed seal, replay and binding checks (§7.4 steps 2–4).
     *
     * @param type      frame type
     * @param id        frame id
     * @param corrId    correlation id, or null
     * @param sessionId session id bound inside the seal
     * @param nonce     replay nonce
     * @param issuedAt  sealed issue time (equals the outer ts)
     * @param body      the sealed body (not yet schema-checked)
     */
    public record Opened(String type, String id, String corrId, String sessionId, String nonce, Instant issuedAt,
                         ObjectNode body) {
    }

    private final ObjectMapper mapper;
    private final ReplayGuard replayGuard;
    private final ContractSchemas schemas;
    private final HokekaSecureEnvelope hse = new HokekaSecureEnvelope();
    private final String edgeId;

    /**
     * @param mapper      JSON mapper
     * @param replayGuard nonce LRU + skew window (§3.4)
     * @param schemas     contract schemas
     * @param edgeId      this node's id (bound into every frame, required in every received one)
     */
    public TunnelFrameCodec(ObjectMapper mapper, ReplayGuard replayGuard, ContractSchemas schemas, String edgeId) {
        this.mapper = mapper;
        this.replayGuard = replayGuard;
        this.schemas = schemas;
        this.edgeId = edgeId;
    }

    /** A fresh lowercase UUID for frame ids. */
    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /** HSE-1 context bytes for a frame type (§3.2). */
    public static byte[] context(String type) {
        return (CONTEXT_PREFIX + type).getBytes(StandardCharsets.UTF_8);
    }

    /** This node's id. */
    public String edgeId() {
        return edgeId;
    }

    /**
     * Seals one Edge→cloud frame: replay envelope {@code {nonce, issuedAt, edgeId, payload:{frame,
     * body}}} sealed under {@code hokeka.tunnel.<type>}, outer frame with the same nonce and ts.
     *
     * @param sessionId live session id; null only for {@code hello}
     * @throws TunnelException {@code FRAME_TOO_LARGE} when the encoded frame exceeds 1 MiB
     */
    public String seal(String type, String id, String corrId, String sessionId, JsonNode body,
                       byte[] cpX25519Raw, PrivateKey edgeEd25519, Instant now) throws TunnelException {
        if (UNSEALED.contains(type) || !FRAME_TYPES.contains(type)) {
            throw new IllegalArgumentException("not a sealed frame type: " + type);
        }
        Instant ts = Instant.ofEpochMilli(now.toEpochMilli());
        String nonce = ReplayGuard.newNonce();
        ObjectNode frame = mapper.createObjectNode();
        frame.put("type", type);
        frame.put("id", id);
        if (corrId != null) {
            frame.put("corrId", corrId);
        }
        if (sessionId != null) {
            frame.put("sessionId", sessionId);
        }
        ObjectNode payload = mapper.createObjectNode();
        payload.set("frame", frame);
        payload.set("body", body);
        ObjectNode plaintext = mapper.createObjectNode();
        plaintext.put("nonce", nonce);
        plaintext.put("issuedAt", TunnelTime.millis(ts));
        plaintext.put("edgeId", edgeId);
        plaintext.set("payload", payload);
        byte[] sealed;
        try {
            sealed = hse.seal(mapper.writeValueAsBytes(plaintext), cpX25519Raw, edgeEd25519, context(type));
        } catch (JsonProcessingException e) {
            throw new TunnelException(TunnelErrorCode.INTERNAL, "frame body is not serialisable");
        }
        ObjectNode outer = mapper.createObjectNode();
        outer.put("v", 1);
        outer.put("type", type);
        outer.put("id", id);
        if (corrId != null) {
            outer.put("corrId", corrId);
        }
        outer.put("ts", ts.toEpochMilli());
        outer.put("nonce", nonce);
        outer.put("sealed", Base64.getEncoder().encodeToString(sealed));
        String text = outer.toString();
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_FRAME_BYTES) {
            throw new TunnelException(TunnelErrorCode.FRAME_TOO_LARGE, "frame exceeds the 1 MiB limit");
        }
        return text;
    }

    /** An unsealed {@code ping} (only v, type, id, ts). */
    public String ping(Instant now) {
        ObjectNode outer = mapper.createObjectNode();
        outer.put("v", 1);
        outer.put("type", "ping");
        outer.put("id", newId());
        outer.put("ts", now.toEpochMilli());
        return outer.toString();
    }

    /** An unsealed {@code pong} answering {@code pingId} (§7.2: corrId = the ping id). */
    public String pong(String pingId, Instant now) {
        ObjectNode outer = mapper.createObjectNode();
        outer.put("v", 1);
        outer.put("type", "pong");
        outer.put("id", newId());
        outer.put("corrId", pingId);
        outer.put("ts", now.toEpochMilli());
        return outer.toString();
    }

    /**
     * §7.4 step 1: parses the outer frame and checks it against {@code frame.schema.json} (the
     * caller has already enforced the 1 MiB limit while assembling the message).
     *
     * @throws TunnelException {@code UNSUPPORTED_FRAME} for an unknown {@code type} (answered with an
     *         error and otherwise ignored); {@code INVALID_PARAMS} for malformed JSON or a schema
     *         failure (the session closes with 4400)
     */
    public ObjectNode parseOuter(String text) throws TunnelException {
        JsonNode node;
        try {
            node = mapper.readTree(text);
        } catch (JsonProcessingException e) {
            throw new TunnelException(TunnelErrorCode.INVALID_PARAMS, "frame is not valid JSON");
        }
        if (!(node instanceof ObjectNode outer)) {
            throw new TunnelException(TunnelErrorCode.INVALID_PARAMS, "frame is not a JSON object");
        }
        JsonNode type = outer.get("type");
        if (type != null && type.isTextual() && !FRAME_TYPES.contains(type.asText())) {
            throw new TunnelException(TunnelErrorCode.UNSUPPORTED_FRAME, "unknown frame type");
        }
        if (!schemas.violations(FRAME_SCHEMA, outer).isEmpty()) {
            throw new TunnelException(TunnelErrorCode.INVALID_PARAMS, "frame fails frame.schema.json");
        }
        return outer;
    }

    /**
     * §7.4 steps 2–4 for a cloud frame: HSE-1 open with the context of the outer type and the
     * pinned Control Plane key, replay check, {@code edgeId} = this node, and the binding of type,
     * id, corrId, nonce, ts and session.
     *
     * @param expectedSessionId the live session id; null while waiting for {@code hello.ack}, whose
     *                          sealed {@code sessionId} is then accepted (it creates the session)
     */
    public Opened open(ObjectNode outer, PrivateKey edgeX25519, PublicKey cpEd25519, String expectedSessionId,
                       Instant now) throws TunnelException {
        String type = outer.get("type").asText();
        if (UNSEALED.contains(type)) {
            throw new IllegalArgumentException("ping/pong carry no sealed body");
        }
        byte[] sealedBytes;
        try {
            sealedBytes = Base64.getDecoder().decode(outer.get("sealed").asText());
        } catch (IllegalArgumentException e) {
            throw new TunnelException(TunnelErrorCode.SEAL_INVALID, "sealed is not base64");
        }
        byte[] plaintextBytes;
        try {
            plaintextBytes = hse.open(sealedBytes, edgeX25519, cpEd25519, context(type));
        } catch (SecurityException e) {
            throw new TunnelException(TunnelErrorCode.SEAL_INVALID, "HSE-1 open failed");
        }
        JsonNode plaintext;
        try {
            plaintext = mapper.readTree(plaintextBytes);
        } catch (java.io.IOException e) {
            throw new TunnelException(TunnelErrorCode.SEAL_INVALID, "sealed plaintext is not JSON");
        }
        if (plaintext == null || !plaintext.isObject()) {
            throw new TunnelException(TunnelErrorCode.SEAL_INVALID, "sealed plaintext is not an object");
        }
        if (!edgeId.equals(plaintext.path("edgeId").asText(null))) {
            throw new TunnelException(TunnelErrorCode.BINDING_MISMATCH, "frame is addressed to another node");
        }
        String nonce = plaintext.path("nonce").asText(null);
        Instant issuedAt;
        try {
            issuedAt = Instant.parse(plaintext.path("issuedAt").asText(""));
        } catch (DateTimeParseException e) {
            throw new TunnelException(TunnelErrorCode.REPLAY_DETECTED, "replay envelope has no valid issuedAt");
        }
        try {
            replayGuard.accept(nonce, issuedAt, now);
        } catch (SecurityException e) {
            throw new TunnelException(TunnelErrorCode.REPLAY_DETECTED, "replay envelope refused");
        }
        JsonNode payload = plaintext.path("payload");
        JsonNode frame = payload.path("frame");
        JsonNode body = payload.get("body");
        if (!frame.isObject() || body == null || !body.isObject()) {
            throw new TunnelException(TunnelErrorCode.BINDING_MISMATCH, "payload lacks frame/body");
        }
        String outerCorr = outer.hasNonNull("corrId") ? outer.get("corrId").asText() : null;
        String innerCorr = frame.hasNonNull("corrId") ? frame.get("corrId").asText() : null;
        String innerSession = frame.hasNonNull("sessionId") ? frame.get("sessionId").asText() : null;
        boolean bound = type.equals(frame.path("type").asText(null))
                && outer.get("id").asText().equals(frame.path("id").asText(null))
                && Objects.equals(outerCorr, innerCorr)
                && outer.get("nonce").asText().equals(nonce)
                && issuedAt.toEpochMilli() == outer.get("ts").asLong()
                && issuedAt.getNano() % 1_000_000 == 0
                && sessionBound(type, innerSession, expectedSessionId);
        if (!bound) {
            throw new TunnelException(TunnelErrorCode.BINDING_MISMATCH, "frame binding mismatch");
        }
        return new Opened(type, outer.get("id").asText(), outerCorr, innerSession, nonce, issuedAt, (ObjectNode) body);
    }

    /**
     * §7.4 step 4 session rule seen from the Edge: {@code hello.ack} creates the session, so while
     * none is live (expected null) it is accepted when it carries one; every other cloud frame must
     * carry exactly the live session's id.
     */
    static boolean sessionBound(String type, String inner, String expected) {
        if ("hello.ack".equals(type) && expected == null) {
            return inner != null;
        }
        return inner != null && inner.equals(expected);
    }

    /**
     * §7.4 step 5: the body's violations against the schema of its frame type, as JSON pointers.
     *
     * @return empty when valid
     */
    public List<ContractSchemas.Violation> checkBody(String type, JsonNode body) {
        String schema = BODY_SCHEMA.get(type);
        if (schema == null) {
            return List.of(new ContractSchemas.Violation("", "type"));
        }
        return schemas.violations(schema, body);
    }
}
