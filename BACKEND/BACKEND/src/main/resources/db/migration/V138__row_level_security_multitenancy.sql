-- =====================================================================
-- V138: Row Level Security (RLS) for True Database-Level Multitenancy
-- =====================================================================
-- Enables PostgreSQL Row Level Security on core tenant-scoped tables.
-- Uses session variable `app.current_psp_id` set by RlsContextFilter.
--
-- Platform admins (psp_id IS NULL) bypass RLS and see all data.
-- PSP users are restricted to their own tenant at the database level.
-- =====================================================================

-- Enable RLS
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE compliance_cases ENABLE ROW LEVEL SECURITY;
ALTER TABLE suspicious_activity_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE merchants ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform_users ENABLE ROW LEVEL SECURITY;

-- RLS Policies
CREATE POLICY psp_isolation_transactions ON transactions
    USING (current_setting('app.current_psp_id', true)::bigint IS NULL 
           OR psp_id = current_setting('app.current_psp_id', true)::bigint);

CREATE POLICY psp_isolation_alerts ON alerts
    USING (current_setting('app.current_psp_id', true)::bigint IS NULL 
           OR psp_id = current_setting('app.current_psp_id', true)::bigint);

CREATE POLICY psp_isolation_cases ON compliance_cases
    USING (current_setting('app.current_psp_id', true)::bigint IS NULL 
           OR psp_id = current_setting('app.current_psp_id', true)::bigint);

CREATE POLICY psp_isolation_sar ON suspicious_activity_reports
    USING (current_setting('app.current_psp_id', true)::bigint IS NULL 
           OR psp_id = current_setting('app.current_psp_id', true)::bigint);

CREATE POLICY psp_isolation_merchants ON merchants
    USING (current_setting('app.current_psp_id', true)::bigint IS NULL 
           OR psp_id = current_setting('app.current_psp_id', true)::bigint);

CREATE POLICY psp_isolation_users ON platform_users
    USING (current_setting('app.current_psp_id', true)::bigint IS NULL 
           OR psp_id = current_setting('app.current_psp_id', true)::bigint);

-- Force RLS even for superusers
ALTER TABLE transactions FORCE ROW LEVEL SECURITY;
ALTER TABLE alerts FORCE ROW LEVEL SECURITY;
ALTER TABLE compliance_cases FORCE ROW LEVEL SECURITY;
ALTER TABLE suspicious_activity_reports FORCE ROW LEVEL SECURITY;
ALTER TABLE merchants FORCE ROW LEVEL SECURITY;
ALTER TABLE platform_users FORCE ROW LEVEL SECURITY;

COMMENT ON POLICY psp_isolation_transactions ON transactions IS 'Database-level PSP tenant isolation';