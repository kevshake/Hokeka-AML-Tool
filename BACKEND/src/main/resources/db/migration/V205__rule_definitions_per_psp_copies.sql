-- Per-PSP rule copies.
-- Each PSP gets its own editable copy of every system-default rule. Copies share the default's
-- name but are scoped by psp_id, so rule names are unique per owner rather than globally, and a
-- copy records the default it came from via derived_from_rule_id (making it undeletable-but-editable).

-- 1) Provenance column on the business table and, if present, the Envers audit table.
ALTER TABLE rule_definitions ADD COLUMN IF NOT EXISTS derived_from_rule_id BIGINT;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'rule_definitions_aud') THEN
        ALTER TABLE rule_definitions_aud ADD COLUMN IF NOT EXISTS derived_from_rule_id BIGINT;
    END IF;
END $$;

-- 2) Replace the global-unique name with uniqueness per owner (name, psp_id).
--    Postgres treats NULL psp_id rows as distinct, which is fine — there is only ever one global
--    (system default) row per name.
ALTER TABLE rule_definitions DROP CONSTRAINT IF EXISTS rule_definitions_name_key;
DROP INDEX IF EXISTS rule_definitions_name_key;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_rule_name_psp'
    ) THEN
        ALTER TABLE rule_definitions ADD CONSTRAINT uq_rule_name_psp UNIQUE (name, psp_id);
    END IF;
END $$;

-- 3) Index provenance for provisioning + delete-protection lookups.
CREATE INDEX IF NOT EXISTS idx_rule_derived_from ON rule_definitions (derived_from_rule_id);
