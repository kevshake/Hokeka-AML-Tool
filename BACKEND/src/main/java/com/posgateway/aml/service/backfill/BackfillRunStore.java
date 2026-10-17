package com.posgateway.aml.service.backfill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence of back-fill runs, batches and per-row outcomes (V270), over JDBC. State changes are
 * conditional updates ({@code WHERE state IN (...)}) so an operator's pause or abort and the worker
 * never overwrite each other. Every read and write names the PSP or the run (tenant isolation).
 */
@Component
public class BackfillRunStore {

    private static final String RUN_COLUMNS = """
            backfill_id, psp_id, site_id, default_site, site_ids_snapshot, state, phase, source_from, source_to,
            retention_cutoff, retention_years, expected_rows, cursor_source, cursor_ts, cursor_id, next_batch_seq,
            resend_round, batch_byte_budget, rows_accepted, rows_duplicate, expected_id_digest, expected_held_rows,
            edge_commit_state, edge_rows_total, edge_id_digest, sample_size, sample_mismatches, last_error_code,
            last_error_at, consecutive_failures, next_attempt_at, abort_reason, created_by, created_reason,
            created_at, begun_at, committed_at, verified_at, finished_at""";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    /** Uses the application's primary database. */
    public BackfillRunStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    // ── targets and routing ──────────────────────────────────────────────────────────────────────

    /** The PSP's recorded default target site. */
    public Optional<String> target(long pspId) {
        return jdbc.query("SELECT default_site_id FROM ledger_backfill_targets WHERE psp_id = ?",
                (rs, i) -> rs.getString(1), pspId).stream().findFirst();
    }

    /** Records (or replaces) the PSP's default target site with who chose it and why. */
    public void saveTarget(long pspId, String siteId, String chosenBy, String reason) {
        jdbc.update("""
                INSERT INTO ledger_backfill_targets (psp_id, default_site_id, chosen_by, chosen_reason, chosen_at)
                VALUES (?, ?, ?, ?, NOW())
                ON CONFLICT (psp_id) DO UPDATE SET default_site_id = EXCLUDED.default_site_id,
                    chosen_by = EXCLUDED.chosen_by, chosen_reason = EXCLUDED.chosen_reason, chosen_at = NOW()
                """, pspId, siteId, chosenBy, reason);
    }

