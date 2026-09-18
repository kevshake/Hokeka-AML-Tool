-- Temporary per-merchant daily limit override.
--
-- Prior to this, TransactionLimitService.setTemporaryLimit() only logged and sent a
-- notification — it persisted nothing, so a compliance officer's temporary limit was
-- never enforced (the API returned 200 while the control was inert). These columns
-- give the temporary override a real home on the existing merchant_transaction_limits
-- row so TransactionLimitEnforcementService can apply it until it expires.

ALTER TABLE merchant_transaction_limits
    ADD COLUMN IF NOT EXISTS temporary_daily_limit NUMERIC(19, 2);

ALTER TABLE merchant_transaction_limits
    ADD COLUMN IF NOT EXISTS temporary_limit_expires_at TIMESTAMP;

ALTER TABLE merchant_transaction_limits
    ADD COLUMN IF NOT EXISTS temporary_limit_set_by BIGINT;
