-- V110__dashboard_psp_performance_indexes.sql
-- PSP-scoped composite indexes to eliminate full-table scans on dashboard endpoints.
-- Targets: /dashboard/stats (295ms->sub-100ms), /dashboard/cases/priority,
--          /dashboard/risk-distribution, /dashboard/live-alerts, /dashboard/recent-transactions

-- merchants: PSP-filtered status and risk-level counts
CREATE INDEX IF NOT EXISTS idx_merchant_psp_status
    ON merchants(psp_id, status);

CREATE INDEX IF NOT EXISTS idx_merchant_psp_risk
    ON merchants(psp_id, risk_level);

-- compliance_cases: PSP-filtered status and priority counts
CREATE INDEX IF NOT EXISTS idx_case_psp_status
    ON compliance_cases(psp_id, status);

CREATE INDEX IF NOT EXISTS idx_case_psp_priority
    ON compliance_cases(psp_id, priority);

-- alerts: supports the INNER JOIN to merchants + PSP filter + ORDER BY
CREATE INDEX IF NOT EXISTS idx_alert_merchant_status_ts
    ON alerts(merchant_id, status, created_at DESC);

-- transactions: PSP-filtered time-ordered scans (recent-transactions, daily volume)
CREATE INDEX IF NOT EXISTS idx_txn_psp_time
    ON transactions(psp_id, txn_ts DESC);

-- Update planner statistics for the changed tables
ANALYZE merchants;
ANALYZE compliance_cases;
ANALYZE alerts;
ANALYZE transactions;
