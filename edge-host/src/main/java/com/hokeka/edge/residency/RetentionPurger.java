package com.hokeka.edge.residency;

import com.hokeka.edge.ledger.EdgeLedger;
import com.hokeka.edge.ledger.LedgerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The daily Edge retention purge (EDGE_DATA_RESIDENCY_CONTRACT.md §8.2, §8.3).
 *
 * <ul>
 *   <li>Due once per day at {@code EDGE_PURGE_TIME} (default 02:00) site-local time
 *       ({@code EDGE_SITE_TIMEZONE}); a run of any outcome uses up that day, so a failed run is
 *       retried the next day.</li>
 *   <li>Only one node per site purges: the run holds the session advisory lock
 *       {@code hokeka.edge.purge:<ledger schema>} and re-checks {@code edge_purge_log} under it.
 *       Advisory locks are database-wide, so the key names the schema being purged: the nodes of
 *       one site (one schema) exclude each other, while ledgers of other sites in other schemas of
 *       the same database purge independently.</li>
 *   <li>Before any config document has been accepted nothing is purged (outcome
 *       {@code SKIPPED_NO_CONFIG}).</li>
 *   <li>{@code cutoff = now − effective retention years} (UTC); every monthly partition whose upper
 *       bound is ≤ cutoff is dropped, each in its own transaction that first copies the rows an
 *       active hold covers into {@code edge_held_evaluations} and removes the partition's
 *       {@code edge_txn_refs} entries (except for held rows). A partition is therefore either
 *       dropped whole with its held rows saved, or left untouched.</li>
 *   <li>Then held copies past the cutoff that no active hold covers any more are deleted, and
 *       {@code edge_access_log}, shipped {@code edge_outbox} rows, superseded {@code edge_config}
 *       history, finished backfill runs and {@code edge_purge_log} (keeping the last 400 runs) older
 *       than the cutoff are purged.</li>
 *   <li>Every run writes one {@code edge_purge_log} row.</li>
 * </ul>
 */
public class RetentionPurger {

    private static final Logger log = LoggerFactory.getLogger(RetentionPurger.class);

    /**
     * Session advisory lock key prefix; the key is this plus {@code current_schema()}, so there is
     * one purger per ledger schema (= per site), not one per database.
     */
    static final String LOCK_KEY_PREFIX = "hokeka.edge.purge:";
    /** The lock key expression, evaluated on the purging session (its search path is the ledger schema). */
    static final String LOCK_KEY_SQL = "hashtext(? || current_schema())";
    /** {@code edge_purge_log} keeps at least this many runs (§8.2). */
    static final int KEEP_PURGE_RUNS = 400;

    private static final Pattern PARTITION = Pattern.compile("^edge_evaluations_(\\d{4})_(\\d{2})$");

    /** Columns copied into {@code edge_held_evaluations} (all of edge_evaluations except legal_hold). */
    private static final String COPY_COLUMNS = "edge_assessment_id, txn_ref, site_id, node_id, evaluated_at, origin, "
            + "pan_hash, amount_cents, currency, merchant_id, mcc, country_code, channel, features, action, score, "
            + "triggered_rule_ids, rule_version_ids, reasons, bundle_version, bundle_hash, evaluator, latency_us, "
            + "fail_closed_code, ctr_required, sanctions_hit, sanctions_hits, typology_codes, backfill_id, "
            + "legacy_transaction_id";

    /**
     * What one run did.
     *
     * @param outcome            {@code OK}, {@code FAILED}, {@code SKIPPED_NO_CONFIG}, or (not logged)
     *                           {@code NOT_DUE} / {@code LOCKED} / {@code LEDGER_DOWN}
     * @param runAt              when the run started
     * @param cutoff             the cutoff used, or null
     * @param retentionYears     effective retention, or null
     * @param configVersion      config version the retention came from, or null
     * @param droppedPartitions  partitions dropped
     * @param heldRowsCopied     rows copied into edge_held_evaluations
     * @param heldRowsDeleted    released held copies deleted
     * @param durationMs         run time
     */
    public record Report(String outcome, Instant runAt, Instant cutoff, Integer retentionYears, Long configVersion,
                         List<String> droppedPartitions, long heldRowsCopied, long heldRowsDeleted, long durationMs) {
    }

    private final EdgeLedger ledger;
    private final LedgerProperties properties;
    private final EdgeConfigStore config;
    private final Clock clock;
    private final ZoneId siteZone;
    private final LocalTime purgeTime;
    private volatile Report last;

