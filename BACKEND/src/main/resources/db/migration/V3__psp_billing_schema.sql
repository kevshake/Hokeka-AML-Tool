-- PSP Multi-Tenant & Billing Module Schema
-- This migration adds PSP onboarding, user management, and billing capabilities

-- ================================================
-- PSP (Payment Service Provider) Registry
-- ================================================
CREATE TABLE IF NOT EXISTS psps (
    psp_id BIGSERIAL PRIMARY KEY,
    psp_code VARCHAR(50) UNIQUE NOT NULL,
    
    -- Company Details
    legal_name VARCHAR(500) NOT NULL,
    trading_name VARCHAR(500),
    country VARCHAR(3) NOT NULL,
    registration_number VARCHAR(100),
    tax_id VARCHAR(100),
    
    -- Contact Information
    contact_email VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(50),
    contact_address TEXT,
    
    -- Billing Configuration
    billing_plan VARCHAR(50) DEFAULT 'PAY_AS_YOU_GO',
    billing_cycle VARCHAR(20) DEFAULT 'MONTHLY',
    payment_terms INTEGER DEFAULT 30,
    currency VARCHAR(3) DEFAULT 'USD',
    
    -- Status
    status VARCHAR(50) DEFAULT 'PENDING',
    is_test_mode BOOLEAN DEFAULT false,
    
    -- Timestamps
    onboarded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMP,
    suspended_at TIMESTAMP,
    terminated_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_psps_code ON psps(psp_code);
CREATE INDEX IF NOT EXISTS idx_psps_status ON psps(status);
CREATE INDEX IF NOT EXISTS idx_psps_country ON psps(country);

COMMENT ON TABLE psps IS 'Payment Service Provider registry for multi-tenant system';

-- ================================================
-- PSP Users (Email-based authentication)
-- ================================================
CREATE TABLE IF NOT EXISTS psp_users (
    user_id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL REFERENCES psps(psp_id) ON DELETE CASCADE,
    
    -- Identity
    email VARCHAR(255) UNIQUE NOT NULL,
    full_name VARCHAR(500) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    
    -- Role & Permissions
    role VARCHAR(50) DEFAULT 'OPERATOR',
    permissions TEXT[],
    
    -- Status
    status VARCHAR(50) DEFAULT 'ACTIVE',
    email_verified BOOLEAN DEFAULT false,
    verification_token VARCHAR(255),
    
    -- Security
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(45),
    failed_login_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    password_reset_token VARCHAR(255),
    password_reset_expires TIMESTAMP,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_psp_users_psp ON psp_users(psp_id);
CREATE INDEX IF NOT EXISTS idx_psp_users_email ON psp_users(email);
CREATE INDEX IF NOT EXISTS idx_psp_users_status ON psp_users(status);

COMMENT ON TABLE psp_users IS 'PSP user accounts tied to specific PSPs via email';

-- ================================================
-- API Usage Logs (Request Tracking)
-- ================================================
CREATE TABLE IF NOT EXISTS api_usage_logs (
    log_id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL REFERENCES psps(psp_id),
    user_id BIGINT REFERENCES psp_users(user_id),
    
    -- Request Details
    endpoint VARCHAR(500) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    request_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    response_status INTEGER,
    response_time_ms INTEGER,
    
    -- Usage Categorization
    service_type VARCHAR(100) NOT NULL,
    billable BOOLEAN DEFAULT true,
    cost_amount DECIMAL(10, 4),
    cost_currency VARCHAR(3) DEFAULT 'USD',
    
    -- Request Metadata
    request_id VARCHAR(100),
    merchant_id BIGINT,
    ip_address VARCHAR(45),
    user_agent TEXT,
    
    -- External Costs (e.g., Sumsub)
    external_provider VARCHAR(100),
    external_cost DECIMAL(10, 4),
    
    -- Additional Data
    request_size_bytes INTEGER,
    response_size_bytes INTEGER,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_api_usage_psp ON api_usage_logs(psp_id, request_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_api_usage_service ON api_usage_logs(service_type, billable);
CREATE INDEX IF NOT EXISTS idx_api_usage_timestamp ON api_usage_logs(request_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_api_usage_request_id ON api_usage_logs(request_id);

COMMENT ON TABLE api_usage_logs IS 'Tracks all API requests for billing and analytics';

-- ================================================
-- Billing Rates (Pricing Configuration)
-- ================================================
CREATE TABLE IF NOT EXISTS billing_rates (
    rate_id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT REFERENCES psps(psp_id),
    
    -- Service Pricing
    service_type VARCHAR(100) NOT NULL,
    pricing_model VARCHAR(50) NOT NULL,
    
    -- Per-Request Pricing
    base_rate DECIMAL(10, 4),
    currency VARCHAR(3) DEFAULT 'USD',
    
    -- Tiered Pricing (stores tier configuration as JSON)
    tier_config JSONB,
    
    -- Subscription Pricing
    monthly_fee DECIMAL(10, 2),
    included_requests INTEGER,
    overage_rate DECIMAL(10, 4),
    
    -- Validity Period
    effective_from DATE NOT NULL,
    effective_to DATE,
    is_active BOOLEAN DEFAULT true,
    
    -- Metadata
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT check_pricing_model CHECK (pricing_model IN ('PER_REQUEST', 'TIERED', 'SUBSCRIPTION', 'HYBRID'))
);

CREATE INDEX IF NOT EXISTS idx_billing_rates_psp ON billing_rates(psp_id, service_type);
CREATE INDEX IF NOT EXISTS idx_billing_rates_active ON billing_rates(is_active, effective_from, effective_to);
CREATE INDEX IF NOT EXISTS idx_billing_rates_service ON billing_rates(service_type);

COMMENT ON TABLE billing_rates IS 'Configurable pricing rates per service type and PSP';

-- ================================================
-- Invoices (Monthly Bills)
-- ================================================
CREATE TABLE IF NOT EXISTS invoices (
    invoice_id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL REFERENCES psps(psp_id),
    
    -- Invoice Details
    invoice_number VARCHAR(100) UNIQUE NOT NULL,
    billing_period_start DATE NOT NULL,
    billing_period_end DATE NOT NULL,
    
    -- Amounts
    subtotal DECIMAL(12, 2) NOT NULL,
    tax_amount DECIMAL(12, 2) DEFAULT 0,
    tax_rate DECIMAL(5, 2) DEFAULT 0,
    discount_amount DECIMAL(12, 2) DEFAULT 0,
    discount_reason TEXT,
    total_amount DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    
    -- Payment Status
    status VARCHAR(50) DEFAULT 'DRAFT',
    due_date DATE NOT NULL,
    paid_at TIMESTAMP,
    payment_method VARCHAR(100),
    payment_reference VARCHAR(255),
    payment_amount DECIMAL(12, 2),
    
    -- Notes
    notes TEXT,
    internal_notes TEXT,
    
    -- Timestamps
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP,
    reminded_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT check_invoice_status CHECK (status IN ('DRAFT', 'SENT', 'PAID', 'OVERDUE', 'CANCELLED', 'PARTIALLY_PAID'))
);

CREATE INDEX IF NOT EXISTS idx_invoices_psp ON invoices(psp_id, billing_period_end DESC);
CREATE INDEX IF NOT EXISTS idx_invoices_status ON invoices(status, due_date);
CREATE INDEX IF NOT EXISTS idx_invoices_number ON invoices(invoice_number);
CREATE INDEX IF NOT EXISTS idx_invoices_period ON invoices(billing_period_start, billing_period_end);

COMMENT ON TABLE invoices IS 'Generated invoices for PSP billing';

-- ================================================
-- Invoice Line Items (Invoice Details)
-- ================================================
CREATE TABLE IF NOT EXISTS invoice_line_items (
    line_item_id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoices(invoice_id) ON DELETE CASCADE,
    
    -- Line Item Details
    line_number INTEGER NOT NULL,
    description TEXT NOT NULL,
    service_type VARCHAR(100) NOT NULL,
    
    -- Quantity & Pricing
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(10, 4) NOT NULL,
    line_total DECIMAL(12, 2) NOT NULL,
    
    -- Period (if applicable)
    period_start DATE,
    period_end DATE,
    
    -- Metadata
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_line_items_invoice ON invoice_line_items(invoice_id, line_number);
CREATE INDEX IF NOT EXISTS idx_line_items_service ON invoice_line_items(service_type);

COMMENT ON TABLE invoice_line_items IS 'Detailed breakdown of invoice charges';

-- ================================================
-- Trigger: Update timestamps
-- ================================================
CREATE OR REPLACE FUNCTION update_psp_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER psps_updated_at BEFORE UPDATE ON psps
    FOR EACH ROW EXECUTE FUNCTION update_psp_updated_at();

CREATE TRIGGER psp_users_updated_at BEFORE UPDATE ON psp_users
    FOR EACH ROW EXECUTE FUNCTION update_psp_updated_at();

CREATE TRIGGER billing_rates_updated_at BEFORE UPDATE ON billing_rates
    FOR EACH ROW EXECUTE FUNCTION update_psp_updated_at();

CREATE TRIGGER invoices_updated_at BEFORE UPDATE ON invoices
    FOR EACH ROW EXECUTE FUNCTION update_psp_updated_at();

-- ================================================
-- Initial Default Billing Rates
-- ================================================
INSERT INTO billing_rates (psp_id, service_type, pricing_model, base_rate, currency, effective_from, is_active, description) VALUES
(NULL, 'MERCHANT_ONBOARDING_TIER1', 'PER_REQUEST', 2.00, 'USD', CURRENT_DATE, true, 'Merchant onboarding with Sumsub comprehensive screening'),
(NULL, 'MERCHANT_ONBOARDING_TIER2', 'PER_REQUEST', 0.10, 'USD', CURRENT_DATE, true, 'Merchant onboarding with Aerospike sanctions only'),
(NULL, 'SANCTIONS_SCREENING_PERSON', 'PER_REQUEST', 0.50, 'USD', CURRENT_DATE, true, 'Individual sanctions screening'),
(NULL, 'SANCTIONS_SCREENING_ORGANIZATION', 'PER_REQUEST', 0.75, 'USD', CURRENT_DATE, true, 'Organization sanctions screening'),
(NULL, 'COMPLIANCE_CASE_CREATION', 'PER_REQUEST', 1.00, 'USD', CURRENT_DATE, true, 'Compliance case creation for manual review'),
(NULL, 'MERCHANT_RESCREENING', 'PER_REQUEST', 0.05, 'USD', CURRENT_DATE, true, 'Weekly automated merchant rescreening'),
(NULL, 'API_CALL_GENERIC', 'PER_REQUEST', 0.01, 'USD', CURRENT_DATE, true, 'Generic API call');

-- ================================================
-- Views for Reporting
-- ================================================

-- PSP Monthly Usage Summary
CREATE OR REPLACE VIEW v_psp_monthly_usage AS
SELECT 
    p.psp_id,
    p.psp_code,
    p.legal_name,
    DATE_TRUNC('month', a.request_timestamp) AS billing_month,
    a.service_type,
    COUNT(*) AS request_count,
    SUM(a.cost_amount) AS total_cost,
    AVG(a.response_time_ms) AS avg_response_time_ms
FROM psps p
JOIN api_usage_logs a ON p.psp_id = a.psp_id
WHERE a.billable = true
GROUP BY p.psp_id, p.psp_code, p.legal_name, DATE_TRUNC('month', a.request_timestamp), a.service_type;

COMMENT ON VIEW v_psp_monthly_usage IS 'Monthly usage summary by PSP and service type';

-- Outstanding Invoices
CREATE OR REPLACE VIEW v_outstanding_invoices AS
SELECT 
    i.invoice_id,
    i.invoice_number,
    p.psp_code,
    p.legal_name,
    i.total_amount,
    i.currency,
    i.due_date,
    CURRENT_DATE - i.due_date AS days_overdue,
    i.status
FROM invoices i
JOIN psps p ON i.psp_id = p.psp_id
WHERE i.status IN ('SENT', 'OVERDUE', 'PARTIALLY_PAID')
  AND i.due_date < CURRENT_DATE
ORDER BY i.due_date ASC;

COMMENT ON VIEW v_outstanding_invoices IS 'All unpaid invoices past due date';

