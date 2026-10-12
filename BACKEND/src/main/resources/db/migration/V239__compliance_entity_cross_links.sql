-- V239: Indexed foreign keys linking fraud alerts, AML/monitoring alerts, cases, disputes,
-- SARs, risk profiles, merchants, cards (pan_hash), and transactions for fast cross-lookups.
-- Complements WP-03 alert/finding provenance without replacing the findings ledger (V238).

-- ---------------------------------------------------------------------------
-- alerts: case + card identity denormalization for indexed lookups
-- ---------------------------------------------------------------------------
ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS compliance_case_id BIGINT;

ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS pan_hash VARCHAR(128);

ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS disposition_review_status VARCHAR(32);

ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS first_reviewer VARCHAR(255);

ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS second_reviewer VARCHAR(255);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_alerts_compliance_case'
    ) THEN
        ALTER TABLE alerts
            ADD CONSTRAINT fk_alerts_compliance_case
                FOREIGN KEY (compliance_case_id) REFERENCES compliance_cases (case_id)
                ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_alerts_compliance_case_id ON alerts (compliance_case_id);
CREATE INDEX IF NOT EXISTS idx_alerts_pan_hash ON alerts (pan_hash);
CREATE INDEX IF NOT EXISTS idx_alerts_merchant_txn ON alerts (merchant_id, txn_id);

-- ---------------------------------------------------------------------------
-- case_alerts: FK back to canonical alerts row (stops drift vs alerts table)
-- ---------------------------------------------------------------------------
ALTER TABLE case_alerts
    ADD COLUMN IF NOT EXISTS source_alert_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_case_alerts_source_alert'
    ) THEN
        ALTER TABLE case_alerts
            ADD CONSTRAINT fk_case_alerts_source_alert
                FOREIGN KEY (source_alert_id) REFERENCES alerts (alert_id)
                ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_case_alerts_source_alert ON case_alerts (source_alert_id);

ALTER TABLE case_alerts_aud
    ADD COLUMN IF NOT EXISTS source_alert_id BIGINT;

-- ---------------------------------------------------------------------------
-- alert_transactions: many transactions per alert (consolidated alerts)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alert_transactions (
    id         BIGSERIAL PRIMARY KEY,
    alert_id   BIGINT NOT NULL REFERENCES alerts (alert_id) ON DELETE CASCADE,
    txn_id     BIGINT NOT NULL REFERENCES transactions (txn_id) ON DELETE CASCADE,
    primary_link BOOLEAN NOT NULL DEFAULT FALSE,
    linked_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (alert_id, txn_id)
);

CREATE INDEX IF NOT EXISTS idx_alert_transactions_txn ON alert_transactions (txn_id);
CREATE INDEX IF NOT EXISTS idx_alert_transactions_alert ON alert_transactions (alert_id);

INSERT INTO alert_transactions (alert_id, txn_id, primary_link)
SELECT a.alert_id, a.txn_id, TRUE
FROM alerts a
WHERE a.txn_id IS NOT NULL
ON CONFLICT (alert_id, txn_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- sar_alert_links: SAR ↔ fraud/AML alerts
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sar_alert_links (
    id        BIGSERIAL PRIMARY KEY,
    sar_id    BIGINT NOT NULL REFERENCES suspicious_activity_reports (id) ON DELETE CASCADE,
    alert_id  BIGINT NOT NULL REFERENCES alerts (alert_id) ON DELETE CASCADE,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (sar_id, alert_id)
);

CREATE INDEX IF NOT EXISTS idx_sar_alert_links_sar ON sar_alert_links (sar_id);
CREATE INDEX IF NOT EXISTS idx_sar_alert_links_alert ON sar_alert_links (alert_id);

-- ---------------------------------------------------------------------------
-- monitoring_alerts (AML ongoing monitoring) ↔ fraud alerts + cases
-- ---------------------------------------------------------------------------
ALTER TABLE monitoring_alerts
    ADD COLUMN IF NOT EXISTS fraud_alert_id BIGINT;

ALTER TABLE monitoring_alerts
    ADD COLUMN IF NOT EXISTS compliance_case_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_monitoring_alerts_fraud_alert'
    ) THEN
        ALTER TABLE monitoring_alerts
            ADD CONSTRAINT fk_monitoring_alerts_fraud_alert
                FOREIGN KEY (fraud_alert_id) REFERENCES alerts (alert_id)
                ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_monitoring_alerts_compliance_case'
    ) THEN
        ALTER TABLE monitoring_alerts
            ADD CONSTRAINT fk_monitoring_alerts_compliance_case
                FOREIGN KEY (compliance_case_id) REFERENCES compliance_cases (case_id)
                ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_monitoring_alerts_fraud_alert ON monitoring_alerts (fraud_alert_id);
CREATE INDEX IF NOT EXISTS idx_monitoring_alerts_compliance_case ON monitoring_alerts (compliance_case_id);

-- ---------------------------------------------------------------------------
-- chargeback_disputes: ensure lookup indexes on alert/case/txn bridges
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_chargeback_disputes_alert ON chargeback_disputes (alert_id);
CREATE INDEX IF NOT EXISTS idx_chargeback_disputes_compliance_case ON chargeback_disputes (compliance_case_id);
CREATE INDEX IF NOT EXISTS idx_chargeback_disputes_psp_txn ON chargeback_disputes (psp_transaction_id);

-- ---------------------------------------------------------------------------
-- screening_activities: persisted payment-time and batch screening evidence
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS screening_activities (
    id              BIGSERIAL PRIMARY KEY,
    psp_id          BIGINT,
    merchant_id     BIGINT,
    txn_id          BIGINT REFERENCES transactions (txn_id) ON DELETE SET NULL,
    subject_type    VARCHAR(32)  NOT NULL,
    subject_name    VARCHAR(512),
    screening_id    UUID         NOT NULL DEFAULT gen_random_uuid(),
    is_hit          BOOLEAN      NOT NULL DEFAULT FALSE,
    is_blocking     BOOLEAN      NOT NULL DEFAULT FALSE,
    match_count     INTEGER      NOT NULL DEFAULT 0,
    highest_score   DOUBLE PRECISION,
    list_version    VARCHAR(128),
    reason          TEXT,
    evidence        JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_screening_activities_txn ON screening_activities (txn_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_screening_activities_merchant ON screening_activities (merchant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_screening_activities_psp_created ON screening_activities (psp_id, created_at DESC);

COMMENT ON TABLE screening_activities IS 'Regulator-evidence screening runs (transaction-time and periodic)';
