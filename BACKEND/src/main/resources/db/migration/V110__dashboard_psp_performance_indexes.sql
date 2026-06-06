-- V110__dashboard_psp_performance_indexes.sql
-- PSP-scoped composite indexes to eliminate full-table scans on dashboard endpoints.
-- Uses conditional blocks so this migration is safe on DBs where psp_id columns
-- were added by intermediate migrations (V18-V107) vs created fresh by Hibernate ddl-auto.

-- merchants: PSP-filtered status and risk-level counts
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name='merchants' AND column_name='psp_id') THEN
        CREATE INDEX IF NOT EXISTS idx_merchant_psp_status  ON merchants(psp_id, status);
        CREATE INDEX IF NOT EXISTS idx_merchant_psp_risk    ON merchants(psp_id, risk_level);
    END IF;
END $$;

-- compliance_cases: PSP-filtered status and priority counts
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name='compliance_cases' AND column_name='psp_id') THEN
        CREATE INDEX IF NOT EXISTS idx_case_psp_status   ON compliance_cases(psp_id, status);
        CREATE INDEX IF NOT EXISTS idx_case_psp_priority ON compliance_cases(psp_id, priority);
    END IF;
END $$;

-- alerts: support dashboard live-alerts queries ordered by status + time
-- (merchant_id on alerts is added by a later migration; join to merchants is via txn_id)
CREATE INDEX IF NOT EXISTS idx_alert_status_ts ON alerts(status, created_at DESC);

-- transactions: PSP-filtered time-ordered scans
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name='transactions' AND column_name='psp_id') THEN
        CREATE INDEX IF NOT EXISTS idx_txn_psp_time ON transactions(psp_id, txn_ts DESC);
    END IF;
END $$;

-- Update planner statistics for the changed tables
ANALYZE merchants;
ANALYZE compliance_cases;
ANALYZE alerts;
ANALYZE transactions;
