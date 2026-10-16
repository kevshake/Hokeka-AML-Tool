-- Edge Ledger, schema version 1 (EDGE_DATA_RESIDENCY_CONTRACT.md §1, §2, §4.3, §7.15, §8;
-- plan work package R1). The site's PostgreSQL 16 system of record. Applied by Flyway from inside
-- edge-host (EdgeLedger.initialize) into the schema named by EDGE_LEDGER_SCHEMA; every object below
-- is created unqualified, so it lands in that schema. Never edit this file once released: add V2+.

-- ── edge_evaluations ─────────────────────────────────────────────────────────────────────────────
-- One row per /edge/evaluate call (origin LIVE) or per imported legacy row (origin BACKFILL, R15).
-- Range-partitioned by UTC calendar month on evaluated_at, so the retention purge (R2) is a cheap
-- DROP of whole partitions. Partitions are created ahead of time by
-- edge_ensure_evaluation_partitions() (called at startup, hourly, and on demand by a write that
-- finds its month missing). There is deliberately no DEFAULT partition: a row with nowhere to go
-- must fail loudly rather than land in a partition the purge cannot drop.
--
-- PostgreSQL requires every primary key / unique constraint of a partitioned table to contain the
-- partition key, so the primary key is (edge_assessment_id, evaluated_at). Global uniqueness of
-- edge_assessment_id for live rows is guaranteed by edge_outbox (UNIQUE, not partitioned), and the
-- site-wide unique txn_ref index lives in edge_txn_refs below.
CREATE TABLE edge_evaluations (
    edge_assessment_id    uuid        NOT NULL,
    txn_ref               text,
    site_id               text        NOT NULL,
    node_id               text        NOT NULL,
    evaluated_at          timestamptz NOT NULL,
    origin                text        NOT NULL DEFAULT 'LIVE',
    -- transaction (class E / T)
    pan_hash              text,
    amount_cents          bigint,
    currency              text,
    merchant_id           text,
    mcc                   text,
    country_code          text,
    channel               text,
    features              jsonb       NOT NULL DEFAULT '{}'::jsonb,
    -- decision (class D, plus T reasons)
    action                text        NOT NULL,
    score                 integer     NOT NULL,
    triggered_rule_ids    bigint[]    NOT NULL DEFAULT '{}',
    rule_version_ids      bigint[]    NOT NULL DEFAULT '{}',
    reasons               text[]      NOT NULL DEFAULT '{}',
    bundle_version        bigint      NOT NULL,
    bundle_hash           text,
    evaluator             text        NOT NULL,
    latency_us            bigint      NOT NULL,
    fail_closed_code      text,
    ctr_required          boolean     NOT NULL DEFAULT false,
    sanctions_hit         boolean     NOT NULL DEFAULT false,
    sanctions_hits        jsonb       NOT NULL DEFAULT '[]'::jsonb,
    typology_codes        text[]      NOT NULL DEFAULT '{}',
    -- holds and backfill provenance
    legal_hold            boolean     NOT NULL DEFAULT false,
    backfill_id           uuid,
    legacy_transaction_id text,
    CONSTRAINT edge_evaluations_pk PRIMARY KEY (edge_assessment_id, evaluated_at),
    CONSTRAINT edge_evaluations_origin_chk CHECK (origin IN ('LIVE', 'BACKFILL')),
    CONSTRAINT edge_evaluations_action_chk CHECK (action IN ('ALLOW', 'ALERT', 'HOLD', 'BLOCK')),
    CONSTRAINT edge_evaluations_evaluator_chk CHECK (evaluator IN ('native', 'java', 'none', 'cloud-legacy')),
    CONSTRAINT edge_evaluations_fail_closed_chk CHECK (
        fail_closed_code IS NULL
        OR (fail_closed_code IN ('NODE_NOT_AUTHORIZED', 'NO_BUNDLE', 'FEATURE_STORE_UNAVAILABLE',
                                 'LEDGER_UNAVAILABLE', 'EVALUATOR_FAULT')
            AND action = 'HOLD' AND evaluator = 'none')),
    CONSTRAINT edge_evaluations_score_chk CHECK (score >= 0),
    CONSTRAINT edge_evaluations_latency_chk CHECK (latency_us >= 0),
    CONSTRAINT edge_evaluations_bundle_chk CHECK (bundle_version >= -1),
    CONSTRAINT edge_evaluations_amount_chk CHECK (amount_cents IS NULL OR amount_cents >= 0),
    CONSTRAINT edge_evaluations_currency_chk CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$'),
    CONSTRAINT edge_evaluations_country_chk CHECK (country_code IS NULL OR country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT edge_evaluations_mcc_chk CHECK (mcc IS NULL OR mcc ~ '^[0-9]{4}$'),
    CONSTRAINT edge_evaluations_txn_ref_chk CHECK (txn_ref IS NULL OR txn_ref ~ '^[\x21-\x7E]{1,128}$'),
    -- The feature bag never carries pan_hash or a card secret (§2.4); belt and braces for hygiene.
    CONSTRAINT edge_evaluations_features_chk CHECK (
        jsonb_typeof(features) = 'object'
        AND NOT (features ?| ARRAY['pan', 'pan_hash', 'card_number', 'pan_masked_full', 'cvv', 'cvv2',
                                   'cvc', 'cvc2', 'track1', 'track2', 'track_data', 'pin', 'pin_block',
                                   'expiry', 'exp_date', 'card_expiry']))
) PARTITION BY RANGE (evaluated_at);

COMMENT ON TABLE edge_evaluations IS
    'Edge Ledger: one row per evaluation; monthly range partitions on evaluated_at (UTC).';

-- Tunnel ordering (evaluatedAt desc, edgeAssessmentId desc) for txn.search / export.stream keysets.
CREATE INDEX edge_evaluations_time_id_idx ON edge_evaluations (evaluated_at DESC, edge_assessment_id DESC);
-- Per-merchant searches and merchant-range legal holds.
CREATE INDEX edge_evaluations_merchant_time_idx ON edge_evaluations (merchant_id, evaluated_at);

-- ── edge_txn_refs ────────────────────────────────────────────────────────────────────────────────
-- The site's unique txn_ref index (contract §7.6 txn.get "by txnRef it uses the site's unique
-- index"). A partitioned table cannot carry a unique index without the partition key, so the index
-- is this small unpartitioned table, written in the same statement as the evaluation. A live
-- re-evaluation of the same txn_ref (a PSP retry) re-points the reference at the newest
-- evaluation; the older row keeps its txn_ref column. Purged together with the evaluations (R2)
-- by evaluated_at.
CREATE TABLE edge_txn_refs (
    txn_ref            text        PRIMARY KEY,
    edge_assessment_id uuid        NOT NULL,
    evaluated_at       timestamptz NOT NULL
);
CREATE INDEX edge_txn_refs_evaluated_at_idx ON edge_txn_refs (evaluated_at);

-- ── edge_outbox ──────────────────────────────────────────────────────────────────────────────────
-- Decision records v2 waiting for (or already given) delivery to POST /api/v1/edge/assessments
-- (§4.3). Written in the same local transaction as the evaluation; drained per node in seq order;
-- shipped_at set only on HTTP 200. Rejected records are marked shipped with their ack code.
CREATE TABLE edge_outbox (
    seq                bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    edge_assessment_id uuid        NOT NULL,
    node_id            text        NOT NULL,
    kind               text        NOT NULL,
    payload            jsonb       NOT NULL,
    created_at         timestamptz NOT NULL DEFAULT now(),
    shipped_at         timestamptz,
    attempts           integer     NOT NULL DEFAULT 0,
    last_attempt_at    timestamptz,
    last_error         text,
    reject_code        text,
    CONSTRAINT edge_outbox_assessment_uk UNIQUE (edge_assessment_id),
    CONSTRAINT edge_outbox_kind_chk CHECK (kind IN ('DECISION_RECORD_V2')),
    CONSTRAINT edge_outbox_reject_chk CHECK (
        reject_code IS NULL
        OR (shipped_at IS NOT NULL
            AND reject_code IN ('SCHEMA_INVALID', 'FORBIDDEN_FIELD', 'CONFLICT', 'UNKNOWN_SITE', 'NODE_MISMATCH'))),
    CONSTRAINT edge_outbox_last_error_chk CHECK (last_error IS NULL OR last_error ~ '^[A-Z][A-Z0-9_]{0,63}$')
);
CREATE INDEX edge_outbox_pending_idx ON edge_outbox (node_id, seq) WHERE shipped_at IS NULL;
CREATE INDEX edge_outbox_shipped_at_idx ON edge_outbox (shipped_at) WHERE shipped_at IS NOT NULL;

-- ── edge_legal_holds ─────────────────────────────────────────────────────────────────────────────
-- Legal holds received via notify hold.place / hold.release (§7.10, §8.3). Idempotent on hold_id.
-- reason_code is stored as received: an unknown code still holds (§12.2), so it is not an enum here.
CREATE TABLE edge_legal_holds (
    hold_id             uuid        PRIMARY KEY,
    placed_at           timestamptz NOT NULL,
    placed_by           text        NOT NULL,
    reason_code         text        NOT NULL,
    reference           text        NOT NULL,
    scope_kind          text        NOT NULL,
    edge_assessment_ids uuid[],
    merchant_id         text,
    range_from          timestamptz,
    range_to            timestamptz,
    received_at         timestamptz NOT NULL DEFAULT now(),
    matched_rows        bigint      NOT NULL DEFAULT 0,
    released_at         timestamptz,
    released_by         text,
    release_reason_code text,
    CONSTRAINT edge_legal_holds_scope_chk CHECK (
        (scope_kind = 'IDS'
            AND edge_assessment_ids IS NOT NULL AND cardinality(edge_assessment_ids) BETWEEN 1 AND 1000
            AND merchant_id IS NULL AND range_from IS NULL AND range_to IS NULL)
        OR (scope_kind = 'MERCHANT_RANGE'
            AND edge_assessment_ids IS NULL
            AND merchant_id IS NOT NULL AND range_from IS NOT NULL AND range_to IS NOT NULL)),
    CONSTRAINT edge_legal_holds_release_chk CHECK (
        (released_at IS NULL AND released_by IS NULL AND release_reason_code IS NULL)
        OR (released_at IS NOT NULL AND released_by IS NOT NULL AND release_reason_code IS NOT NULL))
);
CREATE INDEX edge_legal_holds_active_idx ON edge_legal_holds (hold_id) WHERE released_at IS NULL;

-- ── edge_held_evaluations ────────────────────────────────────────────────────────────────────────
-- Copies of held rows, made in the same transaction as the DROP of their partition (§8.2), so held
-- evaluations stay readable after their month is purged. Same columns and checks as
-- edge_evaluations, unpartitioned, keyed by the assessment id.
CREATE TABLE edge_held_evaluations (
    LIKE edge_evaluations INCLUDING DEFAULTS INCLUDING CONSTRAINTS,
    hold_ids  uuid[]      NOT NULL,
    copied_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT edge_held_evaluations_pk PRIMARY KEY (edge_assessment_id)
);
CREATE INDEX edge_held_evaluations_time_idx ON edge_held_evaluations (evaluated_at DESC, edge_assessment_id DESC);

-- ── edge_config ──────────────────────────────────────────────────────────────────────────────────
-- History of accepted Edge config documents (§6.3: appended before use). One row per version; a
-- second node of the site accepting the same version is a no-op (ON CONFLICT DO NOTHING).
CREATE TABLE edge_config (
    config_version  bigint      PRIMARY KEY,
    psp_id          bigint      NOT NULL,
    document        jsonb       NOT NULL,
    document_sha256 text        NOT NULL,
    accepted_at     timestamptz NOT NULL DEFAULT now(),
    accepted_by     text        NOT NULL,
    CONSTRAINT edge_config_version_chk CHECK (config_version >= 1),
    CONSTRAINT edge_config_sha_chk CHECK (document_sha256 ~ '^[0-9a-f]{64}$')
);

-- ── edge_purge_log ───────────────────────────────────────────────────────────────────────────────
-- One row per daily retention run (§8.2), surfaced in ledger.health. cutoff / retention_years are
-- NULL when a run is skipped before any config document was accepted (no purge before config).
CREATE TABLE edge_purge_log (
    run_id             bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_at             timestamptz NOT NULL,
    node_id            text        NOT NULL,
    cutoff             timestamptz,
    retention_years    integer,
    config_version     bigint,
    dropped_partitions text[]      NOT NULL DEFAULT '{}',
    held_rows_copied   bigint      NOT NULL DEFAULT 0,
    duration_ms        bigint      NOT NULL DEFAULT 0,
    outcome            text        NOT NULL,
    CONSTRAINT edge_purge_log_retention_chk CHECK (retention_years IS NULL OR retention_years BETWEEN 5 AND 25),
    CONSTRAINT edge_purge_log_outcome_chk CHECK (outcome ~ '^[A-Z][A-Z0-9_]{1,63}$')
);
CREATE INDEX edge_purge_log_run_at_idx ON edge_purge_log (run_at);

-- ── edge_access_log ──────────────────────────────────────────────────────────────────────────────
-- Edge side of the tunnel audit (§7.15): one row per request, also for refused requests. A read op
-- retried on another session of the same site shares its audit_id, hence the surrogate key.
CREATE TABLE edge_access_log (
    id              bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    audit_id        uuid        NOT NULL,
    received_at     timestamptz NOT NULL,
    completed_at    timestamptz,
    session_id      uuid,
    node_id         text        NOT NULL,
    op              text        NOT NULL,
    actor_kind      text,
    actor_user_id   text,
    actor_role      text,
    actor_psp_id    bigint,
    purpose         text,
    case_id         text,
    alert_id        text,
    filing_ref      text,
    support_access  boolean     NOT NULL DEFAULT false,
    support_reason  text,
    params_digest   text,
    outcome         text,
    item_count      integer     NOT NULL DEFAULT 0,
    returned_ids    uuid[]      NOT NULL DEFAULT '{}',
    CONSTRAINT edge_access_log_digest_chk CHECK (params_digest IS NULL OR params_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT edge_access_log_items_chk CHECK (item_count >= 0)
);
CREATE INDEX edge_access_log_audit_idx ON edge_access_log (audit_id);
CREATE INDEX edge_access_log_received_at_idx ON edge_access_log (received_at);

-- ── partition maintenance ────────────────────────────────────────────────────────────────────────
-- Creates the monthly edge_evaluations partitions for the month containing from_day and the
-- months_ahead months after it, returning how many it created. Idempotent and safe to run from
-- every node of a site at once: a transaction-scoped advisory lock serialises creators, and an
-- existing partition is detected before CREATE. Partition bounds are UTC month boundaries; the
-- name is edge_evaluations_YYYY_MM.
CREATE FUNCTION edge_ensure_evaluation_partitions(from_day date, months_ahead integer)
    RETURNS integer
    LANGUAGE plpgsql
AS $$
DECLARE
    month_start date;
    month_end   date;
    part_name   text;
    created     integer := 0;
BEGIN
    IF months_ahead < 0 OR months_ahead > 120 THEN
        RAISE EXCEPTION 'months_ahead must be between 0 and 120 (got %)', months_ahead;
    END IF;
    PERFORM pg_advisory_xact_lock(hashtext('hokeka.edge_evaluations.partitions'));
    FOR i IN 0..months_ahead LOOP
        month_start := (date_trunc('month', from_day) + make_interval(months => i))::date;
        month_end   := (month_start + interval '1 month')::date;
        part_name   := format('edge_evaluations_%s', to_char(month_start, 'YYYY_MM'));
        IF NOT EXISTS (
            SELECT 1
              FROM pg_class c
              JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE c.relname = part_name
               AND n.nspname = current_schema()) THEN
            EXECUTE format(
                'CREATE TABLE %I PARTITION OF edge_evaluations FOR VALUES FROM (%L) TO (%L)',
                part_name,
                to_char(month_start, 'YYYY-MM-DD') || ' 00:00:00+00',
                to_char(month_end, 'YYYY-MM-DD') || ' 00:00:00+00');
            created := created + 1;
        END IF;
    END LOOP;
    RETURN created;
END
$$;

COMMENT ON FUNCTION edge_ensure_evaluation_partitions(date, integer) IS
    'Create monthly edge_evaluations partitions (UTC) for from_day''s month plus months_ahead more.';

-- Partitions for the current UTC month and the three after it, so a fresh ledger can take writes
-- immediately; the application keeps the window rolling from then on.
SELECT edge_ensure_evaluation_partitions((now() AT TIME ZONE 'UTC')::date, 3);
