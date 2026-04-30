-- Add psp_id column to audit_logs_enhanced table for PSP isolation
-- This column is required by the AuditLog entity
-- 0 = Super Admin/System, >0 for specific PSP

ALTER TABLE audit_logs_enhanced
ADD COLUMN IF NOT EXISTS psp_id BIGINT;

-- Set default value for existing records (0 = System/Super Admin)
UPDATE audit_logs_enhanced
SET psp_id = 0
WHERE psp_id IS NULL;

-- Create index for efficient PSP filtering
CREATE INDEX IF NOT EXISTS idx_audit_logs_enhanced_psp_id ON audit_logs_enhanced(psp_id);

-- Add comment for documentation
COMMENT ON COLUMN audit_logs_enhanced.psp_id IS 'PSP ID for multi-tenancy isolation. 0 for Super Admin/System, >0 for specific PSP. Used for PSP-based data isolation.';
