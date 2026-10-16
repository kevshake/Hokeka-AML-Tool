package com.posgateway.aml.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.entity.edge.EdgeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The Java API through which cloud code (R6 data proxy, R9 filing, R15 backfill) reads a PSP's
 * transaction data from its Edge Sites — the "EdgeDataProxy" of the plan.
 *
 * <p>Per request it: enforces the §9 support-access gate without contacting the Edge; validates the
 * request against {@code tunnel/request.schema.json} and the §7.6 limits; writes the cloud
 * {@code edge_data_access_log} row before sending and completes it on the terminal frame (§7.15);
 * fans the request out to the PSP's sites (or the sites the caller names) through
 * {@link EdgeTunnelClient}; and merges the answers (§7.13): k-way merge with an HMAC-signed composite
 * cursor for lists, union for gets, partial results with {@code incomplete} and
 * {@code unavailableSites}, and the "best" failure when no site answers.
 *
 * <p><b>No payload persistence (§7.16).</b> Items are held only in the returned object. Nothing
 * here logs params beyond the audit summary, or any item, at any level; error messages are fixed
 * phrases.
 */
public class EdgeDataGateway {

    private static final Logger log = LoggerFactory.getLogger(EdgeDataGateway.class);

    /** Site errors that are a property of the request, not of the site: fail the whole pull. */
    private static final Set<TunnelErrorCode> REQUEST_FATAL = EnumSet.of(TunnelErrorCode.INVALID_PARAMS,
            TunnelErrorCode.INVALID_CURSOR, TunnelErrorCode.PAGE_LIMIT_EXCEEDED, TunnelErrorCode.TENANT_MISMATCH,
            TunnelErrorCode.SUPPORT_ACCESS_DISABLED, TunnelErrorCode.BACKFILL_NOT_ALLOWED,
            TunnelErrorCode.BACKFILL_REJECTED, TunnelErrorCode.UNSUPPORTED_VERSION, TunnelErrorCode.FRAME_TOO_LARGE,
            TunnelErrorCode.CANCELLED);

    /** §7.13 "best" failure order when no site answered (lower index wins). */
    private static final List<TunnelErrorCode> FAILURE_PREFERENCE = List.of(TunnelErrorCode.EDGE_BUSY,
            TunnelErrorCode.EDGE_TIMEOUT, TunnelErrorCode.DEADLINE_EXCEEDED, TunnelErrorCode.LEDGER_UNAVAILABLE,
            TunnelErrorCode.STREAM_STALLED, TunnelErrorCode.INTERNAL, TunnelErrorCode.SEAL_INVALID,
            TunnelErrorCode.REPLAY_DETECTED, TunnelErrorCode.BINDING_MISMATCH, TunnelErrorCode.NOT_FOUND,
            TunnelErrorCode.OP_NOT_ALLOWED, TunnelErrorCode.EDGE_OFFLINE);

    private static final Duration SEARCH_MAX_SPAN = Duration.ofDays(93);
    private static final Duration EXPORT_MAX_SPAN = Duration.ofDays(366);

    private final EdgeTunnelClient client;
    private final TunnelNodeDirectory directory;
    private final SupportAccessPolicy supportAccess;
    private final EdgeDataAccessAuditor auditor;
    private final CompositeCursorCodec cursors;
    private final TunnelSchemas schemas;
    private final TunnelMessaging messaging;
    private final TunnelProperties props;
    private final ObjectMapper mapper;

    /** Wires the gateway. */
    public EdgeDataGateway(EdgeTunnelClient client, TunnelNodeDirectory directory, SupportAccessPolicy supportAccess,
                           EdgeDataAccessAuditor auditor, CompositeCursorCodec cursors, TunnelSchemas schemas,
                           TunnelMessaging messaging, TunnelProperties props, ObjectMapper mapper) {
        this.client = client;
        this.directory = directory;
        this.supportAccess = supportAccess;
        this.auditor = auditor;
        this.cursors = cursors;
        this.schemas = schemas;
        this.messaging = messaging;
        this.props = props;
        this.mapper = mapper;
    }

    // ── public API ───────────────────────────────────────────────────────────────────────────────

