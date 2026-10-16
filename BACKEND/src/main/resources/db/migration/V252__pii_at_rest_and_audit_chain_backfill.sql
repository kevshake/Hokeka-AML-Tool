-- V252: PII-at-rest hardening (gap P2-11) and legacy audit hash-chain backfill support (gap P2-10).
--
-- Encryption itself cannot be done in SQL: the AES-256-GCM key lives only in the application
-- (ENCRYPTION_KEY / SECURITY_ENCRYPTION_KEY). This migration widens the columns so they can hold
-- "enc:v1:<base64(iv||ciphertext||tag)>" values; PiiAtRestBackfillService encrypts the existing
-- plaintext rows idempotently at application startup (rows already prefixed "enc:v1:" are skipped).

-- 1. Account-number columns: widen to TEXT so ciphertext fits.
ALTER TABLE psp_trust_accounts
    ALTER COLUMN bank_account_number TYPE TEXT;

COMMENT ON COLUMN psp_trust_accounts.bank_account_number IS
    'Trust bank account number. AES-256-GCM encrypted at rest (enc:v1: prefix). Legacy plaintext rows are encrypted by PiiAtRestBackfillService.';

ALTER TABLE market_executions
    ALTER COLUMN settlement_account_reference TYPE TEXT;

COMMENT ON COLUMN market_executions.settlement_account_reference IS
    'Settlement account identifier. AES-256-GCM encrypted at rest (enc:v1: prefix). Legacy plaintext rows are encrypted by PiiAtRestBackfillService.';

COMMENT ON COLUMN merchants.cbk_settlement_account_number IS
    'Merchant settlement account number. AES-256-GCM encrypted at rest (enc:v1: prefix). Legacy plaintext rows are encrypted by PiiAtRestBackfillService.';

-- 2. One-shot job ledger so expensive full-table scrubs (raw PAN in pan_hash) run once.
CREATE TABLE IF NOT EXISTS pii_backfill_runs (
    job_name      VARCHAR(100) PRIMARY KEY,
    completed_at  TIMESTAMP    NOT NULL,
    rows_changed  BIGINT       NOT NULL DEFAULT 0
);

COMMENT ON TABLE pii_backfill_runs IS
    'Completion ledger for one-shot PII backfill jobs (e.g. raw-PAN scrub of pan_hash columns).';

-- 3. Audit hash-chain backfill bookkeeping. Legacy rows (written before V232 chaining, or by
--    paths that bypassed AuditLogService) are re-sealed by AuditChainBackfillService. The original
--    checksum is preserved in legacy_checksum so the successor row's stored previous_checksum
--    link can still be verified against what it actually recorded.
ALTER TABLE audit_logs_enhanced
    ADD COLUMN IF NOT EXISTS legacy_checksum VARCHAR(255);

ALTER TABLE audit_logs_enhanced
    ADD COLUMN IF NOT EXISTS chain_backfilled_at TIMESTAMP;

COMMENT ON COLUMN audit_logs_enhanced.legacy_checksum IS
    'Checksum value the row carried before the hash-chain backfill (NULL if it had none).';
COMMENT ON COLUMN audit_logs_enhanced.chain_backfilled_at IS
    'When AuditChainBackfillService sealed this legacy row into the hash chain. NULL for rows sealed at write time.';