    /**
     * @param ledger     the site ledger
     * @param properties ledger settings (purge time, site zone, local floor, lock timeout)
     * @param config     the config document in force
     * @param clock      time source
     */
    public RetentionPurger(EdgeLedger ledger, LedgerProperties properties, EdgeConfigStore config, Clock clock) {
        this.ledger = ledger;
        this.properties = properties;
        this.config = config;
        this.clock = clock;
        this.siteZone = zone(properties.getSiteTimezone());
        this.purgeTime = time(properties.getPurgeTime());
    }

    /** {@code EDGE_SITE_TIMEZONE}, or the host zone when blank or invalid (logged by name). */
    private static ZoneId zone(String raw) {
        if (raw == null || raw.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(raw.trim());
        } catch (DateTimeException e) {
            log.warn("EDGE_SITE_TIMEZONE is not a valid IANA zone id; using the host zone {}", ZoneId.systemDefault());
            return ZoneId.systemDefault();
        }
    }

    /** {@code EDGE_PURGE_TIME} as {@code HH:mm}, or 02:00 when invalid (logged by name). */
    private static LocalTime time(String raw) {
        try {
            return LocalTime.parse(raw == null ? "02:00" : raw.trim());
        } catch (DateTimeException e) {
            log.warn("EDGE_PURGE_TIME is not HH:mm; using 02:00");
            return LocalTime.of(2, 0);
        }
    }

    /** The most recent report of this process (null before the first run). */
    public Report lastReport() {
        return last;
    }

    /** Start of the purge day containing {@code now}: today's purge time, or yesterday's if not reached. */
    Instant slotStart(Instant now) {
        ZonedDateTime local = now.atZone(siteZone);
        ZonedDateTime slot = local.toLocalDate().atTime(purgeTime).atZone(siteZone);
        if (slot.isAfter(local)) {
            slot = slot.minusDays(1);
        }
        return slot.toInstant();
    }

    /** When the next daily run becomes due. */
    public Instant nextPurgeAt(Instant now) {
        return slotStart(now).atZone(siteZone).plusDays(1).toInstant();
    }

    /** Scheduler entry: runs the purge when today's run is due. Never throws. */
    public void runIfDue() {
        try {
            Report r = run(clock.instant(), false);
            if (!"NOT_DUE".equals(r.outcome()) && !"LOCKED".equals(r.outcome())) {
                last = r;
            }
        } catch (RuntimeException e) {
            log.error("Retention purge crashed ({}); it will be retried at the next slot", e.getClass().getSimpleName());
        }
    }

    /**
     * Runs one purge now, regardless of the daily slot (still single-node via the advisory lock).
     * Used by tests and operators.
     */
    public Report purgeNow() {
        Report r = run(clock.instant(), true);
        last = r;
        return r;
    }

    /** The run: lock, due check, purge, purge-log row, unlock. */
    Report run(Instant now, boolean force) {
        if (!ledger.ready() || ledger.dataSource() == null) {
            return new Report("LEDGER_DOWN", now, null, null, null, List.of(), 0, 0, 0);
        }
        long started = System.nanoTime();
        try (Connection c = ledger.dataSource().getConnection()) {
            if (!tryLock(c)) {
                return new Report("LOCKED", now, null, null, null, List.of(), 0, 0, 0);
            }
            try {
                if (!force && ranSince(c, slotStart(now))) {
                    return new Report("NOT_DUE", now, null, null, null, List.of(), 0, 0, 0);
                }
                Optional<EdgeConfigDocument> doc = config.current();
                OptionalInt years = RetentionPolicy.effectiveYears(doc, properties.getRetentionMinYears());
                if (years.isEmpty()) {
                    Report skipped = new Report("SKIPPED_NO_CONFIG", now, null, null, null, List.of(), 0, 0,
                            elapsed(started));
                    writeLog(c, skipped);
                    log.info("Retention purge skipped: no Edge config document has been accepted yet (§8.2)");
                    return skipped;
                }
                Instant cutoff = RetentionPolicy.cutoff(now, years.getAsInt());
                Long version = doc.get().configVersion();
                List<String> dropped = new ArrayList<>();
                long copied = 0;
                long heldDeleted = 0;
                String outcome = "OK";
                try {
                    for (Partition p : partitionsToDrop(c, cutoff)) {
                        copied += dropPartition(c, p);
                        dropped.add(p.name());
                    }
                    heldDeleted = purgeReleasedHeldRows(c, cutoff);
                    purgeAuxiliary(c, cutoff);
                } catch (SQLException e) {
                    outcome = "FAILED";
                    log.error("Retention purge failed after dropping {} partition(s) ({}); retried at the next slot",
                            dropped.size(), EdgeLedger.safeMessage(e));
                    rollbackQuietly(c);
                }
                Report report = new Report(outcome, now, cutoff, years.getAsInt(), version, List.copyOf(dropped), copied,
                        heldDeleted, elapsed(started));
                writeLog(c, report);
                if ("OK".equals(outcome)) {
                    log.info("Retention purge OK: cutoff {} ({} y, config v{}), {} partition(s) dropped, {} held row(s) "
                            + "kept, {} released held row(s) deleted", cutoff, years.getAsInt(), version, dropped.size(),
                            copied, heldDeleted);
                }
                return report;
            } finally {
                unlock(c);
            }
        } catch (SQLException e) {
            log.error("Retention purge could not run ({}); retried at the next check", EdgeLedger.safeMessage(e));
            return new Report("FAILED", now, null, null, null, List.of(), 0, 0, elapsed(started));
        }
    }

