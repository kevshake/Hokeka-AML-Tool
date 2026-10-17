package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.edge.crypto.HokekaSecureEnvelope;
import com.posgateway.aml.service.edge.EdgeReplayGuard;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.XECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fake Edge Node for the integration tests: opens the tunnel with the JDK
 * {@code java.net.http.WebSocket} client (as the real Edge does, §7.1), authenticates through the
 * trusted-proxy {@code X-SSL-Client-*} headers, sends a sealed {@code hello}, pings, opens every
 * cloud frame with the shared {@link TunnelFrameCodec} (so binding, replay and signatures are
 * really checked from the Edge's side) and answers requests through a pluggable handler.
 */
final class FakeEdge implements WebSocket.Listener {

    /** Reacts to one verified request frame. */
    @FunctionalInterface
    interface RequestHandler {
        void onRequest(FakeEdge edge, TunnelFrameCodec.OpenedFrame request) throws Exception;
    }

    /** Reacts to one verified notify frame (R9: hold.place / hold.release acknowledgement). */
    @FunctionalInterface
    interface NotifyHandler {
        void onNotify(FakeEdge edge, TunnelFrameCodec.OpenedFrame notify) throws Exception;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    volatile NotifyHandler notifyHandler = (e, n) -> { };

    final String edgeId;
    final String siteId;
    final long pspId;
    final KeyPair x25519;
    final KeyPair ed25519;
    private final TunnelFrameCodec codec;
    private final String cpIngestPublicB64;
    private final byte[] cpIngestRaw;
    private final PublicKey cpSigningPub;

    volatile WebSocket ws;
    volatile String sessionId;
    volatile ObjectNode helloAck;
    volatile RequestHandler handler = (e, r) -> { };
    final CompletableFuture<ObjectNode> ackFuture = new CompletableFuture<>();
    final CompletableFuture<Integer> closeCode = new CompletableFuture<>();
    final List<TunnelFrameCodec.OpenedFrame> received = new CopyOnWriteArrayList<>();
    final List<String> openFailures = new CopyOnWriteArrayList<>();
    /** Outstanding export credit per request id (initial credit + grants − chunks sent). */
    final Map<String, AtomicInteger> credit = new ConcurrentHashMap<>();
    final AtomicInteger creditViolations = new AtomicInteger();
    private final StringBuilder partial = new StringBuilder();
    private final ScheduledExecutorService pinger = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "fake-edge-ping");
        t.setDaemon(true);
        return t;
    });

    /** A node of {@code siteId} for {@code pspId}, pinned to fresh keys. */
    FakeEdge(String edgeId, String siteId, long pspId, String cpIngestPublicB64, PublicKey cpSigningPub) {
        this(edgeId, siteId, pspId, cpIngestPublicB64, cpSigningPub, gen("X25519"), gen("Ed25519"));
    }

    /** A node with given keys (used for a second process of the same node). */
    private FakeEdge(String edgeId, String siteId, long pspId, String cpIngestPublicB64, PublicKey cpSigningPub,
                     KeyPair x25519, KeyPair ed25519) {
        this.x25519 = x25519;
        this.ed25519 = ed25519;
        this.cpIngestPublicB64 = cpIngestPublicB64;
        this.edgeId = edgeId;
        this.siteId = siteId;
        this.pspId = pspId;
        this.cpIngestRaw = Base64.getDecoder().decode(cpIngestPublicB64);
        this.cpSigningPub = cpSigningPub;
        this.codec = new TunnelFrameCodec(new HokekaSecureEnvelope(), new EdgeReplayGuard(), new TunnelSchemas(),
                MAPPER, 1_048_576);
    }

    /** Same node identity and keys, fresh connection state (a restarted or duplicate process). */
    FakeEdge twin() {
        return new FakeEdge(edgeId, siteId, pspId, cpIngestPublicB64, cpSigningPub, x25519, ed25519);
    }

    /** Generates a key pair of the given algorithm. */
    static KeyPair gen(String alg) {
        try {
            return KeyPairGenerator.getInstance(alg).generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Base64 raw X25519 public key, as pinned in {@code edge_nodes}. */
    String x25519PublicB64() {
        return Base64.getEncoder().encodeToString(HokekaSecureEnvelope.rawFromX25519Public((XECPublicKey) x25519.getPublic()));
    }

    /** Base64 raw Ed25519 public key, as pinned in {@code edge_nodes}. */
    String ed25519PublicB64() {
        return Base64.getEncoder().encodeToString(
                com.posgateway.aml.config.edge.EdgeControlPlaneKeys.rawEd25519Public((EdECPublicKey) ed25519.getPublic()));
    }

    /** Opens the WebSocket with the given headers (identity via trusted-proxy headers). */
    CompletableFuture<WebSocket> connect(URI uri, boolean offerSubprotocol, Map<String, String> headers) {
        WebSocket.Builder b = HttpClient.newHttpClient().newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5));
        if (offerSubprotocol) {
            b.subprotocols(TunnelFrameCodec.SUBPROTOCOL);
        }
        headers.forEach(b::header);
        return b.buildAsync(uri, this).thenApply(w -> {
            this.ws = w;
            return w;
        });
    }

    /** Standard identity headers of this node. */
    Map<String, String> identityHeaders() {
        return Map.of("X-SSL-Client-Verify", "SUCCESS", "X-SSL-Client-S-DN", "CN=" + edgeId + ",O=PSP",
                "X-Hokeka-Edge-Id", edgeId, "X-Edge-Agent-Version", "2.4.0");
    }

    /** Connects, sends hello, waits for hello.ack and starts pinging every {@code pingMs}. */
    FakeEdge start(URI uri, long pingMs) throws Exception {
        connect(uri, true, identityHeaders()).get(10, TimeUnit.SECONDS);
        sendHello(List.of(1));
        ackFuture.get(10, TimeUnit.SECONDS);
        pinger.scheduleAtFixedRate(() -> {
            WebSocket w = ws;
            if (w != null && !w.isOutputClosed()) {
                send(codec.ping(Instant.now()));
            }
        }, pingMs, pingMs, TimeUnit.MILLISECONDS);
        return this;
    }

    /** Sends the sealed hello (§7.2) with the given protocol versions. */
    void sendHello(List<Integer> versions) throws Exception {
        ObjectNode hello = (ObjectNode) ContractFixtures.fixture("hello--node.json");
        hello.put("edgeId", edgeId);
        hello.put("siteId", siteId);
        hello.put("pspId", pspId);
        hello.put("bootId", TunnelFrameCodec.newId());
        ArrayNode v = hello.putArray("protocolVersions");
        versions.forEach(v::add);
        ArrayNode ops = hello.putArray("opsEnabled");
        for (TunnelOp op : TunnelOp.values()) {
            ops.add(op.wire());
        }
        send(codec.seal(new TunnelFrameCodec.FrameSpec("hello", TunnelFrameCodec.newId(), null, null, edgeId, hello),
                cpIngestRaw, ed25519.getPrivate(), Instant.now()));
    }

    /** Sends an Edge → cloud sealed frame of {@code type} bound to the live session. */
    void sendSealed(String type, String corrId, JsonNode body) throws Exception {
        send(codec.seal(new TunnelFrameCodec.FrameSpec(type, TunnelFrameCodec.newId(), corrId, sessionId, edgeId, body),
                cpIngestRaw, ed25519.getPrivate(), Instant.now()));
    }

    /** Answers a request with one final response frame. */
    void respond(TunnelFrameCodec.OpenedFrame request, List<? extends JsonNode> items, String nextCursor,
                 List<String> missing) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("op", request.body().get("op").asText());
        body.put("status", "OK");
        body.put("seq", 0);
        body.put("last", true);
        ArrayNode arr = body.putArray("items");
        items.forEach(arr::add);
        if (nextCursor != null) {
            body.put("nextCursor", nextCursor);
        }
        if (missing != null && !missing.isEmpty()) {
            ArrayNode m = body.putArray("missing");
            missing.forEach(m::add);
        }
        sendSealed("response", request.id(), body);
    }

    /** Sends one export chunk; refuses (and counts a violation) when no credit is outstanding. */
    void chunk(TunnelFrameCodec.OpenedFrame request, int seq, List<? extends JsonNode> items, boolean last,
               Long totalRows) throws Exception {
        AtomicInteger c = credit.get(request.id());
        if (c.get() <= 0) {
            creditViolations.incrementAndGet();
            return;
        }
        c.decrementAndGet();
        ObjectNode body = MAPPER.createObjectNode();
        body.put("op", "export.stream");
        body.put("status", "OK");
        body.put("seq", seq);
        body.put("last", last);
        ArrayNode arr = body.putArray("items");
        items.forEach(arr::add);
        if (last && totalRows != null) {
            body.put("totalRows", totalRows);
        }
        sendSealed("chunk", request.id(), body);
    }

    /** Answers a request with a terminal error frame. */
    void error(TunnelFrameCodec.OpenedFrame request, TunnelError err) throws Exception {
        sendSealed("error", request.id(), err.toJson());
    }

    /** Sends raw text (whole message). */
    synchronized void send(String text) {
        ws.sendText(text, true).join();
    }

    /** Closes the socket from the Edge side. */
    void close() {
        pinger.shutdownNow();
        WebSocket w = ws;
        if (w != null && !w.isOutputClosed()) {
            w.sendClose(WebSocket.NORMAL_CLOSURE, "bye").exceptionally(t -> null);
        }
    }

    /** Stops pinging without closing (to let the idle timeout fire). */
    void stopPinging() {
        pinger.shutdownNow();
    }

    /** Requests of a given op received so far. */
    List<TunnelFrameCodec.OpenedFrame> requests(String op) {
        return received.stream().filter(f -> f.type().equals("request") && op.equals(f.body().path("op").asText()))
                .toList();
    }

    /** Frames of a given type received so far. */
    List<TunnelFrameCodec.OpenedFrame> frames(String type) {
        return received.stream().filter(f -> f.type().equals(type)).toList();
    }

    // ── WebSocket.Listener ───────────────────────────────────────────────────────────────────────

    /** Starts demand on the opened socket. */
    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    /** Reassembles fragments and handles each complete frame. */
    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        partial.append(data);
        if (last) {
            String text = partial.toString();
            partial.setLength(0);
            try {
                handleFrame(text);
            } catch (Exception e) {
                openFailures.add(e.getClass().getSimpleName() + ":" + e.getMessage());
            }
        }
        webSocket.request(1);
        return null;
    }

    /** Binary frames are not part of the protocol; ignored by the fake Edge. */
    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        webSocket.request(1);
        return null;
    }

    /** Records the close code the Control Plane sent. */
    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        closeCode.complete(statusCode);
        pinger.shutdownNow();
        return null;
    }

    /** Records a transport failure. */
    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        closeCode.complete(-1);
        pinger.shutdownNow();
    }

    /** Opens one cloud frame from the Edge's side and dispatches it. */
    private void handleFrame(String text) throws Exception {
        ObjectNode outer = codec.parseOuter(text);
        String type = outer.get("type").asText();
        if ("pong".equals(type) || "ping".equals(type)) {
            return;
        }
        TunnelFrameCodec.OpenedFrame f;
        try {
            f = codec.open(outer, x25519.getPrivate(), cpSigningPub, edgeId,
                    "hello.ack".equals(type) ? null : sessionId, Instant.now());
        } catch (TunnelFrameException e) {
            openFailures.add(type + ":" + e.code());
            return;
        }
        List<String> bodyErrors = codec.checkBody(type, f.body());
        if (!bodyErrors.isEmpty()) {
            openFailures.add(type + ":body:" + bodyErrors);
            return;
        }
        received.add(f);
        switch (type) {
            case "hello.ack" -> {
                if (!f.sessionId().equals(f.body().get("sessionId").asText())) {
                    openFailures.add("hello.ack:session mismatch");
                    return;
                }
                sessionId = f.sessionId();
                helloAck = f.body();
                ackFuture.complete(f.body());
            }
            case "request" -> {
                if ("export.stream".equals(f.body().get("op").asText())) {
                    credit.put(f.id(), new AtomicInteger(f.body().get("params").path("initialCredit").asInt(4)));
                }
                handler.onRequest(this, f);
            }
            case "notify" -> notifyHandler.onNotify(this, f);
            case "credit" -> {
                AtomicInteger c = credit.get(f.corrId());
                if (c != null) {
                    c.addAndGet(f.body().get("grant").asInt());
                    synchronized (this) {
                        notifyAll();
                    }
                }
            }
            default -> { }
        }
    }

    /** Waits until credit is available for an export request (or the timeout passes). */
    synchronized boolean awaitCredit(String requestId, long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (credit.get(requestId).get() <= 0) {
            long left = end - System.currentTimeMillis();
            if (left <= 0) {
                return false;
            }
            wait(left);
        }
        return true;
    }
}
