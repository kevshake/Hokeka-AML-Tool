-- ============================================================================
-- V255: Align the Flyway schema with the JPA/Envers mappings so that the
-- production profile can run with spring.jpa.hibernate.ddl-auto=validate.
--
-- Until now application-production.properties also set
-- hibernate.hbm2ddl.auto=none, which overrode ddl-auto and silently disabled
-- validation. The drift below therefore only surfaced at runtime, e.g. every
-- rule create/update failed on rule_definitions_aud.applies_to, and resolving a
-- case wrote a VARCHAR into a JSONB column. The exact list was produced by
-- ProductionSchemaValidationIntegrationTest (Hibernate boot metadata diffed
-- against a fresh Flyway database). Everything is idempotent (IF NOT EXISTS /
-- type checks) so databases that were patched by hand converge too.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Envers audit tables: columns the audited entities write.
--    Required by the mappings: rule_definitions_aud (10), compliance_cases_aud
--    .case_id, cbk_submissions_aud.source_record_count. The remaining columns
--    mirror base-table columns so each *_aud table is a full copy of its base
--    table (nullable, as Envers history rows are partial).
-- ----------------------------------------------------------------------------
ALTER TABLE rule_definitions_aud ADD COLUMN IF NOT EXISTS applies_to        VARCHAR(20);
ALTER TABLE rule_definitions_aud ADD COLUMN IF NOT EXISTS category          VARCHAR(20);
ALTER TABLE rule_definitions_aud ADD COLUMN IF NOT EXISTS checks_for        TEXT;
ALTER TABLE rule_definitions_aud ADD COLUMN IF NOT EXISTS external_code     VARCHAR(20);
ALTER TABLE rule_definitions_aud ADD COLUMN IF NOT EXISTS is_system_managed BOOLEAN;
ALTER TABLE rule_definitions_aud ADD COLUMN IF NOT EXISTS parameters        TEXT;
ALTER TABLE rule_definitions_aud ADD COLUMN IF NOT EXISTS recommended       BOOLEAN;
ALTER TABLE rule_definitions_aud ADD COLUMN IF NOT EXISTS rule_subtype      VARCHAR(64);
ALTER TABLE rule_definitions_aud ADD COLUMN IF NOT EXISTS sample_use_case   TEXT;
ALTER TABLE rule_definitions_aud ADD COLUMN IF NOT EXISTS typology          VARCHAR(255);

ALTER TABLE compliance_cases_aud ADD COLUMN IF NOT EXISTS case_id     BIGINT;
ALTER TABLE compliance_cases_aud ADD COLUMN IF NOT EXISTS assigned_to VARCHAR(255);
ALTER TABLE compliance_cases_aud ADD COLUMN IF NOT EXISTS case_status VARCHAR(255);
ALTER TABLE compliance_cases_aud ADD COLUMN IF NOT EXISTS case_type   VARCHAR(255);
ALTER TABLE compliance_cases_aud ADD COLUMN IF NOT EXISTS closed_at   TIMESTAMP;
ALTER TABLE compliance_cases_aud ADD COLUMN IF NOT EXISTS created_by  VARCHAR(255);
ALTER TABLE compliance_cases_aud ADD COLUMN IF NOT EXISTS due_date    TIMESTAMP;

ALTER TABLE cbk_submissions_aud ADD COLUMN IF NOT EXISTS source_record_count INTEGER;

ALTER TABLE case_evidence_aud ADD COLUMN IF NOT EXISTS checksum      VARCHAR(255);
ALTER TABLE case_evidence_aud ADD COLUMN IF NOT EXISTS evidence_type VARCHAR(255);
ALTER TABLE case_evidence_aud ADD COLUMN IF NOT EXISTS file_size     BIGINT;

ALTER TABLE case_alerts_aud ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
ALTER TABLE case_alerts_aud ADD COLUMN IF NOT EXISTS severity   VARCHAR(255);

ALTER TABLE case_notes_aud ADD COLUMN IF NOT EXISTS parent_note_id BIGINT;
ALTER TABLE case_notes_aud ADD COLUMN IF NOT EXISTS updated_at     TIMESTAMP;

-- SuspiciousActivityReport.suspiciousTransactions is an audited @ManyToMany
-- (join table sar_transactions); Envers needs its middle audit table.
CREATE TABLE IF NOT EXISTS sar_transactions_aud (
    rev     INTEGER  NOT NULL,
    sar_id  BIGINT   NOT NULL,
    txn_id  BIGINT   NOT NULL,
    revtype SMALLINT,
    PRIMARY KEY (rev, sar_id, txn_id),
    CONSTRAINT fk_sar_transactions_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev)
);
CREATE INDEX IF NOT EXISTS idx_sar_transactions_aud_rev ON sar_transactions_aud (rev);

-- ----------------------------------------------------------------------------
-- 2. Integer primary/foreign keys mapped as Long (BIGINT) by the entities.
--    Widening INTEGER -> BIGINT is lossless; the owning sequences are widened
--    too so the id space matches the column. Only small configuration tables.
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    target RECORD;
    seq    TEXT;
BEGIN
    FOR target IN
        SELECT * FROM (VALUES
            ('billing_calculations',     'calculation_id'),
            ('clients',                  'client_id'),
            ('cost_metrics',             'metric_id'),
            ('high_risk_countries',      'id'),
            ('model_config',             'id'),
            ('model_metrics',            'id'),
            ('roles',                    'id'),
            ('platform_users',           'id'),
            ('platform_users',           'role_id'),
            ('role_permissions_dynamic', 'role_id'),
            ('subscriptions',            'subscription_id')
        ) AS t(tbl, col)
    LOOP
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public' AND table_name = target.tbl
                     AND column_name = target.col AND data_type = 'integer') THEN
            EXECUTE format('ALTER TABLE %I ALTER COLUMN %I TYPE BIGINT', target.tbl, target.col);
        END IF;
        seq := pg_get_serial_sequence(target.tbl, target.col);
        IF seq IS NOT NULL THEN
            EXECUTE format('ALTER SEQUENCE %s AS BIGINT', seq);
        END IF;
    END LOOP;