    /**
     * {@code request(pspId, op, params, actor, purpose)}: one pull with default paging/deadline
     * over all sites of the PSP. {@code purpose} replaces the actor's purpose.
     *
     * @throws EdgeDataException when the pull fails as a whole (body = §7.11 error)
     */
    public EdgeDataResult request(long pspId, TunnelOp op, ObjectNode params, TunnelActor actor,
                                  TunnelActor.Purpose purpose) {
        return request(EdgeDataRequest.of(pspId, op, params, actor.withPurpose(purpose)));
    }

    /**
     * One pull of a non-streamed op ({@code export.stream} uses {@link #openExport}).
     *
     * @throws EdgeDataException when the pull fails as a whole
     */
    public EdgeDataResult request(EdgeDataRequest r) {
        if (r.op() == TunnelOp.EXPORT_STREAM) {
            throw new IllegalArgumentException("export.stream is streamed: use openExport");
        }
        UUID auditId = UUID.randomUUID();
        Set<String> contacted = new TreeSet<>();
        Set<String> answered = new TreeSet<>();
        long[] bytes = {0};
        int[] itemCount = {0};
        String outcome = TunnelErrorCode.INTERNAL.name();
        beginAudit(auditId, r);
        try {
            Prepared p = prepare(r, auditId);
            Map<String, EdgeTunnelClient.SiteCall> calls = startCalls(r, p, contacted);
            Map<String, EdgeTunnelClient.SiteResult> results = new LinkedHashMap<>();
            Map<String, TunnelError> failures = new LinkedHashMap<>();
            await(calls, r.effectiveDeadlineMs(), results, failures);
            calls.values().forEach(c -> bytes[0] += c.bytes());
            answered.addAll(results.keySet());
            EdgeDataResult result = merge(r, p, auditId, results, failures);
            itemCount[0] = result.items().size();
            outcome = result.unavailableSites().isEmpty() && !result.incomplete() ? "OK" : "PARTIAL";
            return result;
        } catch (EdgeDataException e) {
            outcome = e.error().code().name();
            throw e;
        } catch (RuntimeException e) {
            log.error("Tunnel pull {} failed: {}", r.op().wire(), e.getClass().getSimpleName());
            throw new EdgeDataException(TunnelError.of(TunnelErrorCode.INTERNAL, "internal error"));
        } finally {
            completeAudit(auditId, contacted, answered, itemCount[0], bytes[0], outcome);
        }
    }

    /**
     * Opens a streamed {@code export.stream} across the PSP's sites (§7.8, §7.13: merged
     * chunk-wise with per-site credit). Waits for every site's first event, so a pull that no site
     * can serve fails here, before the caller has started its HTTP response.
     *
     * @throws EdgeDataException when no site can serve the export
     */
    public EdgeDataStream openExport(EdgeDataRequest r) {
        if (r.op() != TunnelOp.EXPORT_STREAM) {
            throw new IllegalArgumentException("openExport serves export.stream only");
        }
        UUID auditId = UUID.randomUUID();
        Set<String> contacted = new TreeSet<>();
        beginAudit(auditId, r);
        try {
            Prepared p = prepare(r, auditId);
            Map<String, EdgeTunnelClient.SiteCall> calls = startCalls(r, p, contacted);
            int maxRows = r.params().path("maxRows").asInt(100_000);
            return new EdgeDataStream(auditId, calls, contacted, maxRows, r.effectiveDeadlineMs()
                    + props.getDeadlineGraceMs(), this);
        } catch (EdgeDataException e) {
            completeAudit(auditId, contacted, Set.of(), 0, 0, e.error().code().name());
            throw e;
        } catch (RuntimeException e) {
            completeAudit(auditId, contacted, Set.of(), 0, 0, TunnelErrorCode.INTERNAL.name());
            log.error("Tunnel export failed to open: {}", e.getClass().getSimpleName());
            throw new EdgeDataException(TunnelError.of(TunnelErrorCode.INTERNAL, "internal error"));
        }
    }

