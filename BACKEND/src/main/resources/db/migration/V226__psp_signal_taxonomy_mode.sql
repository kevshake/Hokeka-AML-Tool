ALTER TABLE psps
    ADD COLUMN IF NOT EXISTS signal_taxonomy_mode VARCHAR(32) NOT NULL DEFAULT 'INFLUENCE_DECISION';

ALTER TABLE psps DROP CONSTRAINT IF EXISTS chk_psp_signal_taxonomy_mode;
ALTER TABLE psps ADD CONSTRAINT chk_psp_signal_taxonomy_mode
    CHECK (signal_taxonomy_mode IN ('REPORTING_ONLY', 'INFLUENCE_DECISION', 'ALERT_ROUTING'));