END $$;

-- ----------------------------------------------------------------------------
-- 3. Floating-point scores stored as NUMERIC while the entities (and the
--    case_alerts_aud audit column) use Double -> DOUBLE PRECISION.
-- ----------------------------------------------------------------------------
ALTER TABLE case_alerts ALTER COLUMN score TYPE DOUBLE PRECISION;
ALTER TABLE monthly_report_metrics ALTER COLUMN metric_value TYPE DOUBLE PRECISION;

-- ----------------------------------------------------------------------------
-- 4. compliance_cases.resolution: V2 created it as JSONB, but the entity (and
--    compliance_cases_aud) store a plain decision code such as CLEARED or
--    SAR_FILED, so every case resolution failed with a type error. Convert to
--    TEXT; legacy JSON string values are unwrapped, JSON objects keep their
--    JSON text.
-- ----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'public' AND table_name = 'compliance_cases'
                 AND column_name = 'resolution' AND data_type = 'jsonb') THEN
        ALTER TABLE compliance_cases ALTER COLUMN resolution TYPE TEXT
            USING CASE WHEN jsonb_typeof(resolution) = 'string' THEN resolution #>> '{}'
                       ELSE resolution::text END;
    END IF;
END $$;

-- ----------------------------------------------------------------------------
-- 5. merchant_risk_scores: MerchantRiskScore persists a component breakdown
--    that V2's table never had. Add the columns, backfill from the legacy
--    columns, and keep the legacy total_points / recommended_action (read by
--    merchant_risk_summary_mv, v_high_risk_merchants and an index) in sync.
-- ----------------------------------------------------------------------------
ALTER TABLE merchant_risk_scores ADD COLUMN IF NOT EXISTS total_score         INTEGER;
ALTER TABLE merchant_risk_scores ADD COLUMN IF NOT EXISTS sanctions_score     INTEGER;
ALTER TABLE merchant_risk_scores ADD COLUMN IF NOT EXISTS pep_score           INTEGER;
ALTER TABLE merchant_risk_scores ADD COLUMN IF NOT EXISTS country_risk_score  INTEGER;
ALTER TABLE merchant_risk_scores ADD COLUMN IF NOT EXISTS industry_risk_score INTEGER;
ALTER TABLE merchant_risk_scores ADD COLUMN IF NOT EXISTS volume_risk_score   INTEGER;
ALTER TABLE merchant_risk_scores ADD COLUMN IF NOT EXISTS business_age_score  INTEGER;
ALTER TABLE merchant_risk_scores ADD COLUMN IF NOT EXISTS decision            VARCHAR(50);
ALTER TABLE merchant_risk_scores ADD COLUMN IF NOT EXISTS decision_reason     TEXT;
ALTER TABLE merchant_risk_scores ADD COLUMN IF NOT EXISTS calculated_by       VARCHAR(100);

UPDATE merchant_risk_scores SET total_score = total_points WHERE total_score IS NULL;
UPDATE merchant_risk_scores SET decision = recommended_action
WHERE decision IS NULL AND recommended_action IS NOT NULL;
ALTER TABLE merchant_risk_scores ALTER COLUMN total_score SET DEFAULT 0;
ALTER TABLE merchant_risk_scores ALTER COLUMN total_score SET NOT NULL;

CREATE OR REPLACE FUNCTION merchant_risk_scores_sync_legacy() RETURNS TRIGGER AS $$
BEGIN
    -- New writers set total_score/decision; legacy readers use total_points/recommended_action.
    IF NEW.total_score IS NULL THEN
        NEW.total_score := COALESCE(NEW.total_points, 0);
    END IF;
    NEW.total_points := NEW.total_score;
    IF NEW.decision IS NOT NULL THEN
        NEW.recommended_action := NEW.decision;
    ELSIF NEW.recommended_action IS NOT NULL THEN
        NEW.decision := NEW.recommended_action;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_merchant_risk_scores_sync_legacy ON merchant_risk_scores;
CREATE TRIGGER trg_merchant_risk_scores_sync_legacy
    BEFORE INSERT OR UPDATE ON merchant_risk_scores
    FOR EACH ROW EXECUTE FUNCTION merchant_risk_scores_sync_legacy();

-- ----------------------------------------------------------------------------
-- 6. owner_screening_results: OwnerScreeningResult maps is_pep/is_sanctioned
--    and only the owner FK; V2 requires merchant_id NOT NULL. Add the flags and
--    derive merchant_id from the beneficial owner when the writer omits it.
-- ----------------------------------------------------------------------------
ALTER TABLE owner_screening_results ADD COLUMN IF NOT EXISTS is_pep        BOOLEAN DEFAULT FALSE;
ALTER TABLE owner_screening_results ADD COLUMN IF NOT EXISTS is_sanctioned BOOLEAN DEFAULT FALSE;

CREATE OR REPLACE FUNCTION owner_screening_results_fill_merchant() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.merchant_id IS NULL THEN
        SELECT bo.merchant_id INTO NEW.merchant_id
        FROM beneficial_owners bo WHERE bo.owner_id = NEW.owner_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_owner_screening_results_fill_merchant ON owner_screening_results;
CREATE TRIGGER trg_owner_screening_results_fill_merchant
    BEFORE INSERT ON owner_screening_results
    FOR EACH ROW EXECUTE FUNCTION owner_screening_results_fill_merchant();
