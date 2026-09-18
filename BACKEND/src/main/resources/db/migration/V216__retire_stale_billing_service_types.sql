-- V216__retire_stale_billing_service_types.sql
-- Purpose (W36-6): V147 seeded billing_rates rows in an old service_type vocabulary that
--          UsageTrackingFilter's URL_SERVICE_MAP no longer produces at all:
--            TRANSACTION_PROCESSING  -> superseded by TRANSACTION_MONITORING (seeded in V149)
--            SANCTIONS_SCREENING     -> superseded by SANCTIONS_SCREENING_PERSON /
--                                       SANCTIONS_SCREENING_ORGANIZATION (seeded in V149)
--            AML_CHECK               -> superseded by AML_SCREENING (seeded in V149)
--            SCREENING               -> superseded by AML_SCREENING (seeded in V149)
--            MERCHANT_ONBOARDING     -> superseded by KYC_VERIFICATION (seeded in V149)
--          RISK_ASSESSMENT and REPORT_GENERATION from that same V147 seed are NOT stale --
--          both are still live in the current URL_SERVICE_MAP -- so they are left untouched.
--
--          Separately, and a real revenue-leak gap found while auditing this: the current
--          URL_SERVICE_MAP also maps calls to SAR_FILING and CBK_REPORTING, but no migration
--          (not V147, not V149) ever seeded billing_rates rows for either -- every SAR filing
--          and every CBK regulatory submission has been billed at $0 since those routes were
--          added. Seeded here so calculateUsageCost resolves a non-zero rate for them too.
--
-- Tables affected: billing_rates
-- Audited entities: no
-- Idempotent: the deactivation is a no-op on rows already inactive; the SAR_FILING/
--             CBK_REPORTING insert is guarded by NOT EXISTS like every other billing_rates seed.
-- Never edit V147 or V149 (already-applied migrations) -- this corrects them forward instead.

-- Deactivate (not delete -- preserve the audit trail of what rates were in effect when) the
-- five V147-seeded rows that no longer correspond to any service_type the app can produce.
UPDATE billing_rates
SET is_active = FALSE
WHERE psp_id IS NULL
  AND service_type IN ('TRANSACTION_PROCESSING', 'SANCTIONS_SCREENING', 'AML_CHECK',
                        'SCREENING', 'MERCHANT_ONBOARDING')
  AND is_active = TRUE;

-- Seed the two service types the current URL_SERVICE_MAP produces that were never billable.
INSERT INTO billing_rates (psp_id, service_type, pricing_model, base_rate, currency,
                            effective_from, is_active, description)
SELECT v.psp_id, v.service_type, v.pricing_model, v.base_rate, v.currency, CURRENT_DATE,
       v.is_active, v.description
FROM (VALUES
    (CAST(NULL AS BIGINT), 'SAR_FILING',     'PER_REQUEST', CAST(3.0000 AS NUMERIC), 'USD', TRUE,
     'Suspicious Activity Report filing'),
    (CAST(NULL AS BIGINT), 'CBK_REPORTING',  'PER_REQUEST', CAST(1.5000 AS NUMERIC), 'USD', TRUE,
     'CBK regulatory submission (create or replay)')
) AS v(psp_id, service_type, pricing_model, base_rate, currency, is_active, description)
WHERE NOT EXISTS (
    SELECT 1 FROM billing_rates b
    WHERE b.psp_id IS NULL AND b.service_type = v.service_type
);
