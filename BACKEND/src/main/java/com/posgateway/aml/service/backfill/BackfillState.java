package com.posgateway.aml.service.backfill;

import java.util.EnumSet;
import java.util.Set;

/**
 * Cloud state of one back-fill run ({@code ledger_backfill_runs.state}).
 *
 * <pre>
 *   PENDING ──BEGIN ok──▶ RUNNING ──all batches, COMMIT VERIFIED, sample ok──▶ VERIFIED
 *      │                    │  ▲
 *      │ pause              │  │ resume (operator)
 *      ▼                    ▼  │
 *   PAUSED ◀──pause──── RUNNING, STALLED (too many retryable failures), FAILED (non-retryable code)
 *
 *   any non-terminal ──abort──▶ ABORTING ──ABORT sent (or never begun)──▶ ABORTED
 * </pre>
 *
 * Retryable Edge failures (offline, busy, timeout, ledger unavailable) keep the run RUNNING with a
 * back-off ({@code next_attempt_at}); {@code VERIFY_MISMATCH} re-sends every batch (idempotent) up to
 * the configured rounds, then FAILED.
 */
public enum BackfillState {
    PENDING, RUNNING, PAUSED, STALLED, FAILED, ABORTING, ABORTED, VERIFIED;

    /** States the worker drives. */
    public static final Set<BackfillState> DRIVEN = EnumSet.of(PENDING, RUNNING, ABORTING);

    /** States an operator may pause. */
    public static final Set<BackfillState> PAUSABLE = EnumSet.of(PENDING, RUNNING, STALLED, FAILED);

    /** States an operator may resume. */
    public static final Set<BackfillState> RESUMABLE = EnumSet.of(PAUSED, STALLED, FAILED);

    /** States an operator may abort. */
    public static final Set<BackfillState> ABORTABLE = EnumSet.of(PENDING, RUNNING, PAUSED, STALLED, FAILED);

    /** True once the run can never change again. */
    public boolean terminal() {
        return this == ABORTED || this == VERIFIED;
    }
}
