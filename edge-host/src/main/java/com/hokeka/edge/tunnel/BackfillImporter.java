package com.hokeka.edge.tunnel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hokeka.edge.contract.FeatureHygiene;
import com.hokeka.edge.contract.Jcs;
import com.hokeka.edge.ledger.EdgeLedger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * {@code ledger.backfill} (EDGE_DATA_RESIDENCY_CONTRACT.md §11): the one write op of the tunnel,
 * insert-only and idempotent on {@code edgeAssessmentId}.
 *
 * <ul>
 *   <li><b>BEGIN</b> opens a run ({@code edge_backfill_runs}); a re-sent identical BEGIN answers the
 *       run's state, a conflicting one {@code BACKFILL_REJECTED}.</li>
 *   <li><b>BATCH</b> {@code batchSeq} must not skip ahead of the run (a re-send of an earlier batch is
 *       fine); each row is hygienically inserted with {@code origin = BACKFILL},
 *       {@code evaluator = cloud-legacy}, or rejected with {@code SCHEMA_INVALID},
 *       {@code PAST_RETENTION}, {@code ID_CONFLICT} or {@code TXN_REF_CONFLICT}; an exact
 *       re-insert counts as a duplicate.</li>
 *   <li><b>COMMIT</b> compares the expected count and SHA-256 over the sorted, newline-joined
 *       legacy ids with what the Edge holds for the run ({@code VERIFIED} / {@code VERIFY_MISMATCH}).</li>
 *   <li><b>ABORT</b> closes the run; inserted rows stay (insert-only).</li>
 * </ul>
 *
 * <p>Acknowledgements carry counts, ids and codes only — never row content (§11). Each phase runs
 * in one transaction under a per-run advisory lock, so two nodes of a site never interleave.
 */
public class BackfillImporter {

    private static final Logger log = LoggerFactory.getLogger(BackfillImporter.class);

    private final EdgeLedger ledger;
    private final ObjectMapper mapper;
    private final String siteId;

    /**
     * @param ledger the site ledger (write pool)
     * @param mapper JSON mapper
     * @param siteId this node's site id (stamped on every imported row)
     */
    public BackfillImporter(EdgeLedger ledger, ObjectMapper mapper, String siteId) {
        this.ledger = ledger;
        this.mapper = mapper;
        this.siteId = siteId;
    }

    /** A pre-validated BATCH row with its index, or a row the schema already rejected. */
    public record BatchRow(JsonNode row, boolean schemaValid) {
    }

