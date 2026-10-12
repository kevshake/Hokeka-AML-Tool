-- WP-0A: Normalize CRA to 0–100 on merchants and transactions (legacy rows stored as 0–1).

UPDATE merchants
SET cra = cra * 100.0
WHERE cra IS NOT NULL AND cra > 0 AND cra <= 1.0;

UPDATE transactions
SET cra = cra * 100.0
WHERE cra IS NOT NULL AND cra > 0 AND cra <= 1.0;

COMMENT ON COLUMN merchants.cra IS 'Customer risk assessment score 0–100 (WP-0A unified scale)';
