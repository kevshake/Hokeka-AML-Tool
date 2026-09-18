-- V140__compliance_review_tracking.sql
-- Adds CDD (Customer Due Diligence) and EDD (Enhanced Due Diligence) review
-- timestamps to the merchants table so the dashboard "Compliance Health"
-- aggregate can report what percentage of merchants have had a CDD/EDD
-- review within the regulatory review window (12 months / 6 months).
--
-- Back-fills NULL deliberately — we do NOT fabricate prior review dates.
-- A merchant with NULL last_cdd_review_at counts as "no review on record",
-- which the dashboard treats as not reviewed. As real CDD/EDD reviews are
-- captured by the compliance workflow these columns will be populated.

ALTER TABLE merchants
    ADD COLUMN IF NOT EXISTS last_cdd_review_at TIMESTAMP NULL;

ALTER TABLE merchants
    ADD COLUMN IF NOT EXISTS last_edd_review_at TIMESTAMP NULL;

CREATE INDEX IF NOT EXISTS idx_merchant_last_cdd_review_at
    ON merchants (last_cdd_review_at);

CREATE INDEX IF NOT EXISTS idx_merchant_last_edd_review_at
    ON merchants (last_edd_review_at);

-- Partial index for the EDD-on-high-risk-merchants dashboard query.
CREATE INDEX IF NOT EXISTS idx_merchant_high_risk_last_edd
    ON merchants (last_edd_review_at)
    WHERE risk_level = 'HIGH';
