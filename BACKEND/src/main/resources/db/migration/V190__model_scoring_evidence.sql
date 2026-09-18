ALTER TABLE transaction_features
    ADD COLUMN IF NOT EXISTS risk_details JSONB;

CREATE INDEX IF NOT EXISTS idx_transaction_features_model_version
    ON transaction_features(model_version)
    WHERE model_version IS NOT NULL;

COMMENT ON COLUMN transaction_features.risk_details IS
    'Rule, model, anomaly, explanation, and regulatory evidence returned for the scoring decision';
