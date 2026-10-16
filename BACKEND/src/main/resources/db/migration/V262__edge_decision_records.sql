-- R7 (docs/EDGE_DATA_RESIDENCY_CONTRACT.md section 4): decision record v2 columns on assessments,
-- the hourly decision rollup built from decision records, and the Edge Site of a node.

-- ── Decision record v2 on assessments ───────────────────────────────────────────────────────────
-- Only class-D fields (contract 2.2). Nothing here may hold an amount, currency, country, card,
-- customer, device or IP identifier, name or free-text reason.
ALTER TABLE assessments
    ADD COLUMN IF NOT EXISTS record_version      INTEGER,
    ADD COLUMN IF NOT EXISTS edge_node_id        VARCHAR(64),
    ADD COLUMN IF NOT EXISTS site_id             VARCHAR(64),
    ADD COLUMN IF NOT EXISTS txn_ref             VARCHAR(128),
    ADD COLUMN IF NOT EXISTS evaluated_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS merchant_id         VARCHAR(64),
    ADD COLUMN IF NOT EXISTS score               INTEGER,
    ADD COLUMN IF NOT EXISTS ctr_required        BOOLEAN,
    ADD COLUMN IF NOT EXISTS sanctions_hit       BOOLEAN,
    ADD COLUMN IF NOT EXISTS fail_closed_code    VARCHAR(32),
    ADD COLUMN IF NOT EXISTS bundle_version      BIGINT,
    ADD COLUMN IF NOT EXISTS evaluator           VARCHAR(16),
    ADD COLUMN IF NOT EXISTS triggered_rule_ids  JSONB,
    ADD COLUMN IF NOT EXISTS rule_version_ids    JSONB,
    ADD COLUMN IF NOT EXISTS watchlist_entry_ids JSONB,
    ADD COLUMN IF NOT EXISTS typology_codes      JSONB,
    ADD COLUMN IF NOT EXISTS record_digest       VARCHAR(64),
    ADD COLUMN IF NOT EXISTS received_at         TIMESTAMPTZ;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_assessments_record_version') THEN
        ALTER TABLE assessments ADD CONSTRAINT ck_assessments_record_version
            CHECK (record_version IS NULL OR record_version IN (1, 2));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_assessments_fail_closed_code') THEN
        ALTER TABLE assessments ADD CONSTRAINT ck_assessments_fail_closed_code
            CHECK (fail_closed_code IS NULL OR fail_closed_code IN
                   ('NODE_NOT_AUTHORIZED', 'NO_BUNDLE', 'FEATURE_STORE_UNAVAILABLE',
                    'LEDGER_UNAVAILABLE', 'EVALUATOR_FAULT'));
    END IF;
END $$;

-- Retention purge scans by PSP and evaluation time (contract 8.4).
CREATE INDEX IF NOT EXISTS idx_assessments_psp_evaluated
    ON assessments (psp_id, evaluated_at) WHERE evaluated_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_assessments_psp_txn_ref
    ON assessments (psp_id, txn_ref) WHERE txn_ref IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_assessments_site
    ON assessments (site_id) WHERE site_id IS NOT NULL;

COMMENT ON COLUMN assessments.record_digest IS 'SHA-256 of the canonical decision record v2 JSON: equal digest = duplicate, different = CONFLICT';

-- Alerts raised from a decision record use source_type 'EDGE_DECISION' and source_reference =
-- edgeAssessmentId; the existing unique index uk_alert_source_reference (V152) already makes that
-- one alert per decision record and serves the purge job's lookups.

-- ── Hourly decision rollup (contract 5, last bullet) ────────────────────────────────────────────
-- Built from decision records only; per PSP, site and merchant. merchant_key '' = no merchant.
CREATE TABLE IF NOT EXISTS decision_stats_hourly (
    psp_id              BIGINT       NOT NULL,
    hour_start          TIMESTAMPTZ  NOT NULL,
    site_id             VARCHAR(64)  NOT NULL,
    merchant_key        VARCHAR(64)  NOT NULL DEFAULT '',
    record_count        BIGINT       NOT NULL DEFAULT 0,
    allow_count         BIGINT       NOT NULL DEFAULT 0,
    alert_count         BIGINT       NOT NULL DEFAULT 0,
    hold_count          BIGINT       NOT NULL DEFAULT 0,
    block_count         BIGINT       NOT NULL DEFAULT 0,
    fail_closed_count   BIGINT       NOT NULL DEFAULT 0,
    ctr_required_count  BIGINT       NOT NULL DEFAULT 0,
    sanctions_hit_count BIGINT       NOT NULL DEFAULT 0,
    score_sum           BIGINT       NOT NULL DEFAULT 0,
    latency_ms_sum      BIGINT       NOT NULL DEFAULT 0,
    latency_ms_max      INTEGER      NOT NULL DEFAULT 0,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_decision_stats_hourly PRIMARY KEY (psp_id, hour_start, site_id, merchant_key),
    CONSTRAINT ck_decision_stats_hourly_aligned
        CHECK (hour_start = date_trunc('hour', hour_start AT TIME ZONE 'UTC') AT TIME ZONE 'UTC')
);

CREATE INDEX IF NOT EXISTS idx_decision_stats_hourly_psp_hour
    ON decision_stats_hourly (psp_id, hour_start DESC);

COMMENT ON TABLE decision_stats_hourly IS 'Hourly decision counts per PSP/site/merchant, built from Edge decision records (no amounts)';

-- edge_nodes.site_id (the node's Edge Site) is added by V263 (work package R4, tunnel server).
