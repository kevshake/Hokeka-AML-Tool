-- Audit hash-chain column + peer-group deviation rule wiring

ALTER TABLE audit_logs_enhanced
    ADD COLUMN IF NOT EXISTS previous_checksum VARCHAR(128);

COMMENT ON COLUMN audit_logs_enhanced.previous_checksum IS
    'Checksum of the prior audit row in the append-only chain (tamper-evidence)';

INSERT INTO rule_definitions (
    name, description, rule_type, rule_expression, score_impact, action_type,
    priority, enabled, created_at, updated_at, psp_id, created_by,
    is_system_managed, category, rule_subtype, applies_to, typology,
    checks_for, external_code, recommended, sample_use_case, parameters
)
SELECT
    'Peer Group Volume Deviation',
    'Merchant 30d volume exceeds peer-group average by configurable ratio.',
    'SPEL',
    '#features[''peer_group_volume_ratio''] != null && #features[''peer_group_volume_ratio''] >= (#params[''ratio_x''] ?: 2)',
    35,
    'ALERT',
    100,
    TRUE,
    NOW(),
    NOW(),
    NULL,
    1,
    TRUE,
    'FRAUD',
    'Peer comparison',
    'Merchant',
    'Unusual behaviour',
    'Merchant volume, Peer group',
    'R-127',
    FALSE,
    'Merchant volume is 3x the MCC peer-group average over 30 days.',
    '{"ratio_x": 2}'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM rule_definitions WHERE external_code = 'R-127');
