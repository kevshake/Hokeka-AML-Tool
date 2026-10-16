-- R3 (docs/EDGE_DATA_RESIDENCY_CONTRACT.md sections 6, 8, 9, 10): PSP-set data retention, Hokeka
-- support access and cross-PSP intelligence opt-in, the inputs of the sealed Edge config document
-- that previously had no cloud source, and the per-PSP config version history.

-- ── PSP data settings (contract 8.1, 9, 10) ─────────────────────────────────────────────────────
ALTER TABLE psps
    ADD COLUMN IF NOT EXISTS retention_years INTEGER NOT NULL DEFAULT 5,
    ADD COLUMN IF NOT EXISTS retention_effective_since TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS support_access_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS support_access_changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS cross_psp_opt_in BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cross_psp_opt_in_changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_psps_retention_years') THEN
        -- FATF R.11 floor of 5 years; 25-year cap (contract 8.1).
        ALTER TABLE psps ADD CONSTRAINT ck_psps_retention_years
            CHECK (retention_years BETWEEN 5 AND 25);
    END IF;
END $$;

COMMENT ON COLUMN psps.retention_years IS 'Data retention in force for the PSP (5..25 years); Edge purge and cloud decision-record purge follow it';
COMMENT ON COLUMN psps.support_access_enabled IS 'PSP consent for Hokeka support staff to pull its Edge data (contract 9)';
COMMENT ON COLUMN psps.cross_psp_opt_in IS 'PSP opt-in to cross-PSP indicators from filed SARs (contract 10)';

-- ── Maker-checker change requests for the three settings ────────────────────────────────────────
-- A PSP admin proposes; a second, different PSP admin / MLRO / compliance officer approves.
-- An approved retention reduction stays APPROVED until effective_at (approval + 30 days), then a
-- scheduled job applies it (APPLIED).
CREATE TABLE IF NOT EXISTS psp_data_setting_changes (
    id                    BIGSERIAL PRIMARY KEY,
    psp_id                BIGINT       NOT NULL REFERENCES psps (psp_id),
    setting               VARCHAR(32)  NOT NULL,
    old_value             VARCHAR(16)  NOT NULL,
    new_value             VARCHAR(16)  NOT NULL,
    status                VARCHAR(24)  NOT NULL,
    reason                TEXT,
    requested_by          BIGINT,
    requested_by_username VARCHAR(255) NOT NULL,
    requested_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    reviewed_by           BIGINT,
    reviewed_by_username  VARCHAR(255),
    reviewed_at           TIMESTAMPTZ,
    review_reason         TEXT,
    effective_at          TIMESTAMPTZ,
    applied_at            TIMESTAMPTZ,
    CONSTRAINT ck_psp_data_setting_changes_setting
        CHECK (setting IN ('RETENTION_YEARS', 'SUPPORT_ACCESS', 'CROSS_PSP_OPT_IN')),
    CONSTRAINT ck_psp_data_setting_changes_status
        CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'APPLIED', 'REJECTED', 'SUPERSEDED'))
);

CREATE INDEX IF NOT EXISTS idx_psp_data_setting_changes_psp
    ON psp_data_setting_changes (psp_id, requested_at DESC);

CREATE INDEX IF NOT EXISTS idx_psp_data_setting_changes_due
    ON psp_data_setting_changes (status, effective_at);

-- At most one change awaiting a checker per PSP and setting.
CREATE UNIQUE INDEX IF NOT EXISTS uq_psp_data_setting_changes_pending
    ON psp_data_setting_changes (psp_id, setting) WHERE status = 'PENDING_APPROVAL';

-- ── Edge CTR and tunnel settings (contract 6.1 'ctr' and 'tunnel' sections) ─────────────────────
-- No row = the contract defaults: CTR off (Appendix A.20), tunnel on with every read op, no backfill.
CREATE TABLE IF NOT EXISTS psp_edge_settings (
    psp_id                    BIGINT       PRIMARY KEY REFERENCES psps (psp_id),
    ctr_enabled               BOOLEAN      NOT NULL DEFAULT FALSE,
    ctr_business_day_timezone VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    ctr_reporting_currency    VARCHAR(3)   NOT NULL DEFAULT 'USD',
    ctr_threshold_minor       BIGINT,
    ctr_cash_channels         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    tunnel_enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    tunnel_ops_allowed        JSONB        NOT NULL DEFAULT
        '["txn.search","txn.get","txn.batchGet","txn.related","filing.extract","export.stream","ledger.health"]'::jsonb,
    tunnel_backfill_allowed   BOOLEAN      NOT NULL DEFAULT FALSE,
    tunnel_max_inflight       INTEGER      NOT NULL DEFAULT 16,
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by                VARCHAR(255),
    CONSTRAINT ck_psp_edge_settings_inflight CHECK (tunnel_max_inflight BETWEEN 1 AND 16),
    CONSTRAINT ck_psp_edge_settings_threshold CHECK (ctr_threshold_minor IS NULL OR ctr_threshold_minor >= 0),
    CONSTRAINT ck_psp_edge_settings_currency CHECK (ctr_reporting_currency ~ '^[A-Z]{3}$')
);

