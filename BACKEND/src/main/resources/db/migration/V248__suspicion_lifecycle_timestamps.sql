-- Nullable suspicion lifecycle timestamps. SAR deadlines still use suspicion_arose_at;
-- writers prefer a case suspicion_formed_at when the SAR itself has none.

ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS suspicion_formed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS investigation_started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS mlro_notified_at TIMESTAMP;

ALTER TABLE compliance_cases
    ADD COLUMN IF NOT EXISTS suspicion_formed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS investigation_started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS mlro_notified_at TIMESTAMP;

ALTER TABLE compliance_cases_aud
    ADD COLUMN IF NOT EXISTS suspicion_formed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS investigation_started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS mlro_notified_at TIMESTAMP;
