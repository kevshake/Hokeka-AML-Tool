-- V147__seed_default_billing_rates.sql
-- Purpose: Seed platform-default billing rates (psp_id NULL = default fallback rate).
--          The original V3 seed never materialised on the test baseline, leaving
--          billing_rates empty, which made every metered usage cost 0 and every
--          generated invoice non-billable. These rows are reference/config data and
--          their canonical provenance is a Flyway migration (per hokeka-database skill).
-- Tables affected: billing_rates
-- Audited entities: no
-- Idempotent: guarded by NOT EXISTS so re-running / re-baselining is safe.

INSERT INTO billing_rates (psp_id, service_type, pricing_model, base_rate, currency, effective_from, is_active, description)
SELECT v.psp_id, v.service_type, v.pricing_model, v.base_rate, v.currency, CURRENT_DATE, v.is_active, v.description
FROM (VALUES
    (CAST(NULL AS BIGINT), 'TRANSACTION_MONITORING',          'PER_REQUEST', CAST(0.0200 AS NUMERIC), 'USD', TRUE, 'Real-time AML transaction monitoring / scoring per ingested transaction'),
    (CAST(NULL AS BIGINT), 'AML_SCREENING',                   'PER_REQUEST', CAST(0.1000 AS NUMERIC), 'USD', TRUE, 'AML screening orchestration call'),
    (CAST(NULL AS BIGINT), 'SANCTIONS_SCREENING_PERSON',      'PER_REQUEST', CAST(0.5000 AS NUMERIC), 'USD', TRUE, 'Individual sanctions screening'),
    (CAST(NULL AS BIGINT), 'SANCTIONS_SCREENING_ORGANIZATION','PER_REQUEST', CAST(0.7500 AS NUMERIC), 'USD', TRUE, 'Organization sanctions screening'),
    (CAST(NULL AS BIGINT), 'KYC_VERIFICATION',                'PER_REQUEST', CAST(2.0000 AS NUMERIC), 'USD', TRUE, 'KYC / merchant onboarding verification'),
    (CAST(NULL AS BIGINT), 'COMPLIANCE_CASE_CREATION',        'PER_REQUEST', CAST(1.0000 AS NUMERIC), 'USD', TRUE, 'Compliance case creation for manual review'),
    (CAST(NULL AS BIGINT), 'API_CALL_GENERIC',                'PER_REQUEST', CAST(0.0100 AS NUMERIC), 'USD', TRUE, 'Generic billable API call')
) AS v(psp_id, service_type, pricing_model, base_rate, currency, is_active, description)
WHERE NOT EXISTS (
    SELECT 1 FROM billing_rates b
    WHERE b.psp_id IS NULL AND b.service_type = v.service_type
);

-- Indexes already created in V3 (idx_billing_rates_psp, idx_billing_rates_active, idx_billing_rates_service).
