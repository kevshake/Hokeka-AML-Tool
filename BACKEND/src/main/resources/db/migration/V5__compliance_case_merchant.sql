-- Add merchant_id to compliance_cases for merchant-level filtering
-- Note: merchant_id should be BIGINT to match merchants.merchant_id type
ALTER TABLE compliance_cases
ADD COLUMN IF NOT EXISTS merchant_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_compliance_cases_merchant ON compliance_cases(merchant_id);


