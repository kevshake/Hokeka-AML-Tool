package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.service.edge.EdgeReplayGuard;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Edge Tunnel frame codec (contract §7.3, §7.4): builds and parses the outer JSON frame, seals and
 * opens the HSE-1 envelope with the per-frame-type context {@code hokeka.tunnel.<type>} (§3.2),
 * runs the replay check (§3.4) and verifies the frame binding.
 *
 * <p>The codec is symmetric: the Control Plane seals cloud→Edge frames with its Ed25519 signing
 * key to the node's pinned X25519 key, and opens Edge→cloud frames with its X25519 ingest key and
 * the node's pinned Ed25519 key. The fake Edge in the tests uses the same class with the roles
 * swapped, so both directions are exercised against one implementation of the wire format.
 *
 * <p>Plain class (no Spring annotations); {@link EdgeTunnelConfiguration} wires it.
 */
public class TunnelFrameCodec {

    /** §7.1 WebSocket subprotocol. */
    public static final String SUBPROTOCOL = "hokeka-tunnel.v1";

    /** §3.2 context prefix; the frame type is appended. */
    public static final String CONTEXT_PREFIX = "hokeka.tunnel.";

    /** Every frame type of §7.3. */
    public static final Set<String> FRAME_TYPES = Set.of("hello", "hello.ack", "request", "response",
            "chunk", "cancel", "credit", "notify", "error", "ping", "pong");

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
    private static final Pattern ITEM_POINTER = Pattern.compile("^/items/(\\d+)(/.*)?$");

    private final HokekaSecureEnvelope hse;
    private final EdgeReplayGuard replayGuard;
    private final TunnelSchemas schemas;
    private final ObjectMapper mapper;
    private final int maxFrameBytes;

    /** What the sender binds into one sealed frame. {@code corrId}/{@code sessionId} may be null. */
    public record FrameSpec(String type, String id, String corrId, String sessionId, String edgeId,
                            JsonNode body) {}

    /** A frame that passed seal, replay and binding checks (§7.4 steps 2–4). */
    public record OpenedFrame(String type, String id, String corrId, String sessionId, String edgeId,
                              String nonce, Instant issuedAt, ObjectNode body) {}

    /** Result of validating a response/chunk body: the body with failing items removed. */
    public record CheckedResponse(ObjectNode body, int discardedItems, List<String> discardedPointers) {}

    /** Creates the codec over the existing HSE-1 and replay primitives. */
    public TunnelFrameCodec(HokekaSecureEnvelope hse, EdgeReplayGuard replayGuard, TunnelSchemas schemas,
                            ObjectMapper mapper, int maxFrameBytes) {
        this.hse = hse;
        this.replayGuard = replayGuard;
        this.schemas = schemas;
        this.mapper = mapper;
        this.maxFrameBytes = maxFrameBytes;
    }

    /** A fresh lowercase UUID for frame ids, session ids and audit ids. */
    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /** HSE-1 context bytes for a frame type (§3.2). */
    public static byte[] context(String type) {
        return (CONTEXT_PREFIX + type).getBytes(StandardCharsets.UTF_8);
    }

    // ── sending ──────────────────────────────────────────────────────────────────────────────────