    /**
     * §9 / §7.9: support access was switched off for a PSP. Every Control Plane instance cancels
     * its in-flight {@code PLATFORM_USER} pulls of that PSP ({@code cancel SUPPORT_ACCESS_REVOKED}).
     * Called by the R3 settings flow after it stores the flag.
     */
    public void revokeSupportAccess(long pspId) {
        messaging.broadcast(RelayMessage.supportRevoked(pspId));
    }

    /** Broadcast consumer: cancel this instance's platform pulls of the PSP. */
    void onBroadcast(RelayMessage m) {
        if (m.kind() == RelayMessage.Kind.SUPPORT_REVOKED && m.pspId() != null) {
            int n = client.cancelPlatformRequests(m.pspId());
            if (n > 0) {
                log.info("Cancelled {} platform pull(s) of PSP {} after support access was revoked", n, m.pspId());
            }
        }
    }

    // ── preparation ──────────────────────────────────────────────────────────────────────────────

    /** Validated request ready to fan out. */
    record Prepared(ObjectNode baseBody, String q, Map<String, List<EdgeNode>> sites, Map<String, String> cursorStates) {}

    /** Gate, validate, resolve sites and decode the composite cursor. */
    private Prepared prepare(EdgeDataRequest r, UUID auditId) {
        gate(r);
        ObjectNode body = mapper.createObjectNode();
        body.put("op", r.op().wire());
        body.set("params", r.params());
        body.put("deadlineMs", r.effectiveDeadlineMs());
        if (r.op().paged()) {
            body.putObject("page").put("limit", r.effectivePageLimit());
        }
        body.set("actor", r.actor().toJson());
        body.put("auditId", auditId.toString());
        List<TunnelSchemas.Violation> violations = schemas.validate("tunnel/request.schema.json", body);
        if (!violations.isEmpty()) {
            throw new EdgeDataException(mapViolation(violations, r.actor()));
        }
        checkLimits(r);

        Map<String, List<EdgeNode>> all = directory.sites(r.pspId());
        Map<String, List<EdgeNode>> sites = new LinkedHashMap<>();
        all.forEach((site, nodes) -> {
            if (r.sites() == null || r.sites().contains(site)) {
                sites.put(site, nodes);
            }
        });
        if (r.op().shape() == TunnelOp.Shape.SINGLE_SITE && (r.sites() == null || r.sites().size() != 1)) {
            throw new EdgeDataException(new TunnelError(TunnelErrorCode.INVALID_PARAMS, 400,
                    "ledger.backfill needs exactly one target site", false, null, null, null, ""));
        }
        if (sites.isEmpty()) {
            String only = r.sites() != null && r.sites().size() == 1 ? r.sites().iterator().next() : null;
            throw new EdgeDataException(TunnelError.offline(only, Instant.EPOCH));
        }
        String q = CompositeCursorCodec.queryHash(r.params());
        Map<String, String> states = new HashMap<>();
        if (r.op().paged() && r.cursor() != null) {
            states.putAll(cursors.decode(r.cursor(), q));
        }
        return new Prepared(body, q, sites, states);
    }

    /**
     * §9 at the Control Plane: a {@code PLATFORM_USER} pull needs the PSP's support-access flag,
     * purpose {@code SUPPORT_ACCESS}, a 10..500 character reason and a platform role — otherwise
     * {@code 403 SUPPORT_ACCESS_DISABLED} without contacting the Edge. The actor must belong to
     * the addressed PSP.
     */
    private void gate(EdgeDataRequest r) {
        TunnelActor a = r.actor();
        if (a.pspId() != r.pspId()) {
            throw new EdgeDataException(TunnelError.of(TunnelErrorCode.TENANT_MISMATCH,
                    "actor does not belong to the addressed PSP"));
        }
        if (a.kind() == TunnelActor.Kind.PLATFORM_USER) {
            String reason = a.supportReason();
            boolean ok = a.purpose() == TunnelActor.Purpose.SUPPORT_ACCESS
                    && reason != null && reason.length() >= 10 && reason.length() <= 500
                    && TunnelActor.PLATFORM_ROLES.contains(a.role())
                    && supportAccess.isSupportAccessEnabled(r.pspId());
            if (!ok) {
                throw new EdgeDataException(TunnelError.of(TunnelErrorCode.SUPPORT_ACCESS_DISABLED,
                        "support access is not enabled for this PSP or the pull lacks a reason"));
            }
        }
    }

