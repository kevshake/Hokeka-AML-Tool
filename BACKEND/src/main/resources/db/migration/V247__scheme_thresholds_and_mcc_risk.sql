-- Effective-dated scheme thresholds and MCC risk scores.
CREATE TABLE IF NOT EXISTS scheme_program_thresholds (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT,
    program VARCHAR(16) NOT NULL,
    source_document_version VARCHAR(64),
    effective_from TIMESTAMPTZ,
    effective_to TIMESTAMPTZ,
    critical_rate DOUBLE PRECISION,
    critical_volume BIGINT,
    high_rate DOUBLE PRECISION,
    high_volume BIGINT,
    warning_rate DOUBLE PRECISION,
    warning_volume BIGINT,
    watch_rate DOUBLE PRECISION,
    watch_volume BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_scheme_program_thresholds_lookup
    ON scheme_program_thresholds (program, effective_from DESC);

CREATE TABLE IF NOT EXISTS mcc_risk_scores (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT,
    mcc VARCHAR(8) NOT NULL,
    risk_score DOUBLE PRECISION NOT NULL,
    source_document_version VARCHAR(64),
    effective_from TIMESTAMPTZ,
    effective_to TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_mcc_risk_scores_lookup
    ON mcc_risk_scores (mcc, effective_from DESC);

INSERT INTO rule_definitions (
    name, description, rule_type, rule_expression, score_impact, action_type,
    priority, enabled, created_at, updated_at, psp_id, created_by,
    is_system_managed, category, rule_subtype, applies_to, typology,
    checks_for, external_code, recommended, sample_use_case, parameters
)
SELECT
    'Scheme monitoring high stage',
    'Visa VFMP or Mastercard HECM simulator reached HIGH/CRITICAL (review only; does not block settlement).',
    'SPEL',
    '(#features[''vfmp_stage''] != null && (#features[''vfmp_stage''] == ''HIGH_RISK'' || #features[''vfmp_stage''] == ''CRITICAL'')) || (#features[''hecm_stage''] != null && (#features[''hecm_stage''] == ''HIGH'' || #features[''hecm_stage''] == ''EXCESSIVE''))',
    25, 'ALERT', 90, TRUE, NOW(), NOW(), NULL, 1, TRUE,
    'FRAUD', 'Scheme monitoring', 'Merchant', 'Unusual behaviour',
    'VFMP stage, HECM stage', 'R-173', FALSE,
    'Merchant entered a scheme HIGH or CRITICAL monitoring stage.',
    '{}'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM rule_definitions WHERE external_code = 'R-173');
