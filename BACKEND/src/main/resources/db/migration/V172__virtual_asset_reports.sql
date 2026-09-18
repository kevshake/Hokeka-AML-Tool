INSERT INTO reports (report_code, report_name, report_category, description, report_type, base_entity, requires_approval, enabled)
VALUES
 ('VA_001', 'Virtual Asset Wallet Screening History', 'VIRTUAL_ASSET', 'Append-only wallet screening, exposure, attribution, and provider availability history.', 'DYNAMIC', 'wallet_screening_records', FALSE, TRUE),
 ('VA_002', 'Travel Rule Transfer Audit', 'VIRTUAL_ASSET', 'Jurisdiction policy, verification, transmission, acknowledgement, retention, and payload-hash evidence.', 'DYNAMIC', 'travel_rule_transfers', TRUE, TRUE),
 ('VA_003', 'Counterparty VASP Due Diligence', 'VIRTUAL_ASSET', 'VASP licence, regulator, sanctions, ownership, protocol, risk, and transfer-decision records.', 'DYNAMIC', 'vasp_directory_entries', FALSE, TRUE),
 ('VA_004', 'Virtual Asset Regulator Access Audit', 'VIRTUAL_ASSET', 'Immutable audit of automated regulator read-only access to virtual-asset records.', 'DYNAMIC', 'virtual_asset_regulator_access_logs', TRUE, TRUE)
ON CONFLICT (report_code) DO NOTHING;

INSERT INTO report_definitions (report_id, version, sql_query, count_query, parameters, filters, columns, order_by_default, is_active, created_by, created_at)
SELECT r.id, 1,
 'SELECT s.id AS wallet_screening_record_id, s.wallet_profile_id AS crypto_wallet_profile_id,
         s.transaction_id AS multi_asset_transaction_id, w.customer_id AS multi_asset_customer_id,
         c.external_customer_id, c.display_name, w.wallet_address, w.network,
         v.id AS vasp_directory_entry_id, v.legal_name AS vasp_name,
         s.trigger_type, s.provider, s.provider_reference, s.available, s.risk_score,
         s.categories, s.direct_exposure_percent, s.indirect_exposure_percent,
         s.maximum_exposure_depth, s.attributions, s.evidence, s.unavailable_reason,
         s.screened_at, s.retain_until
    FROM wallet_screening_records s
    JOIN crypto_wallet_profiles w ON w.id = s.wallet_profile_id
    JOIN multi_asset_customers c ON c.id = w.customer_id
    LEFT JOIN vasp_directory_entries v ON v.id = w.vasp_id
   WHERE s.psp_id = :pspId AND s.screened_at BETWEEN :dateFrom AND :dateTo
   ORDER BY s.screened_at DESC',
 'SELECT COUNT(*) FROM wallet_screening_records s WHERE s.psp_id = :pspId AND s.screened_at BETWEEN :dateFrom AND :dateTo',
 '[{"name":"pspId","type":"LONG","required":true},{"name":"dateFrom","type":"DATETIME","required":true},{"name":"dateTo","type":"DATETIME","required":true}]'::jsonb,
 '[{"field":"trigger_type","type":"ENUM","options":["ONBOARDING","ADDRESS_ADDED","DEPOSIT","PRE_WITHDRAWAL","PERIODIC","MANUAL"]},{"field":"available","type":"BOOLEAN"},{"field":"provider","type":"STRING"}]'::jsonb,
 '[{"name":"wallet_screening_record_id","type":"LONG","label":"Screening ID"},{"name":"external_customer_id","type":"STRING","label":"Customer"},{"name":"wallet_address","type":"STRING","label":"Wallet"},{"name":"network","type":"STRING","label":"Network"},{"name":"trigger_type","type":"STRING","label":"Trigger"},{"name":"provider","type":"STRING","label":"Provider"},{"name":"available","type":"BOOLEAN","label":"Available"},{"name":"risk_score","type":"INTEGER","label":"Risk Score"},{"name":"direct_exposure_percent","type":"DECIMAL","label":"Direct Exposure %"},{"name":"indirect_exposure_percent","type":"DECIMAL","label":"Indirect Exposure %"},{"name":"maximum_exposure_depth","type":"INTEGER","label":"Exposure Depth"},{"name":"screened_at","type":"DATETIME","label":"Screened"}]'::jsonb,
 'screened_at DESC', TRUE, NULL, CURRENT_TIMESTAMP FROM reports r WHERE r.report_code = 'VA_001'
