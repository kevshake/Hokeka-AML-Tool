package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.config.edge.EdgeControlPlaneKeys;
import com.posgateway.aml.entity.edge.EdgeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Origin side of the Edge Tunnel: runs one request against <b>one site</b> (§7.12). It picks the
 * least-busy live session of the site from the Redis registry, seals the request to that node,
 * hands it to the session-holding instance (locally or over Redis pub/sub), and opens, validates
 * and re-orders the Edge's answer frames, which only this instance ever decrypts.
 *
 * <p>It implements the per-site failure semantics: {@code EDGE_OFFLINE} (no live session),
 * {@code EDGE_BUSY} (every session at its in-flight cap), {@code EDGE_TIMEOUT} after
 * {@code deadlineMs + 1 s} with a {@code cancel DEADLINE}, one retry of a read op on another session
 * of the same site when a session drops, credit for {@code export.stream}, and caller cancels.
 * Fan-out and merging across sites is {@link EdgeDataGateway}'s job.
 */
public class EdgeTunnelClient {

    private static final Logger log = LoggerFactory.getLogger(EdgeTunnelClient.class);

    private final TunnelProperties props;
    private final TunnelFrameCodec codec;
    private final TunnelRegistry registry;
    private final TunnelNodeDirectory directory;
    private final TunnelMessaging messaging;
    private final EdgeControlPlaneKeys cpKeys;
    private final Clock clock;
    private final ScheduledExecutorService scheduler;
    private final ObjectMapper mapper;

    private final Map<String, SiteCall> byCorrId = new ConcurrentHashMap<>();
    private final Set<SiteCall> active = ConcurrentHashMap.newKeySet();

    /**
     * One site-level request.
     *
     * @param pspId         PSP whose site is addressed
     * @param siteId        target site
     * @param op            the op
     * @param body          request body (op, params, page, actor, auditId); {@code deadlineMs} is
     *                      set per attempt from the remaining budget
     * @param deadlineMs    budget measured from the first attempt
     * @param actorKind     actor kind (platform pulls are cancelled when support access is revoked)
     * @param initialCredit export.stream: chunks the Edge may send before the first credit
     */
    public record SiteRequest(long pspId, String siteId, TunnelOp op, ObjectNode body, long deadlineMs,
                              TunnelActor.Kind actorKind, int initialCredit) {}

    /** The complete answer of one site to a non-streamed request. */
    public record SiteResult(String siteId, List<ObjectNode> items, String nextCursor, boolean incomplete,
                             String incompleteReason, List<String> missing, Long totalRows,
                             int discardedItems, long bytes) {}

    /** One event of a streamed site answer. */
    public sealed interface StreamEvent permits Chunk, Failed {}

    /** A validated chunk (failing items already discarded). */
    public record Chunk(List<ObjectNode> items, boolean last, Long totalRows) implements StreamEvent {}

    /** The stream ended with an error. */
    public record Failed(TunnelError error) implements StreamEvent {}

    /** Creates the client. */
    public EdgeTunnelClient(TunnelProperties props, TunnelFrameCodec codec, TunnelRegistry registry,
                            TunnelNodeDirectory directory, TunnelMessaging messaging, EdgeControlPlaneKeys cpKeys,
                            Clock clock, ScheduledExecutorService scheduler, ObjectMapper mapper) {
        this.props = props;
        this.codec = codec;
        this.registry = registry;
        this.directory = directory;
        this.messaging = messaging;
        this.cpKeys = cpKeys;
        this.clock = clock;
        this.scheduler = scheduler;
        this.mapper = mapper;
    }

    /** Starts a site request; the returned call completes asynchronously. */
    public SiteCall start(SiteRequest request) {
        SiteCall call = new SiteCall(request);
        active.add(call);
        call.begin();
        return call;
    }

