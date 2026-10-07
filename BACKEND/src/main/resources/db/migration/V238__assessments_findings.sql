-- WP-01: Assessment + Finding ledger (shadow mode). Additive; does not change decision paths.
-- assessments: one row per transaction ingest assessment (trigger TXN).
-- findings: normalized engine outputs linked to an assessment.

CREATE TABLE IF NOT EXISTS assessments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    psp_id              BIGINT       NOT NULL,
    trigger_type        VARCHAR(32)  NOT NULL,
    trigger_ref         VARCHAR(128) NOT NULL,
    txn_id              BIGINT,
    parent_assessment_id UUID,
    edge_assessment_id  UUID,
    versions            JSONB,
    context_hash        VARCHAR(64),
    decision            VARCHAR(16),
    latency_ms          BIGINT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_assessments_psp_created
    ON assessments (psp_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_assessments_trigger_ref
    ON assessments (trigger_type, trigger_ref);

CREATE INDEX IF NOT EXISTS idx_assessments_txn_created
    ON assessments (txn_id, created_at DESC);

COMMENT ON TABLE assessments IS 'AssessmentContext ledger: one assessment per ingest/event trigger (WP-01 shadow)';

CREATE TABLE IF NOT EXISTS findings (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_id          UUID         NOT NULL REFERENCES assessments (id) ON DELETE CASCADE,
    txn_id                 BIGINT,
    party_id               BIGINT,
    counterparty_party_id  BIGINT,
    source_type            VARCHAR(32)  NOT NULL,
    source_id              VARCHAR(128),
    source_version         VARCHAR(256),
    phase                  VARCHAR(16)  NOT NULL DEFAULT 'CP_SYNC',
    shadow                 BOOLEAN      NOT NULL DEFAULT TRUE,
    nature                 VARCHAR(32),
    severity               VARCHAR(16),
    triggered              BOOLEAN      NOT NULL,
    score                  DOUBLE PRECISION,
    proposed_action        VARCHAR(16),
    proposed_actions       JSONB,
    evidence               JSONB,
    feature_references     JSONB,
    explanation            TEXT,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_findings_assessment
    ON findings (assessment_id);

CREATE INDEX IF NOT EXISTS idx_findings_txn
    ON findings (txn_id);

CREATE INDEX IF NOT EXISTS idx_findings_party
    ON findings (party_id);

CREATE INDEX IF NOT EXISTS idx_findings_source
    ON findings (source_type, source_id);

COMMENT ON TABLE findings IS 'Common Finding model: provenance from limits, lists, screening, rules, risk, ML, AI (WP-01)';

ALTER TABLE rule_execution_logs
    ADD COLUMN IF NOT EXISTS rule_version_id BIGINT;

ALTER TABLE rule_execution_logs
    ADD COLUMN IF NOT EXISTS assessment_id UUID;

CREATE INDEX IF NOT EXISTS idx_rule_exec_assessment
    ON rule_execution_logs (assessment_id);

ALTER TABLE rule_execution_logs_aud
    ADD COLUMN IF NOT EXISTS rule_version_id BIGINT;

ALTER TABLE rule_execution_logs_aud
    ADD COLUMN IF NOT EXISTS assessment_id UUID;

-- Optional TimescaleDB monthly partition for findings (same guard pattern as V215).
DO $$
DECLARE
    has_extension BOOLEAN;
BEGIN
    SELECT EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'timescaledb')
      INTO has_extension;

    IF NOT has_extension THEN
        RAISE NOTICE 'TimescaleDB not available — findings remain a plain PostgreSQL table.';
        RETURN;
    END IF;

    CREATE EXTENSION IF NOT EXISTS timescaledb;

    IF NOT EXISTS (SELECT 1 FROM timescaledb_information.hypertables
                   WHERE hypertable_name = 'findings') THEN
        IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'findings_pkey') THEN
            ALTER TABLE findings DROP CONSTRAINT findings_pkey;
        END IF;
        ALTER TABLE findings ADD PRIMARY KEY (id, created_at);

        PERFORM create_hypertable('findings', 'created_at',
                                  if_not_exists => TRUE,
                                  migrate_data  => TRUE,
                                  chunk_time_interval => INTERVAL '1 month');
        RAISE NOTICE 'findings converted to hypertable on created_at (monthly chunks).';
    END IF;
END $$;
