-- =========================================================================
-- V147: Reference data seed + production indexes
-- =========================================================================

-- 1. Seed high_risk_countries (FATF + CBK flagged jurisdictions)
INSERT INTO high_risk_countries (country_code, country_name, risk_level, added_by)
SELECT * FROM (VALUES
    ('KP', 'North Korea', 'CRITICAL', 'BOOTSTRAP'),
    ('IR', 'Iran', 'CRITICAL', 'BOOTSTRAP'),
    ('SY', 'Syria', 'CRITICAL', 'BOOTSTRAP'),
    ('YE', 'Yemen', 'HIGH', 'BOOTSTRAP'),
    ('SD', 'Sudan', 'HIGH', 'BOOTSTRAP'),
    ('MM', 'Myanmar', 'HIGH', 'BOOTSTRAP'),
    ('CF', 'Central African Republic', 'HIGH', 'BOOTSTRAP'),
    ('SS', 'South Sudan', 'HIGH', 'BOOTSTRAP'),
    ('LY', 'Libya', 'HIGH', 'BOOTSTRAP'),
    ('SO', 'Somalia', 'HIGH', 'BOOTSTRAP'),
    ('AF', 'Afghanistan', 'HIGH', 'BOOTSTRAP'),
    ('IQ', 'Iraq', 'HIGH', 'BOOTSTRAP'),
    ('ML', 'Mali', 'HIGH', 'BOOTSTRAP'),
    ('BF', 'Burkina Faso', 'HIGH', 'BOOTSTRAP'),
    ('VE', 'Venezuela', 'HIGH', 'BOOTSTRAP'),
    ('CU', 'Cuba', 'HIGH', 'BOOTSTRAP'),
    ('RU', 'Russia', 'MEDIUM', 'BOOTSTRAP'),
    ('CN', 'China', 'MEDIUM', 'BOOTSTRAP'),
    ('NG', 'Nigeria', 'MEDIUM', 'BOOTSTRAP')
) AS src(cc, cn, rl, ab)
WHERE NOT EXISTS (
    SELECT 1 FROM high_risk_countries h WHERE h.country_code = src.cc
);