    /** Maps the first schema violation to the §7.11 error the cloud raises (pointer only). */
    private static TunnelError mapViolation(List<TunnelSchemas.Violation> violations, TunnelActor actor) {
        TunnelSchemas.Violation pick = violations.stream()
                .min(Comparator.comparingInt(v -> rank(v.pointer()))).orElse(violations.get(0));
        String p = pick.pointer();
        if (p.startsWith("/op")) {
            return TunnelError.of(TunnelErrorCode.OP_NOT_ALLOWED, "op is not in the tunnel allowlist");
        }
        if (p.startsWith("/actor") && actor.kind() == TunnelActor.Kind.PLATFORM_USER) {
            return TunnelError.of(TunnelErrorCode.SUPPORT_ACCESS_DISABLED, "platform pull without support access");
        }
        if ((p.equals("/page/limit") && "maximum".equals(pick.keyword()))
                || (p.equals("/params/edgeAssessmentIds") && "maxItems".equals(pick.keyword()))) {
            return TunnelError.of(TunnelErrorCode.PAGE_LIMIT_EXCEEDED, "page or id limit exceeded");
        }
        String param = p.startsWith("/params") ? p.substring("/params".length()) : p;
        return TunnelError.invalidParams(param);
    }

    /** Ordering of violations: op first, then actor, page, params, anything else. */
    private static int rank(String pointer) {
        if (pointer.startsWith("/op")) {
            return 0;
        }
        if (pointer.startsWith("/actor")) {
            return 1;
        }
        if (pointer.startsWith("/page")) {
            return 2;
        }
        if (pointer.startsWith("/params")) {
            return 3;
        }
        return 4;
    }

    /** §7.6 limits not expressible in the schemas: search span ≤ 93 d, export span ≤ 366 d. */
    private static void checkLimits(EdgeDataRequest r) {
        if (r.op() == TunnelOp.TXN_SEARCH && span(r.params()).compareTo(SEARCH_MAX_SPAN) > 0) {
            throw new EdgeDataException(TunnelError.invalidParams("/to"));
        }
        if (r.op() == TunnelOp.EXPORT_STREAM && span(r.params().path("filter")).compareTo(EXPORT_MAX_SPAN) > 0) {
            throw new EdgeDataException(TunnelError.of(TunnelErrorCode.PAGE_LIMIT_EXCEEDED,
                    "export span exceeds 366 days"));
        }
    }

    /** {@code to − from} of a search filter (validated instants). */
    private static Duration span(JsonNode filter) {
        try {
            return Duration.between(Instant.parse(filter.get("from").asText()), Instant.parse(filter.get("to").asText()));
        } catch (DateTimeParseException | NullPointerException e) {
            throw new EdgeDataException(TunnelError.invalidParams("/from"));
        }
    }

    // ── fan-out ──────────────────────────────────────────────────────────────────────────────────

    /** Starts one site call per addressed site (skipping sites whose cursor state is DONE). */
    private Map<String, EdgeTunnelClient.SiteCall> startCalls(EdgeDataRequest r, Prepared p, Set<String> contacted) {
        Map<String, EdgeTunnelClient.SiteCall> calls = new LinkedHashMap<>();
        int initialCredit = r.params().path("initialCredit").asInt(4);
        for (String site : p.sites().keySet()) {
            ObjectNode body = p.baseBody().deepCopy();
            if (r.op().paged()) {
                String state = p.cursorStates().getOrDefault(site, CompositeCursorCodec.START);
                if (CompositeCursorCodec.DONE.equals(state)) {
                    continue;
                }
                if (!CompositeCursorCodec.START.equals(state)) {
                    ((ObjectNode) body.get("page")).put("cursor", state);
                }
            }
            contacted.add(site);
            calls.put(site, client.start(new EdgeTunnelClient.SiteRequest(r.pspId(), site, r.op(), body,
                    r.effectiveDeadlineMs(), r.actor().kind(), initialCredit)));
        }
        return calls;
    }

