INSERT INTO reports (report_code, report_name, report_category, description, report_type, base_entity, requires_approval, enabled)
VALUES ('VA_005', 'VASP Sanctions Screening History', 'VIRTUAL_ASSET',
        'Append-only screening history for VASP legal names, trading names, and named beneficial owners.',
        'DYNAMIC', 'vasp_screening_records', TRUE, TRUE)
ON CONFLICT (report_code) DO NOTHING;

INSERT INTO report_definitions (report_id, version, sql_query, count_query, parameters, filters, columns, order_by_default, is_active, created_by, created_at)
SELECT r.id, 1,
 'SELECT s.id AS vasp_screening_record_id, s.vasp_id AS vasp_directory_entry_id,
         v.legal_name AS vasp_name, v.jurisdiction, v.licence_status, v.transfer_decision,
         s.subject_name, s.subject_type, s.provider, s.available, s.status,
         s.match_count, s.matches, s.evidence, s.screened_at, s.retain_until
    FROM vasp_screening_records s
    JOIN vasp_directory_entries v ON v.id = s.vasp_id
   WHERE s.psp_id = :pspId AND s.screened_at BETWEEN :dateFrom AND :dateTo
   ORDER BY s.screened_at DESC',
 'SELECT COUNT(*) FROM vasp_screening_records s WHERE s.psp_id = :pspId AND s.screened_at BETWEEN :dateFrom AND :dateTo',
 '[{"name":"pspId","type":"LONG","required":true},{"name":"dateFrom","type":"DATETIME","required":true},{"name":"dateTo","type":"DATETIME","required":true}]'::jsonb,
 '[{"field":"status","type":"ENUM","options":["CLEAR","POTENTIAL_MATCH","MATCH","UNAVAILABLE"]},{"field":"subject_type","type":"ENUM","options":["PERSON","ORGANIZATION"]},{"field":"provider","type":"STRING"}]'::jsonb,
 '[{"name":"vasp_screening_record_id","type":"LONG","label":"Screening ID"},{"name":"vasp_name","type":"STRING","label":"VASP"},{"name":"jurisdiction","type":"STRING","label":"Jurisdiction"},{"name":"subject_name","type":"STRING","label":"Screened Subject"},{"name":"subject_type","type":"STRING","label":"Subject Type"},{"name":"provider","type":"STRING","label":"Provider"},{"name":"available","type":"BOOLEAN","label":"Available"},{"name":"status","type":"STRING","label":"Status"},{"name":"match_count","type":"INTEGER","label":"Matches"},{"name":"screened_at","type":"DATETIME","label":"Screened"},{"name":"retain_until","type":"DATE","label":"Retain Until"}]'::jsonb,
 'screened_at DESC', TRUE, NULL, CURRENT_TIMESTAMP FROM reports r WHERE r.report_code = 'VA_005'
ON CONFLICT (report_id, version) DO NOTHING;
