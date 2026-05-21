-- ===================================================================================
-- V111: Final Index Optimization
-- Comprehensive index coverage for production workloads
-- ===================================================================================

-- ===================================================================================
-- COMPOSITE INDEXES FOR COMMON QUERY PATTERNS
-- ===================================================================================

-- Transaction monitoring dashboard (merchant + date + amount for risk analysis)
CREATE INDEX IF NOT EXISTS idx_txn_merchant_date_amount 
ON transactions(merchant_id, txn_ts DESC, amount_cents)
WHERE amount_cents > 1000000;  -- High-value transactions only

COMMENT ON INDEX idx_txn_merchant_date_amount IS 'High-value transaction monitoring by merchant';

-- Case workload assignment (assigned cases by priority and age)
CREATE INDEX IF NOT EXISTS idx_case_workload 
ON compliance_cases(assigned_to, priority, created_at DESC)
WHERE case_status IN ('OPEN', 'IN_PROGRESS');

COMMENT ON INDEX idx_case_workload IS 'Optimizes case assignment and workload views';

-- Alert triage (open alerts by severity and age)
CREATE INDEX IF NOT EXISTS idx_alert_triage 
ON alerts(status, score DESC, created_at DESC)
WHERE status = 'open';

COMMENT ON INDEX idx_alert_triage IS 'Alert triage dashboard - highest scores first';

-- ===================================================================================
-- FOREIGN KEY INDEXES (if missing)
-- ===================================================================================

-- Transaction features foreign key
CREATE INDEX IF NOT EXISTS idx_features_txn_id 
ON transaction_features(txn_id);

-- Alert transaction reference
CREATE INDEX IF NOT EXISTS idx_alert_txn_ref 
ON alerts(txn_id);

-- ===================================================================================
-- TEXT SEARCH INDEXES (for name lookups)
-- ===================================================================================

-- Merchant name search (trigram for fuzzy matching)
CREATE INDEX IF NOT EXISTS idx_merchants_name_trgm 
ON merchants USING gin(legal_name gin_trgm_ops);

COMMENT ON INDEX idx_merchants_name_trgm IS 'Fuzzy merchant name search';

-- Beneficial owner name search
CREATE INDEX IF NOT EXISTS idx_owners_name_trgm 
ON beneficial_owners USING gin(full_name gin_trgm_ops);

COMMENT ON INDEX idx_owners_name_trgm IS 'Fuzzy owner name search for sanctions screening';

-- Platform user name search
CREATE INDEX IF NOT EXISTS idx_users_name_trgm 
ON platform_users USING gin((first_name || ' ' || last_name) gin_trgm_ops);

COMMENT ON INDEX idx_users_name_trgm IS 'User name search';

-- ===================================================================================
-- DATE RANGE INDEXES (for reporting)
-- ===================================================================================

-- Monthly transaction aggregation
CREATE INDEX IF NOT EXISTS idx_txn_monthly 
ON transactions(DATE_TRUNC('month', txn_ts), merchant_id, amount_cents);

COMMENT ON INDEX idx_txn_monthly IS 'Monthly transaction reporting';

-- Daily API usage aggregation
CREATE INDEX IF NOT EXISTS idx_api_usage_daily 
ON api_usage_logs(DATE_TRUNC('day', request_timestamp), psp_id, service_type);

COMMENT ON INDEX idx_api_usage_daily IS 'Daily usage analytics';

-- ===================================================================================
-- COVERING INDEXES (Index-only scans)
-- ===================================================================================

-- Merchant list with status (covers common listing query)
CREATE INDEX IF NOT EXISTS idx_merchant_listing 
ON merchants(status, created_at DESC, merchant_id, legal_name, country, mcc);

COMMENT ON INDEX idx_merchant_listing IS 'Merchant listing page (index-only scan)';

-- Case list for dashboard (covers case listing query)
CREATE INDEX IF NOT EXISTS idx_case_listing 
ON compliance_cases(case_status, priority, created_at DESC, case_id, case_type, assigned_to);

COMMENT ON INDEX idx_case_listing IS 'Case dashboard listing (index-only scan)';

-- ===================================================================================
-- UNIQUE INDEXES WITH CONDITIONS
-- ===================================================================================

-- Prevent duplicate active screenings
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_active_screening 
ON merchant_screening_results(merchant_id, screening_type)
WHERE screening_status IN ('PENDING', 'IN_PROGRESS');

COMMENT ON INDEX idx_unique_active_screening IS 'Prevents duplicate active screenings per merchant';

-- ===================================================================================
-- PARTITIONING PREP (for future partitioning)
-- ===================================================================================

-- Range index on transaction timestamp (prepares for partitioning)
CREATE INDEX IF NOT EXISTS idx_txn_ts_brin 
ON transactions USING brin(txn_ts)
WITH (pages_per_range = 128);

COMMENT ON INDEX idx_txn_ts_brin IS 'Block range index for time-series queries (space-efficient)';

-- BRIN index for audit logs (very space-efficient for append-only data)
CREATE INDEX IF NOT EXISTS idx_audit_brin 
ON audit_logs_enhanced USING brin(timestamp)
WITH (pages_per_range = 128);

COMMENT ON INDEX idx_audit_brin IS 'Space-efficient time-series index for audit logs';

-- ===================================================================================
-- STATISTICS UPDATE
-- ===================================================================================

ANALYZE transactions;
ANALYZE transaction_features;
ANALYZE alerts;
ANALYZE compliance_cases;
ANALYZE merchants;
ANALYZE beneficial_owners;
ANALYZE platform_users;
ANALYZE api_usage_logs;

-- ===================================================================================
-- INDEX VERIFICATION VIEW
-- ===================================================================================

CREATE OR REPLACE VIEW v_index_summary AS
SELECT 
    schemaname,
    tablename,
    indexname,
    indexdef,
    pg_size_pretty(pg_relation_size(indexrelid)) as index_size,
    idx_scan as times_used,
    idx_tup_read as tuples_read,
    idx_tup_fetch as tuples_fetched
FROM pg_stat_user_indexes
JOIN pg_indexes USING (schemaname, tablename, indexname)
WHERE schemaname = 'public'
ORDER BY pg_relation_size(indexrelid) DESC;

COMMENT ON VIEW v_index_summary IS 'Summary of all indexes with usage statistics';

-- ===================================================================================
-- END OF MIGRATION
-- ===================================================================================
