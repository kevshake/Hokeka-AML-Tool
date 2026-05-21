-- Add risk_level and decision columns to transactions table for pagination performance
-- These columns store calculated values to avoid post-pagination filtering

ALTER TABLE transactions
ADD COLUMN IF NOT EXISTS risk_level VARCHAR(20),
ADD COLUMN IF NOT EXISTS decision VARCHAR(20);

-- Create indexes for efficient filtering
CREATE INDEX IF NOT EXISTS idx_txn_risk_level ON transactions(risk_level);
CREATE INDEX IF NOT EXISTS idx_txn_decision ON transactions(decision);

-- Add comments for documentation
COMMENT ON COLUMN transactions.risk_level IS 'Calculated risk level: LOW, MEDIUM, HIGH, CRITICAL';
COMMENT ON COLUMN transactions.decision IS 'Calculated decision: APPROVED, MANUAL_REVIEW, DECLINED';

-- Backfill existing transactions with calculated values
-- This uses the same logic as TransactionIngestionService
UPDATE transactions
SET risk_level = CASE
    WHEN trs >= 76 THEN 'CRITICAL'
    WHEN trs >= 51 THEN 'HIGH'
    WHEN trs >= 26 THEN 'MEDIUM'
    ELSE 'LOW'
END,
decision = CASE
    WHEN trs >= 76 THEN 'DECLINED'
    WHEN trs >= 51 THEN 'MANUAL_REVIEW'
    ELSE 'APPROVED'
END
WHERE risk_level IS NULL AND trs IS NOT NULL;

-- For transactions without TRS, use amount-based fallback
UPDATE transactions
SET risk_level = CASE
    WHEN amount_cents > 100000 THEN 'HIGH'
    WHEN amount_cents > 50000 THEN 'CRITICAL'
    ELSE 'LOW'
END,
decision = CASE
    WHEN amount_cents > 100000 THEN 'MANUAL_REVIEW'
    WHEN amount_cents > 50000 THEN 'DECLINED'
    ELSE 'APPROVED'
END
WHERE risk_level IS NULL AND trs IS NULL;
