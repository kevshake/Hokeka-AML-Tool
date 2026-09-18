-- V154: Hot-path indexes, R-2 threshold correction, monitoring_alerts status index

-- Fix R-2 high-value threshold: 10000 major currency units (was 1000000 — unreachable in dollars)
UPDATE rule_definitions
SET parameters = '{"threshold_amount": 10000, "currency": "KES"}'::jsonb
WHERE external_code = 'R-2';

-- alerts.status standalone filter (dashboard open-alert counts)
CREATE INDEX IF NOT EXISTS idx_alert_status ON alerts(status);

-- compliance_cases.status for unscoped status dashboards
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='compliance_cases' AND column_name='status') THEN
        CREATE INDEX IF NOT EXISTS idx_case_status ON compliance_cases(status);
    END IF;
END $$;

-- merchants.psp_id for tenant-scoped merchant lists
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='merchants' AND column_name='psp_id') THEN
        CREATE INDEX IF NOT EXISTS idx_merchant_psp_id ON merchants(psp_id);
    END IF;
END $$;

-- monitoring_alerts: unacknowledged feed
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='monitoring_alerts') THEN
        CREATE INDEX IF NOT EXISTS idx_monitoring_alerts_ack_created
            ON monitoring_alerts(acknowledged, created_at DESC);
    END IF;
END $$;

-- rule_definitions: enabled rules loaded at startup
CREATE INDEX IF NOT EXISTS idx_rule_def_enabled_priority
    ON rule_definitions(enabled, priority DESC)
    WHERE enabled = TRUE;

ANALYZE rule_definitions;
