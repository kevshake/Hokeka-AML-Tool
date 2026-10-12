-- WP-03: Alert linkage to multiple transactions and findings; case_alerts → alerts FK.

ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS case_id BIGINT,
    ADD COLUMN IF NOT EXISTS rule_instance_id BIGINT,
    ADD COLUMN IF NOT EXISTS rule_version_id BIGINT,
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_alert_rule_version ON alerts (rule_version_id);
CREATE INDEX IF NOT EXISTS idx_alert_case ON alerts (case_id);

CREATE TABLE IF NOT EXISTS alert_transactions (
    alert_id BIGINT NOT NULL REFERENCES alerts (alert_id) ON DELETE CASCADE,
    txn_id   BIGINT NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (alert_id, txn_id)
);

CREATE INDEX IF NOT EXISTS idx_alert_transactions_txn ON alert_transactions (txn_id);

CREATE TABLE IF NOT EXISTS alert_findings (
    alert_id   BIGINT NOT NULL REFERENCES alerts (alert_id) ON DELETE CASCADE,
    finding_id UUID   NOT NULL REFERENCES findings (id) ON DELETE CASCADE,
    linked_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (alert_id, finding_id)
);

CREATE INDEX IF NOT EXISTS idx_alert_findings_finding ON alert_findings (finding_id);

ALTER TABLE case_alerts
    ADD COLUMN IF NOT EXISTS alert_id BIGINT;

ALTER TABLE case_alerts
    ADD CONSTRAINT fk_case_alerts_alert
        FOREIGN KEY (alert_id) REFERENCES alerts (alert_id)
        ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_case_alerts_alert_id ON case_alerts (alert_id);
