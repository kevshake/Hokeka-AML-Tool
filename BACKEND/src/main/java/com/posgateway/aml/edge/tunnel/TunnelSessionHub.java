package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import com.posgateway.aml.entity.edge.EdgeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongUnaryOperator;

/**
 * Session-holder side of the Edge Tunnel (§7.2, §7.12): owns the WebSocket sessions connected to
 * this instance, runs the hello handshake, answers pings, enforces frame limits, the in-flight
 * cap, idle/hello timeouts and seal-failure closes, keeps the Redis registry current, and moves
 * frames between the Edge and the instance that originated each request.
 *
 * <p><b>Frames stay sealed here.</b> {@code response}/{@code chunk}/{@code error} frames that
 * answer a request are forwarded to the origin as received (the origin opens them). The hub opens
 * only frames addressed to the session itself: {@code hello}, session-level {@code error}, and
 * {@code hold.ack} responses to notifies it delivered.
 */
public class TunnelSessionHub {

    private static final Logger log = LoggerFactory.getLogger(TunnelSessionHub.class);

    /** §7.2 close codes. */
    static final int CLOSE_NORMAL = 1000;
    static final int CLOSE_GOING_AWAY = 4000;
    static final int CLOSE_MALFORMED = 4400;
    static final int CLOSE_SEAL = 4401;
    static final int CLOSE_FORBIDDEN = 4403;
    static final int CLOSE_TIMEOUT = 4408;
    static final int CLOSE_REPLACED = 4409;
    static final int CLOSE_TOO_LARGE = 4413;
    static final int CLOSE_VERSION = 4426;
    static final int CLOSE_INTERNAL = 4500;

    /** Tunnel protocol versions this Control Plane implements (§12.1). */
    static final Set<Integer> SUPPORTED_VERSIONS = Set.of(1);

    private final TunnelProperties props;
    private final TunnelFrameCodec codec;
    private final TunnelNodeDirectory directory;
    private final TunnelRegistry registry;
    private final TunnelMessaging messaging;
    private final TunnelPspSettingsProvider settings;
    private final EdgeControlPlaneKeys cpKeys;
    private final LongUnaryOperator bundleVersion;
    private final List<TunnelSessionListener> listeners;
    private final Clock clock;
    private final ObjectMapper mapper;

    private final Map<String, LocalTunnelSession> byConnection = new ConcurrentHashMap<>();
    private final Map<String, LocalTunnelSession> bySession = new ConcurrentHashMap<>();
    private volatile Instant lastReap = Instant.EPOCH;

    /** Wires the hub; {@code bundleVersion} maps a PSP id to its current rule bundle version. */
    public TunnelSessionHub(TunnelProperties props, TunnelFrameCodec codec, TunnelNodeDirectory directory,
                            TunnelRegistry registry, TunnelMessaging messaging, TunnelPspSettingsProvider settings,
                            EdgeControlPlaneKeys cpKeys, LongUnaryOperator bundleVersion,
                            List<TunnelSessionListener> listeners, Clock clock, ObjectMapper mapper) {
        this.props = props;
        this.codec = codec;
        this.directory = directory;
        this.registry = registry;
        this.messaging = messaging;
        this.settings = settings;
        this.cpKeys = cpKeys;
        this.bundleVersion = bundleVersion;
        this.listeners = List.copyOf(listeners);
        this.clock = clock;
        this.mapper = mapper;
    }

    // ── WebSocket lifecycle (called by TunnelWebSocketHandler) ───────────────────────────────────

    /** A WebSocket was upgraded for an authorised node; wait for its hello. */
    void onOpen(WebSocketSession ws, EdgeNode node) {
        LocalTunnelSession s = new LocalTunnelSession(ws, node.getEdgeId(), node.getPspId(),
                node.effectiveSiteId(), TunnelNodeDirectory.keysOf(node), clock.instant());
        byConnection.put(ws.getId(), s);
    }

    /** One (possibly partial) text message arrived. */
    void onText(WebSocketSession ws, String payload, boolean last) {
        LocalTunnelSession s = byConnection.get(ws.getId());
        if (s == null || s.state() == LocalTunnelSession.State.CLOSED) {
            return;
        }
        String frame;
        try {
            frame = s.appendPartial(payload, last, props.getMaxFrameBytes());
        } catch (LocalTunnelSession.FrameTooLargeException e) {
            close(s, CLOSE_TOO_LARGE, "frame too large");
            return;
        }
        if (frame != null) {
            handleFrame(s, frame);
        }
    }

