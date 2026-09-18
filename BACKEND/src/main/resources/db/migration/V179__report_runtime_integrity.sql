-- Correct SAR user provenance without changing an already-applied report definition.
UPDATE report_definitions d
SET is_active = FALSE
FROM reports r
WHERE d.report_id = r.id
  AND r.report_code = 'SAR_001'
  AND d.version < 3;

INSERT INTO report_definitions (
    report_id, version, sql_query, count_query, parameters, filters, columns,
    aggregations, group_by_fields, order_by_default, is_active, created_by, created_at
)
SELECT d.report_id, 3,
       replace(
           replace(d.sql_query,
               'LEFT JOIN platform_users creator ON creator.id = sar.created_by_user_id',
               'LEFT JOIN psp_users creator ON creator.user_id = sar.created_by_user_id'),
           'LEFT JOIN platform_users reviewer ON reviewer.id = sar.reviewed_by_user_id',
           'LEFT JOIN psp_users reviewer ON reviewer.user_id = sar.reviewed_by_user_id'),
       d.count_query, d.parameters, d.filters, d.columns, d.aggregations,
       d.group_by_fields, d.order_by_default, TRUE, d.created_by, CURRENT_TIMESTAMP
FROM report_definitions d
JOIN reports r ON r.id = d.report_id
WHERE r.report_code = 'SAR_001' AND d.version = 2
ON CONFLICT (report_id, version) DO NOTHING;

-- Foreign-key joins used by virtual-asset reports and record trails.
CREATE INDEX IF NOT EXISTS idx_crypto_wallet_customer ON crypto_wallet_profiles(customer_id);
CREATE INDEX IF NOT EXISTS idx_crypto_wallet_vasp ON crypto_wallet_profiles(vasp_id);
CREATE INDEX IF NOT EXISTS idx_crypto_wallet_asset_account ON crypto_wallet_profiles(asset_account_id);
CREATE INDEX IF NOT EXISTS idx_travel_rule_policy ON travel_rule_transfers(policy_id);
CREATE INDEX IF NOT EXISTS idx_travel_rule_originator_vasp ON travel_rule_transfers(originator_vasp_id);
CREATE INDEX IF NOT EXISTS idx_travel_rule_beneficiary_vasp ON travel_rule_transfers(beneficiary_vasp_id);
CREATE INDEX IF NOT EXISTS idx_va_regulator_access_grant ON virtual_asset_regulator_access_logs(grant_id);
