ALTER TABLE merchants
    ADD COLUMN IF NOT EXISTS cbk_settlement_account_number TEXT,
    ADD COLUMN IF NOT EXISTS cbk_economic_sector_code VARCHAR(100);

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS customer_account_reference VARCHAR(255),
    ADD COLUMN IF NOT EXISTS customer_email TEXT;

CREATE INDEX IF NOT EXISTS idx_transactions_cbk_failed_daily
    ON transactions (psp_id, txn_ts, decision)
    WHERE decision IN ('BLOCK', 'HOLD', 'DECLINED', 'MANUAL_REVIEW', 'REJECTED');