    /** §7.1: binary frames are not used; a receiver closes with 4400. */
    void onBinary(WebSocketSession ws) {
        LocalTunnelSession s = byConnection.get(ws.getId());
        if (s != null) {
            close(s, CLOSE_MALFORMED, "binary frame");
        }
    }

    /** The container reports the socket closed (either side, any reason). */
    void onClosed(WebSocketSession ws, CloseStatus status) {
        LocalTunnelSession s = byConnection.get(ws.getId());
        if (s != null) {
            cleanup(s);
        }
    }

    // ── frame handling ───────────────────────────────────────────────────────────────────────────

    /** Dispatches one complete frame from the Edge (§7.3, §7.4). */
    private void handleFrame(LocalTunnelSession s, String text) {
        Instant now = clock.instant();
        s.lastFrameAt = now;
        registry.markSeen(s.edgeId, now);
        ObjectNode outer;
        try {
            outer = codec.parseOuter(text);
        } catch (TunnelFrameException e) {
            if (e.code() == TunnelErrorCode.UNSUPPORTED_FRAME && s.state() == LocalTunnelSession.State.OPEN) {
                sendError(s, null, TunnelError.of(TunnelErrorCode.UNSUPPORTED_FRAME, "unknown frame type"));
            } else {
                close(s, CLOSE_MALFORMED, "malformed frame");
            }
            return;
        }
        String type = outer.get("type").asText();
        String corrId = outer.hasNonNull("corrId") ? outer.get("corrId").asText() : null;
        switch (type) {
            case "ping" -> answerPing(s, outer.get("id").asText(), now);
            case "pong" -> { /* the cloud never pings; a pong carries nothing to act on */ }
            case "hello" -> handleHello(s, outer, now);
            case "response", "chunk", "error" -> {
                if (s.state() != LocalTunnelSession.State.OPEN) {
                    close(s, CLOSE_MALFORMED, "frame before hello");
                } else if (corrId == null) {
                    openSessionLevelError(s, outer, now);
                } else {
                    routeAnswer(s, outer, type, corrId, text, now);
                }
            }
            default -> {
                // request / cancel / credit / notify / hello.ack only travel cloud → Edge.
                if (s.state() == LocalTunnelSession.State.OPEN) {
                    sendError(s, null, TunnelError.of(TunnelErrorCode.UNSUPPORTED_FRAME,
                            "frame type not accepted from an Edge"));
                } else {
                    close(s, CLOSE_MALFORMED, "frame before hello");
                }
            }
        }
    }

    /** §7.2 heartbeat: answer with {@code pong}, corrId = ping id; refresh the registry entry. */
    private void answerPing(LocalTunnelSession s, String pingId, Instant now) {
        try {
            s.send(codec.pong(pingId, now));
        } catch (IOException e) {
            close(s, CLOSE_INTERNAL, "send failed");
            return;
        }
        if (s.state() == LocalTunnelSession.State.OPEN) {
            s.lastPongAt = now;
            publishEntry(s);
        }
    }