ON CONFLICT (report_id, version) DO NOTHING;

INSERT INTO report_definitions (report_id, version, sql_query, count_query, parameters, filters, columns, order_by_default, is_active, created_by, created_at)
SELECT r.id, 1,
 'SELECT tr.id AS travel_rule_transfer_id, tr.transaction_id AS multi_asset_transaction_id,
         t.customer_id AS multi_asset_customer_id, t.external_transaction_id,
         p.id AS travel_rule_jurisdiction_policy_id, p.policy_code, tr.jurisdiction,
         ov.id AS originator_vasp_directory_entry_id, ov.legal_name AS originator_vasp,
         bv.id AS beneficiary_vasp_directory_entry_id, bv.legal_name AS beneficiary_vasp,
         tr.status, tr.originator_verification, tr.beneficiary_verification,
         tr.protocol, tr.payload_hash, tr.provider_message_id, tr.transmission_attempts,
         tr.transmitted_at, tr.acknowledged_at, tr.failure_reason,
         tr.retain_until, tr.created_at, tr.updated_at
    FROM travel_rule_transfers tr
    JOIN multi_asset_transactions t ON t.id = tr.transaction_id
    LEFT JOIN travel_rule_jurisdiction_policies p ON p.id = tr.policy_id
    LEFT JOIN vasp_directory_entries ov ON ov.id = tr.originator_vasp_id
    LEFT JOIN vasp_directory_entries bv ON bv.id = tr.beneficiary_vasp_id
   WHERE tr.psp_id = :pspId AND tr.created_at BETWEEN :dateFrom AND :dateTo
   ORDER BY tr.created_at DESC',
 'SELECT COUNT(*) FROM travel_rule_transfers tr WHERE tr.psp_id = :pspId AND tr.created_at BETWEEN :dateFrom AND :dateTo',
 '[{"name":"pspId","type":"LONG","required":true},{"name":"dateFrom","type":"DATETIME","required":true},{"name":"dateTo","type":"DATETIME","required":true}]'::jsonb,
 '[{"field":"status","type":"ENUM","options":["NOT_REQUIRED","PENDING_DATA","PENDING_VERIFICATION","READY","TRANSMITTED","ACKNOWLEDGED","REJECTED","FAILED"]},{"field":"jurisdiction","type":"STRING"},{"field":"protocol","type":"STRING"}]'::jsonb,
 '[{"name":"travel_rule_transfer_id","type":"LONG","label":"Transfer ID"},{"name":"external_transaction_id","type":"STRING","label":"Transaction"},{"name":"policy_code","type":"STRING","label":"Policy"},{"name":"jurisdiction","type":"STRING","label":"Jurisdiction"},{"name":"beneficiary_vasp","type":"STRING","label":"Beneficiary VASP"},{"name":"status","type":"STRING","label":"Status"},{"name":"originator_verification","type":"STRING","label":"Originator Verification"},{"name":"beneficiary_verification","type":"STRING","label":"Beneficiary Verification"},{"name":"protocol","type":"STRING","label":"Protocol"},{"name":"transmission_attempts","type":"INTEGER","label":"Attempts"},{"name":"retain_until","type":"DATE","label":"Retain Until"}]'::jsonb,
 'created_at DESC', TRUE, NULL, CURRENT_TIMESTAMP FROM reports r WHERE r.report_code = 'VA_002'
ON CONFLICT (report_id, version) DO NOTHING;