    /** Origin-bound relay messages: FRAME, REJECT, SESSION_CLOSED. */
    void onOriginMessage(RelayMessage m) {
        SiteCall call = m.corrId() == null ? null : byCorrId.get(m.corrId());
        if (call == null) {
            return;
        }
        switch (m.kind()) {
            case FRAME -> call.onFrame(m.corrId(), m.frame());
            case REJECT -> call.onReject(m.corrId(), TunnelErrorCode.parse(m.code()));
            case SESSION_CLOSED -> call.onSessionClosed(m.corrId());
            default -> log.debug("Tunnel client: ignoring {} on the reply channel", m.kind());
        }
    }

    /**
     * §9 / §7.9: support access was switched off for {@code pspId} — cancel every in-flight
     * {@code PLATFORM_USER} request of that PSP started on this instance.
     *
     * @return the number of requests cancelled
     */
    public int cancelPlatformRequests(long pspId) {
        int n = 0;
        for (SiteCall call : List.copyOf(active)) {
            if (call.request.pspId() == pspId && call.request.actorKind() == TunnelActor.Kind.PLATFORM_USER) {
                call.cancel("SUPPORT_ACCESS_REVOKED");
                n++;
            }
        }
        return n;
    }

    /** Number of site requests in flight from this instance. */
    public int activeCount() {
        return active.size();
    }

    /** {@code EDGE_OFFLINE} for a site, with the site's last-seen instant (§7.11: REQUIRED). */
    TunnelError offline(long pspId, String siteId) {
        List<EdgeNode> nodes = directory.sites(pspId).getOrDefault(siteId, List.of());
        return TunnelError.offline(siteId, directory.lastSeen(nodes));
    }

    /**
     * State machine of one site request across its attempts. All mutation happens under the
     * call's monitor; completion is exactly-once.
     */
    public final class SiteCall {

        final SiteRequest request;
        private final long startedAt;
        private final long hardDeadlineAt;
        private final CompletableFuture<SiteResult> result = new CompletableFuture<>();
        private final BlockingQueue<StreamEvent> events = new LinkedBlockingQueue<>();
        private final Set<String> triedSessions = new HashSet<>();
        private final Deque<TunnelRegistry.Entry> candidates = new ArrayDeque<>();

        private String corrId;
        private TunnelRegistry.Entry entry;
        private TunnelNodeDirectory.NodeKeys keys;
        private boolean retried;
        private boolean sawBusy;
        private boolean sawOpNotAllowed;
        private boolean done;
        private final TreeMap<Integer, ObjectNode> pending = new TreeMap<>();
        private int nextSeq;
        private final List<ObjectNode> items = new ArrayList<>();
        private int discarded;
        private long bytes;
        private ScheduledFuture<?> timeout;

        /** Records the start instant and the hard deadline {@code deadlineMs + grace}. */
        SiteCall(SiteRequest request) {
            this.request = request;
            this.startedAt = clock.millis();
            this.hardDeadlineAt = startedAt + request.deadlineMs() + props.getDeadlineGraceMs();
        }

        /** The site this call addresses. */
        public String siteId() {
            return request.siteId();
        }

        /** Completes with the site's answer, or exceptionally with an {@link EdgeDataException}. */
        public CompletableFuture<SiteResult> result() {
            return result;
        }

        /** Bytes of Edge frames received so far (audit {@code bytes}). */
        public synchronized long bytes() {
            return bytes;
        }

        /** Next stream event, waiting at most {@code timeoutMs}; {@code null} on timeout. */
        public StreamEvent poll(long timeoutMs) throws InterruptedException {
            return events.poll(timeoutMs, TimeUnit.MILLISECONDS);
        }

        /** First attempt: arm the deadline timer and pick a session. */
        synchronized void begin() {
            long delay = Math.max(0, hardDeadlineAt - clock.millis());
            timeout = scheduler.schedule(this::onDeadline, delay, TimeUnit.MILLISECONDS);
            loadCandidates();
            dispatchNext();
        }