    /**
     * §7.2 handshake: open and bind {@code hello}, check identity/site/PSP (4403), lifecycle (4403),
     * protocol version (4426), tunnel enabled (4403); answer {@code hello.ack}, register in Redis and
     * replace any older session of the same node (4409).
     */
    private void handleHello(LocalTunnelSession s, ObjectNode outer, Instant now) {
        if (s.state() != LocalTunnelSession.State.AWAITING_HELLO) {
            close(s, CLOSE_MALFORMED, "duplicate hello");
            return;
        }
        TunnelFrameCodec.OpenedFrame hello;
        try {
            hello = codec.open(outer, cpKeys.ingestPrivateKey(), s.keys.ed25519(), s.edgeId, null, now);
        } catch (TunnelFrameException e) {
            log.warn("Tunnel hello from {} refused: {}", s.edgeId, e.code());
            close(s, CLOSE_SEAL, "hello refused");
            return;
        }
        if (!codec.checkBody("hello", hello.body()).isEmpty()) {
            close(s, CLOSE_MALFORMED, "hello body invalid");
            return;
        }
        JsonNode body = hello.body();
        Optional<EdgeNode> node = directory.authorized(s.edgeId);
        if (node.isEmpty()
                || !s.edgeId.equals(body.get("edgeId").asText())
                || !node.get().effectiveSiteId().equals(body.get("siteId").asText())
                || node.get().getPspId() != body.get("pspId").asLong()) {
            log.warn("Tunnel hello from {} refused: identity, site, PSP or lifecycle mismatch", s.edgeId);
            close(s, CLOSE_FORBIDDEN, "not authorised");
            return;
        }
        TunnelPspSettingsProvider.TunnelPspSettings cfg = settings.settings(s.pspId);
        if (!cfg.tunnelEnabled()) {
            close(s, CLOSE_FORBIDDEN, "tunnel disabled");
            return;
        }
        int version = -1;
        for (JsonNode v : body.get("protocolVersions")) {
            if (SUPPORTED_VERSIONS.contains(v.asInt()) && v.asInt() > version) {
                version = v.asInt();
            }
        }
        if (version < 0) {
            close(s, CLOSE_VERSION, "no common protocol version");
            return;
        }
        Set<String> ops = new LinkedHashSet<>();
        for (JsonNode op : body.get("opsEnabled")) {
            String name = op.asText();
            boolean allowed = cfg.opsAllowed().contains(name)
                    && (!TunnelOp.LEDGER_BACKFILL.wire().equals(name) || cfg.backfillAllowed());
            if (allowed) {
                ops.add(name);
            }
        }
        int maxInflight = Math.min(body.get("maxInflight").asInt(),
                Math.min(cfg.maxInflightPerSession(), props.getMaxInflight()));
        String sessionId = TunnelFrameCodec.newId();

        ObjectNode ack = mapper.createObjectNode();
        ack.put("sessionId", sessionId);
        ack.put("cpInstance", messaging.instanceId());
        ack.put("protocolVersion", version);
        ack.put("serverTime", TunnelTime.formatMillis(now));
        ArrayNode eff = ack.putArray("opsEffective");
        ops.forEach(eff::add);
        ack.put("maxInflight", maxInflight);
        ack.put("heartbeatIntervalMs", props.getHeartbeatIntervalMs());
        ack.put("idleTimeoutMs", props.getIdleTimeoutMs());
        ack.put("currentConfigVersion", cfg.configVersion());
        ack.put("currentBundleVersion", currentBundleVersion(s.pspId));
        if (!codec.checkBody("hello.ack", ack).isEmpty()) {
            log.error("Tunnel hello.ack for {} fails its own schema; closing", s.edgeId);
            close(s, CLOSE_INTERNAL, "internal error");
            return;
        }
        String ackFrame;
        try {
            ackFrame = codec.seal(new TunnelFrameCodec.FrameSpec("hello.ack", TunnelFrameCodec.newId(),
                    hello.id(), sessionId, s.edgeId, ack), s.keys.x25519Raw(), cpKeys.signingPrivateKey(), now);
        } catch (TunnelFrameException e) {
            close(s, CLOSE_INTERNAL, "internal error");
            return;
        }
        s.sessionId = sessionId;
        s.opsEffective = Set.copyOf(ops);
        s.maxInflight = maxInflight;
        s.connectedAt = now;
        s.lastPongAt = now;
        if (!s.markOpen()) {
            return;
        }
        bySession.put(sessionId, s);
        publishEntry(s);
        try {
            s.send(ackFrame);
        } catch (IOException e) {
            close(s, CLOSE_INTERNAL, "send failed");
            return;
        }
        replaceOlderSessions(s);
        log.info("Tunnel session {} open for edge {} (site {}, PSP {}, {} ops, maxInflight {})",
                sessionId, s.edgeId, s.siteId, s.pspId, ops.size(), maxInflight);
        TunnelSessionListener.TunnelSessionInfo info = info(s);
        for (TunnelSessionListener l : listeners) {
            try {
                l.onSessionOpened(info);
            } catch (RuntimeException e) {
                log.warn("Tunnel session listener {} failed on open: {}", l.getClass().getSimpleName(),
                        e.getClass().getSimpleName());
            }
        }
    }

