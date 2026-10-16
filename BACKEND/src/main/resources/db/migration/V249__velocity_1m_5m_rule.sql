-- Add the short-window PAN velocity rule; existing SQL COUNT velocity remains unchanged.
INSERT INTO rule_definitions (
    name, description, rule_type, rule_expression, score_impact, action_type,
    priority, enabled, created_at, updated_at, psp_id, created_by,
    is_system_managed, category, rule_subtype, applies_to, typology,
    checks_for, external_code, recommended, sample_use_case, parameters
)
SELECT
    'PAN velocity 1 minute',
    'Card transaction count in the last minute exceeds a conservative burst threshold.',
    'SPEL',
    '#features[''pan_txn_count_1m''] != null && #features[''pan_txn_count_1m''] >= (#params[''max_txn''] ?: 8)',
    20, 'ALERT', 80, TRUE, NOW(), NOW(), NULL, 1, TRUE,
    'FRAUD', 'Velocity', 'Card', 'Unusual behaviour',
    'No. of transactions, Time', 'R-172', FALSE,
    'Eight or more PAN transactions in one minute.',
    '{"max_txn": 8}'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM rule_definitions WHERE external_code = 'R-172');
