-- Add psp_id column to suspicious_activity_reports table for PSP isolation
-- This column is required by the SuspiciousActivityReport entity (pspId field)
-- Without this column, POST /compliance/sar fails with DATABASE_UNAVAILABLE (503)
-- 0 = Super Admin/System, >0 for specific PSP

ALTER TABLE suspicious_activity_reports
ADD COLUMN IF NOT EXISTS psp_id BIGINT;

-- Set default value for existing records (0 = System/Super Admin)
UPDATE suspicious_activity_reports
SET psp_id = 0
WHERE psp_id IS NULL;

-- Create index for efficient PSP filtering (used by getAllSars and report definitions)
CREATE INDEX IF NOT EXISTS idx_sar_psp_id ON suspicious_activity_reports(psp_id);

COMMENT ON COLUMN suspicious_activity_reports.psp_id IS 'PSP ID for multi-tenancy isolation. 0 for Super Admin/System, >0 for specific PSP. Used for PSP-based data isolation.';