    /** Current bundle version for {@code hello.ack}; -1 when no bundle can be compiled yet. */
    private long currentBundleVersion(long pspId) {
        try {
            return Math.max(-1L, bundleVersion.applyAsLong(pspId));
        } catch (RuntimeException e) {
            return -1L;
        }
    }

    /** §7.2 one session per node: close every other registered session of the same edgeId. */
    private void replaceOlderSessions(LocalTunnelSession s) {
        for (TunnelRegistry.Entry e : registry.entries(s.pspId)) {
            if (e.edgeId().equals(s.edgeId) && !e.sessionId().equals(s.sessionId)) {
                registry.remove(s.pspId, e.sessionId());
                messaging.toHolder(e.cpInstance(), RelayMessage.control(RelayMessage.Kind.CLOSE_REPLACED,
                        e.sessionId(), null));
            }
        }
    }

    /**
     * Routes an Edge answer by corrId: to the origin of the request (still sealed), or — for a
     * notify this hub delivered — opens the {@code hold.ack} locally. Late frames for finished
     * requests are dropped.
     */
    private void routeAnswer(LocalTunnelSession s, ObjectNode outer, String type, String corrId, String text,
                             Instant now) {
        LocalTunnelSession.Route route = s.routes.get(corrId);
        if (route != null) {
            messaging.toOrigin(route.origin(), RelayMessage.frame(s.sessionId, corrId, text));
            return;
        }
        if (s.notifyIds.containsKey(corrId)) {
            handleNotifyAnswer(s, outer, type, corrId, now);
            return;
        }
        log.debug("Tunnel session {}: dropping {} for unknown or finished request", s.sessionId, type);
    }

    /** Opens a {@code hold.ack} response (or an error answering a notify) and informs listeners. */
    private void handleNotifyAnswer(LocalTunnelSession s, ObjectNode outer, String type, String notifyId,
                                    Instant now) {
        TunnelFrameCodec.OpenedFrame f;
        try {
            f = codec.open(outer, cpKeys.ingestPrivateKey(), s.keys.ed25519(), s.edgeId, s.sessionId, now);
        } catch (TunnelFrameException e) {
            sealFailure(s, e.code());
            return;
        }
        s.notifyIds.remove(notifyId);
        if ("error".equals(type)) {
            if (codec.checkBody("error", f.body()).isEmpty()) {
                log.warn("Tunnel session {}: Edge refused notify with {}", s.sessionId, f.body().get("code").asText());
            }
            return;
        }
        TunnelFrameCodec.CheckedResponse checked;
        try {
            checked = codec.checkResponse(f.body());
        } catch (TunnelFrameException e) {
            log.warn("Tunnel session {}: invalid hold.ack envelope discarded", s.sessionId);
            return;
        }
        if (!"hold.ack".equals(checked.body().path("op").asText()) || checked.body().get("items").isEmpty()) {
            log.warn("Tunnel session {}: notify answer is not a valid hold.ack", s.sessionId);
            return;
        }
        ObjectNode item = (ObjectNode) checked.body().get("items").get(0);
        TunnelSessionListener.TunnelSessionInfo info = info(s);
        for (TunnelSessionListener l : listeners) {
            try {
                l.onHoldAck(info, notifyId, item);
            } catch (RuntimeException e) {
                log.warn("Tunnel session listener {} failed on hold.ack: {}", l.getClass().getSimpleName(),
                        e.getClass().getSimpleName());
            }
        }
    }

    /** A session-level {@code error} (no corrId): open, validate and log its code only. */
    private void openSessionLevelError(LocalTunnelSession s, ObjectNode outer, Instant now) {
        try {
            TunnelFrameCodec.OpenedFrame f = codec.open(outer, cpKeys.ingestPrivateKey(), s.keys.ed25519(),
                    s.edgeId, s.sessionId, now);
            if (codec.checkBody("error", f.body()).isEmpty()) {
                log.warn("Tunnel session {}: Edge reported session error {}", s.sessionId,
                        f.body().get("code").asText());
            }
        } catch (TunnelFrameException e) {
            sealFailure(s, e.code());
        }
    }

