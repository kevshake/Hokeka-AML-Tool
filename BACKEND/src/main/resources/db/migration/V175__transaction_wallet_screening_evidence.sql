ALTER TABLE wallet_screening_records
    ADD COLUMN IF NOT EXISTS customer_id BIGINT REFERENCES multi_asset_customers(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS screened_address VARCHAR(512),
    ADD COLUMN IF NOT EXISTS network VARCHAR(64);

UPDATE wallet_screening_records s
SET customer_id = w.customer_id,
    screened_address = w.wallet_address,
    network = w.network
FROM crypto_wallet_profiles w
WHERE s.wallet_profile_id = w.id
  AND (s.customer_id IS NULL OR s.screened_address IS NULL OR s.network IS NULL);

ALTER TABLE wallet_screening_records
    ALTER COLUMN wallet_profile_id DROP NOT NULL,
    ALTER COLUMN customer_id SET NOT NULL;

CREATE INDEX idx_wallet_screening_customer ON wallet_screening_records(psp_id, customer_id, screened_at DESC);
CREATE INDEX idx_wallet_screening_address ON wallet_screening_records(psp_id, network, screened_address, screened_at DESC);

COMMENT ON COLUMN wallet_screening_records.wallet_profile_id IS
    'Optional registered-wallet link; transaction screening also persists external counterparty addresses';
