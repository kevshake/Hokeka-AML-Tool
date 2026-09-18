UPDATE report_definitions d SET is_active = FALSE
FROM reports r
WHERE d.report_id = r.id AND r.report_code = 'VA_001' AND d.is_active = TRUE;

INSERT INTO report_definitions (report_id, version, sql_query, count_query, parameters, filters, columns, order_by_default, is_active, created_by, created_at)
SELECT r.id, 2,
 'SELECT s.id AS wallet_screening_record_id, s.wallet_profile_id AS crypto_wallet_profile_id,
         s.transaction_id AS multi_asset_transaction_id, s.customer_id AS multi_asset_customer_id,
         c.external_customer_id, c.display_name, s.screened_address AS wallet_address, s.network,
         v.id AS vasp_directory_entry_id, v.legal_name AS vasp_name,
         s.trigger_type, s.provider, s.provider_reference, s.available, s.risk_score,
         s.categories, s.direct_exposure_percent, s.indirect_exposure_percent,
         s.maximum_exposure_depth, s.attributions, s.evidence, s.unavailable_reason,
         s.screened_at, s.retain_until
    FROM wallet_screening_records s
    JOIN multi_asset_customers c ON c.id = s.customer_id
    LEFT JOIN crypto_wallet_profiles w ON w.id = s.wallet_profile_id
    LEFT JOIN vasp_directory_entries v ON v.id = w.vasp_id
   WHERE s.psp_id = :pspId AND s.screened_at BETWEEN :dateFrom AND :dateTo
   ORDER BY s.screened_at DESC',
 'SELECT COUNT(*) FROM wallet_screening_records s WHERE s.psp_id = :pspId AND s.screened_at BETWEEN :dateFrom AND :dateTo',
 '[{"name":"pspId","type":"LONG","required":true},{"name":"dateFrom","type":"DATETIME","required":true},{"name":"dateTo","type":"DATETIME","required":true}]'::jsonb,
 '[{"field":"trigger_type","type":"ENUM","options":["ONBOARDING","ADDRESS_ADDED","DEPOSIT","PRE_WITHDRAWAL","PERIODIC","MANUAL"]},{"field":"available","type":"BOOLEAN"},{"field":"provider","type":"STRING"},{"field":"network","type":"STRING"}]'::jsonb,
 '[{"name":"wallet_screening_record_id","type":"LONG","label":"Screening ID"},{"name":"multi_asset_transaction_id","type":"LONG","label":"Transaction ID"},{"name":"external_customer_id","type":"STRING","label":"Customer"},{"name":"wallet_address","type":"STRING","label":"Screened Address"},{"name":"network","type":"STRING","label":"Network"},{"name":"trigger_type","type":"STRING","label":"Trigger"},{"name":"provider","type":"STRING","label":"Provider"},{"name":"provider_reference","type":"STRING","label":"Provider Reference"},{"name":"available","type":"BOOLEAN","label":"Available"},{"name":"risk_score","type":"INTEGER","label":"Risk Score"},{"name":"direct_exposure_percent","type":"DECIMAL","label":"Direct Exposure %"},{"name":"indirect_exposure_percent","type":"DECIMAL","label":"Indirect Exposure %"},{"name":"maximum_exposure_depth","type":"INTEGER","label":"Exposure Depth"},{"name":"screened_at","type":"DATETIME","label":"Screened"},{"name":"retain_until","type":"DATE","label":"Retain Until"}]'::jsonb,
 'screened_at DESC', TRUE, NULL, CURRENT_TIMESTAMP FROM reports r WHERE r.report_code = 'VA_001'
ON CONFLICT (report_id, version) DO NOTHING;
