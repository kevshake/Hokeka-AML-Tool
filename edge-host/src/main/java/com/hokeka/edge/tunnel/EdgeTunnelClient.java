package com.hokeka.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.activation.AuthorizationGate;
import com.hokeka.edge.contract.ContractSchemas;
import com.hokeka.edge.crypto.EdgeKeys;
import com.hokeka.edge.residency.EdgeConfigDocument;
import com.hokeka.edge.residency.EdgeConfigStore;
import com.hokeka.edge.residency.LegalHoldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The Edge Tunnel client (EDGE_DATA_RESIDENCY_CONTRACT.md §7): the node's one outbound WebSocket to
 * the Control Plane, over which the cloud reads the Edge Ledger.
 *
 * <h2>Session</h2>
 * JDK {@code java.net.http.WebSocket} over the node's mTLS {@code SSLContext}, subprotocol
 * {@code hokeka-tunnel.v1}, headers {@code X-Hokeka-Edge-Id} / {@code X-Edge-Agent-Version}. The node
 * sends a sealed {@code hello} at once and expects a {@code hello.ack} within 10 s; it then pings
 * every {@code heartbeatIntervalMs} and closes 4408 after {@code idleTimeoutMs} without a frame.
 *
 * <h2>Reconnect (§7.2)</h2>
 * Only while authorised and allowed (EDGE_TUNNEL_ENABLED, config {@code tunnel.enabled}); full-jitter
 * exponential backoff 1 s → 60 s, reset by a successful handshake. 4000: within 5 s. 4403: not until
 * the next successful bundle poll. 4409 (replaced by a newer session of this node): no reconnect for
 * {@code supersededHoldOff}, so two processes sharing an identity do not replace each other in a
 * loop. 4426: never (needs an upgrade).
 *
 * <h2>Frames</h2>
 * Every cloud frame is checked in the §7.4 order (outer schema and 1 MiB, HSE-1 open against the
 * pinned Control Plane key, replay window, binding, body schema) before it is acted on. Three seal,
 * replay or binding failures within 60 s close the session with 4401. Requests run on virtual
 * threads within the session's in-flight cap; each is written to {@code edge_access_log} on receipt
 * (refusals included) and completed with its outcome and returned ids.
 */
public class EdgeTunnelClient {

    private static final Logger log = LoggerFactory.getLogger(EdgeTunnelClient.class);

    /** Tunnel path on the Control Plane (§7.1). */
    public static final String TUNNEL_PATH = "/api/v1/edge/tunnel";

    /** §7.2 close codes. */
    public static final int CLOSE_NORMAL = 1000;
    public static final int CLOSE_GOING_AWAY = 4000;
    public static final int CLOSE_MALFORMED = 4400;
    public static final int CLOSE_SEAL = 4401;
    public static final int CLOSE_FORBIDDEN = 4403;
    public static final int CLOSE_TIMEOUT = 4408;
    public static final int CLOSE_REPLACED = 4409;
    public static final int CLOSE_TOO_LARGE = 4413;
    public static final int CLOSE_VERSION = 4426;
    public static final int CLOSE_INTERNAL = 4500;
    /** Locally recorded code for a connection that failed without a close frame. */
    public static final int CLOSE_ABNORMAL = 1006;

    /** Tunnel protocol versions this Edge implements (§12.1). */
    static final List<Integer> PROTOCOL_VERSIONS = List.of(1);

    /** hello.ack members this version understands; others are ignored (§12.2, control docs open at the top). */
    private static final Set<String> ACK_MEMBERS = Set.of("sessionId", "cpInstance", "protocolVersion", "serverTime",
            "opsEffective", "maxInflight", "heartbeatIntervalMs", "idleTimeoutMs", "currentConfigVersion",
            "currentBundleVersion");

    /** Notify types this version handles (§7.10). */
    private static final Set<String> NOTIFY_TYPES = Set.of("config.updated", "bundle.updated", "hold.place",
            "hold.release");

    /** Client lifecycle state, as surfaced on {@code /edge/status}. */
    public enum State {
        /** Not started, or shut down. */
        STOPPED,
        /** EDGE_TUNNEL_ENABLED=false, channel disabled, or config tunnel.enabled=false. */
        DISABLED,
        /** Waiting for authorisation or for missing settings. */
        WAITING,
        /** WebSocket upgrade in progress. */
        CONNECTING,
        /** hello sent, waiting for hello.ack. */
        HANDSHAKING,
        /** Session open. */
        OPEN,
        /** Waiting to reconnect. */
        BACKOFF,
        /** Closed 4403: waiting for the next successful bundle poll. */
        FORBIDDEN,
        /** Closed 4409: a newer session of this node is live; holding off. */
        SUPERSEDED,
        /** Closed 4426: no common protocol version; needs an upgrade. */
        VERSION_UNSUPPORTED
    }

    /**
     * Who this node is.
     *
     * @param edgeId  {@code EDGE_EDGEID}
     * @param siteId  the Edge Site id
     * @param pspId   {@code EDGE_PSPID}, or null when not a positive integer
     */
    public record Identity(String edgeId, String siteId, Long pspId) {
    }

    /**
     * The node's and the Control Plane's keys (§3.1).
     *
     * @param cpX25519Raw    pinned Control Plane ingest key (Edge→cloud frames are sealed to it)
     * @param cpEd25519      pinned Control Plane signing key (cloud frames are verified with it)
     * @param edgeX25519     this node's X25519 private key (opens cloud frames)
     * @param edgeEd25519    this node's Ed25519 private key (signs Edge frames)
     */
    public record Keys(byte[] cpX25519Raw, PublicKey cpEd25519, PrivateKey edgeX25519, PrivateKey edgeEd25519) {
    }

    /**
     * External signals the client reacts to or reports.
     *
     * @param configPollSoon     fetch /edge/config now (notify config.updated, newer currentConfigVersion)
     * @param bundlePollSoon     poll /edge/bundle now (notify bundle.updated, newer currentBundleVersion)
     * @param bundleLastSuccess  last successful bundle poll (lifts the 4403 block)
     * @param bundleVersion      active rule bundle version (hello)
     * @param ledgerSchemaVersion ledger schema version (hello)
     */
    public record Signals(Runnable configPollSoon, Runnable bundlePollSoon, Supplier<Instant> bundleLastSuccess,
                          LongSupplier bundleVersion, IntSupplier ledgerSchemaVersion) {
    }

    private final TunnelProperties properties;
    private final URI uri;
    private final Identity identity;
    private final Supplier<Keys> keys;
    private final Supplier<HttpClient> httpClients;
    private final AuthorizationGate authorization;
    private final TunnelFrameCodec codec;
    private final TunnelOps ops;
    private final AccessLogWriter accessLog;
    private final EdgeConfigStore config;
    private final LegalHoldService holds;
    private final ResponseFrames frames;
    private final Signals signals;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String agentVersion;
    private final String bootId = UUID.randomUUID().toString();
    private final Instant startedAt;
    private final ScheduledExecutorService exec;

