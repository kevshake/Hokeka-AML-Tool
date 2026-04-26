-- HOK-60: sar_reference and case_reference were VARCHAR(255) which is too short
-- for client-supplied or auto-generated values (e.g. SAR-{timestamp}-{uuid}).
-- Widen both to TEXT with no data loss.

ALTER TABLE suspicious_activity_reports
    ALTER COLUMN sar_reference TYPE TEXT;

ALTER TABLE compliance_cases
    ALTER COLUMN case_reference TYPE TEXT;
