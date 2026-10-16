-- WP-00: tenant-scope payment blacklist. Null psp_id + platform_wide = shared list.
ALTER TABLE payment_blacklist_entries
    ADD COLUMN IF NOT EXISTS psp_id BIGINT,
    ADD COLUMN IF NOT EXISTS platform_wide BOOLEAN NOT NULL DEFAULT FALSE;

-- Prefer PSP of the creating user when derivable; otherwise keep as platform-wide.
UPDATE payment_blacklist_entries e
SET psp_id = u.psp_id,
    platform_wide = FALSE
FROM platform_users u
WHERE e.created_by IS NOT NULL
  AND e.created_by = u.id
  AND u.psp_id IS NOT NULL
  AND e.psp_id IS NULL;

UPDATE payment_blacklist_entries
SET platform_wide = TRUE
WHERE psp_id IS NULL;

ALTER TABLE payment_blacklist_entries
    DROP CONSTRAINT IF EXISTS payment_blacklist_entries_entry_type_entry_value_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_blacklist_tenant
    ON payment_blacklist_entries (psp_id, entry_type, entry_value)
    WHERE psp_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_blacklist_platform
    ON payment_blacklist_entries (entry_type, entry_value)
    WHERE psp_id IS NULL AND platform_wide = TRUE;

CREATE INDEX IF NOT EXISTS idx_payment_blacklist_psp_lookup
    ON payment_blacklist_entries (psp_id, entry_type, entry_value, active);
