ALTER TABLE global_limits
    ADD COLUMN IF NOT EXISTS psp_id BIGINT;

ALTER TABLE country_compliance_rules
    ADD COLUMN IF NOT EXISTS psp_id BIGINT;

ALTER TABLE global_limits
    DROP CONSTRAINT IF EXISTS unique_global_limit_name;

ALTER TABLE risk_thresholds
    DROP CONSTRAINT IF EXISTS unique_risk_level;

ALTER TABLE velocity_rules
    DROP CONSTRAINT IF EXISTS unique_velocity_rule_name;

ALTER TABLE country_compliance_rules
    DROP CONSTRAINT IF EXISTS unique_country_code;

CREATE INDEX IF NOT EXISTS idx_global_limits_psp_id
    ON global_limits(psp_id);

CREATE INDEX IF NOT EXISTS idx_country_compliance_rules_psp_id
    ON country_compliance_rules(psp_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_global_limits_scope_name
    ON global_limits ((COALESCE(psp_id, 0)), name);

CREATE UNIQUE INDEX IF NOT EXISTS uq_risk_thresholds_scope_level
    ON risk_thresholds ((COALESCE(psp_id, 0)), risk_level);

CREATE UNIQUE INDEX IF NOT EXISTS uq_velocity_rules_scope_name
    ON velocity_rules ((COALESCE(psp_id, 0)), rule_name);

CREATE UNIQUE INDEX IF NOT EXISTS uq_country_compliance_scope_country
    ON country_compliance_rules ((COALESCE(psp_id, 0)), country_code);