    /** True when the PSP's merchant routing has already been frozen by an earlier start. */
    public boolean merchantSitesFrozen(long pspId) {
        Boolean any = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM ledger_backfill_runs WHERE psp_id = ?)",
                Boolean.class, pspId);
        return Boolean.TRUE.equals(any);
    }

    /**
     * Merchants of the PSP whose Edge decision records name exactly one site among {@code sites}
     * (the R15 routing rule's unambiguous case).
     */
    public Map<String, String> unambiguousMerchantSites(long pspId, Collection<String> sites) {
        Map<String, String> out = new LinkedHashMap<>();
        jdbc.query("""
                SELECT merchant_id, min(site_id) AS site_id
                  FROM assessments
                 WHERE psp_id = ? AND merchant_id IS NOT NULL AND site_id IS NOT NULL
                 GROUP BY merchant_id
                HAVING count(DISTINCT site_id) = 1
                """, rs -> {
            String site = rs.getString("site_id");
            if (sites.contains(site)) {
                out.put(rs.getString("merchant_id"), site);
            }
        }, pspId);
        return out;
    }

    /** Freezes merchant → site routes for the PSP (only merchants not yet frozen). */
    public void freezeMerchantSites(long pspId, Map<String, String> routes) {
        List<Object[]> args = new ArrayList<>();
        routes.forEach((merchant, site) -> args.add(new Object[]{pspId, merchant, site}));
        if (!args.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO ledger_backfill_merchant_sites (psp_id, merchant_id, site_id) VALUES (?, ?, ?)
                    ON CONFLICT (psp_id, merchant_id) DO NOTHING
                    """, args);
        }
    }

    /** Sites that frozen merchant routes of the PSP point at. */
    public List<String> routedSites(long pspId) {
        return jdbc.queryForList("SELECT DISTINCT site_id FROM ledger_backfill_merchant_sites WHERE psp_id = ? "
                + "ORDER BY site_id", String.class, pspId);
    }

    // ── runs ─────────────────────────────────────────────────────────────────────────────────────

    /** Inserts a new PENDING run. */
    public void insertRun(UUID backfillId, long pspId, String siteId, boolean defaultSite, List<String> sites,
                          Instant sourceFrom, Instant sourceTo, int retentionYears, int batchByteBudget,
                          String createdBy, String reason) {
        jdbc.update("""
                INSERT INTO ledger_backfill_runs (backfill_id, psp_id, site_id, default_site, site_ids_snapshot, state,
                    phase, source_from, source_to, retention_cutoff, retention_years, expected_rows,
                    cursor_source, batch_byte_budget, created_by, created_reason, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?::jsonb, 'PENDING', 'BEGIN', ?, ?, ?, ?, 0, 'TXN', ?, ?, ?, NOW(), NOW())
                """, backfillId, pspId, siteId, defaultSite, json(sites), utc(sourceFrom),
                utc(sourceTo), utc(sourceFrom), retentionYears, batchByteBudget, createdBy, reason);
    }

    /** Sets the run's expected row count (computed once, right after insert, from the frozen window). */
    public void setExpectedRows(UUID backfillId, long expectedRows) {
        jdbc.update("UPDATE ledger_backfill_runs SET expected_rows = ?, updated_at = NOW() WHERE backfill_id = ?",
                expectedRows, backfillId);
    }

    /** A run of the PSP, or empty (another PSP's run id resolves to nothing). */
    public Optional<BackfillRun> find(long pspId, UUID backfillId) {
        return jdbc.query("SELECT " + RUN_COLUMNS + " FROM ledger_backfill_runs WHERE backfill_id = ? AND psp_id = ?",
                this::run, backfillId, pspId).stream().findFirst();
    }

    /** A run by id (worker use; the run row carries its PSP). */
    public Optional<BackfillRun> find(UUID backfillId) {
        return jdbc.query("SELECT " + RUN_COLUMNS + " FROM ledger_backfill_runs WHERE backfill_id = ?",
                this::run, backfillId).stream().findFirst();
    }

    /** Every run of the PSP, newest first. */
    public List<BackfillRun> runs(long pspId) {
        return jdbc.query("SELECT " + RUN_COLUMNS + " FROM ledger_backfill_runs WHERE psp_id = ? "
                + "ORDER BY created_at DESC, backfill_id", this::run, pspId);
    }

    /** True when the PSP has a run that is not VERIFIED or ABORTED. */
    public boolean hasUnfinishedRun(long pspId) {
        Boolean any = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM ledger_backfill_runs WHERE psp_id = ? "
                + "AND state NOT IN ('VERIFIED', 'ABORTED'))", Boolean.class, pspId);
        return Boolean.TRUE.equals(any);
    }

    /**
     * Claims one due run for {@code owner} (a lease of {@code leaseMs}), skipping runs another
     * instance holds. Postgres row locks make two instances never claim the same run.
     */
    public Optional<UUID> claimDue(String owner, long leaseMs) {
        List<UUID> ids = jdbc.query("""
                UPDATE ledger_backfill_runs SET lease_owner = ?, lease_until = NOW() + (? * INTERVAL '1 millisecond')
                 WHERE backfill_id = (
                       SELECT backfill_id FROM ledger_backfill_runs
                        WHERE state IN ('PENDING', 'RUNNING', 'ABORTING')
                          AND (next_attempt_at IS NULL OR next_attempt_at <= NOW())
                          AND (lease_until IS NULL OR lease_until < NOW() OR lease_owner = ?)
                        ORDER BY created_at, backfill_id
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED)
                RETURNING backfill_id
                """, (rs, i) -> (UUID) rs.getObject(1), owner, leaseMs, owner);
        return ids.stream().findFirst();
    }

    /** Extends the lease; false when another owner took the run over. */
    public boolean renewLease(UUID backfillId, String owner, long leaseMs) {
        return jdbc.update("UPDATE ledger_backfill_runs SET lease_until = NOW() + (? * INTERVAL '1 millisecond') "
                + "WHERE backfill_id = ? AND lease_owner = ?", leaseMs, backfillId, owner) == 1;
    }

    /** Releases the lease. */
    public void releaseLease(UUID backfillId, String owner) {
        jdbc.update("UPDATE ledger_backfill_runs SET lease_owner = NULL, lease_until = NULL "
                + "WHERE backfill_id = ? AND lease_owner = ?", backfillId, owner);
    }

    /**
     * Moves a run of the PSP from one of {@code from} to {@code to}; false when the run is not in one
     * of those states (or not of that PSP).
     */
    public boolean transition(long pspId, UUID backfillId, Collection<BackfillState> from, BackfillState to,
                              String abortReason) {
        String in = String.join(",", from.stream().map(s -> "'" + s.name() + "'").toList());
        return jdbc.update("UPDATE ledger_backfill_runs SET state = ?, abort_reason = COALESCE(?, abort_reason), "
                        + "next_attempt_at = NULL, consecutive_failures = CASE WHEN ? = 'RUNNING' THEN 0 "
                        + "ELSE consecutive_failures END, updated_at = NOW() "
                        + "WHERE backfill_id = ? AND psp_id = ? AND state IN (" + in + ")",
                to.name(), abortReason, to.name(), backfillId, pspId) == 1;
    }

    /** BEGIN acknowledged: RUNNING, phase BATCH. */
    public void markBegun(UUID backfillId) {
        jdbc.update("""
                UPDATE ledger_backfill_runs SET state = CASE WHEN state = 'PENDING' THEN 'RUNNING' ELSE state END,
                    phase = 'BATCH', begun_at = COALESCE(begun_at, NOW()), consecutive_failures = 0,
                    last_error_code = NULL, updated_at = NOW()
                 WHERE backfill_id = ?
                """, backfillId);
    }

    /** Moves the cursor to the next source (the current one is exhausted) or to COMMIT after the last. */
    public void advanceSource(UUID backfillId, LegacySource next) {
        if (next == null) {
            jdbc.update("UPDATE ledger_backfill_runs SET phase = 'COMMIT', updated_at = NOW() WHERE backfill_id = ?",
                    backfillId);
        } else {
            jdbc.update("UPDATE ledger_backfill_runs SET cursor_source = ?, cursor_ts = NULL, cursor_id = NULL, "
                    + "updated_at = NOW() WHERE backfill_id = ?", next.name(), backfillId);
        }
    }

    /** One row of a batch acknowledgement: {@code rejectCode} is null when the Edge holds the row. */
    public record RowOutcome(LegacySource source, long sourceId, String legacyTransactionId, UUID edgeAssessmentId,
                             boolean minted, String rejectCode) {}

    /**
     * Records one BATCH acknowledgement and advances the cursor past the batch. Must run inside the
     * caller's transaction together with the decision-record and reference updates.
     *
     * @return true when this was the batch's first acknowledgement (its counts were added)
     */
    public boolean recordBatch(BackfillRun run, long batchSeq, List<RowOutcome> rows, int accepted, int duplicates,
                               LegacySource source, LegacyRowReader.Position last) {
        int rejected = (int) rows.stream().filter(r -> r.rejectCode() != null).count();
        Boolean first = jdbc.queryForObject("""
                INSERT INTO ledger_backfill_batches (backfill_id, batch_seq, row_count, accepted, duplicates, rejected)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (backfill_id, batch_seq) DO UPDATE SET send_count = ledger_backfill_batches.send_count + 1,
                    last_acked_at = NOW()
                RETURNING (xmax = 0)
                """, Boolean.class, run.backfillId(), batchSeq, rows.size(), accepted, duplicates, rejected);
        List<Object[]> args = new ArrayList<>(rows.size());
        for (RowOutcome r : rows) {
            args.add(new Object[]{run.backfillId(), run.pspId(), r.source().name(), r.sourceId(), r.legacyTransactionId(),
                    r.edgeAssessmentId(), r.minted(), batchSeq, r.rejectCode() == null ? "HELD" : "REJECTED",
                    r.rejectCode()});
        }
        jdbc.batchUpdate("""
                INSERT INTO ledger_backfill_rows (backfill_id, psp_id, source, source_id, legacy_transaction_id,
                    edge_assessment_id, minted, batch_seq, outcome, reject_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (backfill_id, legacy_transaction_id) DO UPDATE SET
                    batch_seq = EXCLUDED.batch_seq, outcome = EXCLUDED.outcome, reject_code = EXCLUDED.reject_code,
                    resolution = CASE WHEN EXCLUDED.outcome = 'HELD' THEN NULL ELSE ledger_backfill_rows.resolution END,
                    resolution_reason = CASE WHEN EXCLUDED.outcome = 'HELD' THEN NULL
                                             ELSE ledger_backfill_rows.resolution_reason END,
                    resolved_by = CASE WHEN EXCLUDED.outcome = 'HELD' THEN NULL ELSE ledger_backfill_rows.resolved_by END,
                    resolved_at = CASE WHEN EXCLUDED.outcome = 'HELD' THEN NULL ELSE ledger_backfill_rows.resolved_at END,
                    recorded_at = NOW()
                """, args);
        boolean isFirst = Boolean.TRUE.equals(first);
        jdbc.update("""
                UPDATE ledger_backfill_runs SET cursor_source = ?, cursor_ts = ?, cursor_id = ?,
                    next_batch_seq = ?, rows_accepted = rows_accepted + ?, rows_duplicate = rows_duplicate + ?,
                    consecutive_failures = 0, last_error_code = NULL, next_attempt_at = NULL, updated_at = NOW()
                 WHERE backfill_id = ?
                """, source.name(), last.ts(), last.id(), batchSeq + 1, isFirst ? accepted : 0,
                isFirst ? duplicates : 0, run.backfillId());
        return isFirst;
    }

    /** Held-row count and the §11 digest (SHA-256 hex over the sorted, newline-joined legacy ids). */
    public record HeldDigest(long rows, String digest) {}

    /**
     * The count and digest of the rows the Edge holds for the run, sorted byte-wise
     * ({@code COLLATE "C"}: identical to Java's {@code String} order for these ASCII ids, which is
     * how the Edge sorts), streamed so no id list is held in memory.
     */
    public HeldDigest heldDigest(UUID backfillId) {
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing from this JVM", e);
        }
        long[] count = {0};
        jdbc.query("SELECT legacy_transaction_id FROM ledger_backfill_rows WHERE backfill_id = ? AND outcome = 'HELD' "
                + "ORDER BY legacy_transaction_id COLLATE \"C\"", rs -> {
            if (count[0] > 0) {
                sha.update((byte) '\n');
            }
            sha.update(rs.getString(1).getBytes(StandardCharsets.UTF_8));
            count[0]++;
        }, backfillId);
        return new HeldDigest(count[0], HexFormat.of().formatHex(sha.digest()));
    }

    /** Records a COMMIT answer. VERIFIED moves to phase VERIFY; a mismatch is handled by the caller. */
    public void recordCommit(UUID backfillId, HeldDigest expected, String edgeState, long edgeRows, String edgeDigest) {
        jdbc.update("""
                UPDATE ledger_backfill_runs SET expected_held_rows = ?, expected_id_digest = ?, edge_commit_state = ?,
                    edge_rows_total = ?, edge_id_digest = ?, committed_at = NOW(),
                    phase = CASE WHEN ? = 'VERIFIED' THEN 'VERIFY' ELSE phase END,
                    consecutive_failures = 0, last_error_code = NULL, updated_at = NOW()
                 WHERE backfill_id = ?
                """, expected.rows(), expected.digest(), edgeState, edgeRows, edgeDigest, edgeState, backfillId);
    }

    /** VERIFY_MISMATCH: re-send every batch from the start of the window (idempotent on the Edge). */
    public void restartRound(UUID backfillId) {
        jdbc.update("""
                UPDATE ledger_backfill_runs SET phase = 'BATCH', cursor_source = 'TXN', cursor_ts = NULL,
                    cursor_id = NULL, next_batch_seq = 0, resend_round = resend_round + 1, updated_at = NOW()
                 WHERE backfill_id = ?
                """, backfillId);
    }

    /** The verification sample result; VERIFIED only when nothing mismatched. */
    public void recordVerification(UUID backfillId, int sampleSize, int mismatches, boolean verified) {
        jdbc.update("""
                UPDATE ledger_backfill_runs SET sample_size = ?, sample_mismatches = ?,
                    state = CASE WHEN ? THEN 'VERIFIED' ELSE 'FAILED' END,
                    phase = CASE WHEN ? THEN 'DONE' ELSE phase END,
                    last_error_code = CASE WHEN ? THEN NULL ELSE 'SAMPLE_MISMATCH' END,
                    last_error_at = CASE WHEN ? THEN last_error_at ELSE NOW() END,
                    verified_at = CASE WHEN ? THEN NOW() ELSE verified_at END,
                    finished_at = CASE WHEN ? THEN NOW() ELSE finished_at END, updated_at = NOW()
                 WHERE backfill_id = ? AND state = 'RUNNING'
                """, sampleSize, mismatches, verified, verified, verified, verified, verified, verified, backfillId);
    }

    /** The run is ABORTED (ABORT acknowledged, or never begun). */
    public void markAborted(UUID backfillId) {
        jdbc.update("UPDATE ledger_backfill_runs SET state = 'ABORTED', phase = 'DONE', finished_at = NOW(), "
                + "next_attempt_at = NULL, updated_at = NOW() WHERE backfill_id = ? AND state = 'ABORTING'", backfillId);
    }

    /**
     * A retryable failure: back off until {@code nextAttempt}; after {@code maxFailures} consecutive
     * failures the run becomes STALLED (an operator resumes it).
     *
     * @return the state the run is in afterwards
     */
    public BackfillState recordRetry(UUID backfillId, String code, Instant nextAttempt, int maxFailures) {
        List<String> states = jdbc.query("""
                UPDATE ledger_backfill_runs SET consecutive_failures = consecutive_failures + 1,
                    last_error_code = ?, last_error_at = NOW(), next_attempt_at = ?,
                    state = CASE WHEN state = 'ABORTING' THEN state
                                 WHEN consecutive_failures + 1 >= ? THEN 'STALLED' ELSE state END,
                    updated_at = NOW()
                 WHERE backfill_id = ?
                RETURNING state
                """, (rs, i) -> rs.getString(1), code, utc(nextAttempt), maxFailures, backfillId);
        return states.isEmpty() ? BackfillState.FAILED : BackfillState.valueOf(states.get(0));
    }

    /** A non-retryable failure: FAILED with the code (an operator may resume after fixing the cause). */
    public void recordFailure(UUID backfillId, String code) {
        jdbc.update("""
                UPDATE ledger_backfill_runs SET state = CASE WHEN state = 'ABORTING' THEN state ELSE 'FAILED' END,
                    last_error_code = ?, last_error_at = NOW(), next_attempt_at = NULL, updated_at = NOW()
                 WHERE backfill_id = ?
                """, code, backfillId);
    }

    /** Halves the batch byte budget after FRAME_TOO_LARGE; returns the new budget. */
    public int halveBudget(UUID backfillId) {
        Integer b = jdbc.queryForObject("UPDATE ledger_backfill_runs SET batch_byte_budget = GREATEST(1, "
                + "batch_byte_budget / 2), updated_at = NOW() WHERE backfill_id = ? RETURNING batch_byte_budget",
                Integer.class, backfillId);
        return b == null ? 0 : b;
    }

    // ── rows ─────────────────────────────────────────────────────────────────────────────────────

    /** One recorded row (ids and codes only). */
    public record RowRecord(String legacyTransactionId, LegacySource source, long sourceId, UUID edgeAssessmentId,
                            boolean minted, String outcome, String rejectCode, String resolution,
                            String resolutionReason, String resolvedBy, Instant resolvedAt) {}

    /** Rejected rows of a run, by legacy id, paged. */
    public List<RowRecord> rejected(UUID backfillId, int offset, int limit) {
        return jdbc.query("""
                SELECT legacy_transaction_id, source, source_id, edge_assessment_id, minted, outcome, reject_code,
                       resolution, resolution_reason, resolved_by, resolved_at
                  FROM ledger_backfill_rows WHERE backfill_id = ? AND outcome = 'REJECTED'
                 ORDER BY legacy_transaction_id COLLATE "C" OFFSET ? LIMIT ?
                """, this::rowRecord, backfillId, offset, limit);
    }

    /** Unresolved rejected rows of a run with one of {@code codes}, up to {@code limit}. */
    public List<RowRecord> unresolved(UUID backfillId, Collection<String> codes, int limit) {
        if (codes.isEmpty()) {
            return List.of();
        }
        String in = String.join(",", codes.stream().map(c -> "'" + c + "'").toList());
        return jdbc.query("""
                SELECT legacy_transaction_id, source, source_id, edge_assessment_id, minted, outcome, reject_code,
                       resolution, resolution_reason, resolved_by, resolved_at
                  FROM ledger_backfill_rows WHERE backfill_id = ? AND outcome = 'REJECTED' AND resolution IS NULL
                   AND reject_code IN (""" + in + ") ORDER BY legacy_transaction_id COLLATE \"C\" LIMIT ?",
                this::rowRecord, backfillId, limit);
    }

    /** A deterministic sample of held rows: the {@code limit} smallest edgeAssessmentIds. */
    public List<RowRecord> heldSample(UUID backfillId, int limit) {
        return jdbc.query("""
                SELECT legacy_transaction_id, source, source_id, edge_assessment_id, minted, outcome, reject_code,
                       resolution, resolution_reason, resolved_by, resolved_at
                  FROM ledger_backfill_rows WHERE backfill_id = ? AND outcome = 'HELD'
                 ORDER BY md5(edge_assessment_id::text), legacy_transaction_id COLLATE "C" LIMIT ?
                """, this::rowRecord, backfillId, limit);
    }

    /** Marks rejected rows resolved; only unresolved REJECTED rows of the run change. Returns rows changed. */
    public int resolve(UUID backfillId, Collection<String> legacyIds, String resolution, String reason, String by) {
        if (legacyIds.isEmpty()) {
            return 0;
        }
        List<Object[]> args = new ArrayList<>();
        for (String id : legacyIds) {
            args.add(new Object[]{resolution, reason, by, backfillId, id});
        }
        int[] counts = jdbc.batchUpdate("""
                UPDATE ledger_backfill_rows SET resolution = ?, resolution_reason = ?, resolved_by = ?, resolved_at = NOW()
                 WHERE backfill_id = ? AND legacy_transaction_id = ? AND outcome = 'REJECTED' AND resolution IS NULL
                """, args);
        return Arrays.stream(counts).map(c -> Math.max(c, 0)).sum();
    }

    /** Count of rows per outcome / reject code / resolution of a run (report). */
    public Map<String, Long> rowCounts(UUID backfillId) {
        Map<String, Long> out = new LinkedHashMap<>();
        jdbc.query("""
                SELECT outcome, COALESCE(reject_code, '') AS code, COALESCE(resolution, '') AS res, count(*) AS n
                  FROM ledger_backfill_rows WHERE backfill_id = ? GROUP BY outcome, reject_code, resolution
                """, rs -> {
            String key = rs.getString("outcome") + "|" + rs.getString("code") + "|" + rs.getString("res");
            out.put(key, rs.getLong("n"));
        }, backfillId);
        return out;
    }

    // ── mapping ──────────────────────────────────────────────────────────────────────────────────

    /** Maps a run row. */
    private BackfillRun run(ResultSet rs, int i) throws SQLException {
        LocalDateTime cursorTs = rs.getObject("cursor_ts", LocalDateTime.class);
        String cursorSource = rs.getString("cursor_source");
        return new BackfillRun((UUID) rs.getObject("backfill_id"), rs.getLong("psp_id"), rs.getString("site_id"),
                rs.getBoolean("default_site"), sites(rs.getString("site_ids_snapshot")),
                BackfillState.valueOf(rs.getString("state")), BackfillPhase.valueOf(rs.getString("phase")),
                instant(rs, "source_from"), instant(rs, "source_to"), instant(rs, "retention_cutoff"),
                rs.getInt("retention_years"), rs.getLong("expected_rows"),
                cursorSource == null ? null : LegacySource.valueOf(cursorSource),
                cursorTs, (Long) nullable(rs, "cursor_id", true),
                rs.getLong("next_batch_seq"), rs.getInt("resend_round"), rs.getInt("batch_byte_budget"),
                rs.getLong("rows_accepted"), rs.getLong("rows_duplicate"), rs.getString("expected_id_digest"),
                (Long) nullable(rs, "expected_held_rows", true), rs.getString("edge_commit_state"),
                (Long) nullable(rs, "edge_rows_total", true), rs.getString("edge_id_digest"),
                (Integer) nullable(rs, "sample_size", false), (Integer) nullable(rs, "sample_mismatches", false),
                rs.getString("last_error_code"), instant(rs, "last_error_at"), rs.getInt("consecutive_failures"),
                instant(rs, "next_attempt_at"), rs.getString("abort_reason"), rs.getString("created_by"),
                rs.getString("created_reason"), instant(rs, "created_at"), instant(rs, "begun_at"),
                instant(rs, "committed_at"), instant(rs, "verified_at"), instant(rs, "finished_at"));
    }

    /** Maps a row record. */
    private RowRecord rowRecord(ResultSet rs, int i) throws SQLException {
        return new RowRecord(rs.getString("legacy_transaction_id"), LegacySource.valueOf(rs.getString("source")),
                rs.getLong("source_id"), (UUID) rs.getObject("edge_assessment_id"), rs.getBoolean("minted"),
                rs.getString("outcome"), rs.getString("reject_code"), rs.getString("resolution"),
                rs.getString("resolution_reason"), rs.getString("resolved_by"), instant(rs, "resolved_at"));
    }

    /** A timestamptz column as an instant. */
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }

    /** A nullable integral column as Long (asLong) or Integer. */
    private static Object nullable(ResultSet rs, String column, boolean asLong) throws SQLException {
        long v = rs.getLong(column);
        if (rs.wasNull()) {
            return null;
        }
        return asLong ? (Object) v : (Object) (int) v;
    }

    /** The site snapshot JSON array. */
    private List<String> sites(String json) {
        try {
            return List.of(mapper.readValue(json, String[].class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("ledger_backfill_runs.site_ids_snapshot is not a JSON array of site ids", e);
        }
    }

    /** Site ids as a JSON array. */
    private String json(List<String> sites) {
        try {
            return mapper.writeValueAsString(sites);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("site ids could not be serialised", e);
        }
    }

    /** An instant as a UTC {@code OffsetDateTime} (bound as {@code timestamptz} whatever the JVM zone). */
    static java.time.OffsetDateTime utc(Instant t) {
        return t == null ? null : java.time.OffsetDateTime.ofInstant(t, java.time.ZoneOffset.UTC);
    }
}
