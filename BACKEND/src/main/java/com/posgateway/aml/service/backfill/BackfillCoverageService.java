package com.posgateway.aml.service.backfill;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * The R12 purge gate (contract §11 step 7): the cloud may delete a PSP's legacy transaction rows only
 * when a {@code VERIFIED} back-fill run covers every one of them.
 *
 * <p>A legacy row (card {@code transactions} or {@code multi_asset_transactions}) is <b>covered</b>
 * when it is older than the PSP's retention (purged, never copied, §11 step 2), or a {@code VERIFIED}
 * run of the same PSP records it as held by the Edge, or as rejected with a resolution
 * ({@code PRESENT_ON_EDGE}, {@code PAST_RETENTION}, or an operator's {@code EXCLUDED} with a reason).
 * A PSP that has any legacy row must also have at least one {@code VERIFIED} run. Rows ingested after
 * a run's window are not covered until a later (top-up) run includes them.
 *
 * <p>Rows of the legacy tables without a PSP ({@code psp_id IS NULL}) cannot be back-filled to any
 * Edge; {@link #purgeReadinessAll()} counts them so R12 refuses a global purge until an owner
 * decision deals with them.
 */
@Service
public class BackfillCoverageService {

    private static final String COVERED = """
            EXISTS (SELECT 1 FROM ledger_backfill_rows c JOIN ledger_backfill_runs v ON v.backfill_id = c.backfill_id
                     WHERE v.psp_id = ? AND v.state = 'VERIFIED' AND c.psp_id = ? AND c.source = '%s' AND c.source_id = %s
                       AND (c.outcome = 'HELD' OR c.resolution IS NOT NULL))""";

    private final JdbcTemplate jdbc;
    private final java.time.ZoneId zone;
    private final Clock clock;

    /** Uses the application's primary database, legacy timestamps in the configured zone. */
    @org.springframework.beans.factory.annotation.Autowired
    public BackfillCoverageService(JdbcTemplate jdbc, BackfillProperties props) {
        this(jdbc, props, Clock.systemUTC());
    }

    /** Explicit clock (tests). */
    BackfillCoverageService(JdbcTemplate jdbc, BackfillProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.zone = props.legacyZone();
        this.clock = clock;
    }

    /**
     * Whether the cloud may purge the PSP's legacy transaction data.
     *
     * @param legacyRows          legacy rows of the PSP (both stores, any age)
     * @param rowsInRetention     of those, rows inside the PSP's retention (must be copied)
     * @param uncoveredRows       rows inside retention no VERIFIED run covers
     * @param pendingRejections   rejected rows in VERIFIED runs still waiting for an operator decision
     * @param verifiedRuns        VERIFIED runs of the PSP
     * @param reasons             why the purge is refused (empty when allowed)
     */
    public record PurgeReadiness(long pspId, boolean allowed, long legacyRows, long rowsInRetention, long uncoveredRows,
                                 long pendingRejections, long verifiedRuns, List<String> reasons) {}

    /** Readiness of every PSP that has legacy rows, plus the rows that belong to no PSP. */
    public record GlobalReadiness(boolean allowed, List<PurgeReadiness> psps, long unattributedRows) {}

    /** The readiness of one PSP (every query is scoped to it). */
    public PurgeReadiness purgeReadiness(long pspId) {
        int years = retentionYears(pspId);
        LocalDateTime cutoff = LocalDateTime.ofInstant(
                Instant.now(clock).atOffset(ZoneOffset.UTC).minusYears(years).toInstant(), zone);
        long txnRows = count("SELECT count(*) FROM transactions WHERE psp_id = ?", pspId);
        long matRows = count("SELECT count(*) FROM multi_asset_transactions WHERE psp_id = ?", pspId);
        long txnInRetention = count("SELECT count(*) FROM transactions t WHERE t.psp_id = ? "
                + "AND COALESCE(t.txn_ts, t.created_at) >= ?", pspId, cutoff);
        long matInRetention = count("SELECT count(*) FROM multi_asset_transactions m WHERE m.psp_id = ? "
                + "AND m.executed_at >= ?", pspId, cutoff);
        long txnUncovered = count("SELECT count(*) FROM transactions t WHERE t.psp_id = ? "
                + "AND COALESCE(t.txn_ts, t.created_at) >= ? AND NOT " + COVERED.formatted("TXN", "t.txn_id"),
                pspId, cutoff, pspId, pspId);
        long matUncovered = count("SELECT count(*) FROM multi_asset_transactions m WHERE m.psp_id = ? "
                + "AND m.executed_at >= ? AND NOT " + COVERED.formatted("MAT", "m.id"), pspId, cutoff, pspId, pspId);
        long pending = count("""
                SELECT count(DISTINCT (c.source, c.source_id))
                  FROM ledger_backfill_rows c JOIN ledger_backfill_runs v ON v.backfill_id = c.backfill_id
                 WHERE v.psp_id = ? AND v.state = 'VERIFIED' AND c.psp_id = ? AND c.outcome = 'REJECTED'
                   AND c.resolution IS NULL
                   AND NOT EXISTS (SELECT 1 FROM ledger_backfill_rows c2
                                     JOIN ledger_backfill_runs v2 ON v2.backfill_id = c2.backfill_id
                                    WHERE v2.psp_id = ? AND v2.state = 'VERIFIED' AND c2.psp_id = ?
                                      AND c2.source = c.source AND c2.source_id = c.source_id
                                      AND (c2.outcome = 'HELD' OR c2.resolution IS NOT NULL))
                """, pspId, pspId, pspId, pspId);
        long verified = count("SELECT count(*) FROM ledger_backfill_runs WHERE psp_id = ? AND state = 'VERIFIED'", pspId);
        long legacy = txnRows + matRows;
        long uncovered = txnUncovered + matUncovered;
        List<String> reasons = new ArrayList<>();
        if (legacy > 0 && verified == 0) {
            reasons.add("NO_VERIFIED_RUN");
        }
        if (uncovered > 0) {
            reasons.add("UNCOVERED_ROWS");
        }
        if (pending > 0) {
            reasons.add("REJECTIONS_PENDING_DECISION");
        }
        boolean allowed = legacy == 0 || (verified > 0 && uncovered == 0);
        return new PurgeReadiness(pspId, allowed, legacy, txnInRetention + matInRetention, uncovered, pending, verified,
                List.copyOf(reasons));
    }

    /**
     * R12 calls this before deleting a PSP's legacy rows.
     *
     * @throws IllegalStateException naming the reasons when the purge must not run
     */
    public PurgeReadiness requirePurgeAllowed(long pspId) {
        PurgeReadiness r = purgeReadiness(pspId);
        if (!r.allowed()) {
            throw new IllegalStateException("Legacy purge refused for PSP " + pspId + ": " + String.join(", ", r.reasons())
                    + " (" + r.uncoveredRows() + " legacy row(s) not covered by a VERIFIED back-fill run)");
        }
        return r;
    }

    /** Readiness of every PSP with legacy rows; allowed only when all are and no row lacks a PSP. */
    public GlobalReadiness purgeReadinessAll() {
        List<Long> psps = jdbc.queryForList("""
                SELECT psp_id FROM transactions WHERE psp_id IS NOT NULL
                UNION SELECT psp_id FROM multi_asset_transactions
                ORDER BY 1
                """, Long.class);
        List<PurgeReadiness> out = new ArrayList<>();
        boolean allowed = true;
        for (Long psp : psps) {
            PurgeReadiness r = purgeReadiness(psp);
            out.add(r);
            allowed &= r.allowed();
        }
        long unattributed = count("SELECT count(*) FROM transactions WHERE psp_id IS NULL");
        return new GlobalReadiness(allowed && unattributed == 0, List.copyOf(out), unattributed);
    }

    /** The PSP's retention in force (default 5, the contract floor). */
    private int retentionYears(long pspId) {
        List<Integer> years = jdbc.queryForList("SELECT retention_years FROM psps WHERE psp_id = ?", Integer.class, pspId);
        return years.isEmpty() || years.get(0) == null ? 5 : Math.max(5, years.get(0));
    }

    /** A count query. */
    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}
