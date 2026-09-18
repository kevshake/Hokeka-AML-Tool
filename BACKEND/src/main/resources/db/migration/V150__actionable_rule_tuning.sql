ALTER TABLE alert_tuning_recommendations
    ADD COLUMN IF NOT EXISTS rule_id BIGINT REFERENCES rule_definitions(id),
    ADD COLUMN IF NOT EXISTS original_parameters TEXT,
    ADD COLUMN IF NOT EXISTS proposed_parameters TEXT,
    ADD COLUMN IF NOT EXISTS metrics_snapshot JSONB;

UPDATE alert_tuning_recommendations recommendation
SET rule_id = rule_definition.id
FROM rule_definitions rule_definition
WHERE recommendation.rule_id IS NULL
  AND recommendation.rule_name = rule_definition.name;

CREATE INDEX IF NOT EXISTS idx_tuning_rule_id
    ON alert_tuning_recommendations(rule_id);