    private final Object lifecycle = new Object();
    private volatile Conn current;
    private volatile State state = State.STOPPED;
    private volatile boolean stopped = true;
    private volatile int attempts;
    private volatile Instant nextAttemptAt;
    private volatile Instant forbiddenSince;
    private volatile Instant supersededUntil;
    private volatile int lastCloseCode;
    private volatile String lastCloseReason = "";
    private volatile Instant lastCloseAt;
    private volatile String lastError = "";
    private volatile ScheduledFuture<?> pendingAttempt;
    private final AtomicLong serial = new AtomicLong();
    private final AtomicLong requestsServed = new AtomicLong();
    private final AtomicLong requestsRefused = new AtomicLong();
    private final AtomicLong sealFailuresTotal = new AtomicLong();
    private final Set<String> loggedOnce = ConcurrentHashMap.newKeySet();

    /**
     * @param properties  local tunnel settings
     * @param uri         the {@code wss://} endpoint, or null when it cannot be derived (logged by name)
     * @param identity    node identity
     * @param keys        key supplier (resolved per connection, so missing keys are re-checked)
     * @param httpClients mTLS HttpClient factory
     * @param authorization node authorisation (ActivationService)
     * @param codec       frame codec
     * @param ops         op dispatcher
     * @param accessLog   Edge audit writer
     * @param config      the config in force
     * @param holds       legal holds
     * @param signals     poll triggers and hello facts
     * @param mapper      JSON mapper
     * @param clock       time source
     */
    public EdgeTunnelClient(TunnelProperties properties, URI uri, Identity identity, Supplier<Keys> keys,
                            Supplier<HttpClient> httpClients, AuthorizationGate authorization, TunnelFrameCodec codec,
                            TunnelOps ops, AccessLogWriter accessLog, EdgeConfigStore config, LegalHoldService holds,
                            Signals signals, ObjectMapper mapper, Clock clock) {
        this.properties = properties;
        this.uri = uri;
        this.identity = identity;
        this.keys = keys;
        this.httpClients = httpClients;
        this.authorization = authorization;
        this.codec = codec;
        this.ops = ops;
        this.accessLog = accessLog;
        this.config = config;
        this.holds = holds;
        this.frames = new ResponseFrames(mapper);
        this.signals = signals;
        this.mapper = mapper;
        this.clock = clock;
        this.startedAt = clock.instant();
        String v = properties.getAgentVersion();
        this.agentVersion = v == null || !v.matches("^[0-9A-Za-z.+_-]{1,64}$") ? "0.0.0" : v;
        this.exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "edge-tunnel");
            t.setDaemon(true);
            return t;
        });
        config.addListener(this::onConfigAccepted);
    }

    /**
     * Derives the tunnel endpoint: {@code EDGE_TUNNEL_URL} when set (must be {@code wss://}), else
     * {@code CONTROLPLANE_URL} with {@code https} → {@code wss} plus {@link #TUNNEL_PATH}.
     *
     * @return the endpoint, or null when neither yields a {@code wss://} URI
     */
    public static URI tunnelUri(String tunnelUrl, String controlPlaneUrl) {
        try {
            if (tunnelUrl != null && !tunnelUrl.isBlank()) {
                URI u = URI.create(tunnelUrl.trim());
                return "wss".equalsIgnoreCase(u.getScheme()) ? u : null;
            }
            if (controlPlaneUrl == null || controlPlaneUrl.isBlank()) {
                return null;
            }
            String base = controlPlaneUrl.trim();
            if (!base.regionMatches(true, 0, "https://", 0, 8)) {
                return null;
            }
            base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            return URI.create("wss://" + base.substring(8) + TUNNEL_PATH);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── lifecycle ────────────────────────────────────────────────────────────────────────────────

    /** Starts connecting (idempotent). */
    public void start() {
        synchronized (lifecycle) {
            if (!stopped) {
                return;
            }
            stopped = false;
            state = State.WAITING;
        }
        schedule(this::attemptConnect, Duration.ZERO);
    }

    /** Closes the session with 1000 and stops reconnecting. */
    public void stop() {
        synchronized (lifecycle) {
            stopped = true;
            state = State.STOPPED;
            ScheduledFuture<?> p = pendingAttempt;
            if (p != null) {
                p.cancel(false);
            }
        }
        Conn c = current;
        if (c != null) {
            closeLocally(c, CLOSE_NORMAL, "shutdown");
        }
        exec.shutdown();
        try {
            exec.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        exec.shutdownNow();
    }

    /** Current state. */
    public State state() {
        return state;
    }

    /** The live session id, or null. */
    public String sessionId() {
        Conn c = current;
        return c == null || c.closed ? null : c.sessionId;
    }

    /** Status for {@code /edge/status}: state, session, close history, counters. Ids and counts only. */
    public Map<String, Object> status() {
        Map<String, Object> s = new LinkedHashMap<>();
        Conn c = current;
        boolean live = c != null && !c.closed && c.sessionId != null;
        s.put("state", state.name());
        s.put("endpoint", uri == null ? null : uri.toString());
        s.put("sessionId", live ? c.sessionId : null);
        s.put("cpInstance", live ? c.cpInstance : null);
        s.put("connectedAt", live && c.connectedAt != null ? c.connectedAt.toString() : null);
        s.put("opsEffective", live ? List.copyOf(c.opsEffective) : List.of());
        s.put("maxInflight", live ? c.maxInflight : 0);
        s.put("inflight", live ? c.inflight.size() : 0);
        s.put("reconnectAttempts", attempts);
        s.put("nextAttemptAt", nextAttemptAt == null ? null : nextAttemptAt.toString());
        s.put("lastCloseCode", lastCloseCode == 0 ? null : lastCloseCode);
        s.put("lastCloseReason", lastCloseReason);
        s.put("lastCloseAt", lastCloseAt == null ? null : lastCloseAt.toString());
        s.put("requestsServed", requestsServed.get());
        s.put("requestsRefused", requestsRefused.get());
        s.put("sealFailures", sealFailuresTotal.get());
        s.put("itemDefects", ops.itemDefects());
        s.put("lastError", lastError);
        return s;
    }

    /** Runs {@code task} on the client thread after {@code delay}. */
    private void schedule(Runnable task, Duration delay) {
        synchronized (lifecycle) {
            if (stopped || exec.isShutdown()) {
                return;
            }
            ScheduledFuture<?> previous = pendingAttempt;
            if (previous != null && !previous.isDone()) {
                previous.cancel(false);
            }
            nextAttemptAt = clock.instant().plus(delay);
            pendingAttempt = exec.schedule(() -> {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    lastError = e.getClass().getSimpleName();
                    log.error("Edge tunnel task failed ({}); retrying with backoff", e.getClass().getSimpleName());
                    reconnectAfter(CLOSE_ABNORMAL);
                }
            }, Math.max(0, delay.toMillis()), TimeUnit.MILLISECONDS);
        }
    }

    /** Runs {@code task} on the client thread; dropped silently once the client is shut down. */
    private void later(Runnable task) {
        try {
            exec.execute(task);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // Shut down: the connection is being torn down anyway.
        }
    }

    /** Logs a message once per key (missing settings, disabled tunnel). */
    private void logOnce(String key, String message, Object... args) {
        if (loggedOnce.add(key)) {
            log.warn(message, args);
        }
    }

    /**
     * The connect gate (§7.2, §7.6.1): enabled locally and by config, settings present, node
     * authorised, not blocked by 4403/4409/4426. Reschedules itself while gated.
     */
    private void attemptConnect() {
        if (stopped) {
            return;
        }
        Conn live = current;
        if (live != null && !live.closed) {
            return;
        }
        Duration recheck = properties.getGateRecheckInterval();
        if (state == State.VERSION_UNSUPPORTED) {
            return;
        }
        if (!properties.isEnabled()) {
            state = State.DISABLED;
            logOnce("disabled", "Edge tunnel disabled locally (EDGE_TUNNEL_ENABLED=false): the cloud cannot read this "
                    + "site's ledger");
            schedule(this::attemptConnect, recheck);
            return;
        }
        Optional<EdgeConfigDocument> doc = config.current();
        if (doc.isPresent() && !doc.get().tunnel().enabled()) {
            state = State.DISABLED;
            logOnce("config-disabled", "Edge tunnel disabled by the Edge config (tunnel.enabled=false)");
            schedule(this::attemptConnect, recheck);
            return;
        }
        loggedOnce.remove("config-disabled");
        List<String> missing = missingSettings();
        if (!missing.isEmpty()) {
            state = State.WAITING;
            logOnce("missing:" + missing, "Edge tunnel cannot connect: {} not set", String.join(", ", missing));
            schedule(this::attemptConnect, recheck);
            return;
        }
        if (!authorization.authorized()) {
            state = State.WAITING;
            schedule(this::attemptConnect, recheck);
            return;
        }
        Instant now = clock.instant();
        if (forbiddenSince != null) {
            Instant bundleOk = signals.bundleLastSuccess().get();
            if (bundleOk == null || !bundleOk.isAfter(forbiddenSince)) {
                state = State.FORBIDDEN;
                schedule(this::attemptConnect, recheck);
                return;
            }
            forbiddenSince = null;
        }
        if (supersededUntil != null && now.isBefore(supersededUntil)) {
            state = State.SUPERSEDED;
            schedule(this::attemptConnect, Duration.between(now, supersededUntil));
            return;
        }
        supersededUntil = null;
        connect();
    }

    /** Names of settings the tunnel needs and does not have (never their values). */
    private List<String> missingSettings() {
        List<String> missing = new ArrayList<>();
        if (identity.edgeId() == null || identity.edgeId().isBlank()) {
            missing.add("EDGE_EDGEID");
        }
        if (identity.pspId() == null) {
            missing.add("EDGE_PSPID");
        }
        if (uri == null) {
            missing.add("CONTROLPLANE_URL (or EDGE_TUNNEL_URL)");
        }
        return missing;
    }

    /** Opens a new WebSocket. */
    private void connect() {
        Keys k;
        HttpClient http;
        try {
            k = keys.get();
            http = httpClients.get();
        } catch (RuntimeException e) {
            state = State.WAITING;
            lastError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            logOnce("keys:" + lastError, "Edge tunnel cannot connect: {}", lastError);
            schedule(this::attemptConnect, properties.getGateRecheckInterval());
            return;
        }
        Conn conn = new Conn(serial.incrementAndGet(), k);
        current = conn;
        state = State.CONNECTING;
        http.newWebSocketBuilder()
                .subprotocols(TunnelFrameCodec.SUBPROTOCOL)
                .header("X-Hokeka-Edge-Id", identity.edgeId())
                .header("X-Edge-Agent-Version", agentVersion)
                .connectTimeout(properties.getConnectTimeout())
                .buildAsync(uri, conn)
                .whenComplete((ws, err) -> later(() -> onUpgraded(conn, ws, err)));
    }

    /** The upgrade finished (or failed): check the subprotocol and send hello. */
    private void onUpgraded(Conn conn, WebSocket ws, Throwable err) {
        if (err != null) {
            Throwable cause = err.getCause() != null ? err.getCause() : err;
            lastError = "connect failed (" + cause.getClass().getSimpleName() + ")";
            log.warn("Edge tunnel connect to {} failed ({})", uri, cause.getClass().getSimpleName());
            onGone(conn, CLOSE_ABNORMAL, "connect failed");
            return;
        }
        if (conn != current || conn.closed || stopped) {
            ws.abort();
            return;
        }
        conn.ws = ws;
        if (!TunnelFrameCodec.SUBPROTOCOL.equals(ws.getSubprotocol())) {
            log.warn("Edge tunnel: the Control Plane did not select {}; closing", TunnelFrameCodec.SUBPROTOCOL);
            closeLocally(conn, CLOSE_MALFORMED, "subprotocol not selected");
            return;
        }
        try {
            sendHello(conn);
        } catch (TunnelException | RuntimeException e) {
            lastError = "hello could not be sent";
            closeLocally(conn, CLOSE_INTERNAL, "hello could not be sent");
            return;
        }
        state = State.HANDSHAKING;
        conn.helloTimer = exec.schedule(() -> {
            if (conn == current && !conn.closed && conn.sessionId == null) {
                log.warn("Edge tunnel: no hello.ack within {} ms; closing", properties.getHelloTimeout().toMillis());
                closeLocally(conn, CLOSE_TIMEOUT, "hello.ack timeout");
            }
        }, properties.getHelloTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Builds and sends the sealed hello (§7.2). */
    private void sendHello(Conn conn) throws TunnelException {
        ObjectNode hello = mapper.createObjectNode();
        hello.put("edgeId", identity.edgeId());
        hello.put("siteId", identity.siteId());
        hello.put("pspId", identity.pspId());
        hello.put("bootId", bootId);
        hello.put("agentVersion", agentVersion);
        ArrayNode versions = hello.putArray("protocolVersions");
        PROTOCOL_VERSIONS.forEach(versions::add);
        hello.put("ledgerSchemaVersion", Math.max(1, signals.ledgerSchemaVersion().getAsInt()));
        ArrayNode enabled = hello.putArray("opsEnabled");
        localOps().forEach(enabled::add);
        hello.put("maxInflight", localMaxInflight());
        hello.put("maxFrameBytes", TunnelFrameCodec.MAX_FRAME_BYTES);
        config.currentVersion().ifPresent(v -> hello.put("configVersion", v));
        hello.put("bundleVersion", Math.max(-1L, signals.bundleVersion().getAsLong()));
        hello.put("startedAt", TunnelTime.millis(startedAt));
        List<ContractSchemas.Violation> v = codec.checkBody("hello", hello);
        if (!v.isEmpty()) {
            log.error("Edge tunnel hello fails hello.schema.json at {}; check EDGE_EDGEID / EDGE_SITE_ID / EDGE_PSPID",
                    v.stream().map(ContractSchemas.Violation::pointer).toList());
            throw new TunnelException(TunnelErrorCode.INTERNAL, "hello invalid");
        }
        conn.helloId = TunnelFrameCodec.newId();
        String frame = codec.seal("hello", conn.helloId, null, null, hello, conn.keys.cpX25519Raw(),
                conn.keys.edgeEd25519(), clock.instant());
        send(conn, frame);
    }

    /** implemented ∩ EDGE_TUNNEL_OPS (§7.6.1): what hello advertises. */
    Set<String> localOps() {
        Set<String> filter = properties.opsFilter();
        Set<String> out = new LinkedHashSet<>();
        for (String op : TunnelOps.IMPLEMENTED) {
            if (filter.isEmpty() || filter.contains(op)) {
                out.add(op);
            }
        }
        return out;
    }

    /** min(local cap, config maxInflightPerSession), within 1..16. */
    private int localMaxInflight() {
        int cap = Math.max(1, Math.min(16, properties.getMaxInflight()));
        Optional<EdgeConfigDocument> doc = config.current();
        return doc.map(d -> Math.min(cap, d.tunnel().maxInflightPerSession())).orElse(cap);
    }

    // ── closing and reconnecting ─────────────────────────────────────────────────────────────────

    /** The Edge closes the session with {@code code} and schedules the reconnect it implies. */
    void closeLocally(Conn conn, int code, String reason) {
        if (!conn.markClosed()) {
            return;
        }
        WebSocket ws = conn.ws;
        if (ws != null) {
            try {
                ws.sendClose(code, reason).orTimeout(2, TimeUnit.SECONDS).whenComplete((w, e) -> ws.abort());
            } catch (RuntimeException e) {
                ws.abort();
            }
        }
        recordClose(code, reason, "closed by the Edge");
        if (!stopped) {
            reconnectAfter(code);
        }
    }

    /** The connection ended from the far side or the network ({@code code} as received). */
    private void onGone(Conn conn, int code, String reason) {
        if (!conn.markClosed()) {
            return;
        }
        WebSocket ws = conn.ws;
        if (ws != null) {
            ws.abort();
        }
        recordClose(code, reason, "closed by the Control Plane");
        if (!stopped) {
            reconnectAfter(code);
        }
    }

    /** Remembers and logs a close. */
    private void recordClose(int code, String reason, String by) {
        lastCloseCode = code;
        lastCloseReason = reason == null ? "" : reason.substring(0, Math.min(reason.length(), 123));
        lastCloseAt = clock.instant();
        log.info("Edge tunnel session {} with {} ({})", by, code, meaning(code));
    }

    /** The §7.2 meaning of a close code, for logs. */
    static String meaning(int code) {
        return switch (code) {
            case CLOSE_NORMAL -> "normal";
            case CLOSE_GOING_AWAY -> "Control Plane instance going away";
            case CLOSE_MALFORMED -> "malformed frame";
            case CLOSE_SEAL -> "repeated seal or replay failures";
            case CLOSE_FORBIDDEN -> "node not ACTIVE, identity/site/PSP mismatch, or tunnel disabled";
            case CLOSE_TIMEOUT -> "hello or idle timeout";
            case CLOSE_REPLACED -> "replaced by a newer session of this node";
            case CLOSE_TOO_LARGE -> "frame over 1 MiB";
            case CLOSE_VERSION -> "no common protocol version";
            case CLOSE_INTERNAL -> "internal error";
            default -> "connection lost";
        };
    }

    /** Applies the §7.2 reconnect rule for a close code. */
    private void reconnectAfter(int code) {
        Instant now = clock.instant();
        switch (code) {
            case CLOSE_FORBIDDEN -> {
                forbiddenSince = now;
                state = State.FORBIDDEN;
                log.warn("Edge tunnel refused (4403): not reconnecting until the next successful bundle poll");
                schedule(this::attemptConnect, properties.getGateRecheckInterval());
            }
            case CLOSE_REPLACED -> {
                supersededUntil = now.plus(properties.getSupersededHoldOff());
                state = State.SUPERSEDED;
                log.error("Edge tunnel replaced by another session of edge {} (4409): another process is using this "
                        + "node's identity. Not reconnecting for {}", identity.edgeId(), properties.getSupersededHoldOff());
                schedule(this::attemptConnect, properties.getSupersededHoldOff());
            }
            case CLOSE_VERSION -> {
                state = State.VERSION_UNSUPPORTED;
                log.error("Edge tunnel: the Control Plane supports none of protocol versions {} (4426). This node needs "
                        + "an upgrade; the tunnel stays down", PROTOCOL_VERSIONS);
            }
            case CLOSE_GOING_AWAY -> {
                state = State.BACKOFF;
                schedule(this::attemptConnect, Duration.ofMillis(ThreadLocalRandom.current().nextLong(0, 5_001)));
            }
            default -> {
                state = State.BACKOFF;
                schedule(this::attemptConnect, backoff(attempts++));
            }
        }
    }

    /** Full-jitter exponential backoff: uniform in [0, min(max, min × 2^attempt)]. */
    Duration backoff(int attempt) {
        long min = Math.max(1, properties.getBackoffMin().toMillis());
        long max = Math.max(min, properties.getBackoffMax().toMillis());
        long cap = attempt >= 30 ? max : Math.min(max, min << attempt);
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(0, cap + 1));
    }

    // ── receiving ────────────────────────────────────────────────────────────────────────────────

    /** Dispatches one complete text message of {@code conn}. */
    private void onFrame(Conn conn, String text) {
        if (conn.closed) {
            return;
        }
        Instant now = clock.instant();
        conn.lastReceived = now;
        ObjectNode outer;
        try {
            outer = codec.parseOuter(text);
        } catch (TunnelException e) {
            if (e.code() == TunnelErrorCode.UNSUPPORTED_FRAME && conn.sessionId != null) {
                sendSessionError(conn, new TunnelException(TunnelErrorCode.UNSUPPORTED_FRAME, "unknown frame type"));
            } else {
                closeLocally(conn, CLOSE_MALFORMED, "malformed frame");
            }
            return;
        }
        String type = outer.get("type").asText();
        switch (type) {
            case "ping" -> sendQuietly(conn, codec.pong(outer.get("id").asText(), now));
            case "pong" -> { /* liveness only: lastReceived was updated above */ }
            case "hello.ack" -> {
                if (conn.sessionId != null) {
                    closeLocally(conn, CLOSE_MALFORMED, "duplicate hello.ack");
                } else {
                    onHelloAck(conn, outer, now);
                }
            }
            case "request", "cancel", "credit", "notify", "error" -> {
                if (conn.sessionId == null) {
                    closeLocally(conn, CLOSE_MALFORMED, "frame before hello.ack");
                } else {
                    onSealed(conn, outer, type, now);
                }
            }
            default -> {
                // hello / response / chunk only travel Edge → cloud.
                if (conn.sessionId == null) {
                    closeLocally(conn, CLOSE_MALFORMED, "unexpected frame before hello.ack");
                } else {
                    sendSessionError(conn, new TunnelException(TunnelErrorCode.UNSUPPORTED_FRAME,
                            "frame type not accepted from the Control Plane"));
                }
            }
        }
    }

    /** Opens and applies hello.ack: the session exists from here on. */
    private void onHelloAck(Conn conn, ObjectNode outer, Instant now) {
        TunnelFrameCodec.Opened ack;
        try {
            ack = codec.open(outer, conn.keys.edgeX25519(), conn.keys.cpEd25519(), null, now);
        } catch (TunnelException e) {
            sealFailuresTotal.incrementAndGet();
            log.warn("Edge tunnel hello.ack refused ({})", e.code());
            closeLocally(conn, CLOSE_SEAL, "hello.ack refused");
            return;
        }
        if (!conn.helloId.equals(ack.corrId())) {
            closeLocally(conn, CLOSE_SEAL, "hello.ack answers another hello");
            return;
        }
        ObjectNode body = mapper.createObjectNode();
        ack.body().fields().forEachRemaining(e -> {
            if (ACK_MEMBERS.contains(e.getKey())) {
                body.set(e.getKey(), e.getValue());
            }
        });
        if (!codec.checkBody("hello.ack", body).isEmpty()) {
            closeLocally(conn, CLOSE_MALFORMED, "hello.ack body invalid");
            return;
        }
        if (!body.get("sessionId").asText().equals(ack.sessionId())) {
            closeLocally(conn, CLOSE_SEAL, "hello.ack session binding mismatch");
            return;
        }
        if (!PROTOCOL_VERSIONS.contains(body.get("protocolVersion").asInt())) {
            closeLocally(conn, CLOSE_MALFORMED, "hello.ack selected a version this node did not offer");
            return;
        }
        Set<String> effective = new LinkedHashSet<>();
        body.get("opsEffective").forEach(o -> effective.add(o.asText()));
        conn.opsEffective = effective;
        conn.maxInflight = Math.max(1, Math.min(body.get("maxInflight").asInt(), localMaxInflight()));
        conn.heartbeatMs = body.get("heartbeatIntervalMs").asLong();
        conn.idleMs = body.get("idleTimeoutMs").asLong();
        conn.cpInstance = body.get("cpInstance").asText();
        conn.connectedAt = now;
        conn.sessionId = ack.sessionId();
        ScheduledFuture<?> helloTimer = conn.helloTimer;
        if (helloTimer != null) {
            helloTimer.cancel(false);
        }
        attempts = 0;
        state = State.OPEN;
        lastError = "";
        log.info("Edge tunnel session {} open (Control Plane {}, {} op(s), maxInflight {})", conn.sessionId,
                conn.cpInstance, effective.size(), conn.maxInflight);
        conn.heartbeat = exec.scheduleAtFixedRate(() -> heartbeat(conn), conn.heartbeatMs, conn.heartbeatMs,
                TimeUnit.MILLISECONDS);
        long cfg = body.get("currentConfigVersion").asLong();
        if (config.currentVersion().orElse(0L) < cfg) {
            signals.configPollSoon().run();
        }
        if (signals.bundleVersion().getAsLong() < body.get("currentBundleVersion").asLong()) {
            signals.bundlePollSoon().run();
        }
    }

    /** Heartbeat tick: idle check (4408), else ping (§7.2). */
    private void heartbeat(Conn conn) {
        if (conn != current || conn.closed) {
            ScheduledFuture<?> h = conn.heartbeat;
            if (h != null) {
                h.cancel(false);
            }
            return;
        }
        Instant now = clock.instant();
        if (Duration.between(conn.lastReceived, now).toMillis() > conn.idleMs) {
            log.warn("Edge tunnel session {}: nothing received for {} ms; closing", conn.sessionId, conn.idleMs);
            closeLocally(conn, CLOSE_TIMEOUT, "idle timeout");
            return;
        }
        Thread.ofVirtual().start(() -> sendQuietly(conn, codec.ping(clock.instant())));
    }

    /** §7.4 steps 2–5 for a cloud frame on an open session, then dispatch. */
    private void onSealed(Conn conn, ObjectNode outer, String type, Instant now) {
        TunnelFrameCodec.Opened f;
        try {
            f = codec.open(outer, conn.keys.edgeX25519(), conn.keys.cpEd25519(), conn.sessionId, now);
        } catch (TunnelException e) {
            sealFailure(conn, outer, type, e);
            return;
        }
        switch (type) {
            case "request" -> Thread.ofVirtual().name("edge-tunnel-request").start(() -> onRequest(conn, f, now));
            case "cancel" -> onCancel(conn, f);
            case "credit" -> onCredit(conn, f);
            case "notify" -> Thread.ofVirtual().name("edge-tunnel-notify").start(() -> onNotify(conn, f));
            case "error" -> onCloudError(f);
            default -> throw new IllegalStateException("unreachable frame type " + type);
        }
    }

    /**
     * A frame that failed open/replay/binding: answer a request with the error (§3.3), count it,
     * and close 4401 at the threshold.
     */
    private void sealFailure(Conn conn, ObjectNode outer, String type, TunnelException e) {
        sealFailuresTotal.incrementAndGet();
        log.warn("Edge tunnel session {}: {} frame refused ({})", conn.sessionId, type, e.code());
        if ("request".equals(type)) {
            requestsRefused.incrementAndGet();
            sendError(conn, outer.get("id").asText(), e);
        }
        long nowMs = clock.millis();
        boolean tooMany;
        synchronized (conn.sealFailures) {
            conn.sealFailures.addLast(nowMs);
            while (!conn.sealFailures.isEmpty()
                    && conn.sealFailures.peekFirst() < nowMs - properties.getSealFailureWindow().toMillis()) {
                conn.sealFailures.pollFirst();
            }
            tooMany = conn.sealFailures.size() >= properties.getSealFailureLimit();
        }
        if (tooMany) {
            closeLocally(conn, CLOSE_SEAL, "repeated seal failures");
        }
    }

    /** A session-level or request-level error from the Control Plane: log its code only. */
    private void onCloudError(TunnelFrameCodec.Opened f) {
        if (codec.checkBody("error", f.body()).isEmpty()) {
            log.warn("Edge tunnel: Control Plane reported {}{}", f.body().get("code").asText(),
                    f.corrId() == null ? "" : " for frame " + f.corrId());
        }
    }

    // ── requests ─────────────────────────────────────────────────────────────────────────────────

    /**
     * One request: audit row first (refused with LEDGER_UNAVAILABLE when the ledger cannot take
     * it), then validation and gates, then the in-flight cap, then a virtual thread runs it.
     */
    private void onRequest(Conn conn, TunnelFrameCodec.Opened f, Instant now) {
        AccessLogWriter.Entry entry = AccessLogWriter.Entry.from(f.body(), f.id());
        long row;
        try {
            row = accessLog.begin(entry, conn.sessionId, now);
        } catch (SQLException | RuntimeException e) {
            requestsRefused.incrementAndGet();
            sendError(conn, f.id(), new TunnelException(TunnelErrorCode.LEDGER_UNAVAILABLE,
                    "the request cannot be audited: Edge Ledger unavailable"));
            return;
        }
        TunnelOps.Prepared prepared;
        try {
            prepared = ops.prepare(f, gate(conn));
        } catch (TunnelException e) {
            refuse(conn, f.id(), row, e);
            return;
        }
        Inflight inf = new Inflight(prepared, row, "export.stream".equals(prepared.op())
                ? new Credit(TunnelOps.initialCredit(prepared), clock) : null);
        boolean busy;
        synchronized (conn) {
            busy = conn.closed || conn.inflight.size() >= conn.maxInflight;
            if (!busy) {
                conn.inflight.put(f.id(), inf);
            }
        }
        if (busy) {
            refuse(conn, f.id(), row, new TunnelException(TunnelErrorCode.EDGE_BUSY, "in-flight limit reached", null,
                    properties.getBusyRetryAfterMs()));
            return;
        }
        if (conn.earlyCancels.remove(f.id()) != null) {
            // The cancel overtook this request while it was being audited and validated.
            cancelInflight(conn, f.id(), inf, TunnelErrorCode.CANCELLED);
        }
        run(conn, f.id(), inf);
    }

    /** The per-request gate snapshot. */
    private TunnelOps.Gate gate(Conn conn) {
        Optional<EdgeConfigDocument> doc = config.current();
        Set<String> effective = new LinkedHashSet<>(localOps());
        effective.retainAll(conn.opsEffective);
        doc.ifPresent(d -> effective.retainAll(d.tunnel().opsAllowed()));
        return new TunnelOps.Gate(identity.pspId(), doc, effective);
    }

    /** Refuses a request: error frame plus a completed audit row with the code. */
    private void refuse(Conn conn, String frameId, long row, TunnelException e) {
        requestsRefused.incrementAndGet();
        sendError(conn, frameId, e);
        completeAudit(row, e.code().name(), 0, List.of());
    }

    /** Runs an accepted request to its terminal frame. */
    private void run(Conn conn, String frameId, Inflight inf) {
        TunnelOps.Prepared p = inf.prepared;
        try {
            if (inf.credit != null) {
                ExportStream stream = new ExportStream(conn, frameId, inf);
                ops.export(p, stream);
                requestsServed.incrementAndGet();
                completeAudit(inf.auditRow, "OK", stream.items, List.of());
            } else {
                TunnelOps.Result r = ops.execute(p);
                sendResult(conn, frameId, inf, p.op(), r);
                requestsServed.incrementAndGet();
                completeAudit(inf.auditRow, "OK", r.items().size(), r.returnedIds());
            }
        } catch (TunnelException e) {
            TunnelException terminal = p.control().cancelled()
                    ? new TunnelException(p.control().cancelCode(), "request was cancelled") : e;
            sendTerminal(conn, frameId, inf, terminal);
            completeAudit(inf.auditRow, terminal.code().name(), 0, List.of());
        } catch (RuntimeException e) {
            log.error("Edge tunnel {} request failed ({})", p.op(), e.getClass().getSimpleName());
            sendTerminal(conn, frameId, inf, new TunnelException(TunnelErrorCode.INTERNAL, "unexpected Edge fault"));
            completeAudit(inf.auditRow, TunnelErrorCode.INTERNAL.name(), 0, List.of());
        } finally {
            conn.inflight.remove(frameId);
        }
    }

    /** Sends a non-streaming result as response frames (split to fit 1 MiB), the last one terminal. */
    private void sendResult(Conn conn, String frameId, Inflight inf, String op, TunnelOps.Result r)
            throws TunnelException {
        List<List<ObjectNode>> groups = frames.groups(r.items(), 500);
        for (int i = 0; i < groups.size(); i++) {
            boolean last = i == groups.size() - 1;
            ObjectNode body = frames.body(op, i, last, groups.get(i), r.nextCursor(), r.missing(),
                    r.incompleteReason(), null);
            sendBody(conn, frameId, inf, "response", body, last);
        }
    }

    /**
     * Seals and sends one answer frame for a request under its lock: nothing is sent after the
     * terminal frame (including a cancel's CANCELLED).
     *
     * @throws TunnelException CANCELLED when the request was cancelled meanwhile, FRAME_TOO_LARGE
     *                         when a single item cannot fit one frame (groups are sized so that
     *                         several never overflow), INTERNAL when the body is invalid
     */
    private void sendBody(Conn conn, String frameId, Inflight inf, String type, ObjectNode body, boolean terminal)
            throws TunnelException {
        List<ContractSchemas.Violation> v = codec.checkBody(type, body);
        if (!v.isEmpty()) {
            log.error("Edge tunnel {} body fails response.schema.json at {} (defect; not sent)", type,
                    v.stream().map(ContractSchemas.Violation::pointer).distinct().limit(5).toList());
            throw new TunnelException(TunnelErrorCode.INTERNAL, "response body is invalid");
        }
        String frame = codec.seal(type, TunnelFrameCodec.newId(), frameId, conn.sessionId, body,
                conn.keys.cpX25519Raw(), conn.keys.edgeEd25519(), clock.instant());
        synchronized (inf) {
            if (inf.terminalSent) {
                throw new TunnelException(inf.prepared.control().cancelCode(), "request was cancelled");
            }
            if (terminal) {
                inf.terminalSent = true;
            }
            if (!send(conn, frame)) {
                throw new TunnelException(TunnelErrorCode.CANCELLED, "session closed");
            }
        }
    }

    /** Sends the terminal error of a request unless a terminal frame was already sent. */
    private void sendTerminal(Conn conn, String frameId, Inflight inf, TunnelException e) {
        synchronized (inf) {
            if (inf.terminalSent) {
                return;
            }
            inf.terminalSent = true;
            sendError(conn, frameId, e);
        }
    }

    /** §7.9: stop the request, send CANCELLED once, nothing after it. Cancel is idempotent. */
    private void onCancel(Conn conn, TunnelFrameCodec.Opened f) {
        if (!codec.checkBody("cancel", f.body()).isEmpty() || f.corrId() == null) {
            return;
        }
        Inflight inf = conn.inflight.get(f.corrId());
        if (inf != null) {
            cancelInflight(conn, f.corrId(), inf, TunnelErrorCode.CANCELLED);
            return;
        }
        // Possibly a request still being audited on its own thread: remember the cancel briefly.
        long nowMs = clock.millis();
        conn.earlyCancels.entrySet().removeIf(e -> e.getValue() < nowMs - 60_000);
        conn.earlyCancels.put(f.corrId(), nowMs);
    }

    /** Cancels one in-flight request with {@code code} and sends that terminal error. */
    private void cancelInflight(Conn conn, String frameId, Inflight inf, TunnelErrorCode code) {
        inf.prepared.control().cancel(code);
        if (inf.credit != null) {
            inf.credit.wake();
        }
        sendTerminal(conn, frameId, inf, new TunnelException(code, code == TunnelErrorCode.CANCELLED
                ? "request was cancelled" : "support access was withdrawn by the PSP"));
    }

    /** §7.8: add credit to an export; overflow beyond 1 024 is CREDIT_VIOLATION. Unknown ids are ignored. */
    private void onCredit(Conn conn, TunnelFrameCodec.Opened f) {
        if (!codec.checkBody("credit", f.body()).isEmpty() || f.corrId() == null) {
            return;
        }
        Inflight inf = conn.inflight.get(f.corrId());
        if (inf == null || inf.credit == null) {
            return;
        }
        if (!inf.credit.grant(f.body().get("grant").asInt())) {
            cancelInflight(conn, f.corrId(), inf, TunnelErrorCode.CREDIT_VIOLATION);
        }
    }

    /** The export sink: chunk frames within credit, ids recorded per chunk in the access log. */
    private final class ExportStream implements TunnelOps.ExportSink {
        private final Conn conn;
        private final String frameId;
        private final Inflight inf;
        private int seq;
        private int chunkSeq;
        private int items;

        ExportStream(Conn conn, String frameId, Inflight inf) {
            this.conn = conn;
            this.frameId = frameId;
            this.inf = inf;
        }

        /** Splits the chunk to fit 1 MiB; each frame waits for credit (§7.8), the last ends the stream. */
        @Override
        public void chunk(List<ObjectNode> chunkItems, List<UUID> ids, boolean last, long totalRows)
                throws TunnelException {
            List<List<ObjectNode>> groups = frames.groups(chunkItems, 500);
            int idIndex = 0;
            for (int i = 0; i < groups.size(); i++) {
                boolean finalFrame = last && i == groups.size() - 1;
                List<ObjectNode> g = groups.get(i);
                inf.credit.acquire(inf.prepared.control(), properties.getCreditStallTimeout());
                ObjectNode body = frames.body("export.stream", seq++, finalFrame, g, null, null, null,
                        finalFrame ? totalRows : null);
                sendBody(conn, frameId, inf, "chunk", body, finalFrame);
                List<UUID> groupIds = ids.subList(idIndex, Math.min(ids.size(), idIndex + g.size()));
                idIndex += g.size();
                items += g.size();
                try {
                    accessLog.exportChunk(inf.auditRow, chunkSeq++, List.copyOf(groupIds));
                } catch (SQLException e) {
                    throw new TunnelException(TunnelErrorCode.LEDGER_UNAVAILABLE,
                            "export ids cannot be audited: Edge Ledger unavailable");
                }
            }
        }
    }

    // ── notifies ─────────────────────────────────────────────────────────────────────────────────

    /** §7.10: config/bundle triggers and hold place/release with hold.ack. Unknown types are logged once. */
    private void onNotify(Conn conn, TunnelFrameCodec.Opened f) {
        String type = f.body().path("type").asText("");
        if (!NOTIFY_TYPES.contains(type)) {
            logOnce("notify:" + type, "Edge tunnel: ignoring notify of unknown type (not acknowledged)");
            return;
        }
        List<ContractSchemas.Violation> v = codec.checkBody("notify", f.body());
        boolean onlyReason = !v.isEmpty() && v.stream().allMatch(x -> x.pointer().equals("/reasonCode"));
        try {
            switch (type) {
                case "config.updated" -> {
                    if (v.isEmpty()) {
                        signals.configPollSoon().run();
                    }
                }
                case "bundle.updated" -> {
                    if (v.isEmpty()) {
                        signals.bundlePollSoon().run();
                    }
                }
                case "hold.place" -> {
                    if (!v.isEmpty() && !onlyReason) {
                        sendError(conn, f.id(), invalid(v));
                        return;
                    }
                    // An unknown reasonCode still holds: the most conservative reading (§12.2).
                    holdAck(conn, f.id(), holds.place(placement(f.body())));
                }
                case "hold.release" -> {
                    if (!v.isEmpty()) {
                        // An unknown release reason keeps the hold (§12.2): refuse rather than release.
                        sendError(conn, f.id(), invalid(v));
                        return;
                    }
                    JsonNode b = f.body();
                    holdAck(conn, f.id(), holds.release(UUID.fromString(b.get("holdId").asText()),
                            Instant.parse(b.get("releasedAt").asText()), b.get("releasedBy").asText(),
                            b.get("reasonCode").asText()));
                }
                default -> throw new IllegalStateException("unreachable notify type " + type);
            }
        } catch (SQLException e) {
            sendError(conn, f.id(), new TunnelException(TunnelErrorCode.LEDGER_UNAVAILABLE,
                    "the hold cannot be recorded: Edge Ledger unavailable"));
        } catch (TunnelException e) {
            sendError(conn, f.id(), e);
        }
    }

    /** INVALID_PARAMS for a notify body, pointing at its first violation. */
    private static TunnelException invalid(List<ContractSchemas.Violation> v) {
        return new TunnelException(TunnelErrorCode.INVALID_PARAMS, "notify body fails its schema", v.get(0).pointer(),
                null);
    }

    /** Builds a hold placement from a (schema-checked) hold.place body. */
    private static LegalHoldService.Placement placement(JsonNode b) {
        JsonNode scope = b.get("scope");
        List<UUID> ids = null;
        String merchant = null;
        Instant from = null;
        Instant to = null;
        if (scope.has("edgeAssessmentIds")) {
            ids = new ArrayList<>();
            for (JsonNode id : scope.get("edgeAssessmentIds")) {
                ids.add(UUID.fromString(id.asText()));
            }
        } else {
            merchant = scope.get("merchantId").asText();
            from = Instant.parse(scope.get("from").asText());
            to = Instant.parse(scope.get("to").asText());
        }
        return new LegalHoldService.Placement(UUID.fromString(b.get("holdId").asText()),
                Instant.parse(b.get("placedAt").asText()), b.get("placedBy").asText(), b.get("reasonCode").asText(),
                b.get("reference").asText(), ids, merchant, from, to);
    }

    /** Sends {@code response op=hold.ack} with corrId = the notify id (§7.10). */
    private void holdAck(Conn conn, String notifyId, LegalHoldService.Ack ack) throws TunnelException {
        ObjectNode item = mapper.createObjectNode();
        item.put("holdId", ack.holdId().toString());
        item.put("state", ack.state());
        item.put("matchedRows", ack.matchedRows());
        item.put("activeHoldDigest", ack.activeHoldDigest());
        ObjectNode body = frames.body("hold.ack", 0, true, List.of(item), null, null, null, null);
        if (!codec.checkBody("response", body).isEmpty()) {
            throw new TunnelException(TunnelErrorCode.INTERNAL, "hold.ack fails its schema");
        }
        send(conn, codec.seal("response", TunnelFrameCodec.newId(), notifyId, conn.sessionId, body,
                conn.keys.cpX25519Raw(), conn.keys.edgeEd25519(), clock.instant()));
    }

    // ── config changes ───────────────────────────────────────────────────────────────────────────

    /**
     * A newly accepted config: withdrawn support access cancels in-flight platform requests (§9);
     * {@code tunnel.enabled=false} closes the session; re-enabling reconnects.
     */
    private void onConfigAccepted(EdgeConfigDocument doc) {
        Conn conn = current;
        if (conn != null && !conn.closed) {
            if (!doc.supportAccess()) {
                for (Map.Entry<String, Inflight> e : conn.inflight.entrySet()) {
                    if (e.getValue().prepared.platformActor()) {
                        cancelInflight(conn, e.getKey(), e.getValue(), TunnelErrorCode.SUPPORT_ACCESS_DISABLED);
                    }
                }
            }
            if (!doc.tunnel().enabled()) {
                closeLocally(conn, CLOSE_NORMAL, "tunnel disabled by config");
                return;
            }
        }
        if (doc.tunnel().enabled() && state == State.DISABLED && !stopped) {
            schedule(this::attemptConnect, Duration.ZERO);
        }
    }

    // ── sending ──────────────────────────────────────────────────────────────────────────────────

    /** Sends a sealed error answering {@code corrId}. */
    private void sendError(Conn conn, String corrId, TunnelException e) {
        try {
            send(conn, codec.seal("error", TunnelFrameCodec.newId(), corrId, conn.sessionId, e.toBody(mapper),
                    conn.keys.cpX25519Raw(), conn.keys.edgeEd25519(), clock.instant()));
        } catch (TunnelException | RuntimeException x) {
            log.warn("Edge tunnel: could not send error {} ({})", e.code(), x.getClass().getSimpleName());
        }
    }

    /** Sends a session-level error (no corrId). */
    private void sendSessionError(Conn conn, TunnelException e) {
        sendError(conn, null, e);
    }

    /** Sends without reporting failure (ping/pong). */
    private void sendQuietly(Conn conn, String frame) {
        send(conn, frame);
    }

    /**
     * Sends one text frame; WebSocket sends are serialised per connection. A failed send aborts
     * the connection (the reconnect rule then applies).
     *
     * @return false when the connection is closed or the send failed
     */
    private boolean send(Conn conn, String frame) {
        WebSocket ws = conn.ws;
        if (ws == null || conn.closed) {
            return false;
        }
        conn.sendLock.lock();
        try {
            ws.sendText(frame, true).get(30, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("Edge tunnel send failed ({}); dropping the connection", e.getClass().getSimpleName());
            later(() -> onGone(conn, CLOSE_ABNORMAL, "send failed"));
            return false;
        } finally {
            conn.sendLock.unlock();
        }
    }

    /** Completes an audit row; a failure is logged (the request already ran). */
    private void completeAudit(long row, String outcome, int items, List<UUID> ids) {
        try {
            accessLog.complete(row, outcome, items, ids, clock.instant());
        } catch (SQLException | RuntimeException e) {
            log.error("Edge tunnel: could not complete edge_access_log row {} ({})", row, e.getClass().getSimpleName());
        }
    }

    // ── per-connection state ─────────────────────────────────────────────────────────────────────

    /** One request in flight. */
    private static final class Inflight {
        final TunnelOps.Prepared prepared;
        final long auditRow;
        final Credit credit;
        boolean terminalSent;

        Inflight(TunnelOps.Prepared prepared, long auditRow, Credit credit) {
            this.prepared = prepared;
            this.auditRow = auditRow;
            this.credit = credit;
        }
    }

    /** Export credit (§7.8). */
    static final class Credit {
        static final int MAX_OUTSTANDING = 1_024;
        private final Clock clock;
        private int outstanding;
        private Instant lastGrant;

        Credit(int initial, Clock clock) {
            this.clock = clock;
            this.outstanding = Math.max(1, Math.min(16, initial));
            this.lastGrant = clock.instant();
        }

        /** Adds credit; false (no change) when it would exceed 1 024 outstanding. */
        synchronized boolean grant(int n) {
            if ((long) outstanding + n > MAX_OUTSTANDING) {
                return false;
            }
            outstanding += n;
            lastGrant = clock.instant();
            notifyAll();
            return true;
        }

        /** Wakes a waiter (after a cancel). */
        synchronized void wake() {
            notifyAll();
        }

        /**
         * Takes one credit, waiting while none is outstanding.
         *
         * @throws TunnelException STREAM_STALLED after {@code stall} without credit, the cancel code
         *                         when cancelled, DEADLINE_EXCEEDED when the budget runs out
         */
        synchronized void acquire(RequestControl control, Duration stall) throws TunnelException {
            while (outstanding == 0) {
                control.checkpoint();
                Instant now = clock.instant();
                long stallLeft = Duration.between(now, lastGrant.plus(stall)).toMillis();
                if (stallLeft <= 0) {
                    throw new TunnelException(TunnelErrorCode.STREAM_STALLED, "no credit for "
                            + stall.toSeconds() + " s");
                }
                long wait = Math.max(1, Math.min(stallLeft, control.remaining().toMillis()));
                try {
                    wait(Math.min(wait, 1_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new TunnelException(TunnelErrorCode.CANCELLED, "interrupted");
                }
            }
            outstanding--;
        }
    }

    /** One WebSocket connection attempt and, after hello.ack, its session. */
    final class Conn implements WebSocket.Listener {
        final long serial;
        final Keys keys;
        final ReentrantLock sendLock = new ReentrantLock();
        final Map<String, Inflight> inflight = new ConcurrentHashMap<>();
        final Map<String, Long> earlyCancels = new ConcurrentHashMap<>();
        final Deque<Long> sealFailures = new ArrayDeque<>();
        private final StringBuilder partial = new StringBuilder();
        private final AtomicBoolean closedFlag = new AtomicBoolean();
        volatile boolean closed;
        volatile WebSocket ws;
        volatile String helloId;
        volatile String sessionId;
        volatile String cpInstance;
        volatile Instant connectedAt;
        volatile Instant lastReceived;
        volatile Set<String> opsEffective = Set.of();
        volatile int maxInflight = 1;
        volatile long heartbeatMs;
        volatile long idleMs;
        volatile ScheduledFuture<?> helloTimer;
        volatile ScheduledFuture<?> heartbeat;

        Conn(long serial, Keys keys) {
            this.serial = serial;
            this.keys = keys;
            this.lastReceived = clock.instant();
            this.heartbeatMs = properties.getHeartbeatInterval().toMillis();
            this.idleMs = properties.getIdleTimeout().toMillis();
        }

        /** Marks the connection closed exactly once; stops timers and in-flight work. */
        boolean markClosed() {
            if (!closedFlag.compareAndSet(false, true)) {
                return false;
            }
            closed = true;
            ScheduledFuture<?> h = heartbeat;
            if (h != null) {
                h.cancel(false);
            }
            ScheduledFuture<?> t = helloTimer;
            if (t != null) {
                t.cancel(false);
            }
            for (Inflight inf : inflight.values()) {
                inf.prepared.control().cancel();
                if (inf.credit != null) {
                    inf.credit.wake();
                }
            }
            return true;
        }

        /** Requests the first message. */
        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        /** Assembles a text message under the 1 MiB limit (4413 above it), then dispatches it. */
        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (closed) {
                return null;
            }
            partial.append(data);
            if (partial.length() > TunnelFrameCodec.MAX_FRAME_BYTES) {
                partial.setLength(0);
                later(() -> closeLocally(this, CLOSE_TOO_LARGE, "frame too large"));
                return null;
            }
            if (last) {
                String text = partial.toString();
                partial.setLength(0);
                if (text.getBytes(StandardCharsets.UTF_8).length > TunnelFrameCodec.MAX_FRAME_BYTES) {
                    later(() -> closeLocally(this, CLOSE_TOO_LARGE, "frame too large"));
                    return null;
                }
                try {
                    onFrame(this, text);
                } catch (RuntimeException e) {
                    log.error("Edge tunnel frame handling failed ({})", e.getClass().getSimpleName());
                    later(() -> closeLocally(this, CLOSE_INTERNAL, "internal error"));
                    return null;
                }
            }
            webSocket.request(1);
            return null;
        }

        /** §7.1: binary frames close with 4400. */
        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            later(() -> closeLocally(this, CLOSE_MALFORMED, "binary frame"));
            return null;
        }

        /** The Control Plane closed the session. */
        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            later(() -> onGone(this, statusCode, reason));
            return CompletableFuture.completedFuture(null);
        }

        /** The connection failed. */
        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            later(() -> onGone(this, CLOSE_ABNORMAL, "connection error"));
        }
    }
}
