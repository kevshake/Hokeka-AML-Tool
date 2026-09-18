-- V169: Seed default billing_rates for the remaining metered service types.
--
-- Why (fixes the "billing bills $0" blocker, W16-1): the live metering path
-- (config/UsageTrackingFilter -> service/psp/ApiUsageTrackingService.logRequest ->
-- BillingService.calculateUsageCost) looks up billing_rates by the exact
-- service_type string. The filter now emits the canonical vocabulary that V149
-- already seeds (TRANSACTION_MONITORING, AML_SCREENING, SANCTIONS_SCREENING_PERSON,
-- SANCTIONS_SCREENING_ORGANIZATION, KYC_VERIFICATION, COMPLIANCE_CASE_CREATION),
-- but four canonical types the filter also emits had NO seeded rate, so those
-- requests still resolved to BigDecimal.ZERO. This migration adds them so every
-- metered request produces a real cost and rolls into invoices.
--
-- Rates: psp_id NULL = platform default fallback; PER_REQUEST; effective today.
-- Idempotent (guarded by NOT EXISTS) so re-running / re-baselining is safe.
-- Mirrors the exact shape of V149__seed_default_billing_rates.sql.

INSERT INTO billing_rates (psp_id, service_type, pricing_model, base_rate, currency, effective_from, is_active, description)
SELECT v.psp_id, v.service_type, v.pricing_model, v.base_rate, v.currency, CURRENT_DATE, v.is_active, v.description
FROM (VALUES
    (CAST(NULL AS BIGINT), 'RISK_ASSESSMENT',   'PER_REQUEST', CAST(0.1500 AS NUMERIC), 'USD', TRUE, 'On-demand customer/transaction risk assessment'),
    (CAST(NULL AS BIGINT), 'REPORT_GENERATION', 'PER_REQUEST', CAST(0.2500 AS NUMERIC), 'USD', TRUE, 'Regulatory/analytics report generation'),
    (CAST(NULL AS BIGINT), 'SAR_FILING',        'PER_REQUEST', CAST(3.0000 AS NUMERIC), 'USD', TRUE, 'Suspicious Activity Report filing'),
    (CAST(NULL AS BIGINT), 'CBK_REPORTING',     'PER_REQUEST', CAST(1.5000 AS NUMERIC), 'USD', TRUE, 'CBK regulatory submission')
) AS v(psp_id, service_type, pricing_model, base_rate, currency, is_active, description)
WHERE NOT EXISTS (
    SELECT 1 FROM billing_rates b
    WHERE b.psp_id IS NULL AND b.service_type = v.service_type
);
