-- WP-03: Finding provenance on alerts (alert_transactions + case source_alert_id live in V239).

ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS rule_instance_id BIGINT,
    ADD COLUMN IF NOT EXISTS rule_version_id BIGINT,
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_alert_rule_version ON alerts (rule_version_id);

CREATE TABLE IF NOT EXISTS alert_findings (
    alert_id   BIGINT NOT NULL REFERENCES alerts (alert_id) ON DELETE CASCADE,
    finding_id UUID   NOT NULL REFERENCES findings (id) ON DELETE CASCADE,
    linked_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (alert_id, finding_id)
);

CREATE INDEX IF NOT EXISTS idx_alert_findings_finding ON alert_findings (finding_id);
