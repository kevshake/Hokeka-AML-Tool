-- V230: Align case_alerts with CaseAlert entity (V102 created a minimal table;
-- V120 case_alerts_aud already mirrors the full entity).

ALTER TABLE case_alerts
    ADD COLUMN IF NOT EXISTS rule_name VARCHAR(255);

ALTER TABLE case_alerts
    ADD COLUMN IF NOT EXISTS rule_id VARCHAR(255);

ALTER TABLE case_alerts
    ADD COLUMN IF NOT EXISTS model_version VARCHAR(255);

ALTER TABLE case_alerts
    ADD COLUMN IF NOT EXISTS rule_version VARCHAR(255);

ALTER TABLE case_alerts
    ADD COLUMN IF NOT EXISTS raw_data TEXT;

ALTER TABLE case_alerts
    ADD COLUMN IF NOT EXISTS triggered_at TIMESTAMP;

UPDATE case_alerts
SET triggered_at = created_at
WHERE triggered_at IS NULL AND created_at IS NOT NULL;

UPDATE case_alerts
SET triggered_at = CURRENT_TIMESTAMP
WHERE triggered_at IS NULL;
