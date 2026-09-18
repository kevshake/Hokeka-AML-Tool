-- =====================================================================
-- V128__transaction_cbk_classification_columns.sql
-- Add CBK classification columns to the transactions table.
-- These fields support the 6 transaction-aggregate CBK endpoints:
--   CARD_BRANDS, TRANSACTION_DETAILS, BILLING_TEMPLATE,
--   SYSTEM_ACTIVITY, MERCHANT_TRANSACTIONS, FAILED_TRANSACTIONS
-- =====================================================================

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS card_brand              VARCHAR(16),
    ADD COLUMN IF NOT EXISTS card_type               VARCHAR(16),
    ADD COLUMN IF NOT EXISTS card_class              VARCHAR(16),
    ADD COLUMN IF NOT EXISTS channel_type            VARCHAR(32),
    ADD COLUMN IF NOT EXISTS bill_classification_code VARCHAR(16);

-- Composite index for the time-windowed PSP queries used by the CBK orchestrator.
-- Covers: WHERE psp_id = ? AND txn_ts >= ? AND txn_ts < ?
CREATE INDEX IF NOT EXISTS idx_txn_psp_ts
    ON transactions (psp_id, txn_ts);
