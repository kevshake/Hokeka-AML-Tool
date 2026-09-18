-- Persisted registry entries for synchronous scheme-report runs.
INSERT INTO reports (report_code, report_name, report_category, description, report_type, base_entity, requires_approval, enabled)
VALUES
    ('SCM_001', 'Central Bank Reporting Pack', 'REGULATORY_SUBMISSION',
     'Traceable central-bank pack containing compliance case and SAR source records.', 'REGULATORY',
     'compliance_cases', TRUE, TRUE),
    ('SCM_002', 'Scheme Monitoring Risk Report', 'FRAUD_INCIDENTS',
     'Traceable scheme-monitoring pack containing high-risk merchant source records.', 'DYNAMIC',
     'merchants', FALSE, TRUE)
ON CONFLICT (report_code) DO NOTHING;