    /**
     * Seals one frame: builds the replay envelope {@code {nonce, issuedAt, edgeId, payload:{frame,
     * body}}}, seals it with HSE-1 under context {@code hokeka.tunnel.<type>}, and renders the outer
     * frame whose {@code nonce}/{@code ts} equal the sealed ones.
     *
     * @throws TunnelFrameException {@code FRAME_TOO_LARGE} when the encoded frame exceeds 1 MiB
     */
    public String seal(FrameSpec spec, byte[] recipientX25519Raw, PrivateKey senderEd25519Priv, Instant now)
            throws TunnelFrameException {
        if (UNSEALED.contains(spec.type()) || !FRAME_TYPES.contains(spec.type())) {
            throw new IllegalArgumentException("not a sealed frame type: " + spec.type());
        }
        Instant ts = Instant.ofEpochMilli(now.toEpochMilli());
        String nonce = EdgeReplayGuard.newNonce();

        ObjectNode frame = mapper.createObjectNode();
        frame.put("type", spec.type());
        frame.put("id", spec.id());
        if (spec.corrId() != null) {
            frame.put("corrId", spec.corrId());
        }
        if (spec.sessionId() != null) {
            frame.put("sessionId", spec.sessionId());
        }
        ObjectNode payload = mapper.createObjectNode();
        payload.set("frame", frame);
        payload.set("body", spec.body());
        ObjectNode plaintext = mapper.createObjectNode();
        plaintext.put("nonce", nonce);
        plaintext.put("issuedAt", TunnelTime.formatMillis(ts));
        plaintext.put("edgeId", spec.edgeId());
        plaintext.set("payload", payload);

        byte[] sealed;
        try {
            sealed = hse.seal(mapper.writeValueAsBytes(plaintext), recipientX25519Raw, senderEd25519Priv,
                    context(spec.type()));
        } catch (JsonProcessingException e) {
            throw new TunnelFrameException(TunnelErrorCode.INTERNAL, "frame body is not serialisable");
        }

        ObjectNode outer = mapper.createObjectNode();
        outer.put("v", 1);
        outer.put("type", spec.type());
        outer.put("id", spec.id());
        if (spec.corrId() != null) {
            outer.put("corrId", spec.corrId());
        }
        outer.put("ts", ts.toEpochMilli());
        outer.put("nonce", nonce);
        outer.put("sealed", Base64.getEncoder().encodeToString(sealed));
        String text = outer.toString();
        if (text.getBytes(StandardCharsets.UTF_8).length > maxFrameBytes) {
            throw new TunnelFrameException(TunnelErrorCode.FRAME_TOO_LARGE, "frame exceeds the 1 MiB limit");
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

    /** An unsealed {@code pong} answering {@code pingId} (corrId = the ping id, §7.2). */
    public String pong(String pingId, Instant now) {
        ObjectNode outer = mapper.createObjectNode();
        outer.put("v", 1);
        outer.put("type", "pong");
        outer.put("id", newId());
        outer.put("corrId", pingId);
        outer.put("ts", now.toEpochMilli());
        return outer.toString();
    }

    // ── receiving ────────────────────────────────────────────────────────────────────────────────

    /**
     * §7.4 step 1: parse the outer frame and check it against {@code frame.schema.json}.
     *
     * @throws TunnelFrameException {@code UNSUPPORTED_FRAME} for an unknown {@code type} (answered
     *         with an error and otherwise ignored), {@code INVALID_PARAMS} for malformed JSON or a
     *         schema failure (the session handler closes 4400 on those)
     */
    public ObjectNode parseOuter(String text) throws TunnelFrameException {
        JsonNode node;
        try {
            node = mapper.readTree(text);
        } catch (JsonProcessingException e) {
            throw new TunnelFrameException(TunnelErrorCode.INVALID_PARAMS, "frame is not valid JSON");
        }
        if (!(node instanceof ObjectNode outer)) {
            throw new TunnelFrameException(TunnelErrorCode.INVALID_PARAMS, "frame is not a JSON object");
        }
        JsonNode type = outer.get("type");
        if (type != null && type.isTextual() && !FRAME_TYPES.contains(type.asText())) {
            throw new TunnelFrameException(TunnelErrorCode.UNSUPPORTED_FRAME, "unknown frame type");
        }
        if (!schemas.isValid(FRAME_SCHEMA, outer)) {
            throw new TunnelFrameException(TunnelErrorCode.INVALID_PARAMS, "frame fails frame.schema.json");
        }
        return outer;
    }

    /**
     * §7.4 steps 2–4: open the HSE-1 envelope with the context of the outer type, run the replay
     * check and verify the binding. Body validation (step 5) is {@link #checkBody} /
     * {@link #checkResponse}, done by the caller that knows the op.
     *
     * @param expectedEdgeId    the node identity established by mTLS (or, at the Edge, its own id)
     * @param expectedSessionId the live session id; {@code null} only for {@code hello}
     */
    public OpenedFrame open(ObjectNode outer, PrivateKey recipientX25519Priv, PublicKey senderEd25519Pub,
                            String expectedEdgeId, String expectedSessionId, Instant now)
            throws TunnelFrameException {
        String type = outer.get("type").asText();
        if (UNSEALED.contains(type)) {
            throw new IllegalArgumentException("ping/pong carry no sealed body");
        }
        byte[] sealedBytes;
        try {
            sealedBytes = Base64.getDecoder().decode(outer.get("sealed").asText());
        } catch (IllegalArgumentException e) {
            throw new TunnelFrameException(TunnelErrorCode.SEAL_INVALID, "sealed is not base64");
        }
        byte[] plaintextBytes;
        try {
            plaintextBytes = hse.open(sealedBytes, recipientX25519Priv, senderEd25519Pub, context(type));
        } catch (SecurityException e) {
            throw new TunnelFrameException(TunnelErrorCode.SEAL_INVALID, "HSE-1 open failed");
        }
        JsonNode plaintext;
        try {
            plaintext = mapper.readTree(plaintextBytes);
        } catch (java.io.IOException e) {
            throw new TunnelFrameException(TunnelErrorCode.SEAL_INVALID, "sealed plaintext is not JSON");
        }
        if (!plaintext.isObject()) {
            throw new TunnelFrameException(TunnelErrorCode.SEAL_INVALID, "sealed plaintext is not an object");
        }
        String sealedEdgeId = plaintext.path("edgeId").asText(null);
        if (expectedEdgeId != null && !expectedEdgeId.equals(sealedEdgeId)) {
            throw new TunnelFrameException(TunnelErrorCode.BINDING_MISMATCH, "edgeId does not match identity");
        }
        EdgeReplayGuard.OpenedEnvelope env;
        try {
            env = replayGuard.validate(plaintext, expectedEdgeId, now);
        } catch (SecurityException e) {
            throw new TunnelFrameException(TunnelErrorCode.REPLAY_DETECTED, "replay envelope refused");
        }
        JsonNode payload = env.payload();
        JsonNode frame = payload.path("frame");
        JsonNode body = payload.get("body");
        if (!frame.isObject() || body == null || !body.isObject()) {
            throw new TunnelFrameException(TunnelErrorCode.BINDING_MISMATCH, "payload lacks frame/body");
        }
        String outerCorr = outer.hasNonNull("corrId") ? outer.get("corrId").asText() : null;
        String innerCorr = frame.hasNonNull("corrId") ? frame.get("corrId").asText() : null;
        String innerSession = frame.hasNonNull("sessionId") ? frame.get("sessionId").asText() : null;
        boolean bound = type.equals(frame.path("type").asText(null))
                && outer.get("id").asText().equals(frame.path("id").asText(null))
                && java.util.Objects.equals(outerCorr, innerCorr)
                && outer.get("nonce").asText().equals(env.nonce())
                && sameMillis(plaintext.path("issuedAt").asText(""), outer.get("ts").asLong())
                && sessionBound(type, innerSession, expectedSessionId);
        if (!bound) {
            throw new TunnelFrameException(TunnelErrorCode.BINDING_MISMATCH, "frame binding mismatch");
        }
        return new OpenedFrame(type, outer.get("id").asText(), outerCorr, innerSession, env.edgeId(),
                env.nonce(), env.issuedAt(), (ObjectNode) body);
    }

    /**
     * Session binding rule of §7.4 step 4: {@code hello} carries no session; {@code hello.ack}
     * creates it, so a receiver that has no session yet (expected {@code null}) accepts the one it
     * carries (the Edge then requires it to equal {@code body.sessionId}); every other frame must
     * carry exactly the live session's id.
     */
    private static boolean sessionBound(String type, String inner, String expected) {
        if ("hello".equals(type)) {
            return inner == null;
        }
        if ("hello.ack".equals(type) && expected == null) {
            return inner != null;
        }
        return inner != null && inner.equals(expected);
    }

    /** True when the RFC 3339 {@code issuedAt} denotes exactly epoch-millis {@code ts}. */
    private static boolean sameMillis(String issuedAt, long ts) {
        try {
            return Instant.parse(issuedAt).toEpochMilli() == ts && Instant.parse(issuedAt).getNano() % 1_000_000 == 0;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * §7.4 step 5 for every body except response/chunk: the body must validate against the schema
     * of its frame type.
     *
     * @return the violating pointers (empty when valid)
     */
    public List<String> checkBody(String type, JsonNode body) {
        String schema = BODY_SCHEMA.get(type);
        if (schema == null) {
            return List.of("/");
        }
        List<String> out = new ArrayList<>();
        for (TunnelSchemas.Violation v : schemas.validate(schema, body)) {
            out.add(v.pointer());
        }
        return out;
    }

    /**
     * §7.4 step 5 and §7.7 for response/chunk bodies: the envelope must be valid, and every item
     * that fails the op's item schema is <b>discarded</b> (never forwarded). The returned pointers
     * name the failing members (e.g. {@code /items/0/pan_hash}) and never contain values, so the
     * caller can log the defect without the payload.
     *
     * @throws TunnelFrameException {@code INTERNAL} when the response envelope itself is invalid
     */
    public CheckedResponse checkResponse(ObjectNode body) throws TunnelFrameException {
        List<TunnelSchemas.Violation> violations = schemas.validate("tunnel/response.schema.json", body);
        if (violations.isEmpty()) {
            return new CheckedResponse(body, 0, List.of());
        }
        TreeSet<Integer> badItems = new TreeSet<>();
        List<String> pointers = new ArrayList<>();
        for (TunnelSchemas.Violation v : violations) {
            Matcher m = ITEM_POINTER.matcher(v.pointer());
            if (!m.matches()) {
                throw new TunnelFrameException(TunnelErrorCode.INTERNAL, "response envelope fails its schema");
            }
            badItems.add(Integer.parseInt(m.group(1)));
            pointers.add(v.pointer());
        }
        ObjectNode cleaned = body.deepCopy();
        ArrayNode items = (ArrayNode) cleaned.get("items");
        for (Integer index : badItems.descendingSet()) {
            if (index < items.size()) {
                items.remove(index);
            }
        }
        return new CheckedResponse(cleaned, badItems.size(), List.copyOf(pointers));
    }
}
