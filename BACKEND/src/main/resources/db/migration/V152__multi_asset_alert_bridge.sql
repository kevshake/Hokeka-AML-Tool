ALTER TABLE alerts ADD COLUMN IF NOT EXISTS psp_id BIGINT;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS multi_asset_customer_id BIGINT;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS source_type VARCHAR(50);
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS source_reference VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_alert_psp_id ON alerts(psp_id);
CREATE INDEX IF NOT EXISTS idx_alert_multi_asset_customer ON alerts(multi_asset_customer_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_alert_source_reference
    ON alerts(psp_id, source_type, source_reference)
    WHERE source_type IS NOT NULL AND source_reference IS NOT NULL;

