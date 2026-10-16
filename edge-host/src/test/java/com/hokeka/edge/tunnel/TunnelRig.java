package com.hokeka.edge.tunnel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.channel.ReplayGuard;
import com.hokeka.edge.contract.ContractSchemas;
import com.hokeka.edge.ledger.EdgeLedger;
import com.hokeka.edge.ledger.LedgerEvaluation;
import com.hokeka.edge.ledger.LedgerProperties;
import com.hokeka.edge.ledger.LedgerTestDatabase;
import com.hokeka.edge.residency.EdgeConfigStore;
import com.hokeka.edge.residency.LegalHoldService;
import com.hokeka.edge.residency.RetentionPurger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Shared set-up for the ledger-backed R2/R5 tests: a fresh ledger schema, the config store, holds,
 * purger and the tunnel op stack exactly as {@code TunnelConfig} wires them, a movable clock, the
 * shared contract fixtures (read from {@code docs/contracts}, never copied), and row/request builders.
 */
public final class TunnelRig implements AutoCloseable {

    public static final Path CONTRACTS = Path.of(System.getProperty("contracts.dir", "../docs/contracts"));
    public static final String EDGE_ID = "acme-eu-1";
    public static final String SITE_ID = "acme-eu";
    public static final long PSP_ID = 12;

    /** A clock tests move by hand. */
    public static final class MovableClock extends Clock {
        private volatile Instant now;

        /** Starts at {@code start}. */
        public MovableClock(Instant start) {
            this.now = start;
        }

        /** Moves forward (or back) by {@code d}. */
        public void advance(Duration d) {
            now = now.plus(d);
        }