        /**
         * Live sessions of the site not tried yet, least in-flight first, ties broken by the most
         * recent {@code lastPongAt} (§7.12 node selection).
         */
        private void loadCandidates() {
            candidates.clear();
            Instant now = clock.instant();
            registry.liveEntries(request.pspId(), now).stream()
                    .filter(e -> e.siteId().equals(request.siteId()))
                    .filter(e -> !triedSessions.contains(e.sessionId()))
                    .sorted(Comparator.comparingInt(TunnelRegistry.Entry::inflight)
                            .thenComparing(TunnelRegistry.Entry::lastPongAt, Comparator.reverseOrder()))
                    .forEach(candidates::addLast);
        }

        /** Sends the request on the next usable candidate session, or fails the call. */
        private void dispatchNext() {
            while (!done) {
                TunnelRegistry.Entry next = candidates.pollFirst();
                if (next == null) {
                    if (sawOpNotAllowed && !sawBusy) {
                        fail(TunnelError.of(TunnelErrorCode.OP_NOT_ALLOWED,
                                "op not enabled on any node of site " + request.siteId()).withSite(request.siteId()),
                                false);
                    } else if (sawBusy) {
                        fail(TunnelError.busy(request.siteId(), props.getMaxInflight(), props.getBusyRetryAfterMs()),
                                false);
                    } else {
                        fail(offline(request.pspId(), request.siteId()), false);
                    }
                    return;
                }
                triedSessions.add(next.sessionId());
                if (next.inflight() >= props.getMaxInflight()) {
                    sawBusy = true;
                    continue;
                }
                Optional<TunnelNodeDirectory.NodeKeys> k = directory.keys(next.edgeId());
                if (k.isEmpty()) {
                    continue;
                }
                long remaining = hardDeadlineAt - props.getDeadlineGraceMs() - clock.millis();
                if (remaining < 100) {
                    fail(TunnelError.timeout(request.siteId()), false);
                    return;
                }
                ObjectNode body = request.body().deepCopy();
                body.put("deadlineMs", Math.min(remaining, request.deadlineMs()));
                String id = TunnelFrameCodec.newId();
                String frame;
                try {
                    frame = codec.seal(new TunnelFrameCodec.FrameSpec("request", id, null, next.sessionId(),
                            next.edgeId(), body), k.get().x25519Raw(), cpKeys.signingPrivateKey(), clock.instant());
                } catch (TunnelFrameException e) {
                    fail(TunnelError.of(e.code(), "request frame could not be built"), false);
                    return;
                }
                entry = next;
                keys = k.get();
                corrId = id;
                byCorrId.put(id, this);
                messaging.toHolder(next.cpInstance(), RelayMessage.request(next.sessionId(), id,
                        messaging.instanceId(), request.op().wire(), hardDeadlineAt, frame));
                return;
            }
        }

        /** The holder could not send the request on that session; try the next one. */
        synchronized void onReject(String forCorrId, TunnelErrorCode code) {
            if (done || !forCorrId.equals(corrId)) {
                return;
            }
            byCorrId.remove(forCorrId);
            if (code == TunnelErrorCode.EDGE_BUSY) {
                sawBusy = true;
            } else if (code == TunnelErrorCode.OP_NOT_ALLOWED) {
                sawOpNotAllowed = true;
            }
            corrId = null;
            dispatchNext();
        }

        /** The session dropped before the terminal frame: retry a read op once (§7.12), else offline. */
        synchronized void onSessionClosed(String forCorrId) {
            if (done || !forCorrId.equals(corrId)) {
                return;
            }
            byCorrId.remove(forCorrId);
            corrId = null;
            long remaining = hardDeadlineAt - props.getDeadlineGraceMs() - clock.millis();
            if (request.op().retryableOnSessionLoss() && !retried && remaining >= 100) {
                retried = true;
                pending.clear();
                items.clear();
                nextSeq = 0;
                loadCandidates();
                dispatchNext();
            } else {
                fail(offline(request.pspId(), request.siteId()), false);
            }
        }

