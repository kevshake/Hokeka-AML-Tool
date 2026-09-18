-- V162: Add columns mapped by entity/Alert.java that no prior migration created.
--
-- Why: BACKEND/.../entity/Alert.java maps @Column(name=...) for `severity`,
-- `merchant_id`, `disposition_reason`, and `disposed_by`, but none of these were
-- ever added to the `alerts` table:
--   * V1__Initial_Schema.sql created alerts WITHOUT them
--   * V5 added merchant_id to compliance_cases (NOT alerts)
--   * V10 added only disposition + disposed_at
--   * V152 added psp_id, multi_asset_customer_id, source_type, source_reference
-- Production runs spring.jpa.hibernate.ddl-auto=validate (application.properties:91,
-- application-production.properties:40), so a clean deploy previously aborted at
-- startup with "missing column [severity] in table [alerts]". Dev/testenv masked
-- this via ddl-auto=create-drop/update. This migration closes that gap.
--
-- Column types mirror the entity fields exactly:
--   severity            -> String  "INFO|WARN|CRITICAL"         => VARCHAR(20)
--   merchant_id         -> Long                                 => BIGINT
--   disposition_reason  -> String @Column(columnDefinition=TEXT)=> TEXT
--   disposed_by         -> String  (investigator name, NOT FK)  => VARCHAR(255)
-- Idempotent (IF NOT EXISTS) so it is safe on databases where an earlier
-- ddl-auto run already added some of the columns.

ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS severity           VARCHAR(20),
    ADD COLUMN IF NOT EXISTS merchant_id        BIGINT,
    ADD COLUMN IF NOT EXISTS disposition_reason TEXT,
    ADD COLUMN IF NOT EXISTS disposed_by        VARCHAR(255);

-- merchant_id is used for alert filtering (e.g. merchant-scoped alert queries and
-- the multi-asset alert bridge that writes severity/merchant_id). Soft reference to
-- merchants(merchant_id) — kept loose to match the convention used elsewhere on alerts.
CREATE INDEX IF NOT EXISTS idx_alerts_merchant_id ON alerts(merchant_id);

-- severity is filtered by the regulatory report seed (V109__report_definitions_seed.sql:311)
-- and surfaced in alert dashboards; index it for the status/severity aggregation queries.
CREATE INDEX IF NOT EXISTS idx_alerts_severity ON alerts(severity);
