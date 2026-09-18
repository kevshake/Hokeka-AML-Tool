ALTER TABLE merchants
    ADD COLUMN IF NOT EXISTS cbk_settlement_account_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_merchants_settlement_account_hash
    ON merchants (cbk_settlement_account_hash)
    WHERE cbk_settlement_account_hash IS NOT NULL;

COMMENT ON COLUMN merchants.cbk_settlement_account_hash IS
    'Deterministic keyed HMAC of the normalized settlement account; used for linkage without plaintext comparison.';