-- ── Edge limits (contract 6.1 'limits') ─────────────────────────────────────────────────────────
-- The existing global/merchant limit tables carry no action and no currency, so they cannot be
-- expressed as Edge limits; Edge limits are configured here.
CREATE TABLE IF NOT EXISTS edge_limits (
    id           BIGSERIAL    PRIMARY KEY,
    psp_id       BIGINT       NOT NULL REFERENCES psps (psp_id),
    scope        VARCHAR(16)  NOT NULL,
    metric       VARCHAR(16)  NOT NULL,
    window_code  VARCHAR(8)   NOT NULL,
    currency     VARCHAR(3),
    max_value    BIGINT       NOT NULL,
    action       VARCHAR(8)   NOT NULL,
    merchant_id  VARCHAR(64),
    mcc          VARCHAR(4),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(255),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_edge_limits_scope CHECK (scope IN ('CARD', 'MERCHANT', 'PSP')),
    CONSTRAINT ck_edge_limits_metric CHECK (metric IN ('COUNT', 'AMOUNT')),
    CONSTRAINT ck_edge_limits_window CHECK (window_code IN ('PT1H', 'P1D', 'P7D', 'P30D')),
    CONSTRAINT ck_edge_limits_action CHECK (action IN ('ALERT', 'HOLD', 'BLOCK')),
    CONSTRAINT ck_edge_limits_max CHECK (max_value >= 0),
    CONSTRAINT ck_edge_limits_amount_currency CHECK (metric <> 'AMOUNT' OR currency IS NOT NULL),
    CONSTRAINT ck_edge_limits_currency CHECK (currency IS NULL OR currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_edge_limits_mcc CHECK (mcc IS NULL OR mcc ~ '^[0-9]{4}$')
);

CREATE INDEX IF NOT EXISTS idx_edge_limits_psp ON edge_limits (psp_id, active);

-- ── Edge merchant blocklist (contract 6.1 'merchantBlocklist') ──────────────────────────────────
-- Rows are never deleted: removal sets removed_at, so the blocklist version (inserts + removals)
-- only ever grows.
CREATE TABLE IF NOT EXISTS edge_merchant_blocklist (
    id           BIGSERIAL    PRIMARY KEY,
    psp_id       BIGINT       NOT NULL REFERENCES psps (psp_id),
    merchant_id  VARCHAR(64)  NOT NULL,
    reason_code  VARCHAR(24)  NOT NULL,
    since        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(255),
    removed_at   TIMESTAMPTZ,
    removed_by   VARCHAR(255),
    CONSTRAINT ck_edge_merchant_blocklist_reason
        CHECK (reason_code IN ('SANCTIONED', 'REGULATOR_ORDER', 'PSP_TERMINATED', 'FRAUD_CONFIRMED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_edge_merchant_blocklist_active
    ON edge_merchant_blocklist (psp_id, merchant_id) WHERE removed_at IS NULL;

-- ── Edge config versions (contract 6.2) ─────────────────────────────────────────────────────────
-- One row per issued configVersion. A new version is minted whenever the canonical content hash of
-- the PSP's config document changes, so every input change increments configVersion.
CREATE TABLE IF NOT EXISTS edge_config_versions (
    id             BIGSERIAL    PRIMARY KEY,
    psp_id         BIGINT       NOT NULL REFERENCES psps (psp_id),
    config_version BIGINT       NOT NULL,
    content_hash   VARCHAR(64)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_edge_config_versions_psp_version UNIQUE (psp_id, config_version),
    CONSTRAINT ck_edge_config_versions_version CHECK (config_version >= 1)
);

COMMENT ON TABLE edge_config_versions IS 'Issued Edge config document versions per PSP (ETag of GET /api/v1/edge/config)';