INSERT INTO report_definitions (report_id, version, sql_query, count_query, parameters, filters, columns, order_by_default, is_active, created_by, created_at)
SELECT r.id, 1,
 'SELECT v.id AS vasp_directory_entry_id, v.legal_name, v.trading_name, v.jurisdiction,
         v.regulator_name, v.licence_number, v.licence_status, v.licence_valid_until,
         v.registration_number, v.travel_rule_protocols, v.sanctions_status,
         v.sanctions_screened_at, v.beneficial_ownership, v.wallet_clusters,
         v.risk_score, v.risk_level, v.transfer_decision,
         v.last_reviewed_at, v.next_review_at,
         COUNT(DISTINCT w.id) AS linked_wallet_count
    FROM vasp_directory_entries v
    LEFT JOIN crypto_wallet_profiles w ON w.vasp_id = v.id
   WHERE v.psp_id = :pspId AND v.updated_at BETWEEN :dateFrom AND :dateTo
   GROUP BY v.id ORDER BY v.risk_score DESC, v.legal_name',
 'SELECT COUNT(*) FROM vasp_directory_entries v WHERE v.psp_id = :pspId AND v.updated_at BETWEEN :dateFrom AND :dateTo',
 '[{"name":"pspId","type":"LONG","required":true},{"name":"dateFrom","type":"DATETIME","required":true},{"name":"dateTo","type":"DATETIME","required":true}]'::jsonb,
 '[{"field":"jurisdiction","type":"STRING"},{"field":"licence_status","type":"ENUM","options":["LICENSED","PENDING","SUSPENDED","REVOKED","UNLICENSED","UNKNOWN"]},{"field":"transfer_decision","type":"ENUM","options":["PERMITTED","REVIEW","PROHIBITED"]}]'::jsonb,
 '[{"name":"vasp_directory_entry_id","type":"LONG","label":"VASP ID"},{"name":"legal_name","type":"STRING","label":"Legal Name"},{"name":"jurisdiction","type":"STRING","label":"Jurisdiction"},{"name":"regulator_name","type":"STRING","label":"Regulator"},{"name":"licence_status","type":"STRING","label":"Licence"},{"name":"sanctions_status","type":"STRING","label":"Sanctions"},{"name":"risk_score","type":"INTEGER","label":"Risk Score"},{"name":"transfer_decision","type":"STRING","label":"Transfer Decision"},{"name":"linked_wallet_count","type":"INTEGER","label":"Wallets"},{"name":"next_review_at","type":"DATETIME","label":"Next Review"}]'::jsonb,
 'risk_score DESC, legal_name', TRUE, NULL, CURRENT_TIMESTAMP FROM reports r WHERE r.report_code = 'VA_003'
ON CONFLICT (report_id, version) DO NOTHING;

INSERT INTO report_definitions (report_id, version, sql_query, count_query, parameters, filters, columns, order_by_default, is_active, created_by, created_at)
SELECT r.id, 1,
 'SELECT l.id AS virtual_asset_regulator_access_log_id,
         l.grant_id AS virtual_asset_regulator_access_grant_id,
         g.regulator_name, g.jurisdiction, g.scopes,
         l.endpoint, l.query_hash, l.source_ip, l.user_agent,
         l.rows_returned, l.accessed_at, l.retain_until
    FROM virtual_asset_regulator_access_logs l
    JOIN virtual_asset_regulator_access_grants g ON g.id = l.grant_id
   WHERE l.psp_id = :pspId AND l.accessed_at BETWEEN :dateFrom AND :dateTo
   ORDER BY l.accessed_at DESC',
 'SELECT COUNT(*) FROM virtual_asset_regulator_access_logs l WHERE l.psp_id = :pspId AND l.accessed_at BETWEEN :dateFrom AND :dateTo',
 '[{"name":"pspId","type":"LONG","required":true},{"name":"dateFrom","type":"DATETIME","required":true},{"name":"dateTo","type":"DATETIME","required":true}]'::jsonb,
 '[{"field":"regulator_name","type":"STRING"},{"field":"jurisdiction","type":"STRING"},{"field":"source_ip","type":"STRING"}]'::jsonb,
 '[{"name":"virtual_asset_regulator_access_log_id","type":"LONG","label":"Access ID"},{"name":"regulator_name","type":"STRING","label":"Regulator"},{"name":"jurisdiction","type":"STRING","label":"Jurisdiction"},{"name":"endpoint","type":"STRING","label":"Endpoint"},{"name":"source_ip","type":"STRING","label":"Source IP"},{"name":"rows_returned","type":"INTEGER","label":"Rows Returned"},{"name":"accessed_at","type":"DATETIME","label":"Accessed"},{"name":"retain_until","type":"DATE","label":"Retain Until"}]'::jsonb,
 'accessed_at DESC', TRUE, NULL, CURRENT_TIMESTAMP FROM reports r WHERE r.report_code = 'VA_004'
ON CONFLICT (report_id, version) DO NOTHING;