        /** Opens, binds and validates one Edge answer frame, then applies it in {@code seq} order. */
        synchronized void onFrame(String forCorrId, String text) {
            if (done || !forCorrId.equals(corrId)) {
                return;
            }
            bytes += LocalTunnelSession.utf8Length(text);
            TunnelFrameCodec.OpenedFrame f;
            try {
                ObjectNode outer = codec.parseOuter(text);
                f = codec.open(outer, cpKeys.ingestPrivateKey(), keys.ed25519(), entry.edgeId(),
                        entry.sessionId(), clock.instant());
            } catch (TunnelFrameException e) {
                messaging.toHolder(entry.cpInstance(), RelayMessage.control(RelayMessage.Kind.SEAL_FAILURE,
                        entry.sessionId(), corrId));
                fail(TunnelError.of(e.code(), "Edge answer refused").withSite(request.siteId()), true);
                return;
            }
            if ("error".equals(f.type())) {
                if (!codec.checkBody("error", f.body()).isEmpty()) {
                    fail(TunnelError.of(TunnelErrorCode.INTERNAL, "Edge error body invalid").withSite(request.siteId()),
                            false);
                    return;
                }
                TunnelError err = TunnelError.fromJson(f.body());
                fail(err.siteId() == null ? err.withSite(request.siteId()) : err, false);
                return;
            }
            boolean streaming = request.op() == TunnelOp.EXPORT_STREAM;
            String expectedType = streaming ? "chunk" : "response";
            if (!expectedType.equals(f.type())) {
                fail(TunnelError.of(TunnelErrorCode.INTERNAL, "unexpected answer frame type").withSite(request.siteId()),
                        true);
                return;
            }
            TunnelFrameCodec.CheckedResponse checked;
            try {
                checked = codec.checkResponse(f.body());
            } catch (TunnelFrameException e) {
                fail(TunnelError.of(TunnelErrorCode.INTERNAL, "Edge answer envelope invalid").withSite(request.siteId()),
                        true);
                return;
            }
            if (!request.op().wire().equals(checked.body().path("op").asText())) {
                fail(TunnelError.of(TunnelErrorCode.INTERNAL, "Edge answered a different op").withSite(request.siteId()),
                        true);
                return;
            }
            if (checked.discardedItems() > 0) {
                discarded += checked.discardedItems();
                log.warn("Tunnel: discarded {} item(s) from site {} failing the {} item schema at {}",
                        checked.discardedItems(), request.siteId(), request.op().wire(), checked.discardedPointers());
            }
            pending.put(checked.body().get("seq").asInt(), checked.body());
            while (!done && pending.containsKey(nextSeq)) {
                ObjectNode b = pending.remove(nextSeq);
                nextSeq++;
                apply(b, streaming);
            }
        }

        /** Applies one in-order answer body: accumulate (response) or enqueue (chunk). */
        private void apply(ObjectNode b, boolean streaming) {
            List<ObjectNode> batch = new ArrayList<>();
            for (JsonNode item : b.get("items")) {
                batch.add((ObjectNode) item);
            }
            boolean last = b.get("last").asBoolean();
            Long totalRows = b.hasNonNull("totalRows") ? b.get("totalRows").asLong() : null;
            if (streaming) {
                events.add(new Chunk(List.copyOf(batch), last, totalRows));
                if (last) {
                    finish(new SiteResult(request.siteId(), List.of(), null, false, null, List.of(), totalRows,
                            discarded, bytes));
                }
                return;
            }
            items.addAll(batch);
            if (last) {
                List<String> missing = new ArrayList<>();
                if (b.path("missing").isArray()) {
                    b.get("missing").forEach(m -> missing.add(m.asText()));
                }
                boolean incomplete = b.path("incomplete").asBoolean(false);
                finish(new SiteResult(request.siteId(), List.copyOf(items),
                        b.hasNonNull("nextCursor") ? b.get("nextCursor").asText() : null, incomplete,
                        incomplete ? b.path("incompleteReason").asText(null) : null, List.copyOf(missing),
                        totalRows, discarded, bytes));
            }
        }

