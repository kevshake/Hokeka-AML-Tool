-- Wire extracted zscore_amount_vs_pan_history into the SpEL rule catalogue.
-- R-130 is already used (round-value txn count); this seed is R-171.

INSERT INTO rule_definitions (
    name, description, rule_type, rule_expression, score_impact, action_type,
    priority, enabled, created_at, updated_at, psp_id, created_by,
    is_system_managed, category, rule_subtype, applies_to, typology,
    checks_for, external_code, recommended, sample_use_case, parameters
)
SELECT
    'PAN amount z-score vs history',
    'Current amount is a statistical outlier versus the PAN 30-day amount history.',
    'SPEL',
    '#features[''zscore_amount_vs_pan_history''] != null && #features[''zscore_amount_vs_pan_history''] >= (#params[''z''] ?: 3)',
    35,
    'HOLD',
    100,
    TRUE,
    NOW(),
    NOW(),
    NULL,
    1,
    TRUE,
    'FRAUD',
    'Amount anomaly',
    'Card',
    'Unusual behaviour',
    'Transaction amount, PAN history',
    'R-171',
    FALSE,
    'Card spend is 3+ standard deviations above the PAN 30-day mean.',
    '{"z": 3}'::jsonb
WHERE NOT EXISTS (SELECT 1 FROM rule_definitions WHERE external_code = 'R-171');