    /** One monthly partition and its UTC bounds. */
    record Partition(String name, Instant lower, Instant upper) {
    }

    /** Partitions of edge_evaluations whose upper bound is ≤ cutoff, oldest first. */
    static List<Partition> partitionsToDrop(Connection c, Instant cutoff) throws SQLException {
        List<Partition> out = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT child.relname
                       FROM pg_inherits i
                       JOIN pg_class child ON child.oid = i.inhrelid
                       JOIN pg_class parent ON parent.oid = i.inhparent
                       JOIN pg_namespace n ON n.oid = parent.relnamespace
                      WHERE parent.relname = 'edge_evaluations' AND n.nspname = current_schema()
                     """)) {
            while (rs.next()) {
                Matcher m = PARTITION.matcher(rs.getString(1));
                if (!m.matches()) {
                    continue;
                }
                YearMonth month = YearMonth.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
                Instant lower = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
                Instant upper = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
                if (!upper.isAfter(cutoff)) {
                    out.add(new Partition(rs.getString(1), lower, upper));
                }
            }
        }
        out.sort(java.util.Comparator.comparing(Partition::lower));
        return out;
    }

    /**
     * Drops one partition in one transaction: block writes to it, copy its held rows, delete its
     * txn-ref entries (except held rows'), drop it. Returns the number of rows copied.
     */
    long dropPartition(Connection c, Partition p) throws SQLException {
        String table = "\"" + p.name() + "\"";
        c.setAutoCommit(false);
        try {
            long lockMs = Math.max(1, properties.getPurgeLockTimeout().toMillis());
            try (Statement st = c.createStatement()) {
                st.execute("SET LOCAL lock_timeout = '" + lockMs + "ms'");
                st.execute("LOCK TABLE " + table + " IN SHARE MODE");
            }
            long copied;
            try (Statement st = c.createStatement()) {
                copied = st.executeUpdate("""
                        WITH candidates AS (
                            SELECT e.edge_assessment_id
                              FROM %1$s e
                             WHERE e.edge_assessment_id IN (
                                   SELECT unnest(h.edge_assessment_ids) FROM edge_legal_holds h
                                    WHERE h.released_at IS NULL AND h.scope_kind = 'IDS')
                            UNION
                            SELECT e.edge_assessment_id
                              FROM %1$s e
                              JOIN edge_legal_holds h
                                ON h.released_at IS NULL AND h.scope_kind = 'MERCHANT_RANGE'
                               AND e.merchant_id = h.merchant_id
                               AND e.evaluated_at >= h.range_from AND e.evaluated_at <= h.range_to
                        )
                        INSERT INTO edge_held_evaluations (%2$s, legal_hold, hold_ids)
                        SELECT %3$s, true, edge_hold_ids(e.edge_assessment_id, e.merchant_id, e.evaluated_at)
                          FROM %1$s e
                         WHERE e.edge_assessment_id IN (SELECT edge_assessment_id FROM candidates)
                        ON CONFLICT (edge_assessment_id) DO UPDATE
                            SET hold_ids = EXCLUDED.hold_ids, legal_hold = true
                        """.formatted(table, COPY_COLUMNS, prefixed(COPY_COLUMNS)));
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    DELETE FROM edge_txn_refs r
                     WHERE r.evaluated_at >= ? AND r.evaluated_at < ?
                       AND NOT EXISTS (SELECT 1 FROM edge_held_evaluations h WHERE h.edge_assessment_id = r.edge_assessment_id)
                    """)) {
                ps.setObject(1, OffsetDateTime.ofInstant(p.lower(), ZoneOffset.UTC));
                ps.setObject(2, OffsetDateTime.ofInstant(p.upper(), ZoneOffset.UTC));
                ps.executeUpdate();
            }
            try (Statement st = c.createStatement()) {
                st.execute("DROP TABLE " + table);
            }
            c.commit();
            log.info("Retention purge dropped partition {} ({} held row(s) kept)", p.name(), copied);
            return copied;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }

    /** {@code e.}-prefixed column list for the copy's SELECT. */
    private static String prefixed(String columns) {
        StringBuilder sb = new StringBuilder();
        for (String col : columns.split(",")) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append("e.").append(col.trim());
        }
        return sb.toString();
    }

    /**
     * Deletes held copies past the cutoff that no active hold covers (§8.3), with their txn-ref
     * entries. Returns the number deleted.
     */
    static long purgeReleasedHeldRows(Connection c, Instant cutoff) throws SQLException {
        c.setAutoCommit(false);
        try {
            List<UUID> deleted = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("""
                    DELETE FROM edge_held_evaluations e
                     WHERE e.evaluated_at < ?
                       AND NOT edge_is_held(e.edge_assessment_id, e.merchant_id, e.evaluated_at)
                    RETURNING e.edge_assessment_id
                    """)) {
                ps.setObject(1, OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        deleted.add(rs.getObject(1, UUID.class));
                    }
                }
            }
            if (!deleted.isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM edge_txn_refs WHERE edge_assessment_id = ANY (?)")) {
                    ps.setArray(1, c.createArrayOf("uuid", deleted.toArray()));
                    ps.executeUpdate();
                }
            }
            c.commit();
            return deleted.size();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }

    /** Access log, shipped outbox, superseded config history, finished backfills, old purge-log rows. */
    static void purgeAuxiliary(Connection c, Instant cutoff) throws SQLException {
        OffsetDateTime t = OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC);
        String[] statements = {
                "DELETE FROM edge_access_log WHERE received_at < ?",
                "DELETE FROM edge_outbox WHERE shipped_at IS NOT NULL AND created_at < ?",
                "DELETE FROM edge_config WHERE accepted_at < ? "
                        + "AND config_version < (SELECT max(config_version) FROM edge_config)",
                "DELETE FROM edge_backfill_runs WHERE finished_at IS NOT NULL AND finished_at < ?",
                "DELETE FROM edge_purge_log WHERE run_at < ? AND run_id NOT IN "
                        + "(SELECT run_id FROM edge_purge_log ORDER BY run_id DESC LIMIT " + KEEP_PURGE_RUNS + ")"
        };
        for (String sql : statements) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, t);
                ps.executeUpdate();
            }
        }
    }

    /** Whether any run (of any outcome) is logged at or after {@code since}. */
    static boolean ranSince(Connection c, Instant since) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM edge_purge_log WHERE run_at >= ? LIMIT 1")) {
            ps.setObject(1, OffsetDateTime.ofInstant(since, ZoneOffset.UTC));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Appends the run's {@code edge_purge_log} row. */
    private void writeLog(Connection c, Report r) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO edge_purge_log (run_at, node_id, cutoff, retention_years, config_version, dropped_partitions,
                                            held_rows_copied, duration_ms, outcome)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setObject(1, OffsetDateTime.ofInstant(r.runAt(), ZoneOffset.UTC));
            ps.setString(2, ledger.nodeId());
            ps.setObject(3, r.cutoff() == null ? null : OffsetDateTime.ofInstant(r.cutoff(), ZoneOffset.UTC));
            if (r.retentionYears() == null) {
                ps.setNull(4, java.sql.Types.INTEGER);
            } else {
                ps.setInt(4, r.retentionYears());
            }
            if (r.configVersion() == null) {
                ps.setNull(5, java.sql.Types.BIGINT);
            } else {
                ps.setLong(5, r.configVersion());
            }
            ps.setArray(6, c.createArrayOf("text", r.droppedPartitions().toArray(new String[0])));
            ps.setLong(7, r.heldRowsCopied());
            ps.setLong(8, r.durationMs());
            ps.setString(9, r.outcome());
            ps.executeUpdate();
        }
    }

    /**
     * Session-level advisory lock on this ledger schema's purge key; false when another node of the
     * site (same schema) is purging. Ledgers in other schemas of the database do not contend.
     */
    private static boolean tryLock(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT pg_try_advisory_lock(" + LOCK_KEY_SQL + ")")) {
            ps.setString(1, LOCK_KEY_PREFIX);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    /** Releases every session advisory lock so the pooled connection goes back clean. */
    private static void unlock(Connection c) {
        try (Statement st = c.createStatement()) {
            st.execute("SELECT pg_advisory_unlock_all()");
        } catch (SQLException e) {
            log.warn("Could not release the purge advisory lock ({}); it ends with the session",
                    EdgeLedger.safeMessage(e));
        }
    }

    /** Rolls back a half-done transaction without masking the original error. */
    private static void rollbackQuietly(Connection c) {
        try {
            if (!c.getAutoCommit()) {
                c.rollback();
                c.setAutoCommit(true);
            }
        } catch (SQLException ignored) {
            // The original failure is already being reported.
        }
    }

    /** Milliseconds since {@code startedNanos}. */
    private static long elapsed(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }
}