        /** export.stream: grant the Edge {@code n} more chunks (§7.8). */
        public synchronized void grant(int n) {
            if (done || corrId == null) {
                return;
            }
            ObjectNode body = mapper.createObjectNode().put("grant", n);
            sendSealed("credit", RelayMessage.Kind.CREDIT, body);
        }

        /**
         * Caller cancel (§7.9: client gone, superseded, support access revoked, shutdown). Sends
         * {@code cancel} and completes the call with {@code CANCELLED} (or, for a support-access
         * revocation, {@code SUPPORT_ACCESS_DISABLED}).
         */
        public synchronized void cancel(String reason) {
            if (done) {
                return;
            }
            sendCancel(reason);
            TunnelError err = "SUPPORT_ACCESS_REVOKED".equals(reason)
                    ? TunnelError.of(TunnelErrorCode.SUPPORT_ACCESS_DISABLED, "support access was switched off")
                    : TunnelError.of(TunnelErrorCode.CANCELLED, "request cancelled");
            fail(err.withSite(request.siteId()), false);
        }

        /** Deadline timer: {@code EDGE_TIMEOUT} and {@code cancel DEADLINE} (§7.5). */
        private synchronized void onDeadline() {
            if (done) {
                return;
            }
            sendCancel("DEADLINE");
            fail(TunnelError.timeout(request.siteId()), false);
        }

        /** Sends {@code cancel {reason}} for the current attempt, if one was sent. */
        private void sendCancel(String reason) {
            if (corrId == null || entry == null) {
                return;
            }
            sendSealed("cancel", RelayMessage.Kind.CANCEL, mapper.createObjectNode().put("reason", reason));
        }

        /** Seals a cloud→Edge control frame for the current attempt and hands it to the holder. */
        private void sendSealed(String type, RelayMessage.Kind kind, ObjectNode body) {
            try {
                String frame = codec.seal(new TunnelFrameCodec.FrameSpec(type, TunnelFrameCodec.newId(), corrId,
                        entry.sessionId(), entry.edgeId(), body), keys.x25519Raw(), cpKeys.signingPrivateKey(),
                        clock.instant());
                messaging.toHolder(entry.cpInstance(), RelayMessage.withFrame(kind, entry.sessionId(), corrId,
                        messaging.instanceId(), frame));
            } catch (TunnelFrameException | RuntimeException e) {
                log.warn("Tunnel: could not send {} for site {} ({})", type, request.siteId(),
                        e.getClass().getSimpleName());
            }
        }

        /** Normal completion: release the holder's slot and complete the future. */
        private void finish(SiteResult r) {
            if (done) {
                return;
            }
            done = true;
            release();
            result.complete(r);
        }

        /**
         * Failure: optionally cancel the Edge work, release the holder's slot, complete
         * exceptionally and, for streams, emit the terminal event.
         */
        private void fail(TunnelError error, boolean cancelEdge) {
            if (done) {
                return;
            }
            if (cancelEdge) {
                sendCancel("SUPERSEDED");
            }
            done = true;
            release();
            events.add(new Failed(error));
            result.completeExceptionally(new EdgeDataException(error));
        }

        /** FINISH to the holder (frees the in-flight slot) and forget the call. */
        private void release() {
            if (timeout != null) {
                timeout.cancel(false);
            }
            if (corrId != null) {
                byCorrId.remove(corrId);
                if (entry != null) {
                    messaging.toHolder(entry.cpInstance(), RelayMessage.control(RelayMessage.Kind.FINISH,
                            entry.sessionId(), corrId));
                }
            }
            active.remove(this);
        }
    }
}
