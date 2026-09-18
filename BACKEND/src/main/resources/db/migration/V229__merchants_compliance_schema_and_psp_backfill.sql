-- V229: Align merchants + compliance_cases schema with JPA entities;
-- backfill demo merchant PSP ownership (seed rows had NULL psp_id).

-- Merchant columns mapped by Merchant.java but missing from Flyway history
ALTER TABLE merchants
    ADD COLUMN IF NOT EXISTS contact_email VARCHAR(200);

ALTER TABLE merchants
    ADD COLUMN IF NOT EXISTS daily_limit NUMERIC(19, 2) DEFAULT 0;

ALTER TABLE merchants
    ADD COLUMN IF NOT EXISTS current_usage NUMERIC(19, 2) DEFAULT 0;

ALTER TABLE merchants
    ADD COLUMN IF NOT EXISTS is_pep BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE merchants SET daily_limit = 0 WHERE daily_limit IS NULL;
UPDATE merchants SET current_usage = 0 WHERE current_usage IS NULL;

-- ComplianceCase.description is audited (compliance_cases_aud) but was never on the live table
ALTER TABLE compliance_cases
    ADD COLUMN IF NOT EXISTS description TEXT;

-- Assign demo / seed merchants to demo PSP tenants (product rule: every merchant belongs to a PSP)
UPDATE merchants m
SET psp_id = p.psp_id
FROM psps p
WHERE m.psp_id IS NULL
  AND p.psp_code = 'DEMO_VELOCITY'
  AND m.registration_number = 'REG001'
  AND m.country = 'USA';

UPDATE merchants m
SET psp_id = p.psp_id
FROM psps p
WHERE m.psp_id IS NULL
  AND p.psp_code = 'DEMO_APEX'
  AND m.registration_number = 'REG002'
  AND m.country = 'GBR';

UPDATE merchants m
SET psp_id = p.psp_id
FROM psps p
WHERE m.psp_id IS NULL
  AND p.psp_code = 'DEMO_VELOCITY'
  AND m.registration_number = 'REG003'
  AND m.country = 'CHN';

UPDATE merchants m
SET psp_id = p.psp_id
FROM psps p
WHERE m.psp_id IS NULL AND m.country = 'KEN' AND p.psp_code = 'DEMO_MWANANCHI';

UPDATE merchants m
SET psp_id = p.psp_id
FROM psps p
WHERE m.psp_id IS NULL AND m.country = 'GBR' AND p.psp_code = 'DEMO_APEX';

UPDATE merchants m
SET psp_id = p.psp_id
FROM psps p
WHERE m.psp_id IS NULL AND m.country = 'USA' AND p.psp_code = 'DEMO_VELOCITY';

UPDATE merchants m
SET psp_id = (SELECT psp_id FROM psps WHERE psp_code = 'DEMO_VELOCITY' LIMIT 1)
WHERE m.psp_id IS NULL
  AND EXISTS (SELECT 1 FROM psps WHERE psp_code = 'DEMO_VELOCITY');

-- Propagate merchant PSP to cases missing tenant scope
UPDATE compliance_cases cc
SET psp_id = m.psp_id
FROM merchants m
WHERE cc.psp_id IS NULL
  AND cc.merchant_id = m.merchant_id
  AND m.psp_id IS NOT NULL;