    /** BEGIN: opens (or re-confirms) a run. */
    public ObjectNode begin(ObjectNode params, RequestControl control) throws TunnelException {
        UUID id = UUID.fromString(params.get("backfillId").asText());
        Instant from = Instant.parse(params.get("sourceFrom").asText());
        Instant to = Instant.parse(params.get("sourceTo").asText());
        long expected = params.get("expectedRows").asLong();
        Instant cutoff = Instant.parse(params.get("retentionCutoff").asText());
        return inTransaction(control, c -> {
            lockRun(c, id);
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO edge_backfill_runs (backfill_id, state, source_from, source_to, expected_rows,
                                                    retention_cutoff, node_id)
                    VALUES (?, 'OPEN', ?, ?, ?, ?, ?)
                    ON CONFLICT (backfill_id) DO NOTHING
                    """)) {
                ps.setObject(1, id);
                ps.setObject(2, LedgerReads.utc(from));
                ps.setObject(3, LedgerReads.utc(to));
                ps.setLong(4, expected);
                ps.setObject(5, LedgerReads.utc(cutoff));
                ps.setString(6, ledger.nodeId());
                ps.executeUpdate();
            }
            Run run = loadRun(c, id);
            if (!run.from.equals(from) || !run.to.equals(to) || run.expectedRows != expected || !run.cutoff.equals(cutoff)) {
                throw new TunnelException(TunnelErrorCode.BACKFILL_REJECTED,
                        "backfillId already used for a run with different parameters");
            }
            ObjectNode item = item(id, "BEGIN", run.state);
            log.info("Backfill {} BEGIN: state {}, {} row(s) expected", id, run.state, expected);
            return item;
        });
    }

    /**
     * BATCH: inserts the rows.
     *
     * @param localCutoff the Edge's own purge cutoff, or null before any config (then only the
     *                    run's retentionCutoff applies)
     */
    public ObjectNode batch(ObjectNode params, List<BatchRow> rows, Instant localCutoff, RequestControl control)
            throws TunnelException {
        UUID id = UUID.fromString(params.get("backfillId").asText());
        long batchSeq = params.get("batchSeq").asLong();
        ensurePartitions(rows, control);
        return inTransaction(control, c -> {
            lockRun(c, id);
            Run run = loadRunOrReject(c, id);
            if (!"OPEN".equals(run.state)) {
                throw new TunnelException(TunnelErrorCode.BACKFILL_REJECTED, "backfill run is " + run.state);
            }
            if (batchSeq > run.nextBatchSeq) {
                throw new TunnelException(TunnelErrorCode.BACKFILL_REJECTED,
                        "batchSeq " + batchSeq + " is out of order (next expected " + run.nextBatchSeq + ")");
            }
            Instant cutoff = localCutoff == null || run.cutoff.isAfter(localCutoff) ? run.cutoff : localCutoff;
            long accepted = 0;
            long duplicates = 0;
            ArrayNode rejected = mapper.createArrayNode();
            for (BatchRow br : rows) {
                control.checkpoint();
                JsonNode row = br.row();
                String legacyId = row.get("legacyTransactionId").asText();
                String code = br.schemaValid() ? importRow(c, id, row, cutoff, control) : "SCHEMA_INVALID";
                switch (code) {
                    case "ACCEPTED" -> accepted++;
                    case "DUPLICATE" -> duplicates++;
                    default -> rejected.addObject().put("legacyTransactionId", legacyId).put("code", code);
                }
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE edge_backfill_runs
                       SET next_batch_seq = greatest(next_batch_seq, ?), accepted = accepted + ?,
                           duplicates = duplicates + ?, rejected = rejected + ?
                     WHERE backfill_id = ?
                    """)) {
                ps.setLong(1, batchSeq + 1);
                ps.setLong(2, accepted);
                ps.setLong(3, duplicates);
                ps.setLong(4, rejected.size());
                ps.setObject(5, id);
                ps.executeUpdate();
            }
            ObjectNode item = item(id, "BATCH", "OPEN");
            item.put("batchSeq", batchSeq);
            item.put("accepted", accepted);
            item.put("duplicates", duplicates);
            item.set("rejected", rejected);
            log.info("Backfill {} BATCH {}: {} accepted, {} duplicate(s), {} rejected", id, batchSeq, accepted,
                    duplicates, rejected.size());
            return item;
        });
    }

    /** COMMIT: verifies count and id digest. */
    public ObjectNode commit(ObjectNode params, RequestControl control) throws TunnelException {
        UUID id = UUID.fromString(params.get("backfillId").asText());
        long expectedRows = params.get("expectedRows").asLong();
        String expectedDigest = params.get("expectedIdDigest").asText();
        return inTransaction(control, c -> {
            lockRun(c, id);
            Run run = loadRunOrReject(c, id);
            if ("ABORTED".equals(run.state)) {
                throw new TunnelException(TunnelErrorCode.BACKFILL_REJECTED, "backfill run was aborted");
            }
            TreeSet<String> ids = new TreeSet<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT legacy_transaction_id FROM edge_backfill_rows WHERE backfill_id = ?")) {
                ps.setObject(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getString(1));
                    }
                }
            }
            String digest = Jcs.sha256Hex(String.join("\n", ids).getBytes(StandardCharsets.UTF_8));
            boolean verified = ids.size() == expectedRows && digest.equals(expectedDigest);
            String state = verified ? "VERIFIED" : "VERIFY_MISMATCH";
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE edge_backfill_runs
                       SET last_commit_state = ?,
                           state = CASE WHEN ? THEN 'VERIFIED' ELSE state END,
                           finished_at = CASE WHEN ? THEN coalesce(finished_at, now()) ELSE finished_at END
                     WHERE backfill_id = ?
                    """)) {
                ps.setString(1, state);
                ps.setBoolean(2, verified);
                ps.setBoolean(3, verified);
                ps.setObject(4, id);
                ps.executeUpdate();
            }
            ObjectNode item = item(id, "COMMIT", state);
            item.put("rowsTotal", ids.size());
            item.put("idDigest", digest);
            log.info("Backfill {} COMMIT: {} ({} row(s) held, {} expected)", id, state, ids.size(), expectedRows);
            return item;
        });
    }

    /** ABORT: closes an open run (rows stay: the op never deletes). */
    public ObjectNode abort(ObjectNode params, RequestControl control) throws TunnelException {
        UUID id = UUID.fromString(params.get("backfillId").asText());
        return inTransaction(control, c -> {
            lockRun(c, id);
            Run run = loadRunOrReject(c, id);
            if ("VERIFIED".equals(run.state)) {
                throw new TunnelException(TunnelErrorCode.BACKFILL_REJECTED, "backfill run is already verified");
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE edge_backfill_runs SET state = 'ABORTED', finished_at = coalesce(finished_at, now())
                     WHERE backfill_id = ?
                    """)) {
                ps.setObject(1, id);
                ps.executeUpdate();
            }
            log.info("Backfill {} ABORT ({})", id, params.path("reason").asText());
            return item(id, "ABORT", "ABORTED");
        });
    }

    /**
     * Inserts one schema-valid row, or names why not.
     *
     * @return {@code ACCEPTED}, {@code DUPLICATE}, or a rejection code
     */
    private String importRow(Connection c, UUID backfillId, JsonNode row, Instant cutoff, RequestControl control)
            throws SQLException, TunnelException {
        UUID id = UUID.fromString(row.get("edgeAssessmentId").asText());
        String legacyId = row.get("legacyTransactionId").asText();
        Instant evaluatedAt = Instant.parse(row.get("evaluatedAt").asText()).truncatedTo(ChronoUnit.MILLIS);
        if (evaluatedAt.isBefore(cutoff)) {
            return "PAST_RETENTION";
        }
        String txnRef = text(row, "txnRef");
        String features = features(row);
        String sanctions = row.has("sanctionsHits") ? row.get("sanctionsHits").toString() : "[]";
        Boolean same = sameAsStored(c, id, row, evaluatedAt, txnRef, features, sanctions, legacyId, control);
        if (same != null) {
            if (!same) {
                return "ID_CONFLICT";
            }
            recordRow(c, backfillId, legacyId, id);
            return "DUPLICATE";
        }
        if (txnRef != null && txnRefTaken(c, txnRef, id, control)) {
            return "TXN_REF_CONFLICT";
        }
        Savepoint sp = c.setSavepoint();
        try {
            insert(c, backfillId, id, row, evaluatedAt, txnRef, features, sanctions, legacyId, control);
            if (txnRef != null) {
                try (PreparedStatement ps = prepare(c, control, """
                        INSERT INTO edge_txn_refs (txn_ref, edge_assessment_id, evaluated_at) VALUES (?, ?, ?)
                        ON CONFLICT (txn_ref) DO NOTHING
                        """)) {
                    ps.setString(1, txnRef);
                    ps.setObject(2, id);
                    ps.setObject(3, LedgerReads.utc(evaluatedAt));
                    if (ps.executeUpdate() == 0) {
                        c.rollback(sp);
                        return "TXN_REF_CONFLICT";
                    }
                }
            }
            recordRow(c, backfillId, legacyId, id);
            c.releaseSavepoint(sp);
            return "ACCEPTED";
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                c.rollback(sp);
                return "ID_CONFLICT";
            }
            if ("23514".equals(e.getSQLState())) {
                c.rollback(sp);
                return "SCHEMA_INVALID";
            }
            throw e;
        } finally {
            control.unregister();
        }
    }

    /**
     * Compares a row with the stored row of the same id: null when none is stored, true when it is
     * the same import, false when the id is taken by different content.
     */
    private Boolean sameAsStored(Connection c, UUID id, JsonNode row, Instant evaluatedAt, String txnRef,
                                 String features, String sanctions, String legacyId, RequestControl control)
            throws SQLException, TunnelException {
        try (PreparedStatement ps = prepare(c, control, """
                SELECT e.origin = 'BACKFILL'
                       AND e.txn_ref IS NOT DISTINCT FROM ?
                       AND e.evaluated_at = ?
                       AND e.action = ? AND e.score = ?
                       AND e.triggered_rule_ids = ? AND e.rule_version_ids = ? AND e.reasons = ?
                       AND e.pan_hash IS NOT DISTINCT FROM ?
                       AND e.amount_cents IS NOT DISTINCT FROM ?
                       AND e.currency IS NOT DISTINCT FROM ? AND e.merchant_id IS NOT DISTINCT FROM ?
                       AND e.mcc IS NOT DISTINCT FROM ? AND e.country_code IS NOT DISTINCT FROM ?
                       AND e.channel IS NOT DISTINCT FROM ?
                       AND e.ctr_required = ?
                       AND e.features = ?::jsonb AND e.sanctions_hits = ?::jsonb
                       AND e.legacy_transaction_id IS NOT DISTINCT FROM ?
                  FROM edge_all_evaluations e WHERE e.edge_assessment_id = ? LIMIT 1
                """)) {
            int i = bindRowValues(c, ps, 1, row, evaluatedAt, txnRef);
            ps.setString(i++, features);
            ps.setString(i++, sanctions);
            ps.setString(i++, legacyId);
            ps.setObject(i, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBoolean(1) : null;
            }
        } finally {
            control.unregister();
        }
    }

    /** Whether {@code txnRef} already points at a different evaluation. */
    private static boolean txnRefTaken(Connection c, String txnRef, UUID id, RequestControl control)
            throws SQLException, TunnelException {
        try (PreparedStatement ps = prepare(c, control,
                "SELECT edge_assessment_id FROM edge_txn_refs WHERE txn_ref = ?")) {
            ps.setString(1, txnRef);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && !id.equals(rs.getObject(1, UUID.class));
            }
        } finally {
            control.unregister();
        }
    }

    /** Inserts the evaluation row (origin BACKFILL, evaluator cloud-legacy, no bundle). */
    private void insert(Connection c, UUID backfillId, UUID id, JsonNode row, Instant evaluatedAt, String txnRef,
                        String features, String sanctions, String legacyId, RequestControl control)
            throws SQLException, TunnelException {
        try (PreparedStatement ps = prepare(c, control, """
                INSERT INTO edge_evaluations (
                    txn_ref, evaluated_at, action, score, triggered_rule_ids, rule_version_ids, reasons, pan_hash,
                    amount_cents, currency, merchant_id, mcc, country_code, channel, ctr_required,
                    features, sanctions_hits, legacy_transaction_id, edge_assessment_id, site_id, node_id, origin,
                    bundle_version, evaluator, latency_us, sanctions_hit, backfill_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, 'BACKFILL', -1,
                        'cloud-legacy', 0, ?, ?)
                """)) {
            int i = bindRowValues(c, ps, 1, row, evaluatedAt, txnRef);
            ps.setString(i++, features);
            ps.setString(i++, sanctions);
            ps.setString(i++, legacyId);
            ps.setObject(i++, id);
            ps.setString(i++, siteId);
            ps.setString(i++, ledger.nodeId());
            ps.setBoolean(i++, row.has("sanctionsHits") && !row.get("sanctionsHits").isEmpty());
            ps.setObject(i, backfillId);
            ps.executeUpdate();
        }
    }

    /**
     * Binds, from {@code index}: txn_ref, evaluated_at, action, score, triggered_rule_ids,
     * rule_version_ids, reasons, pan_hash, amount_cents, currency, merchant_id, mcc, country_code,
     * channel, ctr_required. Returns the next index.
     */
    private static int bindRowValues(Connection c, PreparedStatement ps, int index, JsonNode row, Instant evaluatedAt,
                                     String txnRef) throws SQLException {
        int i = index;
        ps.setString(i++, txnRef);
        ps.setObject(i++, LedgerReads.utc(evaluatedAt));
        ps.setString(i++, row.get("decision").asText());
        ps.setInt(i++, row.get("score").asInt());
        ps.setArray(i++, c.createArrayOf("bigint", longs(row.get("triggeredRuleIds"))));
        ps.setArray(i++, c.createArrayOf("bigint", longs(row.get("ruleVersionIds"))));
        ps.setArray(i++, c.createArrayOf("text", strings(row.get("reasons"))));
        ps.setString(i++, text(row, "panHash"));
        if (row.hasNonNull("amountMinor")) {
            ps.setLong(i++, row.get("amountMinor").asLong());
        } else {
            ps.setNull(i++, java.sql.Types.BIGINT);
        }
        ps.setString(i++, text(row, "currency"));
        ps.setString(i++, text(row, "merchantId"));
        ps.setString(i++, text(row, "mcc"));
        ps.setString(i++, text(row, "countryCode"));
        ps.setString(i++, text(row, "channel"));
        ps.setBoolean(i++, row.get("ctrRequired").asBoolean());
        return i;
    }

    /** Records that the run holds this legacy row (for the COMMIT digest). */
    private static void recordRow(Connection c, UUID backfillId, String legacyId, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO edge_backfill_rows (backfill_id, legacy_transaction_id, edge_assessment_id) VALUES (?, ?, ?)
                ON CONFLICT (backfill_id, legacy_transaction_id) DO NOTHING
                """)) {
            ps.setObject(1, backfillId);
            ps.setString(2, legacyId);
            ps.setObject(3, id);
            ps.executeUpdate();
        }
    }

    /** The row's feature bag after §2.4 hygiene, as JSON text. */
    @SuppressWarnings("unchecked")
    private String features(JsonNode row) throws TunnelException {
        Map<String, Object> bag = row.has("features") ? mapper.convertValue(row.get("features"), Map.class) : Map.of();
        try {
            return mapper.writeValueAsString(FeatureHygiene.sanitize(bag));
        } catch (JsonProcessingException e) {
            throw new TunnelException(TunnelErrorCode.INTERNAL, "backfill features could not be serialised");
        }
    }

    /**
     * Creates the monthly partitions the batch's rows need, before the batch transaction (partition
     * DDL must not run inside it).
     */
    private void ensurePartitions(List<BatchRow> rows, RequestControl control) throws TunnelException {
        TreeSet<LocalDate> months = new TreeSet<>();
        for (BatchRow r : rows) {
            if (r.schemaValid()) {
                months.add(Instant.parse(r.row().get("evaluatedAt").asText()).atOffset(ZoneOffset.UTC).toLocalDate()
                        .withDayOfMonth(1));
            }
        }
        if (months.isEmpty()) {
            return;
        }
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement("SELECT edge_ensure_evaluation_partitions(?, 0)")) {
            for (LocalDate m : months) {
                control.checkpoint();
                ps.setObject(1, m);
                ps.executeQuery().close();
            }
        } catch (SQLException e) {
            throw LedgerReads.map(e, control);
        }
    }

    /** A run's state and parameters. */
    private record Run(String state, Instant from, Instant to, long expectedRows, Instant cutoff, long nextBatchSeq) {
    }

    /** Loads a run (null when absent). */
    private static Run loadRun(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT state, source_from, source_to, expected_rows, retention_cutoff, next_batch_seq
                  FROM edge_backfill_runs WHERE backfill_id = ?
                """)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Run(rs.getString(1), LedgerReads.instant(rs, 2), LedgerReads.instant(rs, 3), rs.getLong(4),
                        LedgerReads.instant(rs, 5), rs.getLong(6));
            }
        }
    }

    /** Loads a run or refuses an unknown {@code backfillId} (§11 step 4). */
    private static Run loadRunOrReject(Connection c, UUID id) throws SQLException, TunnelException {
        Run run = loadRun(c, id);
        if (run == null) {
            throw new TunnelException(TunnelErrorCode.BACKFILL_REJECTED, "unknown backfillId");
        }
        return run;
    }

    /** Serialises the phases of one run across the nodes of a site. */
    private static void lockRun(Connection c, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
            ps.setString(1, "hokeka.edge.backfill:" + id);
            ps.executeQuery().close();
        }
    }

    /** A statement under the request's statement timeout and cancel control. */
    private static PreparedStatement prepare(Connection c, RequestControl control, String sql)
            throws SQLException, TunnelException {
        long ms = control.statementTimeoutMs();
        try (Statement st = c.createStatement()) {
            st.execute("SET LOCAL statement_timeout = " + ms);
        }
        PreparedStatement ps = c.prepareStatement(sql);
        control.register(ps);
        return ps;
    }

    /** A phase body inside one transaction. */
    @FunctionalInterface
    private interface TxWork {
        ObjectNode run(Connection c) throws SQLException, TunnelException;
    }

    /** Runs one phase in a transaction, mapping database failures onto §7.11 codes. */
    private ObjectNode inTransaction(RequestControl control, TxWork work) throws TunnelException {
        control.checkpoint();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                ObjectNode out = work.run(c);
                c.commit();
                return out;
            } catch (SQLException | TunnelException | RuntimeException e) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                    // Connection broken; the original failure is reported.
                }
                throw e;
            } finally {
                control.unregister();
            }
        } catch (SQLException e) {
            throw LedgerReads.map(e, control);
        }
    }

    /** The backfill item {@code {backfillId, phase, state}}. */
    private ObjectNode item(UUID id, String phase, String state) {
        ObjectNode n = mapper.createObjectNode();
        n.put("backfillId", id.toString());
        n.put("phase", phase);
        n.put("state", state);
        return n;
    }

    /** A write-pool connection, or LEDGER_UNAVAILABLE. */
    private Connection connection() throws SQLException {
        if (!ledger.ready() || ledger.dataSource() == null) {
            throw new SQLException("Edge Ledger is not ready", "08000");
        }
        return ledger.dataSource().getConnection();
    }

    /** A textual member or null. */
    private static String text(JsonNode node, String name) {
        JsonNode v = node.get(name);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** An integer array member as Long[]. */
    private static Long[] longs(JsonNode array) {
        List<Long> out = new ArrayList<>();
        if (array != null) {
            array.forEach(n -> out.add(n.asLong()));
        }
        return out.toArray(new Long[0]);
    }

    /** A string array member as String[]. */
    private static String[] strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null) {
            array.forEach(n -> out.add(n.asText()));
        }
        return out.toArray(new String[0]);
    }
}