    /** Waits for every site call (each is bounded by its own deadline timer). */
    private void await(Map<String, EdgeTunnelClient.SiteCall> calls, long deadlineMs,
                       Map<String, EdgeTunnelClient.SiteResult> results, Map<String, TunnelError> failures) {
        long waitMs = deadlineMs + props.getDeadlineGraceMs() + 2_000;
        for (Map.Entry<String, EdgeTunnelClient.SiteCall> e : calls.entrySet()) {
            try {
                results.put(e.getKey(), e.getValue().result().get(waitMs, TimeUnit.MILLISECONDS));
            } catch (ExecutionException | CompletionException ex) {
                failures.put(e.getKey(), errorOf(ex.getCause(), e.getKey()));
            } catch (TimeoutException ex) {
                e.getValue().cancel("DEADLINE");
                failures.put(e.getKey(), TunnelError.timeout(e.getKey()));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                e.getValue().cancel("CLIENT_GONE");
                failures.put(e.getKey(), TunnelError.of(TunnelErrorCode.CANCELLED, "request cancelled"));
            }
        }
    }

    /** The tunnel error carried by a failed site call. */
    static TunnelError errorOf(Throwable t, String site) {
        if (t instanceof EdgeDataException ede) {
            return ede.error().siteId() == null ? ede.error().withSite(site) : ede.error();
        }
        return TunnelError.of(TunnelErrorCode.INTERNAL, "internal error").withSite(site);
    }

    // ── merge (§7.13) ────────────────────────────────────────────────────────────────────────────

    /** Combines site answers per op shape, or throws the request's failure. */
    private EdgeDataResult merge(EdgeDataRequest r, Prepared p, UUID auditId,
                                 Map<String, EdgeTunnelClient.SiteResult> results, Map<String, TunnelError> failures) {
        for (TunnelError f : failures.values()) {
            if (REQUEST_FATAL.contains(f.code())) {
                throw new EdgeDataException(f);
            }
        }
        boolean getShape = r.op().shape() == TunnelOp.Shape.GET;
        Map<String, TunnelError> unavailable = new LinkedHashMap<>();
        failures.forEach((site, err) -> {
            if (getShape && err.code() == TunnelErrorCode.NOT_FOUND) {
                results.put(site, new EdgeTunnelClient.SiteResult(site, List.of(), null, false, null, List.of(),
                        null, 0, 0));
            } else {
                unavailable.put(site, err);
            }
        });
        if (results.isEmpty()) {
            throw new EdgeDataException(bestFailure(unavailable.values()));
        }
        List<EdgeDataResult.UnavailableSite> unavailableSites = new ArrayList<>();
        unavailable.forEach((site, err) -> unavailableSites.add(new EdgeDataResult.UnavailableSite(site, err.code(),
                err.code() == TunnelErrorCode.EDGE_OFFLINE ? err.lastSeenAt() : null)));
        Map<String, List<ObjectNode>> bySite = new LinkedHashMap<>();
        results.forEach((site, res) -> bySite.put(site, res.items()));
        String incompleteReason = results.values().stream().filter(EdgeTunnelClient.SiteResult::incomplete)
                .map(EdgeTunnelClient.SiteResult::incompleteReason).filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        boolean siteIncomplete = results.values().stream().anyMatch(EdgeTunnelClient.SiteResult::incomplete);
        List<String> answered = List.copyOf(results.keySet());

        return switch (r.op().shape()) {
            case LIST -> mergeList(r, p, auditId, results, unavailableSites, bySite, siteIncomplete, incompleteReason,
                    answered);
            case GET -> mergeGet(r, auditId, results, unavailableSites, bySite, siteIncomplete, incompleteReason, answered);
            case PER_SITE, SINGLE_SITE -> {
                List<ObjectNode> all = new ArrayList<>();
                bySite.values().forEach(all::addAll);
                List<String> missing = new ArrayList<>();
                results.values().forEach(res -> missing.addAll(res.missing()));
                yield new EdgeDataResult(auditId, r.op(), List.copyOf(all), null,
                        siteIncomplete || !unavailableSites.isEmpty(), incompleteReason, List.copyOf(unavailableSites),
                        List.copyOf(missing), null, bySite, answered);
            }
            case STREAM -> throw new IllegalStateException("streams are merged by EdgeDataStream");
        };
    }

