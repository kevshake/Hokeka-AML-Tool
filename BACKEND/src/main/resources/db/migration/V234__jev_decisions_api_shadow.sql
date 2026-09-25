-- Jev Decisions API audit extensions and provisional per-PSP bands

ALTER TABLE jev_decision_audit
    ADD COLUMN IF NOT EXISTS decision_point VARCHAR(64),
    ADD COLUMN IF NOT EXISTS question_config_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS openrouter_request_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS model_snapshot VARCHAR(128),
    ADD COLUMN IF NOT EXISTS state_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS answers_json JSONB,
    ADD COLUMN IF NOT EXISTS thresholds_json JSONB,
    ADD COLUMN IF NOT EXISTS branch_taken VARCHAR(64),
    ADD COLUMN IF NOT EXISTS shadow_mode BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS would_apply BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS usage_cost_usd NUMERIC(12, 6);

CREATE INDEX IF NOT EXISTS idx_jev_audit_branch_created
    ON jev_decision_audit (branch_taken, created_at DESC);

CREATE TABLE IF NOT EXISTS jev_bands (
    psp_id           BIGINT       NOT NULL,
    decision_point   VARCHAR(64)  NOT NULL,
    bands_version    VARCHAR(32)  NOT NULL DEFAULT 'provisional-v1',
    provisional      BOOLEAN      NOT NULL DEFAULT TRUE,
    thresholds       JSONB        NOT NULL DEFAULT '{}',
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (psp_id, decision_point)
);

COMMENT ON TABLE jev_bands IS 'Per-PSP provisional Jev band thresholds; replace with labeled-sample tuning (DESIGN.md §7).';
COMMENT ON COLUMN jev_decision_audit.shadow_mode IS 'When true, Jev output was logged only and did not mutate decisions.';

INSERT INTO jev_engine_settings (engine_code, enabled, advisory_only, prompt_version) VALUES
    ('SAR_NARRATIVE_VERIFICATION', TRUE, TRUE, 'v1')
ON CONFLICT (engine_code) DO NOTHING;
