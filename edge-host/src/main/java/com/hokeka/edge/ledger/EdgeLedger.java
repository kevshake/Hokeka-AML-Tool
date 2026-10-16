package com.hokeka.edge.ledger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * JDBC access to the Edge Ledger (EDGE_DATA_RESIDENCY_CONTRACT.md §1, §4.3).
 *
 * <p>Owns its connection pool and runs its own Flyway migrations ({@code classpath:db/ledger}) into
 * the configured schema. A database that is down at boot does not stop the node: the ledger stays
 * {@link State#DOWN} and {@link #initialize()} is retried by {@link LedgerScheduling} until it
 * succeeds; meanwhile every write fails fast and the evaluate path fails closed.
 *
 * <p>The evaluation, its txn_ref index entry and its outbox row are written by <b>one</b> SQL
 * statement (data-modifying CTEs), i.e. one local transaction and one network round trip — that is
 * what keeps the ledger inside the +2 ms p99 budget on {@code /edge/evaluate}.
 */
public class EdgeLedger implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EdgeLedger.class);

    /** Ledger lifecycle. */
    public enum State {
        /** No URL / user / password configured. */
        NOT_CONFIGURED,
        /** Configured but not (yet) migrated, or the last initialisation failed. */
        DOWN,
        /** Migrated and accepting writes. */
        OK
    }

    /** PostgreSQL SQLSTATE: unique_violation. */
    static final String UNIQUE_VIOLATION = "23505";
    /** PostgreSQL SQLSTATE: check_violation — raised for "no partition of relation found for row". */
    static final String CHECK_VIOLATION = "23514";

    private static final Pattern SCHEMA_NAME = Pattern.compile("^[a-z_][a-z0-9_]{0,62}$");

    /** Outbox kind of a decision record v2. */
    public static final String KIND_DECISION_RECORD_V2 = "DECISION_RECORD_V2";

    private static final String INSERT_EVALUATION = """
            WITH ev AS (
                INSERT INTO edge_evaluations (
                    edge_assessment_id, txn_ref, site_id, node_id, evaluated_at, origin,
                    pan_hash, amount_cents, currency, merchant_id, mcc, country_code, channel, features,
                    action, score, triggered_rule_ids, rule_version_ids, reasons, bundle_version, bundle_hash,
                    evaluator, latency_us, fail_closed_code, ctr_required, sanctions_hit, sanctions_hits,
                    typology_codes)
                VALUES (?, ?, ?, ?, ?, 'LIVE', ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        ?::jsonb, ?)
                RETURNING edge_assessment_id, evaluated_at, txn_ref, node_id
            ), ref AS (
                INSERT INTO edge_txn_refs (txn_ref, edge_assessment_id, evaluated_at)
                SELECT txn_ref, edge_assessment_id, evaluated_at FROM ev WHERE txn_ref IS NOT NULL
                ON CONFLICT (txn_ref) DO UPDATE
                    SET edge_assessment_id = EXCLUDED.edge_assessment_id,
                        evaluated_at = EXCLUDED.evaluated_at
                RETURNING 1
            )
            INSERT INTO edge_outbox (edge_assessment_id, node_id, kind, payload)
            SELECT edge_assessment_id, node_id, 'DECISION_RECORD_V2', ?::jsonb FROM ev WHERE ?
            """;

    private static final String RECONCILE_EVALUATION = """
            UPDATE edge_evaluations
               SET action = ?, score = ?, triggered_rule_ids = ?, rule_version_ids = ?, reasons = ?,
                   evaluator = ?, fail_closed_code = ?
             WHERE edge_assessment_id = ? AND evaluated_at = ?
            """;

    private static final String RECONCILE_OUTBOX = """
            UPDATE edge_outbox SET payload = ?::jsonb WHERE edge_assessment_id = ? AND shipped_at IS NULL
            """;

    private static final String INSERT_OUTBOX_IF_MISSING = """
            INSERT INTO edge_outbox (edge_assessment_id, node_id, kind, payload)
            VALUES (?, ?, 'DECISION_RECORD_V2', ?::jsonb)
            ON CONFLICT (edge_assessment_id) DO NOTHING
            """;

    private final LedgerProperties properties;
    private final ObjectMapper mapper;
    private final HikariDataSource dataSource;
    private final AtomicReference<State> state = new AtomicReference<>();
    private volatile String stateReason;
    private volatile int schemaVersion = 0;

    /**
     * Creates the ledger. Never connects here: the pool is lazy ({@code initializationFailTimeout=-1})
     * so construction cannot fail because the database is down.
     *
     * @param properties ledger configuration
     * @param mapper     JSON mapper for the jsonb columns
     * @param nodeId     this node's edge id (used only to label database sessions)
     */
    public EdgeLedger(LedgerProperties properties, ObjectMapper mapper, String nodeId) {
        this.properties = properties;
        this.mapper = mapper;
        List<String> missing = properties.missingSettings();
        if (!missing.isEmpty()) {
            this.dataSource = null;
            state.set(State.NOT_CONFIGURED);
            stateReason = "not configured: missing " + String.join(", ", missing);
            return;
        }
        String schema = properties.getSchema() == null ? "" : properties.getSchema().trim();
        if (!SCHEMA_NAME.matcher(schema).matches()) {
            this.dataSource = null;
            state.set(State.NOT_CONFIGURED);
            stateReason = "not configured: EDGE_LEDGER_SCHEMA must match " + SCHEMA_NAME.pattern();
            return;
        }
        this.dataSource = new HikariDataSource(hikariConfig(properties, schema, nodeId));
        state.set(State.DOWN);
        stateReason = "not initialised yet";
    }

    /** Pool settings: lazy start, short checkout and socket timeouts so a stalled DB fails fast. */
    private static HikariConfig hikariConfig(LedgerProperties p, String schema, String nodeId) {
        HikariConfig c = new HikariConfig();
        c.setPoolName("edge-ledger");
        c.setJdbcUrl(p.getUrl().trim());
        c.setUsername(p.getUsername());
        c.setPassword(p.getPassword());
        c.setSchema(schema);
        c.setMaximumPoolSize(Math.max(2, p.getPoolSize()));
        c.setMinimumIdle(Math.min(2, Math.max(1, p.getPoolSize())));
        c.setConnectionTimeout(Math.max(250L, p.getConnectionTimeout().toMillis()));
        c.setValidationTimeout(Math.max(250L, Math.min(p.getConnectionTimeout().toMillis(), 1000L)));
        c.setInitializationFailTimeout(-1);
        c.setAutoCommit(true);
        c.addDataSourceProperty("socketTimeout", Math.max(1L, p.getWriteTimeout().toSeconds()));
        c.addDataSourceProperty("connectTimeout", Math.max(1L, p.getConnectTimeout().toSeconds()));
        c.addDataSourceProperty("tcpKeepAlive", "true");
        c.addDataSourceProperty("ApplicationName", "hokeka-edge-" + (nodeId == null ? "" : nodeId));
        return c;
    }

    /** Current lifecycle state. */
    public State state() {
        return state.get();
    }

    /** True when writes can be attempted. */
    public boolean ready() {
        return state.get() == State.OK;
    }

    /** Log-safe reason for the current state ({@code ""} when OK). */
    public String stateReason() {
        return state.get() == State.OK ? "" : stateReason;
    }

    /** Flyway schema version applied (0 before initialisation). */
    public int schemaVersion() {
        return schemaVersion;
    }

    /** The configuration this ledger was built from. */
    public LedgerProperties properties() {
        return properties;
    }

    /**
     * Migrates the schema and creates the partition window. Idempotent; safe to call repeatedly
     * (it is the retry path for a database that was down at boot) and from every node of a site at
     * once (Flyway and the partition function both lock).
     *
     * @return true when the ledger is now {@link State#OK}
     */
    public synchronized boolean initialize() {
        if (dataSource == null) {
            return false;
        }
        if (state.get() == State.OK) {
            return true;
        }
        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(properties.getSchema().trim())
                    .defaultSchema(properties.getSchema().trim())
                    .createSchemas(true)
                    .table("edge_ledger_schema_history")
                    .locations("classpath:db/ledger")
                    .validateOnMigrate(true)
                    .cleanDisabled(true)
                    .load();
            flyway.migrate();
            MigrationInfo current = flyway.info().current();
            schemaVersion = current == null || current.getVersion() == null ? 0
                    : Integer.parseInt(current.getVersion().getVersion().split("\\.")[0]);
            ensurePartitions(Instant.now());
            state.set(State.OK);
            stateReason = "";
            log.info("Edge Ledger ready at {} (schema '{}', version {})", properties.redactedTarget(),
                    properties.getSchema(), schemaVersion);
            return true;
        } catch (RuntimeException | SQLException e) {
            state.set(State.DOWN);
            stateReason = "initialisation failed: " + safeMessage(e);
            log.error("Edge Ledger at {} is unavailable ({}) — evaluations fail closed until it is reachable",
                    properties.redactedTarget(), safeMessage(e));
            return false;
        }
    }

    /**
     * Creates the monthly partitions for the month of {@code at} and the configured number of months
     * after it (§8.2 needs whole-month partitions; creation runs ahead so writes never wait for DDL).
     *
     * @return number of partitions created
     */
    public int ensurePartitions(Instant at) throws SQLException {
        requireDataSource();
        LocalDate day = at.atOffset(ZoneOffset.UTC).toLocalDate();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT edge_ensure_evaluation_partitions(?, ?)")) {
            ps.setObject(1, day);
            ps.setInt(2, Math.max(0, Math.min(120, properties.getPartitionMonthsAhead())));
            try (ResultSet rs = ps.executeQuery()) {
                int created = rs.next() ? rs.getInt(1) : 0;
                if (created > 0) {
                    log.info("Edge Ledger: created {} monthly edge_evaluations partition(s) from {}", created,
                            day.withDayOfMonth(1));
                }
                return created;
            }
        }
    }

    /**
     * Writes the evaluation, its txn_ref index entry and (when {@code decisionRecord} is non-null)
     * its outbox row in one transaction. If the evaluation's month has no partition yet, the
     * partition is created and the write retried once.
     *
     * @param ev             the ledger row
     * @param decisionRecord the schema-valid decision record v2, or null when it failed validation
     *                       (the evaluation is then stored without an outbox row, §4.1)
     * @throws LedgerWriteException when the transaction did not commit; {@link LedgerWriteException#duplicate()}
     *                              tells a row that already exists from an outage
     */
    public void write(LedgerEvaluation ev, ObjectNode decisionRecord) throws LedgerWriteException {
        if (!ready()) {
            throw new LedgerWriteException("ledger " + state.get().name().toLowerCase(), false, null);
        }
        try {
            insert(ev, decisionRecord);
        } catch (SQLException e) {
            if (CHECK_VIOLATION.equals(e.getSQLState()) && isMissingPartition(e)) {
                try {
                    ensurePartitionFor(ev.evaluatedAt());
                    insert(ev, decisionRecord);
                    return;
                } catch (SQLException retry) {
                    throw classify(retry);
                }
            }
            throw classify(e);
        }
    }

    /**
     * Re-applies the decision of {@code ev} to a row that already exists (a retried write whose
     * first attempt committed although the client saw an error). The decision columns and the
     * unshipped outbox payload are brought in line with what the PSP was answered; a record that was
     * already shipped cannot be changed (the Control Plane keeps the first version, §4.2).
     *
     * @return true when the outbox row was still unshipped (or created) and now carries {@code decisionRecord}
     */
    public boolean reconcile(LedgerEvaluation ev, ObjectNode decisionRecord) throws LedgerWriteException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(RECONCILE_EVALUATION)) {
                    int i = 1;
                    ps.setString(i++, ev.decision());
                    ps.setInt(i++, ev.score());
                    ps.setArray(i++, bigintArray(c, ev.triggeredRuleIds()));
                    ps.setArray(i++, bigintArray(c, ev.ruleVersionIds()));
                    ps.setArray(i++, c.createArrayOf("text", ev.reasons().toArray(new String[0])));
                    ps.setString(i++, ev.evaluator());
                    ps.setString(i++, ev.failClosedCode());
                    ps.setObject(i++, ev.edgeAssessmentId());
                    ps.setObject(i, OffsetDateTime.ofInstant(ev.evaluatedAt(), ZoneOffset.UTC));
                    ps.executeUpdate();
                }
                boolean outboxCurrent = false;
                if (decisionRecord != null) {
                    String payload = mapper.writeValueAsString(decisionRecord);
                    try (PreparedStatement ps = c.prepareStatement(RECONCILE_OUTBOX)) {
                        ps.setString(1, payload);
                        ps.setObject(2, ev.edgeAssessmentId());
                        outboxCurrent = ps.executeUpdate() == 1;
                    }
                    if (!outboxCurrent) {
                        try (PreparedStatement ps = c.prepareStatement(INSERT_OUTBOX_IF_MISSING)) {
                            ps.setObject(1, ev.edgeAssessmentId());
                            ps.setString(2, ev.nodeId());
                            ps.setString(3, payload);
                            outboxCurrent = ps.executeUpdate() == 1;
                        }
                    }
                }
                c.commit();
                return outboxCurrent;
            } catch (SQLException | JsonProcessingException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw classify(e);
        } catch (JsonProcessingException e) {
            throw new LedgerWriteException("decision record could not be serialised", false, e);
        }
    }

    /** The single-statement insert of evaluation + txn ref + outbox. */
    private void insert(LedgerEvaluation ev, ObjectNode decisionRecord) throws SQLException {
        String features;
        String sanctions;
        String payload;
        try {
            features = mapper.writeValueAsString(ev.features());
            sanctions = mapper.writeValueAsString(ev.sanctionsHits());
            payload = decisionRecord == null ? null : mapper.writeValueAsString(decisionRecord);
        } catch (JsonProcessingException e) {
            throw new SQLException("ledger row could not be serialised", "22000", e);
        }
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(INSERT_EVALUATION)) {
            int i = 1;
            ps.setObject(i++, ev.edgeAssessmentId());
            ps.setString(i++, ev.txnRef());
            ps.setString(i++, ev.siteId());
            ps.setString(i++, ev.nodeId());
            ps.setObject(i++, OffsetDateTime.ofInstant(ev.evaluatedAt(), ZoneOffset.UTC));
            ps.setString(i++, ev.panHash());
            if (ev.amountCents() == null) {
                ps.setNull(i++, java.sql.Types.BIGINT);
            } else {
                ps.setLong(i++, ev.amountCents());
            }
            ps.setString(i++, ev.currency());
            ps.setString(i++, ev.merchantId());
            ps.setString(i++, ev.mcc());
            ps.setString(i++, ev.countryCode());
            ps.setString(i++, ev.channel());
            ps.setString(i++, features);
            ps.setString(i++, ev.decision());
            ps.setInt(i++, ev.score());
            ps.setArray(i++, bigintArray(c, ev.triggeredRuleIds()));
            ps.setArray(i++, bigintArray(c, ev.ruleVersionIds()));
            ps.setArray(i++, c.createArrayOf("text", ev.reasons().toArray(new String[0])));
            ps.setLong(i++, ev.bundleVersion());
            ps.setString(i++, ev.bundleHash());
            ps.setString(i++, ev.evaluator());
            ps.setLong(i++, ev.latencyUs());
            ps.setString(i++, ev.failClosedCode());
            ps.setBoolean(i++, ev.ctrRequired());
            ps.setBoolean(i++, ev.sanctionsHit());
            ps.setString(i++, sanctions);
            ps.setArray(i++, c.createArrayOf("text", ev.typologyCodes().toArray(new String[0])));
            ps.setString(i++, payload);
            ps.setBoolean(i, payload != null);
            ps.executeUpdate();
        }
    }

    /** Creates the partition for one month on demand (the write path's self-heal). */
    private void ensurePartitionFor(Instant at) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT edge_ensure_evaluation_partitions(?, 0)")) {
            ps.setObject(1, at.atOffset(ZoneOffset.UTC).toLocalDate());
            ps.executeQuery().close();
        }
        log.warn("Edge Ledger: created a missing partition on demand for {} — partition maintenance is behind",
                at.atOffset(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1));
    }

    private static boolean isMissingPartition(SQLException e) {
        String m = e.getMessage();
        return m != null && m.contains("no partition of relation");
    }

    private static Array bigintArray(Connection c, List<Long> values) throws SQLException {
        return c.createArrayOf("bigint", values.toArray(new Long[0]));
    }

    private static LedgerWriteException classify(SQLException e) {
        boolean duplicate = UNIQUE_VIOLATION.equals(e.getSQLState());
        return new LedgerWriteException(duplicate ? "row already exists" : "write failed (SQLSTATE "
                + e.getSQLState() + ")", duplicate, e);
    }

    // ── outbox ───────────────────────────────────────────────────────────────────────────────────

    /** One unshipped outbox row. */
    public record OutboxRow(long seq, UUID edgeAssessmentId, String payload) {
    }

    /**
     * The oldest unshipped decision records of one node, in {@code seq} order (§4.3).
     *
     * @param nodeId the node whose records to ship (a batch may only carry the sender's records, §4.2)
     * @param limit  maximum rows
     */
    public List<OutboxRow> pendingOutbox(String nodeId, int limit) throws SQLException {
        requireDataSource();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     SELECT seq, edge_assessment_id, payload::text
                       FROM edge_outbox
                      WHERE node_id = ? AND shipped_at IS NULL AND kind = 'DECISION_RECORD_V2'
                      ORDER BY seq
                      LIMIT ?
                     """)) {
            ps.setString(1, nodeId);
            ps.setInt(2, Math.max(1, limit));
            List<OutboxRow> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new OutboxRow(rs.getLong(1), rs.getObject(2, UUID.class), rs.getString(3)));
                }
            }
            return rows;
        }
    }

    /**
     * Marks rows delivered: {@code shipped_at = at}, {@code attempts + 1}; rejected rows also get
     * their acknowledgement code (they are not retried, §4.3).
     *
     * @param seqs        all rows of the acknowledged batch
     * @param rejectCodes reject code by seq, for the rejected subset
     * @param at          acknowledgement time
     */
    public void markShipped(Collection<Long> seqs, Map<Long, String> rejectCodes, Instant at) throws SQLException {
        if (seqs.isEmpty()) {
            return;
        }
        requireDataSource();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("""
                        UPDATE edge_outbox
                           SET shipped_at = ?, attempts = attempts + 1, last_attempt_at = ?, last_error = NULL
                         WHERE seq = ANY(?) AND shipped_at IS NULL
                        """)) {
                    OffsetDateTime t = OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
                    ps.setObject(1, t);
                    ps.setObject(2, t);
                    ps.setArray(3, c.createArrayOf("bigint", seqs.toArray(new Long[0])));
                    ps.executeUpdate();
                }
                if (!rejectCodes.isEmpty()) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE edge_outbox SET reject_code = ? WHERE seq = ?")) {
                        for (Map.Entry<Long, String> e : rejectCodes.entrySet()) {
                            ps.setString(1, e.getValue());
                            ps.setLong(2, e.getKey());
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /**
     * Records a failed delivery attempt (attempt count, time and a code — never a payload).
     *
     * @param seqs      rows of the failed batch
     * @param errorCode e.g. {@code HTTP_503}, {@code IO_ERROR}
     * @param at        attempt time
     */
    public void recordFailedAttempt(Collection<Long> seqs, String errorCode, Instant at) throws SQLException {
        if (seqs.isEmpty()) {
            return;
        }
        requireDataSource();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE edge_outbox SET attempts = attempts + 1, last_attempt_at = ?, last_error = ?
                      WHERE seq = ANY(?) AND shipped_at IS NULL
                     """)) {
            ps.setObject(1, OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
            ps.setString(2, errorCode);
            ps.setArray(3, c.createArrayOf("bigint", seqs.toArray(new Long[0])));
            ps.executeUpdate();
        }
    }

    /** Unshipped outbox rows of the whole site (all nodes). */
    public long outboxBacklog() throws SQLException {
        requireDataSource();
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM edge_outbox WHERE shipped_at IS NULL")) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    /** The pool, for tests and for later ledger consumers (purge, tunnel ops); null when not configured. */
    public DataSource dataSource() {
        return dataSource;
    }

    private void requireDataSource() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Edge Ledger is " + stateReason, "08000");
        }
    }

    /** Exception text without anything that could carry a credential or a row value. */
    public static String safeMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        if (t instanceof SQLException sql && sql.getSQLState() != null) {
            return t.getClass().getSimpleName() + " SQLSTATE " + sql.getSQLState();
        }
        return t.getClass().getSimpleName();
    }

    /** Closes the pool. */
    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    /** A ledger write that did not commit. */
    public static final class LedgerWriteException extends Exception {

        private final boolean duplicate;

        /**
         * @param message   log-safe description (no row values)
         * @param duplicate whether the failure is a unique violation (the row already exists)
         * @param cause     underlying exception, or null
         */
        public LedgerWriteException(String message, boolean duplicate, Throwable cause) {
            super(message, cause);
            this.duplicate = duplicate;
        }

        /** True when the row (or its outbox entry) already exists. */
        public boolean duplicate() {
            return duplicate;
        }
    }
}