    /** Counts a failed open; at the §3.3 threshold the session is closed with 4401. */
    private void sealFailure(LocalTunnelSession s, TunnelErrorCode code) {
        log.warn("Tunnel session {}: frame refused ({})", s.sessionId, code);
        if (s.recordSealFailure(clock.millis(), props.getSealFailureLimit(), props.getSealFailureWindowMs())) {
            close(s, CLOSE_SEAL, "repeated seal failures");
        }
    }

    /** Sends a sealed cloud-originated {@code error} frame on an open session. */
    private void sendError(LocalTunnelSession s, String corrId, TunnelError error) {
        if (s.state() != LocalTunnelSession.State.OPEN) {
            return;
        }
        try {
            s.send(codec.seal(new TunnelFrameCodec.FrameSpec("error", TunnelFrameCodec.newId(), corrId,
                    s.sessionId, s.edgeId, error.toJson()), s.keys.x25519Raw(), cpKeys.signingPrivateKey(),
                    clock.instant()));
        } catch (IOException | TunnelFrameException e) {
            close(s, CLOSE_INTERNAL, "send failed");
        }
    }

    // ── holder-bound relay messages ──────────────────────────────────────────────────────────────

    /** Handles a holder-bound {@link RelayMessage} (from this or another instance). */
    void onHolderMessage(RelayMessage m) {
        LocalTunnelSession s = m.sessionId() == null ? null : bySession.get(m.sessionId());
        boolean live = s != null && s.state() == LocalTunnelSession.State.OPEN;
        switch (m.kind()) {
            case REQUEST -> acceptRequest(s, live, m);
            case CANCEL, CREDIT -> {
                if (live) {
                    sendOrClose(s, m.frame());
                }
            }
            case NOTIFY -> {
                if (live) {
                    s.notifyIds.put(m.corrId(), clock.millis());
                    sendOrClose(s, m.frame());
                }
            }
            case FINISH -> {
                if (s != null && s.routes.remove(m.corrId()) != null && live) {
                    publishEntry(s);
                }
            }
            case SEAL_FAILURE -> {
                if (live) {
                    sealFailure(s, TunnelErrorCode.SEAL_INVALID);
                }
            }
            case CLOSE_REPLACED -> {
                if (s != null) {
                    close(s, CLOSE_REPLACED, "replaced by a newer session");
                }
            }
            default -> log.debug("Tunnel hub: ignoring {} on the holder channel", m.kind());
        }
    }

    /**
     * REQUEST: take an in-flight slot (§7.5 cap → EDGE_BUSY), check the op is effective for the
     * session (OP_NOT_ALLOWED), remember the route, and send the sealed request frame.
     */
    private void acceptRequest(LocalTunnelSession s, boolean live, RelayMessage m) {
        if (!live) {
            messaging.toOrigin(m.origin(), RelayMessage.reject(m.sessionId(), m.corrId(), TunnelErrorCode.EDGE_OFFLINE));
            return;
        }
        if (!s.opsEffective.contains(m.op())) {
            messaging.toOrigin(m.origin(), RelayMessage.reject(m.sessionId(), m.corrId(), TunnelErrorCode.OP_NOT_ALLOWED));
            return;
        }
        boolean busy;
        synchronized (s) {
            busy = s.routes.size() >= s.maxInflight;
            if (!busy) {
                s.routes.put(m.corrId(), new LocalTunnelSession.Route(m.origin(), m.op(),
                        m.deadlineAt() == null ? clock.millis() + 300_000 : m.deadlineAt()));
            }
        }
        if (busy) {
            messaging.toOrigin(m.origin(), RelayMessage.reject(m.sessionId(), m.corrId(), TunnelErrorCode.EDGE_BUSY));
            return;
        }
        publishEntry(s);
        try {
            s.send(m.frame());
        } catch (IOException e) {
            s.routes.remove(m.corrId());
            messaging.toOrigin(m.origin(), RelayMessage.reject(m.sessionId(), m.corrId(), TunnelErrorCode.EDGE_OFFLINE));
            close(s, CLOSE_INTERNAL, "send failed");
        }
    }

    /** Forwards a sealed frame; a failed send closes the session. */
    private void sendOrClose(LocalTunnelSession s, String frame) {
        try {
            s.send(frame);
        } catch (IOException e) {
            close(s, CLOSE_INTERNAL, "send failed");
        }
    }

    // ── housekeeping ─────────────────────────────────────────────────────────────────────────────

