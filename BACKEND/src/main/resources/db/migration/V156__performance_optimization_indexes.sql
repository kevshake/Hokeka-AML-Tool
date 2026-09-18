-- =========================================================================
-- V156: Performance optimization indexes for transaction queries
-- =========================================================================

-- 1. Covering index for PSP transaction ingestion lookups (most frequent query)
--    The TransactionIngestionService looks up merchants by merchant_id during
--    ingestion. This composite index speeds up that join.
CREATE INDEX IF NOT EXISTS idx_merchants_psp_risk_lookup
    ON merchants (merchant_id, psp_id, risk_level, status)
    WHERE status = 'ACTIVE';

-- 2. Composite index for alert queries (dashboard + list pages)
--    Alerts do not have a psp_id column; PSP isolation is done via merchant JOIN.
--    This index covers the most common alert list query pattern: status + date.
CREATE INDEX IF NOT EXISTS idx_alerts_status_created
    ON alerts (status, created_at DESC);

-- 3. Index for compliance case timeline queries
CREATE INDEX IF NOT EXISTS idx_cases_created_priority
    ON compliance_cases (created_at DESC, priority, status);

-- 4. Composite index for the usage tracking aggregate queries
--    Used by BillingService / PspAdminBillingController for PSP billing summary
CREATE INDEX IF NOT EXISTS idx_usage_logs_psp_service_period
    ON api_usage_logs (psp_id, service_type, request_timestamp DESC)
    WHERE billable = TRUE;

-- 5. Covering index for BillingCalculation lookups (monthly billing)
CREATE INDEX IF NOT EXISTS idx_billing_calc_psp_period
    ON billing_calculations (psp_id, period_start, period_end);

-- 6. Composite index for invoice status queries (dunning scheduler)
CREATE INDEX IF NOT EXISTS idx_invoices_psp_status_due
    ON invoices (psp_id, status, due_date)
    WHERE status IN ('SENT', 'OVERDUE');

-- 7. Analyze all tables used in hot paths (update statistics)
ANALYZE merchants;
ANALYZE alerts;
ANALYZE compliance_cases;
ANALYZE api_usage_logs;
ANALYZE billing_calculations;
ANALYZE invoices;
