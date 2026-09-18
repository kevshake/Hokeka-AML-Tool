-- V146: Bridge legacy NOT-NULL columns on `transactions`.
--
-- The current TransactionEntity maps the modern columns (txn_id, amount_cents,
-- currency, txn_ts, pan_hash, decision, direction) but the table — created back
-- in V1__Initial_Schema — still carries the original NOT-NULL columns that no
-- entity field populates:
--   amount, currency_code, transaction_timestamp, account_number,
--   status, transaction_id, transaction_type
-- Because these have no DEFAULT and are never set by Hibernate, every insert
-- from the live ingestion path failed with a NOT-NULL violation
-- ("null value in column \"amount\" ... violates not-null constraint").
--
-- This migration installs a BEFORE INSERT trigger that derives each legacy
-- column from its modern counterpart, so the real ingestion pipeline
-- (TransactionIngestionService -> risk scoring -> fraud orchestrator) persists
-- cleanly without the entity having to map redundant columns. Idempotent so it
-- can be re-applied against any environment.

CREATE OR REPLACE FUNCTION txn_legacy_sync()
  RETURNS trigger
  LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.amount IS NULL THEN
    NEW.amount := COALESCE(NEW.amount_cents, 0)::numeric / 100.0;
  END IF;
  IF NEW.currency_code IS NULL THEN
    NEW.currency_code := COALESCE(NEW.currency, 'KES');
  END IF;
  IF NEW.transaction_timestamp IS NULL THEN
    NEW.transaction_timestamp := COALESCE(NEW.txn_ts, now());
  END IF;
  IF NEW.account_number IS NULL THEN
    NEW.account_number := COALESCE(NEW.pan_hash, 'UNKNOWN');
  END IF;
  -- Legacy `status` has a CHECK constraint limited to
  -- (PENDING, APPROVED, REJECTED, FLAGGED, UNDER_REVIEW, SUSPENDED), whereas the
  -- modern `decision` enum emits APPROVED / MANUAL_REVIEW / DECLINED. Map across
  -- the two vocabularies so high-risk transactions persist instead of tripping
  -- transactions_status_check.
  IF NEW.status IS NULL THEN
    NEW.status := CASE COALESCE(NEW.decision, 'PENDING')
                    WHEN 'APPROVED'      THEN 'APPROVED'
                    WHEN 'MANUAL_REVIEW' THEN 'UNDER_REVIEW'
                    WHEN 'DECLINED'      THEN 'REJECTED'
                    WHEN 'REJECTED'      THEN 'REJECTED'
                    WHEN 'FLAGGED'       THEN 'FLAGGED'
                    WHEN 'SUSPENDED'     THEN 'SUSPENDED'
                    ELSE 'PENDING'
                  END;
  END IF;
  IF NEW.transaction_type IS NULL THEN
    NEW.transaction_type := COALESCE(NEW.direction, 'INBOUND');
  END IF;
  IF NEW.transaction_id IS NULL THEN
    NEW.transaction_id := 'TXN-' || COALESCE(NEW.txn_id::text, 'X') || '-' || nextval('txn_legacy_seq')::text;
  END IF;
  RETURN NEW;
END;
$$;

-- Deterministic uniqueness for the legacy transaction_id (replaces the
-- random()-based id the ad-hoc patch used, so reruns are reproducible).
CREATE SEQUENCE IF NOT EXISTS txn_legacy_seq;

DROP TRIGGER IF EXISTS trg_txn_legacy_sync ON transactions;
CREATE TRIGGER trg_txn_legacy_sync
  BEFORE INSERT ON transactions
  FOR EACH ROW
  EXECUTE FUNCTION txn_legacy_sync();

-- Backfill any rows that slipped in before the trigger existed.
UPDATE transactions SET
    amount = COALESCE(amount, COALESCE(amount_cents, 0)::numeric / 100.0),
    currency_code = COALESCE(currency_code, currency, 'KES'),
    transaction_timestamp = COALESCE(transaction_timestamp, txn_ts, now()),
    account_number = COALESCE(account_number, pan_hash, 'UNKNOWN'),
    status = COALESCE(status, decision, 'PENDING'),
    transaction_type = COALESCE(transaction_type, direction, 'INBOUND'),
    transaction_id = COALESCE(transaction_id, 'TXN-' || COALESCE(txn_id::text, 'X'))
WHERE amount IS NULL
   OR currency_code IS NULL
   OR transaction_timestamp IS NULL
   OR account_number IS NULL
   OR status IS NULL
   OR transaction_type IS NULL
   OR transaction_id IS NULL;
