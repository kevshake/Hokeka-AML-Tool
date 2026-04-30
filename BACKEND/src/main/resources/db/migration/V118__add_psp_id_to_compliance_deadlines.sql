-- Add psp_id column to compliance_deadlines for tenant isolation.
-- NULL means a global / platform-wide deadline visible to every PSP
-- (e.g. CBK SAR filing window). Non-null restricts visibility to one PSP.
ALTER TABLE compliance_deadlines
    ADD COLUMN IF NOT EXISTS psp_id BIGINT NULL;

CREATE INDEX IF NOT EXISTS idx_compliance_deadlines_psp_id
    ON compliance_deadlines (psp_id);

CREATE INDEX IF NOT EXISTS idx_compliance_deadlines_psp_date
    ON compliance_deadlines (psp_id, deadline_date)
    WHERE completed = false;

-- Hibernate Envers audit mirror
ALTER TABLE compliance_deadlines_aud
    ADD COLUMN IF NOT EXISTS psp_id BIGINT NULL;