    /** §7.13 "best" failure when no site answered: EDGE_BUSY over EDGE_TIMEOUT over EDGE_OFFLINE. */
    static TunnelError bestFailure(java.util.Collection<TunnelError> errors) {
        return errors.stream().min(Comparator.comparingInt(e -> {
            int i = FAILURE_PREFERENCE.indexOf(e.code());
            return i < 0 ? FAILURE_PREFERENCE.size() : i;
        })).orElse(TunnelError.offline(null, Instant.EPOCH));
    }

    /**
     * Lists: k-way merge by (evaluatedAt desc, edgeAssessmentId desc), stopping where a site that
     * has more rows ran out of buffered items (so the order is exact), and a composite cursor that
     * records, per site, the position of the last item consumed from it — unanswered sites keep
     * their previous position so a later page includes them.
     */
    private EdgeDataResult mergeList(EdgeDataRequest r, Prepared p, UUID auditId,
                                     Map<String, EdgeTunnelClient.SiteResult> results,
                                     List<EdgeDataResult.UnavailableSite> unavailableSites,
                                     Map<String, List<ObjectNode>> bySite, boolean siteIncomplete,
                                     String incompleteReason, List<String> answered) {
        int limit = r.effectivePageLimit();
        Map<String, Integer> consumed = new HashMap<>();
        results.keySet().forEach(s -> consumed.put(s, 0));
        List<ObjectNode> merged = new ArrayList<>();
        while (merged.size() < limit) {
            String best = null;
            SortKey bestKey = null;
            boolean blocked = false;
            for (Map.Entry<String, EdgeTunnelClient.SiteResult> e : results.entrySet()) {
                int c = consumed.get(e.getKey());
                List<ObjectNode> items = e.getValue().items();
                if (c >= items.size()) {
                    if (e.getValue().nextCursor() != null) {
                        blocked = true;
                    }
                    continue;
                }
                SortKey k = SortKey.of(r.op(), items.get(c));
                if (bestKey == null || k.compareTo(bestKey) > 0) {
                    bestKey = k;
                    best = e.getKey();
                }
            }
            if (blocked || best == null) {
                break;
            }
            int c = consumed.get(best);
            merged.add(results.get(best).items().get(c));
            consumed.put(best, c + 1);
        }

        Map<String, String> states = new LinkedHashMap<>();
        for (String site : p.sites().keySet()) {
            String prev = p.cursorStates().getOrDefault(site, CompositeCursorCodec.START);
            EdgeTunnelClient.SiteResult res = results.get(site);
            if (res == null) {
                states.put(site, prev);
                continue;
            }
            int c = consumed.get(site);
            if (c == res.items().size()) {
                states.put(site, res.nextCursor() != null ? res.nextCursor() : CompositeCursorCodec.DONE);
            } else if (c > 0) {
                SortKey k = SortKey.of(r.op(), res.items().get(c - 1));
                states.put(site, cursors.mintSiteCursor(p.q(), k.evaluatedAtText(), k.id()));
            } else {
                states.put(site, prev);
            }
        }
        boolean allDone = states.values().stream().allMatch(CompositeCursorCodec.DONE::equals);
        String next = allDone ? null : cursors.encode(p.q(), states);
        return new EdgeDataResult(auditId, r.op(), List.copyOf(merged), next,
                siteIncomplete || !unavailableSites.isEmpty(), incompleteReason, List.copyOf(unavailableSites),
                List.of(), null, bySite, answered);
    }

