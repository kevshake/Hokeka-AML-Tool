UPDATE report_definitions d SET is_active = FALSE
FROM reports r
WHERE d.report_id = r.id AND r.report_code IN ('VA_002', 'VA_004') AND d.is_active = TRUE;

INSERT INTO report_definitions (report_id, version, sql_query, count_query, parameters, filters, columns, order_by_default, is_active, created_by, created_at)
SELECT r.id, 2,
 'SELECT tr.id AS travel_rule_transfer_id, tr.transaction_id AS multi_asset_transaction_id,
         t.customer_id AS multi_asset_customer_id, t.external_transaction_id,
         p.id AS travel_rule_jurisdiction_policy_id, p.policy_code, tr.jurisdiction,
         ov.id AS originator_vasp_directory_entry_id, ov.legal_name AS originator_vasp,
         bv.id AS beneficiary_vasp_directory_entry_id, bv.legal_name AS beneficiary_vasp,
         tr.status, tr.originator_verification, tr.originator_verification_reference,
         tr.originator_verified_by, tr.originator_verified_at,
         tr.beneficiary_verification, tr.beneficiary_verification_reference,
         tr.beneficiary_verified_by, tr.beneficiary_verified_at,
         tr.protocol, tr.payload_hash, tr.provider_message_id, tr.transmission_attempts,
         COALESCE((SELECT jsonb_agg(jsonb_build_object(
             ''attemptNumber'', a.attempt_number, ''status'', a.status, ''protocol'', a.protocol,
             ''providerMessageId'', a.provider_message_id, ''responseCode'', a.response_code,
             ''failureReason'', a.failure_reason, ''attemptedAt'', a.attempted_at,
             ''retainUntil'', a.retain_until) ORDER BY a.attempt_number)
           FROM travel_rule_transmission_attempts a
          WHERE a.travel_rule_transfer_id = tr.id), ''[]''::jsonb) AS transmission_attempt_history,
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
 '[{"field":"status","type":"ENUM","options":["NOT_REQUIRED","PENDING_DATA","PENDING_VERIFICATION","READY","TRANSMISSION_PENDING","TRANSMITTED","ACKNOWLEDGED","REJECTED","FAILED"]},{"field":"jurisdiction","type":"STRING"},{"field":"protocol","type":"STRING"}]'::jsonb,
 '[{"name":"travel_rule_transfer_id","type":"LONG","label":"Transfer ID"},{"name":"external_transaction_id","type":"STRING","label":"Transaction"},{"name":"policy_code","type":"STRING","label":"Policy"},{"name":"jurisdiction","type":"STRING","label":"Jurisdiction"},{"name":"beneficiary_vasp","type":"STRING","label":"Beneficiary VASP"},{"name":"status","type":"STRING","label":"Status"},{"name":"originator_verification","type":"STRING","label":"Originator Verification"},{"name":"originator_verification_reference","type":"STRING","label":"Originator Evidence"},{"name":"originator_verified_by","type":"STRING","label":"Originator Verified By"},{"name":"originator_verified_at","type":"DATETIME","label":"Originator Verified At"},{"name":"beneficiary_verification","type":"STRING","label":"Beneficiary Verification"},{"name":"beneficiary_verification_reference","type":"STRING","label":"Beneficiary Evidence"},{"name":"beneficiary_verified_by","type":"STRING","label":"Beneficiary Verified By"},{"name":"beneficiary_verified_at","type":"DATETIME","label":"Beneficiary Verified At"},{"name":"protocol","type":"STRING","label":"Protocol"},{"name":"transmission_attempts","type":"INTEGER","label":"Attempts"},{"name":"transmission_attempt_history","type":"JSON","label":"Attempt History"},{"name":"retain_until","type":"DATE","label":"Retain Until"}]'::jsonb,
 'created_at DESC', TRUE, NULL, CURRENT_TIMESTAMP FROM reports r WHERE r.report_code = 'VA_002'
ON CONFLICT (report_id, version) DO NOTHING;

INSERT INTO report_definitions (report_id, version, sql_query, count_query, parameters, filters, columns, order_by_default, is_active, created_by, created_at)
SELECT r.id, 2,
 'SELECT l.id AS virtual_asset_regulator_access_log_id,
         g.id AS virtual_asset_regulator_access_grant_id,
         g.regulator_name, g.jurisdiction, g.scopes, g.allowed_ip_addresses,
         g.expires_at, g.revoked_at, g.last_accessed_at, g.created_by, g.created_at AS grant_created_at,
         l.endpoint, l.query_hash, l.source_ip, l.user_agent,
         l.rows_returned, l.accessed_at, l.retain_until
    FROM virtual_asset_regulator_access_grants g
    LEFT JOIN virtual_asset_regulator_access_logs l ON l.grant_id = g.id
   WHERE g.psp_id = :pspId
     AND COALESCE(l.accessed_at, g.created_at) BETWEEN :dateFrom AND :dateTo
   ORDER BY COALESCE(l.accessed_at, g.created_at) DESC',
 'SELECT COUNT(*) FROM virtual_asset_regulator_access_grants g LEFT JOIN virtual_asset_regulator_access_logs l ON l.grant_id = g.id WHERE g.psp_id = :pspId AND COALESCE(l.accessed_at, g.created_at) BETWEEN :dateFrom AND :dateTo',
 '[{"name":"pspId","type":"LONG","required":true},{"name":"dateFrom","type":"DATETIME","required":true},{"name":"dateTo","type":"DATETIME","required":true}]'::jsonb,
 '[{"field":"regulator_name","type":"STRING"},{"field":"jurisdiction","type":"STRING"},{"field":"source_ip","type":"STRING"}]'::jsonb,
 '[{"name":"virtual_asset_regulator_access_grant_id","type":"LONG","label":"Grant ID"},{"name":"virtual_asset_regulator_access_log_id","type":"LONG","label":"Access ID"},{"name":"regulator_name","type":"STRING","label":"Regulator"},{"name":"jurisdiction","type":"STRING","label":"Jurisdiction"},{"name":"scopes","type":"JSON","label":"Scopes"},{"name":"allowed_ip_addresses","type":"JSON","label":"IP Allowlist"},{"name":"expires_at","type":"DATETIME","label":"Expires"},{"name":"revoked_at","type":"DATETIME","label":"Revoked"},{"name":"endpoint","type":"STRING","label":"Endpoint"},{"name":"source_ip","type":"STRING","label":"Source IP"},{"name":"rows_returned","type":"INTEGER","label":"Rows Returned"},{"name":"accessed_at","type":"DATETIME","label":"Accessed"},{"name":"retain_until","type":"DATE","label":"Retain Until"}]'::jsonb,
 'accessed_at DESC', TRUE, NULL, CURRENT_TIMESTAMP FROM reports r WHERE r.report_code = 'VA_004'
ON CONFLICT (report_id, version) DO NOTHING;