        /** Sets the time. */
        public void set(Instant i) {
            now = i;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    public final ObjectMapper mapper = new ObjectMapper();
    public final ContractSchemas schemas = ContractSchemas.fromDirectory(CONTRACTS);
    public final MovableClock clock = new MovableClock(Instant.now());
    public final String schema = LedgerTestDatabase.newSchema();
    public final LedgerProperties ledgerProperties = LedgerTestDatabase.properties(schema);
    public final EdgeLedger ledger;
    public final EdgeConfigStore config;
    public final LegalHoldService holds;
    public final RetentionPurger purger;
    public final TunnelFrameCodec codec;
    public final AccessLogWriter accessLog;
    public final LedgerHealth health;
    public final TunnelOps ops;

    /** Opens a ledger in a new schema and wires the stack (purge time 02:00 UTC). */
    public TunnelRig() {
        ledgerProperties.setSiteTimezone("UTC");
        ledger = LedgerTestDatabase.openLedger(ledgerProperties, EDGE_ID);
        config = new EdgeConfigStore(ledger, schemas, mapper, PSP_ID, EDGE_ID);
        holds = new LegalHoldService(ledger);
        purger = new RetentionPurger(ledger, ledgerProperties, config, clock);
        codec = new TunnelFrameCodec(mapper, new ReplayGuard(), schemas, EDGE_ID);
        accessLog = new AccessLogWriter(ledger);
        health = new LedgerHealth(ledger, holds, purger, config, ledgerProperties, new RollingCount(() -> 0L, clock),
                () -> 0, Path.of(System.getProperty("java.io.tmpdir")), SITE_ID, mapper, clock);
        ops = new TunnelOps(codec, schemas, new LedgerReads(ledger), new TunnelItems(mapper),
                new TunnelCursor(mapper, schemas), new BackfillImporter(ledger, mapper, SITE_ID), new CtrAggregator(mapper),
                health, config, ledgerProperties, mapper, clock);
    }

    /** A shared fixture from {@code docs/contracts/tunnel/examples}. */
    public ObjectNode fixture(String name) {
        try {
            return (ObjectNode) mapper.readTree(Files.readString(CONTRACTS.resolve("tunnel/examples").resolve(name)));
        } catch (IOException e) {
            throw new IllegalStateException("fixture " + name, e);
        }
    }

    /**
     * Offers {@code edge-config--default.json} with {@code configVersion} and edits applied, and
     * asserts it is accepted.
     */
    public ObjectNode acceptConfig(long version, Consumer<ObjectNode> edit) {
        ObjectNode doc = fixture("edge-config--default.json");
        doc.put("configVersion", version);
        if (edit != null) {
            edit.accept(doc);
        }
        EdgeConfigStore.OfferResult r = config.offer(doc, clock.instant());
        if (r.outcome() != EdgeConfigStore.Outcome.ACCEPTED) {
            throw new IllegalStateException("config not accepted: " + r.reason());
        }
        return doc;
    }

    /** Edit: allow every op, including ledger.backfill, and backfill itself. */
    public static void allowAllOps(ObjectNode doc) {
        ObjectNode t = (ObjectNode) doc.get("tunnel");
        ArrayNode ops = t.putArray("opsAllowed");
        TunnelOps.IMPLEMENTED.forEach(ops::add);
        t.put("backfillAllowed", true);
    }

    /** A row builder with sensible defaults. */
    public final class Row {
        UUID id = UUID.randomUUID();
        String txnRef;
        Instant at = clock.instant();
        String panHash;
        Long amount = 1000L;
        String currency = "USD";
        String merchant = "mrc_00421";
        String mcc = "5411";
        String country = "KE";
        String channel = "POS";
        Map<String, Object> features = new LinkedHashMap<>(Map.of("is_cross_border", false));
        String decision = "ALLOW";
        int score = 0;
        List<Long> rules = List.of();
        List<String> reasons = List.of();

        /** Sets the id. */
        public Row id(UUID v) {
            id = v;
            return this;
        }

        /** Sets the txn ref. */
        public Row ref(String v) {
            txnRef = v;
            return this;
        }

        /** Sets the evaluation time. */
        public Row at(Instant v) {
            at = v;
            return this;
        }

        /** Sets the card hash. */
        public Row card(String v) {
            panHash = v;
            return this;
        }

        /** Sets amount and currency. */
        public Row amount(long v, String ccy) {
            amount = v;
            currency = ccy;
            return this;
        }

        /** Sets the merchant. */
        public Row merchant(String v) {
            merchant = v;
            return this;
        }

        /** Sets the channel. */
        public Row channel(String v) {
            channel = v;
            return this;
        }

        /** Adds a feature. */
        public Row feature(String k, Object v) {
            features.put(k, v);
            return this;
        }

        /** Sets decision, score and triggered rules. */
        public Row decision(String d, int s, Long... ruleIds) {
            decision = d;
            score = s;
            rules = List.of(ruleIds);
            return this;
        }

        /** Sets free-text reasons. */
        public Row reasons(String... r) {
            reasons = List.of(r);
            return this;
        }

        /** Writes the row (creating its month's partition when needed) and returns its id. */
        public UUID insert() {
            LedgerEvaluation ev = new LedgerEvaluation(id, txnRef, SITE_ID, EDGE_ID, at, panHash, amount, currency,
                    merchant, mcc, country, channel, features, decision, score, rules, List.of(), reasons, 37,
                    "sha256:" + "a".repeat(64), "java", 120, null, false, List.of(), List.of());
            try {
                ledger.write(ev, null);
            } catch (EdgeLedger.LedgerWriteException e) {
                throw new IllegalStateException(e);
            }
            return id;
        }
    }

    /** A new row builder. */
    public Row row() {
        return new Row();
    }

    /** A default PSP actor. */
    public ObjectNode actor() {
        ObjectNode a = mapper.createObjectNode();
        a.put("kind", "PSP_USER");
        a.put("userId", "u-4512");
        a.put("role", "COMPLIANCE_OFFICER");
        a.put("pspId", PSP_ID);
        a.put("purpose", "CASE_INVESTIGATION");
        a.put("caseId", "CASE-2026-0412");
        return a;
    }

    /** A request body. {@code limit} null = no page. */
    public ObjectNode request(String op, JsonNode params, Integer limit, String cursor, long deadlineMs, ObjectNode actor) {
        ObjectNode r = mapper.createObjectNode();
        r.put("op", op);
        r.set("params", params);
        r.put("deadlineMs", deadlineMs);
        if (limit != null) {
            ObjectNode page = r.putObject("page");
            page.put("limit", limit);
            if (cursor != null) {
                page.put("cursor", cursor);
            }
        }
        r.set("actor", actor);
        r.put("auditId", UUID.randomUUID().toString());
        return r;
    }

    /** An opened request frame for {@code body}, issued now. */
    public TunnelFrameCodec.Opened opened(ObjectNode body) {
        return new TunnelFrameCodec.Opened("request", UUID.randomUUID().toString(), null,
                UUID.randomUUID().toString(), ReplayGuard.newNonce(), clock.instant(), body);
    }

    /** The gate with every implemented op effective and the current config. */
    public TunnelOps.Gate gate() {
        return new TunnelOps.Gate(PSP_ID, config.current(), new java.util.LinkedHashSet<>(TunnelOps.IMPLEMENTED));
    }

    /** prepare + execute. */
    public TunnelOps.Result run(ObjectNode body) throws TunnelException {
        return ops.execute(ops.prepare(opened(body), gate()));
    }

    /** prepare + export, collecting chunks. */
    public List<List<ObjectNode>> export(ObjectNode body, List<Long> totals) throws TunnelException {
        List<List<ObjectNode>> chunks = new ArrayList<>();
        TunnelOps.Prepared p = ops.prepare(opened(body), gate());
        ops.export(p, (items, ids, last, total) -> {
            chunks.add(items);
            totals.add(last ? total : -1L);
        });
        return chunks;
    }

    /** Drops the schema. */
    @Override
    public void close() {
        ledger.close();
        LedgerTestDatabase.dropSchema(schema);
    }
}