    /**
     * Gets: union of items across sites (§7.13). txn.batchGet / filing.extract items follow the
     * requested id order; an id is {@code missing} only if no answering site returned it.
     * txn.get with no item from any site, and every site answered, is {@code NOT_FOUND}.
     */
    private EdgeDataResult mergeGet(EdgeDataRequest r, UUID auditId, Map<String, EdgeTunnelClient.SiteResult> results,
                                    List<EdgeDataResult.UnavailableSite> unavailableSites,
                                    Map<String, List<ObjectNode>> bySite, boolean siteIncomplete,
                                    String incompleteReason, List<String> answered) {
        boolean incomplete = siteIncomplete || !unavailableSites.isEmpty();
        if (r.op() == TunnelOp.TXN_GET) {
            List<ObjectNode> all = new ArrayList<>();
            bySite.values().forEach(all::addAll);
            if (all.isEmpty() && unavailableSites.isEmpty()) {
                throw new EdgeDataException(TunnelError.of(TunnelErrorCode.NOT_FOUND, "no such evaluation on any site"));
            }
            return new EdgeDataResult(auditId, r.op(), List.copyOf(all), null, incomplete, incompleteReason,
                    List.copyOf(unavailableSites), List.of(), null, bySite, answered);
        }
        List<String> requested = new ArrayList<>();
        r.params().path("edgeAssessmentIds").forEach(n -> requested.add(n.asText()));
        Map<String, ObjectNode> byId = new HashMap<>();
        List<ObjectNode> aggregates = new ArrayList<>();
        for (List<ObjectNode> items : bySite.values()) {
            for (ObjectNode item : items) {
                if (r.op() == TunnelOp.FILING_EXTRACT && "CTR_AGGREGATE".equals(item.path("kind").asText())) {
                    aggregates.add(item);
                    continue;
                }
                JsonNode tx = r.op() == TunnelOp.FILING_EXTRACT ? item.path("transaction") : item;
                byId.putIfAbsent(tx.path("edgeAssessmentId").asText(), item);
            }
        }
        List<ObjectNode> ordered = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String id : new LinkedHashSet<>(requested)) {
            ObjectNode item = byId.get(id);
            if (item != null) {
                ordered.add(item);
            } else {
                missing.add(id);
            }
        }
        ordered.addAll(aggregates);
        return new EdgeDataResult(auditId, r.op(), List.copyOf(ordered), null, incomplete, incompleteReason,
                List.copyOf(unavailableSites), List.copyOf(missing), null, bySite, answered);
    }

    // ── audit helpers ────────────────────────────────────────────────────────────────────────────

    /** Writes the audit row first; if it cannot be written the pull does not happen (fail closed). */
    private void beginAudit(UUID auditId, EdgeDataRequest r) {
        try {
            auditor.begin(auditId, r.actor(), r.op(), r.params());
        } catch (RuntimeException e) {
            log.error("Tunnel pull refused: access audit row could not be written ({})", e.getClass().getSimpleName());
            throw new EdgeDataException(TunnelError.of(TunnelErrorCode.INTERNAL, "access audit unavailable"));
        }
    }

    /** Completes the audit row; a failure here is logged (the pull itself already happened). */
    void completeAudit(UUID auditId, Set<String> contacted, Set<String> answered, int items, long bytes, String outcome) {
        try {
            auditor.complete(auditId, contacted, answered, items, bytes, outcome);
        } catch (RuntimeException e) {
            log.error("Tunnel access audit {} could not be completed ({})", auditId, e.getClass().getSimpleName());
        }
    }

    /** Sort key of a list item: (evaluatedAt desc, edgeAssessmentId desc), §7.5. */
    record SortKey(Instant evaluatedAt, String evaluatedAtText, String id) implements Comparable<SortKey> {

        /** Extracts the key; txn.related items carry it in {@code transaction}. */
        static SortKey of(TunnelOp op, JsonNode item) {
            JsonNode t = op == TunnelOp.TXN_RELATED ? item.path("transaction") : item;
            String at = t.path("evaluatedAt").asText();
            return new SortKey(Instant.parse(at), at, t.path("edgeAssessmentId").asText());
        }

        /** Larger = earlier in the result (newer first, then larger id). */
        @Override
        public int compareTo(SortKey o) {
            int c = evaluatedAt.compareTo(o.evaluatedAt);
            return c != 0 ? c : id.compareTo(o.id);
        }
    }
}
