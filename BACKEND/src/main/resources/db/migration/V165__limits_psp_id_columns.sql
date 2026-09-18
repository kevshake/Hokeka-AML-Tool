-- V165: Add psp_id columns mapped by the limits entities that V9 never created.
--
-- Why: entity/limits/VelocityRule.java:61 and entity/limits/RiskThreshold.java:55 both map
-- @Column(name="psp_id") private Long pspId (nullable — null = super-admin/global rule,
-- set = PSP-specific rule). But V9__limits_aml_management.sql created `velocity_rules`
-- (V9:71-92) and `risk_thresholds` (V9:49-68) WITHOUT a psp_id column, and no later
-- migration added it. Under prod/testenv spring.jpa.hibernate.ddl-auto=validate
-- (application.properties:91, application-production.properties:40) Hibernate schema
-- validation fails at startup ("missing column [psp_id] in table [velocity_rules]"),
-- so the app does not boot on a clean/validated schema. Dev's ddl-auto=create-drop masked it.
--
-- Type mirrors the entity fields exactly: Long -> BIGINT, nullable.
-- Idempotent (IF NOT EXISTS) so it is safe where an earlier ddl-auto run already added it.
-- Neither entity is @Audited, so no matching *_aud change is required.

ALTER TABLE velocity_rules
    ADD COLUMN IF NOT EXISTS psp_id BIGINT;

ALTER TABLE risk_thresholds
    ADD COLUMN IF NOT EXISTS psp_id BIGINT;

-- Both tables are queried PSP-scoped (active rules for a given PSP, plus global rules where
-- psp_id IS NULL). Index psp_id to support those lookups.
CREATE INDEX IF NOT EXISTS idx_velocity_rules_psp_id  ON velocity_rules(psp_id);
CREATE INDEX IF NOT EXISTS idx_risk_thresholds_psp_id ON risk_thresholds(psp_id);