-- 2. Seed country_risk_scores (if table is empty)
INSERT INTO country_risk_scores (country_code, country_name, risk_score, risk_tier, fatf_listed, fatf_status, source)
SELECT * FROM (VALUES
    ('KP', 'North Korea', 100, 'VERY_HIGH', TRUE, 'BLACKLIST', 'FATF'),
    ('IR', 'Iran', 100, 'VERY_HIGH', TRUE, 'BLACKLIST', 'FATF'),
    ('MM', 'Myanmar', 95, 'VERY_HIGH', TRUE, 'BLACKLIST', 'FATF'),
    ('SY', 'Syria', 95, 'VERY_HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('YE', 'Yemen', 85, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('SD', 'Sudan', 85, 'HIGH', FALSE, NULL, 'MANUAL'),
    ('LY', 'Libya', 80, 'HIGH', FALSE, NULL, 'MANUAL'),
    ('SO', 'Somalia', 80, 'HIGH', FALSE, NULL, 'MANUAL'),
    ('CF', 'Central African Republic', 75, 'HIGH', FALSE, NULL, 'MANUAL'),
    ('SS', 'South Sudan', 75, 'HIGH', TRUE, 'GREYLIST', 'FATF'),
    ('AF', 'Afghanistan', 70, 'HIGH', FALSE, NULL, 'MANUAL'),
    ('IQ', 'Iraq', 65, 'MEDIUM', FALSE, NULL, 'MANUAL'),
    ('KE', 'Kenya', 68, 'HIGH', TRUE, 'GREYLIST', 'FATF')
) AS src(cc, cn, rs, rt, fl, fs, so)
WHERE NOT EXISTS (
    SELECT 1 FROM country_risk_scores c WHERE c.country_code = src.cc
);

-- 3. Seed pricing tiers
-- NOTE: pricing_tiers has no `tier_config` column (that column lives on billing_rates); the
-- volume-discount JSONB column is `volume_discounts` and its expected shape is a flat
-- {threshold:discountFraction} map (see PricingTier.getVolumeDiscountsMap). The prior seed wrote a
-- non-existent column with an unreadable nested shape, which aborted the whole migration run on a
-- fresh database (blocking every migration after V147). Seed the headline pricing only and leave
-- volume_discounts at its '{}' default; a correctly-shaped discount ladder is a later data task.
INSERT INTO pricing_tiers (tier_code, tier_name, monthly_fee_usd, per_check_price_usd, included_checks,
                           monthly_minimum_usd, is_active, created_at)
SELECT * FROM (VALUES
    ('STARTER', 'Starter', 0.00, 0.05, 1000, 0.00, TRUE, NOW()),
    ('GROWTH', 'Growth', 199.00, 0.03, 10000, 199.00, TRUE, NOW()),
    ('SCALE', 'Scale', 499.00, 0.02, 50000, 499.00, TRUE, NOW()),
    ('ENTERPRISE', 'Enterprise', 999.00, 0.01, 200000, 999.00, TRUE, NOW())
) AS src(tc, tn, mf, pcp, ic, mm, ia, ca)
WHERE NOT EXISTS (
    SELECT 1 FROM pricing_tiers p WHERE p.tier_code = src.tc
);

-- 4. Seed default billing rates
-- NOTE: the billing_rates pricing-model column is `pricing_model`, not `billing_model`.
INSERT INTO billing_rates (psp_id, service_type, base_rate, pricing_model, is_active, effective_from)
SELECT NULL, st, 0.050, 'PER_REQUEST', TRUE, NOW()
FROM (VALUES ('TRANSACTION_PROCESSING'), ('SANCTIONS_SCREENING'), ('AML_CHECK'),
             ('SCREENING'), ('RISK_ASSESSMENT'), ('REPORT_GENERATION'),
             ('MERCHANT_ONBOARDING')) AS svc(st)
WHERE NOT EXISTS (
    SELECT 1 FROM billing_rates b WHERE b.psp_id IS NULL AND b.service_type = svc.st
);

-- 5. Seed escalation rules
INSERT INTO escalation_rules (rule_name, min_priority, min_risk_score, days_open, min_amount,
                              reason_template, enabled, created_at)
SELECT * FROM (VALUES
    ('CRITICAL_PRIORITY_AUTO', 'CRITICAL', NULL, NULL, NULL,
     'Case marked CRITICAL — immediate escalation required', TRUE, NOW()),
    ('HIGH_RISK_SCORE', NULL, 0.8, NULL, NULL,
     'Case risk score {riskScore} exceeds threshold — escalate for review', TRUE, NOW()),
    ('AGING_7_DAYS', 'HIGH', NULL, 7, NULL,
     'Case open for {daysOpen} days without resolution — escalate', TRUE, NOW()),
    ('AGING_14_DAYS', 'MEDIUM', NULL, 14, NULL,
     'Case open for {daysOpen} days without resolution — escalate', TRUE, NOW()),
    ('HIGH_VALUE_TXN', NULL, NULL, NULL, 1000000.00,
     'Case involves high-value transactions (KES {amount}) — escalate', TRUE, NOW())
) AS src(rn, mp, mrs, d_o, ma, rt, en, ca)
WHERE NOT EXISTS (
    SELECT 1 FROM escalation_rules e WHERE e.rule_name = src.rn
);

-- 6. Composite index for cross_psp_fraud_flags (query performance)
CREATE INDEX IF NOT EXISTS idx_cross_psp_entity_lookup
    ON cross_psp_fraud_flags (entity_type, entity_value, risk_level);

-- 7. Index for billing_rates lookups (most frequent query)
CREATE INDEX IF NOT EXISTS idx_billing_rates_active_service
    ON billing_rates (psp_id, service_type, is_active) WHERE is_active = TRUE;

-- 8. Composite index for api_usage_logs (aggregation queries) — only if not in V135
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes WHERE indexname = 'idx_api_usage_psp_period'
  ) THEN
    CREATE INDEX idx_api_usage_psp_period
        ON api_usage_logs (psp_id, service_type, request_timestamp DESC);
  END IF;
END $$;

-- 9. Covering index for case assignment queries
CREATE INDEX IF NOT EXISTS idx_cases_assignee_status_covering
    ON compliance_cases (assigned_to_user_id, status, priority)
    INCLUDE (case_reference, created_at, escalated);

-- 10. Partial index for active subscriptions
CREATE INDEX IF NOT EXISTS idx_subscriptions_active
    ON subscriptions (psp_id, status, billing_cycle)
    WHERE status IN ('ACTIVE', 'TRIAL');
