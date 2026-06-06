-- V134: Rule definitions — system-managed protection + taxonomy
--
-- Adds the metadata needed to (a) ship a curated catalog of default AML / Fraud
-- / Screening rules that operators can disable but not delete, and (b) drive a
-- dropdown-based rule editor instead of free-form text fields.
--
-- Design notes:
--   - is_system_managed: TRUE for the 53 seeded defaults. DELETE is forbidden
--     in RulesController when this is TRUE; UPDATE/enable/disable are allowed.
--   - parameters JSONB: per-rule tunables (thresholds, time windows, currency).
--     Editor renders a typed form from this schema instead of asking for SpEL.
--   - external_code: the canonical R-{n} identifier so we can cross-reference
--     compliance specs without depending on auto-incremented IDs.
--   - rule_subtype, applies_to, typology, checks_for: the taxonomy columns the
--     editor uses to drive dropdowns. These mirror industry-standard fields
--     (Velocity, Anomaly detection, Screening, etc.) and feed reporting.

ALTER TABLE rule_definitions
    ADD COLUMN IF NOT EXISTS is_system_managed BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS category          VARCHAR(20),    -- AML | FRAUD | SCREENING
    ADD COLUMN IF NOT EXISTS rule_subtype      VARCHAR(64),    -- Velocity, Anomaly detection, Pattern recognition, Blacklist, Screening, ...
    ADD COLUMN IF NOT EXISTS applies_to        VARCHAR(20),    -- Transaction | User
    ADD COLUMN IF NOT EXISTS typology          VARCHAR(255),   -- Money mules, Structuring, Unusual behaviour, ...
    ADD COLUMN IF NOT EXISTS checks_for        TEXT,           -- comma-separated input variables
    ADD COLUMN IF NOT EXISTS external_code     VARCHAR(20),    -- R-1, R-2, R-3, ...
    ADD COLUMN IF NOT EXISTS recommended       BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS sample_use_case   TEXT,
    ADD COLUMN IF NOT EXISTS parameters        TEXT;   -- JSON string stored as text for compatibility

-- The ruleset list page filters by category and is_system_managed, so index both.
CREATE INDEX IF NOT EXISTS idx_rule_definitions_category
    ON rule_definitions(category)
    WHERE category IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rule_definitions_system
    ON rule_definitions(is_system_managed)
    WHERE is_system_managed = TRUE;

-- external_code is unique among system-managed rules so we can re-run the seed
-- migration safely (V142 uses ON CONFLICT DO NOTHING).
CREATE UNIQUE INDEX IF NOT EXISTS uk_rule_definitions_external_code
    ON rule_definitions(external_code)
    WHERE external_code IS NOT NULL;
