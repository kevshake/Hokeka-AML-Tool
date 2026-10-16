package com.posgateway.aml.edge.tunnel;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * State of one Edge Tunnel WebSocket held by <b>this</b> Control Plane instance (the
 * "session-holding instance" of §7.12): lifecycle, negotiated parameters, in-flight request routes
 * (whose count is the session's {@code inflight}), pending hold notifies, seal-failure window and
 * the partial-message buffer used to enforce the 1 MiB frame limit.
 */
final class LocalTunnelSession {

    /** Lifecycle: upgraded and waiting for hello → open after hello.ack → closed. */
    enum State { AWAITING_HELLO, OPEN, CLOSED }

    /** Where to send the Edge's frames for one in-flight request. */
    record Route(String origin, String op, long deadlineAt) {}

    private final WebSocketSession out;
    private final String wsId;
    final String edgeId;
    final Instant upgradedAt;
    final TunnelNodeDirectory.NodeKeys keys;
    final long pspId;
    final String siteId;

    private final AtomicReference<State> state = new AtomicReference<>(State.AWAITING_HELLO);
    volatile String sessionId;
    volatile Set<String> opsEffective = Set.of();
    /** {@code hello.opsEnabled}: what the Edge offered, to re-derive {@link #opsEffective} on a config change. */
    volatile Set<String> opsOffered = Set.of();
    volatile int maxInflight;
    volatile Instant connectedAt;
    volatile Instant lastFrameAt;
    volatile Instant lastPongAt;
    volatile Instant lastNodeCheck;

    final Map<String, Route> routes = new ConcurrentHashMap<>();
    final Map<String, Long> notifyIds = new ConcurrentHashMap<>();
    /**
     * FINISH messages that arrived before their REQUEST (pub/sub delivery is not ordered across
     * listener threads): corrId → arrival ms. A REQUEST found here is dropped instead of taking a
     * slot that nothing would ever release before its deadline.
     */
    final Map<String, Long> finishedEarly = new ConcurrentHashMap<>();
    private final Deque<Long> sealFailures = new ArrayDeque<>();

    private final StringBuilder partial = new StringBuilder();
    private long partialBytes;

    /**
     * Wraps the raw WebSocket in a send-serialising decorator (Spring sessions are not safe for
     * concurrent sends) and records the identity resolved at the handshake.
     */
    LocalTunnelSession(WebSocketSession raw, String edgeId, long pspId, String siteId,
                       TunnelNodeDirectory.NodeKeys keys, Instant now) {
        this.out = new ConcurrentWebSocketSessionDecorator(raw, 10_000, 4 * 1_048_576);
        this.wsId = raw.getId();
        this.edgeId = edgeId;
        this.pspId = pspId;
        this.siteId = siteId;
        this.keys = keys;
        this.upgradedAt = now;
        this.lastFrameAt = now;
        this.lastNodeCheck = now;
    }

    /** Container id of the WebSocket (key of the hub's by-connection map). */
    String wsId() {
        return wsId;
    }

    /** Current lifecycle state. */
    State state() {
        return state.get();
    }

    /** AWAITING_HELLO → OPEN; false when the session is no longer awaiting hello. */
    boolean markOpen() {
        return state.compareAndSet(State.AWAITING_HELLO, State.OPEN);
    }

    /** Any state → CLOSED; true only for the first caller, so cleanup runs exactly once. */
    boolean markClosed() {
        return state.getAndSet(State.CLOSED) != State.CLOSED;
    }

    /** Sends one text frame. */
    void send(String text) throws IOException {
        out.sendMessage(new TextMessage(text));
    }

    /** Closes the WebSocket with a contract close code; errors closing an already-dead socket are ignored. */
    void close(int code, String reason) {
        try {
            if (out.isOpen()) {
                out.close(new CloseStatus(code, reason));
            }
        } catch (IOException | IllegalStateException e) {
            // The socket is already gone; the container's close callback still runs the cleanup.
        }
    }

    /**
     * Appends one partial text message. Returns the complete frame when {@code last}, or
     * {@code null} while more parts are expected.
     *
     * @throws FrameTooLargeException when the accumulated UTF-8 size exceeds {@code maxBytes}
     */
    synchronized String appendPartial(String part, boolean last, int maxBytes) throws FrameTooLargeException {
        partialBytes += utf8Length(part);
        if (partialBytes > maxBytes) {
            partial.setLength(0);
            partialBytes = 0;
            throw new FrameTooLargeException();
        }
        partial.append(part);
        if (!last) {
            return null;
        }
        String complete = partial.toString();
        partial.setLength(0);
        partialBytes = 0;
        return complete;
    }

    /**
     * Records one seal/replay/binding failure and answers whether the §3.3 threshold (3 within
     * 60 s by default) has been reached.
     */
    synchronized boolean recordSealFailure(long nowMs, int limit, long windowMs) {
        sealFailures.addLast(nowMs);
        while (!sealFailures.isEmpty() && sealFailures.peekFirst() < nowMs - windowMs) {
            sealFailures.removeFirst();
        }
        return sealFailures.size() >= limit;
    }

    /** UTF-8 encoded length of a string without allocating the bytes. */
    static long utf8Length(CharSequence s) {
        long n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x80) {
                n += 1;
            } else if (c < 0x800) {
                n += 2;
            } else if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                n += 4;
                i++;
            } else {
                n += 3;
            }
        }
        return n;
    }

    /** Thrown when a (possibly fragmented) frame exceeds the 1 MiB limit (close 4413). */
    static final class FrameTooLargeException extends Exception {
        /** Stackless: it is a protocol outcome, not a fault. */
        FrameTooLargeException() {
            super("frame exceeds the limit", null, false, false);
        }
    }
}
