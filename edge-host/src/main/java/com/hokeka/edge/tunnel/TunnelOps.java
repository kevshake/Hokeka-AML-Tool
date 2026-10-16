package com.hokeka.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.contract.ContractSchemas;
import com.hokeka.edge.ledger.LedgerProperties;
import com.hokeka.edge.residency.EdgeConfigDocument;
import com.hokeka.edge.residency.EdgeConfigStore;
import com.hokeka.edge.residency.RetentionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The op allowlist and handlers of the Edge Tunnel (EDGE_DATA_RESIDENCY_CONTRACT.md §7.5, §7.6),
 * enforced on the Edge whatever the Control Plane sends.
 *
 * <p>{@link #prepare} applies §7.4 step 5 and the request gates in this order: the request body
 * against {@code request.schema.json} (an unknown op is {@code OP_NOT_ALLOWED}, an oversize page
 * or id list {@code PAGE_LIMIT_EXCEEDED}, anything else {@code INVALID_PARAMS} with the JSON pointer
 * of the first violation), {@code TENANT_MISMATCH}, {@code SUPPORT_ACCESS_DISABLED} (platform actor
 * without PSP consent in the config, §9 double gating), {@code OP_NOT_ALLOWED} (effective set =
 * implemented ∩ {@code EDGE_TUNNEL_OPS} ∩ config {@code opsAllowed} ∩ {@code hello.ack.opsEffective}),
 * {@code BACKFILL_NOT_ALLOWED}. {@link #execute} / {@link #export} then run the op under the
 * request's deadline, apply the op limits (93-day search window, 366-day / 1 000 000-row export)
 * and validate every outgoing item against its item schema; an item that fails is dropped and
 * counted as a defect, never sent.
 */
public class TunnelOps {

    private static final Logger log = LoggerFactory.getLogger(TunnelOps.class);

    /** Ops this Edge implements (§7.6). */
    public static final List<String> IMPLEMENTED = List.of("txn.search", "txn.get", "txn.batchGet", "txn.related",
            "filing.extract", "export.stream", "ledger.backfill", "ledger.health");

    /** §7.6 limits. */
    static final Duration SEARCH_WINDOW_MAX = Duration.ofDays(93);
    static final Duration EXPORT_SPAN_MAX = Duration.ofDays(366);
    static final int EXPORT_CHUNK_ITEMS = 500;
    static final int EXPORT_DEFAULT_MAX_ROWS = 100_000;
    static final int EXPORT_DEFAULT_INITIAL_CREDIT = 4;
    /** txn.related depth-2 frontier: at most this many hop-1 rows are expanded (else DEPTH_LIMIT). */
    static final int RELATED_HOP1_CAP = 1_000;

    private static final String ROW_SCHEMA = "tunnel/ops/ledger.backfill.params.schema.json#/$defs/backfillRow";

    /**
     * What a request may do on this session.
     *
     * @param ownPspId     this node's PSP id
     * @param config       the config document in force, if any
     * @param effectiveOps implemented ∩ local ∩ config ∩ hello.ack ops
     */
    public record Gate(long ownPspId, Optional<EdgeConfigDocument> config, Set<String> effectiveOps) {
    }

    /**
     * A validated request.
     *
     * @param frameId request frame id (the corrId of every answer)
     * @param op      the op
     * @param params  op params
     * @param page    page object, or null
     * @param actor   the actor
     * @param control deadline and cancellation
     * @param backfillRows BATCH rows with their per-row schema verdict (ledger.backfill BATCH only)
     */
    public record Prepared(String frameId, String op, ObjectNode params, ObjectNode page, ObjectNode actor,
                           RequestControl control, List<BackfillImporter.BatchRow> backfillRows) {

        /** Whether the actor is Hokeka staff (support access, §9). */
        public boolean platformActor() {
            return "PLATFORM_USER".equals(actor.path("kind").asText());
        }
    }

    /**
     * A non-streaming op's answer.
     *
     * @param items            validated items
     * @param nextCursor       next-page cursor, or null
     * @param missing          ids not found (batchGet / filing.extract), or empty
     * @param incompleteReason SCAN_LIMIT / DEPTH_LIMIT / DEADLINE, or null
     * @param returnedIds      evaluation ids the items expose (for the access log)
     */
    public record Result(List<ObjectNode> items, String nextCursor, List<String> missing, String incompleteReason,
                         List<UUID> returnedIds) {
    }

    /** Receives export chunks; implemented by the tunnel client under credit (§7.8). */
    public interface ExportSink {
        /**
         * Sends one chunk of at most 500 items (the sink splits further to fit 1 MiB, each frame
         * within credit).
         *
         * @param last      whether this is the final chunk
         * @param totalRows rows streamed including this chunk
         */
        void chunk(List<ObjectNode> items, List<UUID> ids, boolean last, long totalRows) throws TunnelException;
    }

    private final TunnelFrameCodec codec;
    private final ContractSchemas schemas;
    private final LedgerReads reads;
    private final TunnelItems items;
    private final TunnelCursor cursors;
    private final BackfillImporter backfill;
    private final CtrAggregator ctr;
    private final LedgerHealth health;
    private final EdgeConfigStore config;
    private final LedgerProperties ledgerProperties;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final AtomicLong itemDefects = new AtomicLong();

    /**
     * @param codec            frame codec (body schemas)
     * @param schemas          contract schemas (item and row schemas)
     * @param reads            ledger read queries
     * @param items            row → item mapping
     * @param cursors          site cursor codec
     * @param backfill         ledger.backfill phases
     * @param ctr              CTR aggregation
     * @param health           ledger.health item
     * @param config           the config in force
     * @param ledgerProperties local retention floor
     * @param mapper           JSON mapper
     * @param clock            time source
     */
    public TunnelOps(TunnelFrameCodec codec, ContractSchemas schemas, LedgerReads reads, TunnelItems items,
                     TunnelCursor cursors, BackfillImporter backfill, CtrAggregator ctr, LedgerHealth health,
                     EdgeConfigStore config, LedgerProperties ledgerProperties, ObjectMapper mapper, Clock clock) {
        this.codec = codec;
        this.schemas = schemas;
        this.reads = reads;
        this.items = items;
        this.cursors = cursors;
        this.backfill = backfill;
        this.ctr = ctr;
        this.health = health;
        this.config = config;
        this.ledgerProperties = ledgerProperties;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Items dropped because they failed their item schema (a defect counter for status). */
    public long itemDefects() {
        return itemDefects.get();
    }

    // ── validation and gates ─────────────────────────────────────────────────────────────────────

    /**
     * Validates an opened request frame and applies the gates.
     *
     * @throws TunnelException with the §7.11 code the request is refused with
     */
    public Prepared prepare(TunnelFrameCodec.Opened frame, Gate gate) throws TunnelException {
        ObjectNode body = frame.body();
        List<BackfillImporter.BatchRow> rows = null;
        if (isBackfillBatch(body)) {
            rows = validateBackfillBatch(body);
        } else {
            List<ContractSchemas.Violation> v = codec.checkBody("request", body);
            if (!v.isEmpty()) {
                throw fromViolations(v);
            }
        }
        ObjectNode actor = (ObjectNode) body.get("actor");
        String op = body.get("op").asText();
        if (actor.get("pspId").asLong() != gate.ownPspId()) {
            throw new TunnelException(TunnelErrorCode.TENANT_MISMATCH, "actor belongs to another PSP");
        }
        if ("PLATFORM_USER".equals(actor.get("kind").asText())
                && (gate.config().isEmpty() || !gate.config().get().supportAccess())) {
            throw new TunnelException(TunnelErrorCode.SUPPORT_ACCESS_DISABLED,
                    "support access is not enabled by the PSP for this Edge");
        }
        if (!gate.effectiveOps().contains(op)) {
            throw new TunnelException(TunnelErrorCode.OP_NOT_ALLOWED, "op is not in this session's effective set");
        }
        if ("ledger.backfill".equals(op)
                && (gate.config().isEmpty() || !gate.config().get().tunnel().backfillAllowed())) {
            throw new TunnelException(TunnelErrorCode.BACKFILL_NOT_ALLOWED, "the Edge config does not allow backfill");
        }
        Instant deadline = frame.issuedAt().plusMillis(body.get("deadlineMs").asLong());
        ObjectNode page = body.get("page") instanceof ObjectNode p ? p : null;
        return new Prepared(frame.id(), op, (ObjectNode) body.get("params"), page, actor,
                new RequestControl(deadline, clock), rows);
    }

    /** Whether the body is a ledger.backfill BATCH (rows are validated one by one, §11 step 4). */
    private static boolean isBackfillBatch(ObjectNode body) {
        return "ledger.backfill".equals(body.path("op").asText())
                && "BATCH".equals(body.path("params").path("phase").asText())
                && body.path("params").path("rows").isArray();
    }

    /**
     * BATCH validation: the request is checked with each row replaced by a known-good stub (so
     * the envelope, actor, phase, id and seq are fully validated), then each row on its own
     * against the row schema. A row that fails is rejected as {@code SCHEMA_INVALID} instead of
     * failing the whole batch — unless its {@code legacyTransactionId} itself is unusable, since a
     * rejection must name it.
     */
    private List<BackfillImporter.BatchRow> validateBackfillBatch(ObjectNode body) throws TunnelException {
        ArrayNode original = (ArrayNode) body.get("params").get("rows");
        if (original.isEmpty() || original.size() > 500) {
            throw new TunnelException(TunnelErrorCode.INVALID_PARAMS, "a BATCH carries 1..500 rows", "/params/rows",
                    null);
        }
        ObjectNode stubbed = body.deepCopy();
        ArrayNode stubRows = ((ObjectNode) stubbed.get("params")).putArray("rows");
        stubRows.add(stubRow());
        List<ContractSchemas.Violation> v = codec.checkBody("request", stubbed);
        if (!v.isEmpty()) {
            throw fromViolations(v);
        }
        List<BackfillImporter.BatchRow> out = new ArrayList<>(original.size());
        for (int i = 0; i < original.size(); i++) {
            JsonNode row = original.get(i);
            List<ContractSchemas.Violation> rv = schemas.violations(ROW_SCHEMA, row);
            if (!rv.isEmpty()) {
                for (ContractSchemas.Violation x : rv) {
                    if (x.pointer().equals("/legacyTransactionId") || x.pointer().isEmpty()) {
                        throw new TunnelException(TunnelErrorCode.INVALID_PARAMS,
                                "a backfill row has no usable legacyTransactionId",
                                "/params/rows/" + i + "/legacyTransactionId", null);
                    }
                }
            }
            out.add(new BackfillImporter.BatchRow(row, rv.isEmpty()));
        }
        return out;
    }

    /** A minimal schema-valid backfill row (stands in for the real rows while the envelope is checked). */
    private ObjectNode stubRow() {
        ObjectNode r = mapper.createObjectNode();
        r.put("legacyTransactionId", "stub");
        r.put("edgeAssessmentId", "00000000-0000-4000-8000-000000000000");
        r.put("evaluatedAt", "2000-01-01T00:00:00Z");
        r.put("decision", "ALLOW");
        r.put("score", 0);
        r.putArray("triggeredRuleIds");
        r.putArray("ruleVersionIds");
        r.putArray("reasons");
        r.put("ctrRequired", false);
        r.putObject("features");
        return r;
    }

    /** Maps request-schema violations onto the §7.11 code the request is refused with. */
    static TunnelException fromViolations(List<ContractSchemas.Violation> violations) {
        for (ContractSchemas.Violation v : violations) {
            if (v.pointer().equals("/op")) {
                return new TunnelException(TunnelErrorCode.OP_NOT_ALLOWED, "op is not in the allowlist");
            }
        }
        for (ContractSchemas.Violation v : violations) {
            boolean pageLimit = v.pointer().equals("/page/limit") && "maximum".equals(v.keyword());
            boolean idList = v.pointer().matches("^/params/edgeAssessmentIds$") && "maxItems".equals(v.keyword());
            if (pageLimit || idList) {
                return new TunnelException(TunnelErrorCode.PAGE_LIMIT_EXCEEDED, "page or id list exceeds 200",
                        v.pointer(), null);
            }
        }
        ContractSchemas.Violation first = violations.get(0);
        return new TunnelException(TunnelErrorCode.INVALID_PARAMS, "request fails request.schema.json",
                first.pointer().isEmpty() ? null : first.pointer(), null);
    }

    // ── execution ────────────────────────────────────────────────────────────────────────────────

    /** Runs a non-streaming op. */
    public Result execute(Prepared p) throws TunnelException {
        return switch (p.op()) {
            case "txn.search" -> search(p);
            case "txn.get" -> get(p);
            case "txn.batchGet" -> batchGet(p);
            case "txn.related" -> related(p);
            case "filing.extract" -> filingExtract(p);
            case "ledger.backfill" -> backfill(p);
            case "ledger.health" -> health(p);
            default -> throw new TunnelException(TunnelErrorCode.OP_NOT_ALLOWED, "op is not in the allowlist");
        };
    }

    /** txn.search: keyset page of summaries in the 93-day window. */
    private Result search(Prepared p) throws TunnelException {
        LedgerReads.Filter filter = filter(p.params(), "/params");
        if (Duration.between(filter.from(), filter.to()).compareTo(SEARCH_WINDOW_MAX) > 0) {
            throw new TunnelException(TunnelErrorCode.INVALID_PARAMS, "search window exceeds 93 days", "/params/to", null);
        }
        int limit = p.page().get("limit").asInt();
        String q = TunnelCursor.queryHash(p.params());
        TunnelCursor.Key after = cursorKey(p, q);
        List<LedgerReads.Row> rows = reads.read(p.control(), s -> LedgerReads.search(s, filter, after, limit + 1, false));
        boolean more = rows.size() > limit;
        List<LedgerReads.Row> page = more ? rows.subList(0, limit) : rows;
        List<ObjectNode> out = checked("txn.search", page.stream().map(items::summary).toList());
        String next = more ? cursors.encode(q, page.get(page.size() - 1).evaluatedAt(), page.get(page.size() - 1).id())
                : null;
        return new Result(out, next, List.of(), null, ids(out));
    }

    /** txn.get: one detail item by id or txnRef, or NOT_FOUND. */
    private Result get(Prepared p) throws TunnelException {
        Optional<LedgerReads.Row> row;
        if (p.params().has("edgeAssessmentId")) {
            UUID id = UUID.fromString(p.params().get("edgeAssessmentId").asText());
            row = reads.read(p.control(), s -> Optional.ofNullable(LedgerReads.byIds(s, List.of(id)).get(id)));
        } else {
            String ref = p.params().get("txnRef").asText();
            row = reads.read(p.control(), s -> LedgerReads.byTxnRef(s, ref));
        }
        if (row.isEmpty()) {
            throw new TunnelException(TunnelErrorCode.NOT_FOUND, "no evaluation with that reference on this site");
        }
        List<ObjectNode> out = checked("txn.get", List.of(items.detail(row.get())));
        if (out.isEmpty()) {
            throw new TunnelException(TunnelErrorCode.INTERNAL, "the stored evaluation does not form a valid item");
        }
        return new Result(out, null, List.of(), null, ids(out));
    }

    /** txn.batchGet: detail items in request order; absent ids in missing[]. */
    private Result batchGet(Prepared p) throws TunnelException {
        List<UUID> ids = uuids(p.params().get("edgeAssessmentIds"));
        Map<UUID, LedgerReads.Row> found = reads.read(p.control(), s -> LedgerReads.byIds(s, ids));
        List<ObjectNode> raw = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (UUID id : ids) {
            LedgerReads.Row r = found.get(id);
            if (r == null) {
                missing.add(id.toString());
            } else {
                raw.add(items.detail(r));
            }
        }
        List<ObjectNode> out = checked("txn.batchGet", raw);
        return new Result(out, null, missing, null, ids(out));
    }

    /**
     * txn.related: evaluations linked to the anchor by the requested link types inside
     * ±{@code windowDays} of the anchor, hop 1 and (depth 2) hop 2, one item per evaluation with
     * the first matching link in {@code linkBy} order, never the key value. Depth-2 expansion
     * uses at most {@link #RELATED_HOP1_CAP} hop-1 rows; beyond that the answer is
     * {@code incomplete: DEPTH_LIMIT}.
     */
    private Result related(Prepared p) throws TunnelException {
        UUID anchorId = UUID.fromString(p.params().get("edgeAssessmentId").asText());
        List<String> linkBy = new ArrayList<>();
        p.params().get("linkBy").forEach(n -> linkBy.add(n.asText()));
        int windowDays = p.params().path("windowDays").asInt(30);
        int depth = p.params().path("depth").asInt(1);
        int limit = p.page().get("limit").asInt();
        String q = TunnelCursor.queryHash(p.params());
        TunnelCursor.Key after = cursorKey(p, q);
        RelatedPage page = reads.read(p.control(), s -> {
            LedgerReads.AnchorKeys anchor = LedgerReads.anchorKeys(s, anchorId).orElse(null);
            if (anchor == null) {
                return null;
            }
            Instant from = anchor.evaluatedAt().minus(Duration.ofDays(windowDays));
            Instant to = anchor.evaluatedAt().plus(Duration.ofDays(windowDays));
            Map<String, List<String>> anchorLinks = linkValues(linkBy, List.of(anchor));
            if (depth == 1) {
                List<LedgerReads.LinkedRow> rows = LedgerReads.linked(s, anchorLinks, from, to, List.of(anchorId), after,
                        limit + 1);
                List<RelatedItem> out = new ArrayList<>();
                for (LedgerReads.LinkedRow r : rows) {
                    out.add(new RelatedItem(r, linkOf(linkBy, r.keys(), List.of(anchor)), 1, anchorId));
                }
                return new RelatedPage(out, false);
            }
            List<LedgerReads.LinkedRow> hop1 = LedgerReads.linked(s, anchorLinks, from, to, List.of(anchorId), null,
                    RELATED_HOP1_CAP + 1);
            boolean truncated = hop1.size() > RELATED_HOP1_CAP;
            if (truncated) {
                hop1 = hop1.subList(0, RELATED_HOP1_CAP);
            }
            List<UUID> excluded = new ArrayList<>();
            excluded.add(anchorId);
            List<LedgerReads.AnchorKeys> hop1Keys = new ArrayList<>();
            for (LedgerReads.LinkedRow r : hop1) {
                excluded.add(r.row().id());
                hop1Keys.add(r.keys());
            }
            List<LedgerReads.LinkedRow> hop2 = LedgerReads.linked(s, linkValues(linkBy, hop1Keys), from, to, excluded,
                    after, limit + 1);
            List<RelatedItem> merged = new ArrayList<>();
            for (LedgerReads.LinkedRow r : hop1) {
                if (after == null || before(r.row(), after)) {
                    merged.add(new RelatedItem(r, linkOf(linkBy, r.keys(), List.of(anchor)), 1, anchorId));
                }
            }
            for (LedgerReads.LinkedRow r : hop2) {
                LinkMatch m = viaHop1(linkBy, r.keys(), hop1Keys);
                merged.add(new RelatedItem(r, m.link(), 2, m.via()));
            }
            merged.sort(Comparator.comparing((RelatedItem i) -> i.row().row().evaluatedAt())
                    .thenComparing(i -> i.row().row().id().toString()).reversed());
            return new RelatedPage(merged.size() > limit + 1 ? merged.subList(0, limit + 1) : merged, truncated);
        });
        if (page == null) {
            throw new TunnelException(TunnelErrorCode.NOT_FOUND, "anchor evaluation is not on this site");
        }
        boolean more = page.items().size() > limit;
        List<RelatedItem> shown = more ? page.items().subList(0, limit) : page.items();
        List<ObjectNode> raw = new ArrayList<>();
        for (RelatedItem i : shown) {
            ObjectNode n = mapper.createObjectNode();
            n.put("link", i.link());
            n.put("hop", i.hop());
            n.put("viaEdgeAssessmentId", i.via().toString());
            n.set("transaction", items.summary(i.row().row()));
            raw.add(n);
        }
        List<ObjectNode> out = checked("txn.related", raw);
        String next = null;
        if (more) {
            LedgerReads.Row last = shown.get(shown.size() - 1).row().row();
            next = cursors.encode(q, last.evaluatedAt(), last.id());
        }
        List<UUID> returned = new ArrayList<>();
        out.forEach(n -> returned.add(UUID.fromString(n.get("transaction").get("edgeAssessmentId").asText())));
        return new Result(out, next, List.of(), page.depthLimited() ? "DEPTH_LIMIT" : null, returned);
    }

    /** One related item before rendering. */
    private record RelatedItem(LedgerReads.LinkedRow row, String link, int hop, UUID via) {
    }

    /** A related page plus whether the depth-2 frontier was capped. */
    private record RelatedPage(List<RelatedItem> items, boolean depthLimited) {
    }

    /** A link type and the row it goes through. */
    private record LinkMatch(String link, UUID via) {
    }

    /** True when {@code row} sorts strictly after {@code key} in (evaluatedAt desc, id desc) order. */
    private static boolean before(LedgerReads.Row row, TunnelCursor.Key key) {
        int c = row.evaluatedAt().compareTo(key.evaluatedAt());
        return c < 0 || (c == 0 && row.id().toString().compareTo(key.id().toString()) < 0);
    }

    /** Key kind → distinct values of the given rows, for the requested link types. */
    static Map<String, List<String>> linkValues(List<String> linkBy, List<LedgerReads.AnchorKeys> rows) {
        Map<String, Set<String>> values = new LinkedHashMap<>();
        for (String link : linkBy) {
            for (LedgerReads.AnchorKeys k : rows) {
                switch (link) {
                    case "CARD" -> add(values, "CARD", k.panHash());
                    case "MERCHANT" -> add(values, "MERCHANT", k.merchantId());
                    case "DEVICE" -> add(values, "DEVICE", k.deviceId());
                    case "ACCOUNT" -> {
                        add(values, "ACCOUNT_ID", k.accountId());
                        add(values, "CUSTOMER_ID", k.customerId());
                    }
                    case "IP" -> add(values, "IP", k.ipAddress());
                    default -> throw new IllegalArgumentException("unknown link type " + link);
                }
            }
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        values.forEach((k, v) -> out.put(k, List.copyOf(v)));
        return out;
    }

    /** Adds a non-null value to a key kind's set. */
    private static void add(Map<String, Set<String>> values, String kind, String value) {
        if (value != null) {
            values.computeIfAbsent(kind, k -> new LinkedHashSet<>()).add(value);
        }
    }

    /** The first link type in {@code linkBy} order that {@code row} shares with any of {@code targets}. */
    static String linkOf(List<String> linkBy, LedgerReads.AnchorKeys row, List<LedgerReads.AnchorKeys> targets) {
        for (String link : linkBy) {
            for (LedgerReads.AnchorKeys t : targets) {
                if (shares(link, row, t)) {
                    return link;
                }
            }
        }
        return linkBy.get(0);
    }

    /** For a hop-2 row: the first link type and the newest hop-1 row it shares that key with. */
    private static LinkMatch viaHop1(List<String> linkBy, LedgerReads.AnchorKeys row, List<LedgerReads.AnchorKeys> hop1) {
        for (String link : linkBy) {
            for (LedgerReads.AnchorKeys h : hop1) {
                if (shares(link, row, h)) {
                    return new LinkMatch(link, h.id());
                }
            }
        }
        return new LinkMatch(linkBy.get(0), hop1.get(0).id());
    }

    /** Whether two rows share the key of one link type (ACCOUNT: account_id or customer_id). */
    private static boolean shares(String link, LedgerReads.AnchorKeys a, LedgerReads.AnchorKeys b) {
        return switch (link) {
            case "CARD" -> eq(a.panHash(), b.panHash());
            case "MERCHANT" -> eq(a.merchantId(), b.merchantId());
            case "DEVICE" -> eq(a.deviceId(), b.deviceId());
            case "ACCOUNT" -> eq(a.accountId(), b.accountId()) || eq(a.customerId(), b.customerId());
            case "IP" -> eq(a.ipAddress(), b.ipAddress());
            default -> false;
        };
    }

    /** Null-safe equality where null never matches. */
    private static boolean eq(String a, String b) {
        return a != null && a.equals(b);
    }

    /**
     * filing.extract: a TRANSACTION item per id found (absent ids in missing[]) and, for
     * {@code kind = CTR}, a CTR_AGGREGATE per anchor whose card has cash on its business day.
     */
    private Result filingExtract(Prepared p) throws TunnelException {
        List<UUID> ids = uuids(p.params().get("edgeAssessmentIds"));
        boolean ctrKind = "CTR".equals(p.params().get("kind").asText());
        EdgeConfigDocument doc = null;
        if (ctrKind) {
            doc = config.current().orElse(null);
            if (doc == null || !doc.ctr().enabled()) {
                throw new TunnelException(TunnelErrorCode.INVALID_PARAMS, "CTR is not enabled in the Edge config",
                        "/params/kind", null);
            }
        }
        EdgeConfigDocument cfg = doc;
        record Extract(Map<UUID, LedgerReads.Row> rows, Map<UUID, List<LedgerReads.CashTxn>> cash,
                       Map<UUID, LocalDate> days) {
        }
        Extract extract = reads.read(p.control(), s -> {
            Map<UUID, LedgerReads.Row> rows = LedgerReads.byIds(s, ids);
            Map<UUID, List<LedgerReads.CashTxn>> cash = new LinkedHashMap<>();
            Map<UUID, LocalDate> days = new LinkedHashMap<>();
            if (cfg != null) {
                for (UUID id : ids) {
                    LedgerReads.Row anchor = rows.get(id);
                    if (anchor == null) {
                        continue;
                    }
                    s.checkpoint();
                    LocalDate day = CtrAggregator.businessDate(anchor.evaluatedAt(), cfg.ctr());
                    List<LedgerReads.CashTxn> txns = LedgerReads.cashDay(s, id, CtrAggregator.dayStart(day, cfg.ctr()),
                            CtrAggregator.dayStart(day.plusDays(1), cfg.ctr()), List.copyOf(cfg.ctr().cashChannels()),
                            CtrAggregator.MAX_IDS + 1);
                    if (txns.size() > CtrAggregator.MAX_IDS) {
                        throw new TunnelException(TunnelErrorCode.INTERNAL,
                                "a CTR business day exceeds the 10000 transactions one aggregate can list");
                    }
                    cash.put(id, txns);
                    days.put(id, day);
                }
            }
            return new Extract(rows, cash, days);
        });
        List<ObjectNode> raw = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Set<UUID> returned = new LinkedHashSet<>();
        for (UUID id : ids) {
            LedgerReads.Row r = extract.rows().get(id);
            if (r == null) {
                missing.add(id.toString());
                continue;
            }
            ObjectNode t = mapper.createObjectNode();
            t.put("kind", "TRANSACTION");
            t.set("transaction", items.detail(r));
            raw.add(t);
        }
        if (cfg != null) {
            for (Map.Entry<UUID, List<LedgerReads.CashTxn>> e : extract.cash().entrySet()) {
                if (!e.getValue().isEmpty()) {
                    raw.add(ctr.aggregate(e.getKey(), extract.days().get(e.getKey()), e.getValue(), cfg));
                }
            }
        }
        List<ObjectNode> out = checked("filing.extract", raw);
        for (ObjectNode n : out) {
            if (n.has("transaction")) {
                returned.add(UUID.fromString(n.get("transaction").get("edgeAssessmentId").asText()));
            } else {
                n.get("edgeAssessmentIds").forEach(x -> returned.add(UUID.fromString(x.asText())));
            }
        }
        return new Result(out, null, missing, null, List.copyOf(returned));
    }

    /** ledger.backfill: one phase. */
    private Result backfill(Prepared p) throws TunnelException {
        ObjectNode item = switch (p.params().get("phase").asText()) {
            case "BEGIN" -> backfill.begin(p.params(), p.control());
            case "BATCH" -> backfill.batch(p.params(), p.backfillRows(), localCutoff(), p.control());
            case "COMMIT" -> backfill.commit(p.params(), p.control());
            case "ABORT" -> backfill.abort(p.params(), p.control());
            default -> throw new TunnelException(TunnelErrorCode.INVALID_PARAMS, "unknown backfill phase",
                    "/params/phase", null);
        };
        List<ObjectNode> out = checked("ledger.backfill", List.of(item));
        if (out.isEmpty()) {
            throw new TunnelException(TunnelErrorCode.INTERNAL, "backfill acknowledgement fails its schema");
        }
        return new Result(out, null, List.of(), null, List.of());
    }

    /** The Edge's own purge cutoff now, or null before any config (§8.2: nothing is past it then). */
    private Instant localCutoff() {
        OptionalInt years = RetentionPolicy.effectiveYears(config.current(), ledgerProperties.getRetentionMinYears());
        return years.isPresent() ? RetentionPolicy.cutoff(clock.instant(), years.getAsInt()) : null;
    }

    /** ledger.health: one item. */
    private Result health(Prepared p) throws TunnelException {
        List<ObjectNode> out = checked("ledger.health", List.of(health.item(reads, p.control())));
        if (out.isEmpty()) {
            throw new TunnelException(TunnelErrorCode.INTERNAL, "ledger.health item fails its schema");
        }
        return new Result(out, null, List.of(), null, List.of());
    }

    // ── export.stream ────────────────────────────────────────────────────────────────────────────

    /** {@code initialCredit} of an export request (§7.8: 1..16, default 4). */
    public static int initialCredit(Prepared p) {
        return p.params().path("initialCredit").asInt(EXPORT_DEFAULT_INITIAL_CREDIT);
    }

    /**
     * export.stream: streams summaries (projected to {@code columns}) in chunks of ≤ 500, ordered,
     * up to {@code maxRows}; the last chunk has {@code last: true} and {@code totalRows}. Each chunk
     * is read in its own short transaction (keyset continuation), so a long export never holds a
     * snapshot open for minutes.
     */
    public void export(Prepared p, ExportSink sink) throws TunnelException {
        ObjectNode params = p.params();
        LedgerReads.Filter filter = filter((ObjectNode) params.get("filter"), "/params/filter");
        if (Duration.between(filter.from(), filter.to()).compareTo(EXPORT_SPAN_MAX) > 0) {
            throw new TunnelException(TunnelErrorCode.PAGE_LIMIT_EXCEEDED, "export span exceeds 366 days",
                    "/params/filter/to", null);
        }
        long maxRows = params.path("maxRows").asLong(EXPORT_DEFAULT_MAX_ROWS);
        Set<String> columns = null;
        if (params.has("columns")) {
            columns = new LinkedHashSet<>();
            for (JsonNode c : params.get("columns")) {
                columns.add(c.asText());
            }
        }
        long total = 0;
        TunnelCursor.Key after = null;
        while (true) {
            p.control().checkpoint();
            int want = (int) Math.min(EXPORT_CHUNK_ITEMS, maxRows - total);
            TunnelCursor.Key from = after;
            List<LedgerReads.Row> rows = reads.read(p.control(), s -> LedgerReads.search(s, filter, from, want + 1, false));
            List<LedgerReads.Row> chunk = rows.size() > want ? rows.subList(0, want) : rows;
            total += chunk.size();
            boolean last = rows.size() <= want || total >= maxRows;
            List<ObjectNode> raw = new ArrayList<>(chunk.size());
            for (LedgerReads.Row r : chunk) {
                raw.add(items.summary(r, columns));
            }
            List<ObjectNode> out = checked("export.stream", raw);
            sink.chunk(out, ids(out), last, total);
            if (last) {
                return;
            }
            LedgerReads.Row tail = chunk.get(chunk.size() - 1);
            after = new TunnelCursor.Key(tail.evaluatedAt(), tail.id());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /** Parses a {@code searchFilter}; {@code from > to} is INVALID_PARAMS. */
    private static LedgerReads.Filter filter(ObjectNode f, String pointer) throws TunnelException {
        Instant from = Instant.parse(f.get("from").asText());
        Instant to = Instant.parse(f.get("to").asText());
        if (from.isAfter(to)) {
            throw new TunnelException(TunnelErrorCode.INVALID_PARAMS, "from is after to", pointer + "/from", null);
        }
        List<String> decisions = new ArrayList<>();
        if (f.has("decisions")) {
            f.get("decisions").forEach(d -> decisions.add(d.asText()));
        }
        return new LedgerReads.Filter(from, to, text(f, "merchantId"), decisions,
                f.has("ruleId") ? f.get("ruleId").asLong() : null, text(f, "txnRef"),
                f.has("minScore") ? f.get("minScore").asInt() : null,
                f.has("ctrRequired") ? f.get("ctrRequired").asBoolean() : null,
                f.has("sanctionsHit") ? f.get("sanctionsHit").asBoolean() : null);
    }

    /** The page cursor's key (null on the first page). */
    private TunnelCursor.Key cursorKey(Prepared p, String q) throws TunnelException {
        if (p.page() == null || !p.page().hasNonNull("cursor")) {
            return null;
        }
        return cursors.decode(p.page().get("cursor").asText(), q);
    }

    /**
     * Validates each outgoing item against the op's item schema; failing items are dropped and
     * counted, and only their violation pointers are logged (never the item).
     */
    List<ObjectNode> checked(String op, List<ObjectNode> raw) {
        String schema = "tunnel/ops/" + op + ".item.schema.json";
        List<ObjectNode> out = new ArrayList<>(raw.size());
        for (ObjectNode item : raw) {
            List<ContractSchemas.Violation> v = schemas.violations(schema, item);
            if (v.isEmpty()) {
                out.add(item);
            } else {
                itemDefects.incrementAndGet();
                List<String> pointers = v.stream().map(ContractSchemas.Violation::pointer).distinct().limit(5).toList();
                log.error("Tunnel {} item dropped: it fails {} at {} (defect; item not sent)", op, schema, pointers);
            }
        }
        return out;
    }

    /** The {@code edgeAssessmentId}s of summary/detail items. */
    private static List<UUID> ids(List<ObjectNode> out) {
        List<UUID> ids = new ArrayList<>(out.size());
        for (ObjectNode n : out) {
            JsonNode id = n.has("transaction") ? n.get("transaction").get("edgeAssessmentId") : n.get("edgeAssessmentId");
            if (id != null) {
                ids.add(UUID.fromString(id.asText()));
            }
        }
        return ids;
    }

    /** A JSON array of UUID strings as a list. */
    private static List<UUID> uuids(JsonNode array) {
        List<UUID> out = new ArrayList<>();
        array.forEach(n -> out.add(UUID.fromString(n.asText())));
        return out;
    }

    /** A textual member or null. */
    private static String text(JsonNode node, String name) {
        JsonNode v = node.get(name);
        return v == null || v.isNull() ? null : v.asText();
    }
}