    /**
     * Periodic tick: hello timeout and idle timeout (4408), node revocation / tunnel disabled
     * (4403), safety release of in-flight slots past their deadline, expiry of unanswered notify
     * ids, and reaping of stale registry entries.
     */
    void housekeeping() {
        Instant now = clock.instant();
        long nowMs = now.toEpochMilli();
        for (LocalTunnelSession s : new ArrayList<>(byConnection.values())) {
            if (s.state() == LocalTunnelSession.State.AWAITING_HELLO
                    && s.upgradedAt.plusMillis(props.getHelloTimeoutMs()).isBefore(now)) {
                close(s, CLOSE_TIMEOUT, "hello timeout");
                continue;
            }
            if (s.state() != LocalTunnelSession.State.OPEN) {
                continue;
            }
            if (s.lastFrameAt.plusMillis(props.getIdleTimeoutMs()).isBefore(now)) {
                close(s, CLOSE_TIMEOUT, "idle timeout");
                continue;
            }
            if (s.lastNodeCheck.plusMillis(props.getNodeRecheckIntervalMs()).isBefore(now)) {
                s.lastNodeCheck = now;
                if (directory.authorized(s.edgeId).isEmpty() || !settings.settings(s.pspId).tunnelEnabled()) {
                    close(s, CLOSE_FORBIDDEN, "not authorised");
                    continue;
                }
            }
            boolean released = s.routes.entrySet().removeIf(e -> e.getValue().deadlineAt() + 2_000 < nowMs);
            if (released) {
                publishEntry(s);
            }
            s.notifyIds.entrySet().removeIf(e -> e.getValue() + 600_000 < nowMs);
        }
        if (lastReap.plusMillis(Math.max(props.getRegistryStaleMs() / 2, 1_000)).isBefore(now)) {
            lastReap = now;
            try {
                registry.reapStale(now);
            } catch (RuntimeException e) {
                log.warn("Tunnel registry reap failed: {}", e.getClass().getSimpleName());
            }
        }
    }

    /** Instance shutdown: close every session with 4000 so Edges reconnect elsewhere at once. */
    void shutdown() {
        for (LocalTunnelSession s : new ArrayList<>(byConnection.values())) {
            close(s, CLOSE_GOING_AWAY, "instance going away");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /** Closes the socket and runs the cleanup immediately (the container callback is idempotent). */
    private void close(LocalTunnelSession s, int code, String reason) {
        s.close(code, reason);
        cleanup(s);
    }

    /**
     * Exactly-once cleanup: forget the session, remove its registry field, and tell every origin
     * with a request in flight that the session is gone (§7.12 retry-once).
     */
    private void cleanup(LocalTunnelSession s) {
        if (!s.markClosed()) {
            return;
        }
        byConnection.remove(s.wsId());
        if (s.sessionId != null) {
            bySession.remove(s.sessionId);
            try {
                registry.remove(s.pspId, s.sessionId);
            } catch (RuntimeException e) {
                log.warn("Tunnel registry remove failed for session {}", s.sessionId);
            }
            for (Map.Entry<String, LocalTunnelSession.Route> r : s.routes.entrySet()) {
                messaging.toOrigin(r.getValue().origin(), RelayMessage.sessionClosed(s.sessionId, r.getKey()));
            }
            s.routes.clear();
            log.info("Tunnel session {} closed (edge {})", s.sessionId, s.edgeId);
        }
    }

    /** Rewrites this session's registry value from local state. */
    private void publishEntry(LocalTunnelSession s) {
        if (s.sessionId == null || s.state() != LocalTunnelSession.State.OPEN) {
            return;
        }
        registry.put(new TunnelRegistry.Entry(s.pspId, s.sessionId, s.edgeId, s.siteId, messaging.instanceId(),
                s.routes.size(), s.connectedAt, s.lastPongAt));
    }

    /** Ids-only description of a session for listeners. */
    private TunnelSessionListener.TunnelSessionInfo info(LocalTunnelSession s) {
        return new TunnelSessionListener.TunnelSessionInfo(s.pspId, s.siteId, s.edgeId, s.sessionId,
                messaging.instanceId());
    }

    /** Number of open sessions on this instance (operational metric and tests). */
    public int openSessionCount() {
        return bySession.size();
    }
}
