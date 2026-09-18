-- HOK-60: sar_reference and case_reference were VARCHAR(255) which is too short.
-- Both columns may not exist on DBs created purely from migrations (only on Hibernate ddl-auto DBs).
-- This migration is idempotent: it adds the column if absent, then widens if it's VARCHAR.

-- suspicious_activity_reports.sar_reference
ALTER TABLE suspicious_activity_reports
    ALTER COLUMN sar_reference TYPE TEXT;

-- compliance_cases.case_reference
-- Ensure the column exists (Hibernate ddl-auto creates it, but migrations may not)
ALTER TABLE compliance_cases ADD COLUMN IF NOT EXISTS case_reference TEXT;
-- Widen if it exists as a narrower type
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'compliance_cases'
               AND column_name = 'case_reference'
               AND data_type NOT IN ('text')) THEN
        ALTER TABLE compliance_cases ALTER COLUMN case_reference TYPE TEXT;
    END IF;
END $$;
