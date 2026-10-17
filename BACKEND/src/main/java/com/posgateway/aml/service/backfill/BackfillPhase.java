package com.posgateway.aml.service.backfill;

/**
 * Where the worker resumes a run ({@code ledger_backfill_runs.phase}): the next contract §11 step
 * still to be acknowledged by the Edge. Persisted after every acknowledgement, so a Control Plane
 * restart resumes at the right step and batch.
 */
public enum BackfillPhase {
    /** BEGIN not yet acknowledged. */
    BEGIN,
    /** Batches flowing from the persisted cursor. */
    BATCH,
    /** Every batch acknowledged; COMMIT next. */
    COMMIT,
    /** COMMIT verified by the Edge; the cloud's sample check and rejection triage next. */
    VERIFY,
    /** Nothing left to do (VERIFIED or ABORTED). */
    DONE
}
