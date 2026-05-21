-- ===================================================================================
-- V110: Additional Performance Indexes
-- Based on Database Schema Review recommendations
-- ===================================================================================

-- ===================================================================================
-- TRANSACTION MONITORING INDEXES
-- ===================================================================================

-- For fraud detection by merchant + amount (high-value transaction monitoring)
CREATE INDEX IF NOT EXISTS idx_txn_merchant_amount 
ON transactions(merchant_id, amount_cents DESC, txn_ts DESC);

COMMENT ON INDEX idx_txn_merchant_amount IS 'Optimizes high-value transaction queries for fraud detection';

-- For terminal-based analysis (velocity per terminal)
CREATE INDEX IF NOT EXISTS idx_txn_terminal_time 
ON transactions(terminal_id, txn_ts DESC) 
WHERE terminal_id IS NOT NULL;

COMMENT ON INDEX idx_txn_terminal_time IS 'Terminal velocity checks and analysis';

-- ===================================================================================
-- CASE MANAGEMENT INDEXES
-- ===================================================================================

-- For case resolution performance reporting
CREATE INDEX IF NOT EXISTS idx_case_resolved_at 
ON compliance_cases(resolved_at DESC) 
WHERE case_status IN ('RESOLVED', 'CLOSED');

COMMENT ON INDEX idx_case_resolved_at IS 'Optimizes resolved case reporting and metrics';

-- For case type analysis
CREATE INDEX IF NOT EXISTS idx_case_type_status 
ON compliance_cases(case_type, case_status, created_at DESC);

COMMENT ON INDEX idx_case_case_type_status IS 'Case type dashboard and filtering';

-- ===================================================================================
-- SCREENING PERFORMANCE INDEXES
-- ===================================================================================

-- For batch screening operations - merchants needing rescreening
CREATE INDEX IF NOT EXISTS idx_merchants_next_screening 
ON merchants(next_screening_due, last_screened_at) 
WHERE status = 'ACTIVE';

COMMENT ON INDEX idx_merchants_next_screening IS 'Optimizes batch rescreening job performance';

-- For screening by date range + status
CREATE INDEX IF NOT EXISTS idx_merchant_screening_date_status 
ON merchant_screening_results(screened_at DESC, screening_status) 
WHERE match_score > 0.5;

COMMENT ON INDEX idx_merchant_screening_date_status IS 'High-confidence match queries for investigation';

-- ===================================================================================
-- AUDIT & LOGGING INDEXES
-- ===================================================================================

-- For IP-based audit queries (security investigations)
CREATE INDEX IF NOT EXISTS idx_audit_ip_time 
ON audit_logs_enhanced(ip_address, timestamp DESC);

COMMENT ON INDEX idx_audit_ip_time IS 'Security incident investigations by IP';

-- For entity change history (compliance reporting)
CREATE INDEX IF NOT EXISTS idx_audit_entity_change 
ON audit_logs_enhanced(entity_type, entity_id, action_type, timestamp DESC);

COMMENT ON INDEX idx_audit_entity_change IS 'Complete change history for compliance audits';

-- ===================================================================================
-- BILLING & USAGE INDEXES
-- ===================================================================================

-- For PSP usage analytics by service type
CREATE INDEX IF NOT EXISTS idx_api_usage_psp_service 
ON api_usage_logs(psp_id, service_type, request_timestamp DESC);

COMMENT ON INDEX idx_api_usage_psp_service IS 'PSP usage breakdown by service type';

-- For error rate analysis
CREATE INDEX IF NOT EXISTS idx_api_usage_errors 
ON api_usage_logs(psp_id, response_status, request_timestamp DESC) 
WHERE response_status >= 400;

COMMENT ON INDEX idx_api_usage_errors IS 'Error tracking and alerting';

-- ===================================================================================
-- USER MANAGEMENT INDEXES
-- ===================================================================================

-- For active user session lookup
CREATE INDEX IF NOT EXISTS idx_platform_users_last_login 
ON platform_users(last_login_at DESC) 
WHERE enabled = TRUE;

COMMENT ON INDEX idx_platform_users_last_login IS 'Active user reporting and session management';

-- For PSP user listing
CREATE INDEX IF NOT EXISTS idx_psp_users_role 
ON psp_users(psp_id, role, status);

COMMENT ON INDEX idx_psp_users_role IS 'PSP user management and role queries';

-- ===================================================================================
-- UPDATE STATISTICS
-- ===================================================================================

ANALYZE transactions;
ANALYZE compliance_cases;
ANALYZE merchants;
ANALYZE merchant_screening_results;
ANALYZE audit_logs_enhanced;
ANALYZE api_usage_logs;
ANALYZE platform_users;
ANALYZE psp_users;

-- ===================================================================================
-- END OF MIGRATION
-- ===================================================================================
