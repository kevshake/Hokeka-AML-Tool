-- R15 (docs/architecture/EDGE_DATA_RESIDENCY_PLAN.md, contract section 11): legacy cloud transaction
-- data is back-filled to each PSP's Edge over the tunnel (`ledger.backfill`), verified, and only then
-- may R12 purge it from the cloud.
--
-- These tables hold run state, ids and counts only: never a row's content (contract section 11 step 6,
-- invariant I2). A legacy row appears here as its source table, its cloud primary key, the
-- legacyTransactionId sent to the Edge (`txn-<transactions.txn_id>` or
-- `mat-<multi_asset_transactions.id>`) and its edgeAssessmentId.
--
-- Independent of V269 (R9): nothing here reads or alters a table that V269 creates.

-- ── Target site per PSP (section 11 step 1) ──────────────────────────────────────────────────────
-- Chosen by a platform admin. A single-site PSP defaults to its only site; a multi-site PSP must
-- name one. Rows that no other routing rule places go to this site.
CREATE TABLE IF NOT EXISTS ledger_backfill_targets (
    psp_id          BIGINT       PRIMARY KEY,
    default_site_id VARCHAR(64)  NOT NULL,
    chosen_by       VARCHAR(255) NOT NULL,
    chosen_reason   VARCHAR(500) NOT NULL,
    chosen_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE ledger_backfill_targets IS
    'R15: the Edge Site a platform admin chose for a PSP''s legacy back-fill (contract 11 step 1)';

-- ── Merchant routing, frozen at a PSP's first back-fill (multi-site PSPs only) ───────────────────
-- A merchant whose Edge decision records (assessments.site_id) all name one current site of the
-- PSP is routed to that site. A merchant with none, or with records at two or more sites, is
-- ambiguous and goes to the PSP's default target site. Frozen so that later top-up runs send a
-- merchant's rows to the same site as earlier runs.
CREATE TABLE IF NOT EXISTS ledger_backfill_merchant_sites (
    psp_id      BIGINT       NOT NULL,
    merchant_id VARCHAR(255) NOT NULL,
    site_id     VARCHAR(64)  NOT NULL,
    frozen_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_ledger_backfill_merchant_sites PRIMARY KEY (psp_id, merchant_id)
);

COMMENT ON TABLE ledger_backfill_merchant_sites IS
    'R15: merchants routed to a non-default Edge Site (their decision records name exactly one site)';

-- ── Runs ─────────────────────────────────────────────────────────────────────────────────────────
-- One run = one backfillId = one (PSP, site). state: PENDING (created) -> RUNNING (BEGIN acknowledged,
-- batches flowing) -> VERIFIED (COMMIT verified and the sample matched). PAUSED / STALLED / FAILED are
-- resumable; ABORTING sends ABORT to the Edge, then ABORTED. phase is where the worker resumes.
CREATE TABLE IF NOT EXISTS ledger_backfill_runs (
    backfill_id          UUID         PRIMARY KEY,
    psp_id               BIGINT       NOT NULL,
    site_id              VARCHAR(64)  NOT NULL,
    default_site         BOOLEAN      NOT NULL,
    site_ids_snapshot    JSONB        NOT NULL,
    state                VARCHAR(16)  NOT NULL,
    phase                VARCHAR(16)  NOT NULL,
    source_from          TIMESTAMPTZ  NOT NULL,
    source_to            TIMESTAMPTZ  NOT NULL,
    retention_cutoff     TIMESTAMPTZ  NOT NULL,
    retention_years      INTEGER      NOT NULL,
    expected_rows        BIGINT       NOT NULL,
    cursor_source        VARCHAR(8),
    cursor_ts            TIMESTAMP,
    cursor_id            BIGINT,
    next_batch_seq       BIGINT       NOT NULL DEFAULT 0,
    resend_round         INTEGER      NOT NULL DEFAULT 0,
    batch_byte_budget    INTEGER      NOT NULL,
    rows_accepted        BIGINT       NOT NULL DEFAULT 0,
    rows_duplicate       BIGINT       NOT NULL DEFAULT 0,
    expected_id_digest   VARCHAR(64),
    expected_held_rows   BIGINT,
    edge_commit_state    VARCHAR(16),
    edge_rows_total      BIGINT,
    edge_id_digest       VARCHAR(64),
    sample_size          INTEGER,
    sample_mismatches    INTEGER,
    last_error_code      VARCHAR(32),
    last_error_at        TIMESTAMPTZ,
    consecutive_failures INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at      TIMESTAMPTZ,
    lease_owner          VARCHAR(128),
    lease_until          TIMESTAMPTZ,
    abort_reason         VARCHAR(16),
    created_by           VARCHAR(255) NOT NULL,
    created_reason       VARCHAR(500) NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    begun_at             TIMESTAMPTZ,
    committed_at         TIMESTAMPTZ,
    verified_at          TIMESTAMPTZ,
    finished_at          TIMESTAMPTZ,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_ledger_backfill_runs_state CHECK (state IN
        ('PENDING', 'RUNNING', 'PAUSED', 'STALLED', 'FAILED', 'ABORTING', 'ABORTED', 'VERIFIED')),
    CONSTRAINT ck_ledger_backfill_runs_phase CHECK (phase IN ('BEGIN', 'BATCH', 'COMMIT', 'VERIFY', 'DONE')),
    CONSTRAINT ck_ledger_backfill_runs_cursor_source CHECK (cursor_source IS NULL OR cursor_source IN ('TXN', 'MAT')),
    CONSTRAINT ck_ledger_backfill_runs_abort_reason CHECK (abort_reason IS NULL OR abort_reason IN
        ('OPERATOR', 'VERIFY_MISMATCH', 'SOURCE_ERROR')),
    CONSTRAINT ck_ledger_backfill_runs_counts CHECK (expected_rows >= 0 AND next_batch_seq >= 0 AND resend_round >= 0
        AND rows_accepted >= 0 AND rows_duplicate >= 0 AND consecutive_failures >= 0 AND batch_byte_budget > 0),
    CONSTRAINT ck_ledger_backfill_runs_retention CHECK (retention_years BETWEEN 5 AND 25)
);

-- At most one unfinished run per PSP and site: two runs never interleave batches on one site.
CREATE UNIQUE INDEX IF NOT EXISTS uq_ledger_backfill_runs_active
    ON ledger_backfill_runs (psp_id, site_id) WHERE state NOT IN ('VERIFIED', 'ABORTED');

CREATE INDEX IF NOT EXISTS idx_ledger_backfill_runs_psp
    ON ledger_backfill_runs (psp_id, created_at DESC);

-- The worker's claim query: due runs that no live lease holds.
CREATE INDEX IF NOT EXISTS idx_ledger_backfill_runs_due
    ON ledger_backfill_runs (next_attempt_at) WHERE state IN ('PENDING', 'RUNNING', 'ABORTING');

COMMENT ON TABLE ledger_backfill_runs IS
    'R15: one ledger.backfill run per PSP and Edge Site (contract 11 step 6): counts, digests, state; no row content';

-- ── Batches ──────────────────────────────────────────────────────────────────────────────────────
-- The Edge acknowledgement of each BATCH. A re-sent batch keeps its first counts (a re-send is all
-- duplicates by construction) and only bumps send_count.
CREATE TABLE IF NOT EXISTS ledger_backfill_batches (
    backfill_id    UUID        NOT NULL REFERENCES ledger_backfill_runs (backfill_id),
    batch_seq      BIGINT      NOT NULL,
    row_count      INTEGER     NOT NULL,
    accepted       INTEGER     NOT NULL,
    duplicates     INTEGER     NOT NULL,
    rejected       INTEGER     NOT NULL,
    send_count     INTEGER     NOT NULL DEFAULT 1,
    first_acked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_acked_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_ledger_backfill_batches PRIMARY KEY (backfill_id, batch_seq),
    CONSTRAINT ck_ledger_backfill_batches_counts CHECK (batch_seq >= 0 AND row_count > 0 AND accepted >= 0
        AND duplicates >= 0 AND rejected >= 0 AND send_count > 0)
);

-- ── Rows (ids only) ──────────────────────────────────────────────────────────────────────────────
-- outcome HELD = the Edge holds the row for this run (accepted or exact duplicate); REJECTED = the
-- Edge refused it with reject_code. resolution records why a rejected row needs no copy:
-- PRESENT_ON_EDGE (the Edge already holds that edgeAssessmentId or txnRef), PAST_RETENTION (the
-- Edge's cutoff is later than the cloud's), EXCLUDED (an operator decided, with a reason).
CREATE TABLE IF NOT EXISTS ledger_backfill_rows (
    backfill_id           UUID         NOT NULL REFERENCES ledger_backfill_runs (backfill_id),
    psp_id                BIGINT       NOT NULL,
    source                VARCHAR(8)   NOT NULL,
    source_id             BIGINT       NOT NULL,
    legacy_transaction_id VARCHAR(64)  NOT NULL,
    edge_assessment_id    UUID         NOT NULL,
    minted                BOOLEAN      NOT NULL,
    batch_seq             BIGINT       NOT NULL,
    outcome               VARCHAR(16)  NOT NULL,
    reject_code           VARCHAR(32),
    resolution            VARCHAR(24),
    resolution_reason     VARCHAR(500),
    resolved_by           VARCHAR(255),
    resolved_at           TIMESTAMPTZ,
    recorded_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_ledger_backfill_rows PRIMARY KEY (backfill_id, legacy_transaction_id),
    CONSTRAINT ck_ledger_backfill_rows_source CHECK (source IN ('TXN', 'MAT')),
    CONSTRAINT ck_ledger_backfill_rows_outcome CHECK (outcome IN ('HELD', 'REJECTED')),
    CONSTRAINT ck_ledger_backfill_rows_reject CHECK (
        (outcome = 'HELD' AND reject_code IS NULL)
        OR (outcome = 'REJECTED' AND reject_code IN ('SCHEMA_INVALID', 'PAST_RETENTION', 'ID_CONFLICT', 'TXN_REF_CONFLICT'))),
    CONSTRAINT ck_ledger_backfill_rows_resolution CHECK (resolution IS NULL OR
        (outcome = 'REJECTED' AND resolution IN ('PRESENT_ON_EDGE', 'PAST_RETENTION', 'EXCLUDED')))
);

-- Coverage (R12 purge gate): is this legacy row held or resolved in a VERIFIED run of its PSP?
CREATE INDEX IF NOT EXISTS idx_ledger_backfill_rows_source
    ON ledger_backfill_rows (psp_id, source, source_id);

CREATE INDEX IF NOT EXISTS idx_ledger_backfill_rows_rejected
    ON ledger_backfill_rows (backfill_id) WHERE outcome = 'REJECTED';

COMMENT ON TABLE ledger_backfill_rows IS
    'R15: per legacy row, the id it was back-filled under and the Edge outcome (ids and codes only)';
