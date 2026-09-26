-- JEV AI decision layer: audit trail, per-engine settings, PSP inline mode

CREATE TABLE IF NOT EXISTS ai_engine_settings (
    engine_code        VARCHAR(64)  PRIMARY KEY,
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    advisory_only      BOOLEAN      NOT NULL DEFAULT TRUE,
    prompt_version     VARCHAR(32)  NOT NULL DEFAULT 'v1',
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by         VARCHAR(128)
);

INSERT INTO ai_engine_settings (engine_code, enabled, advisory_only, prompt_version) VALUES
    ('TRANSACTION_RISK',       TRUE, TRUE,  'v1'),
    ('ALERT_TRIAGE',           TRUE, TRUE,  'v1'),
    ('CASE_TRIAGE',            TRUE, TRUE,  'v1'),
    ('SANCTIONS_DISAMBIGUATION', TRUE, TRUE, 'v1'),
    ('KYC_EDD',                TRUE, TRUE,  'v1'),
    ('G2_CONTENT',             TRUE, TRUE,  'v1'),
    ('ADVERSE_MEDIA',          TRUE, TRUE,  'v1'),
    ('RULE_SUGGESTION',        TRUE, TRUE,  'v1'),
    ('FRAUD_SCORING',          TRUE, TRUE,  'v1')
ON CONFLICT (engine_code) DO NOTHING;

CREATE TABLE IF NOT EXISTS ai_decision_audit (
    id                     BIGSERIAL PRIMARY KEY,
    psp_id                 BIGINT,
    engine_code            VARCHAR(64)  NOT NULL,
    prompt_version         VARCHAR(32)  NOT NULL,
    model_id               VARCHAR(128),
    request_features       JSONB        NOT NULL DEFAULT '{}',
    raw_response           TEXT,
    parsed_response        JSONB,
    recommendation         VARCHAR(32),
    risk_score             DOUBLE PRECISION,
    confidence             DOUBLE PRECISION,
    reasons                JSONB,
    cited_signals          JSONB,
    latency_ms             BIGINT,
    input_tokens           INTEGER,
    output_tokens          INTEGER,
    estimated_cost_usd     NUMERIC(12, 6),
    fallback_reason        VARCHAR(256),
    ai_applied             BOOLEAN      NOT NULL DEFAULT FALSE,
    baseline_decision      VARCHAR(32),
    final_decision         VARCHAR(32),
    transaction_id         BIGINT,
    alert_id               BIGINT,
    case_id                BIGINT,
    merchant_id            BIGINT,
    screening_hit_id       VARCHAR(128),
    edge_id                VARCHAR(128),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_jev_audit_psp_created ON ai_decision_audit (psp_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_jev_audit_engine_created ON ai_decision_audit (engine_code, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_jev_audit_transaction ON ai_decision_audit (transaction_id) WHERE transaction_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_jev_audit_alert ON ai_decision_audit (alert_id) WHERE alert_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_jev_audit_case ON ai_decision_audit (case_id) WHERE case_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS ai_daily_spend (
    psp_id         BIGINT       NOT NULL,
    spend_date     DATE         NOT NULL,
    call_count     INTEGER      NOT NULL DEFAULT 0,
    total_tokens   BIGINT       NOT NULL DEFAULT 0,
    estimated_usd  NUMERIC(12, 6) NOT NULL DEFAULT 0,
    PRIMARY KEY (psp_id, spend_date)
);

ALTER TABLE psps
    ADD COLUMN IF NOT EXISTS ai_inline_mode BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS ai_inline_budget_ms INTEGER NOT NULL DEFAULT 500;

COMMENT ON COLUMN psps.ai_inline_mode IS 'When true, edge waits for JEV verdict up to ai_inline_budget_ms on borderline pre-auth decisions';
COMMENT ON COLUMN psps.ai_inline_budget_ms IS 'Max milliseconds edge waits for inline JEV verdict (default 500ms)';
