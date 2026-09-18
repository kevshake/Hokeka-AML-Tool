-- V13: Billing and Metering System
-- Creates pricing tiers, subscriptions, cost metrics, and billing calculations tables
-- Supports decoupled billing architecture with async metering

-- Pricing tiers table
CREATE TABLE IF NOT EXISTS pricing_tiers (
    tier_id SERIAL PRIMARY KEY,
    tier_code VARCHAR(20) UNIQUE NOT NULL,
    tier_name VARCHAR(100) NOT NULL,
    monthly_fee_usd DECIMAL(10,2) DEFAULT 0,
    per_check_price_usd DECIMAL(10,4) NOT NULL,
    monthly_minimum_usd DECIMAL(10,2),
    max_checks_per_month INTEGER,
    included_checks INTEGER DEFAULT 0,
    volume_discounts JSONB DEFAULT '{}',
    features JSONB DEFAULT '[]',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Customer subscriptions table
CREATE TABLE IF NOT EXISTS subscriptions (
    subscription_id SERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL REFERENCES psps(psp_id),
    tier_id INTEGER NOT NULL REFERENCES pricing_tiers(tier_id),
    billing_currency VARCHAR(3) DEFAULT 'USD',
    billing_cycle VARCHAR(20) DEFAULT 'MONTHLY',
    discount_percentage DECIMAL(5,2) DEFAULT 0,
    contract_start DATE NOT NULL,
    contract_end DATE,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    trial_ends_at DATE,
    rollover_credits INTEGER DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Cost metrics table (engine reads from here)
CREATE TABLE IF NOT EXISTS cost_metrics (
    metric_id SERIAL PRIMARY KEY,
    metric_date DATE UNIQUE NOT NULL,
    fixed_costs_monthly DECIMAL(12,2),
    variable_cost_per_check DECIMAL(10,4),
    manual_review_cost DECIMAL(10,4),
    data_feed_cost DECIMAL(10,4),
    target_margin DECIMAL(5,4),
    actual_margin DECIMAL(5,4),
    total_checks_processed BIGINT DEFAULT 0,
    total_revenue DECIMAL(12,2) DEFAULT 0,
    total_costs DECIMAL(12,2) DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Currency exchange rates
CREATE TABLE IF NOT EXISTS currency_rates (
    currency_code VARCHAR(3) PRIMARY KEY,
    currency_name VARCHAR(50),
    rate_to_usd DECIMAL(12,6) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Billing calculations (audit trail)
CREATE TABLE IF NOT EXISTS billing_calculations (
    calculation_id SERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    tier_code VARCHAR(20),
    subscription_fee DECIMAL(10,2) DEFAULT 0,
    check_count INTEGER DEFAULT 0,
    base_usage_cost DECIMAL(10,2) DEFAULT 0,
    volume_discount_amount DECIMAL(10,2) DEFAULT 0,
    total_usage_cost DECIMAL(10,2) DEFAULT 0,
    minimum_adjustment DECIMAL(10,2) DEFAULT 0,
    total_amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    cost_metrics_snapshot JSONB,
    calculation_details JSONB,
    status VARCHAR(20) DEFAULT 'CALCULATED',
    invoice_id BIGINT,
    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_subscriptions_psp ON subscriptions(psp_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_status ON subscriptions(status);
CREATE INDEX IF NOT EXISTS idx_billing_calculations_psp ON billing_calculations(psp_id);
CREATE INDEX IF NOT EXISTS idx_billing_calculations_period ON billing_calculations(period_start, period_end);
CREATE INDEX IF NOT EXISTS idx_cost_metrics_date ON cost_metrics(metric_date DESC);

-- Seed pricing tiers
INSERT INTO pricing_tiers (tier_code, tier_name, monthly_fee_usd, per_check_price_usd, 
    monthly_minimum_usd, max_checks_per_month, included_checks, volume_discounts, features)
VALUES 
    ('FREE', 'Free Trial', 0, 0, NULL, 100, 100, 
     '{}', 
     '["sandbox", "basic_kyc", "email_support"]'),
    ('STARTER', 'Starter', 49, 0.20, 49, NULL, 0, 
     '{"5000": 0.10}', 
     '["kyc", "aml_screening", "email_support", "basic_reporting"]'),
    ('GROWTH', 'Growth', 249, 0.09, 149, 50000, 0, 
     '{"5000": 0.10, "25000": 0.20, "50000": 0.30}', 
     '["kyc", "aml_screening", "transaction_monitoring", "webhooks", "priority_support", "advanced_reporting"]'),
    ('BUSINESS', 'Business', 999, 0.03, 500, 500000, 0, 
     '{"5000": 0.10, "25000": 0.20, "50000": 0.30, "100000": 0.40}', 
     '["kyc", "aml_screening", "transaction_monitoring", "case_management", "sar_generation", "phone_support", "sla_99_5", "custom_rules"]'),
    ('ENTERPRISE', 'Enterprise', 0, 0.015, 5000, NULL, 0, 
     '{"100000": 0.30, "500000": 0.50}', 
     '["all_features", "dedicated_support", "custom_sla", "on_prem_option", "white_label", "api_priority"]')
ON CONFLICT (tier_code) DO NOTHING;

-- Seed currency rates (African focus)
INSERT INTO currency_rates (currency_code, currency_name, rate_to_usd)
VALUES 
    ('USD', 'US Dollar', 1.000000),
    ('KES', 'Kenyan Shilling', 0.007800),
    ('NGN', 'Nigerian Naira', 0.000650),
    ('ZAR', 'South African Rand', 0.054000),
    ('UGX', 'Ugandan Shilling', 0.000270),
    ('TZS', 'Tanzanian Shilling', 0.000390),
    ('GHS', 'Ghanaian Cedi', 0.063000),
    ('RWF', 'Rwandan Franc', 0.000760),
    ('ETB', 'Ethiopian Birr', 0.008500),
    ('EUR', 'Euro', 1.100000),
    ('GBP', 'British Pound', 1.270000)
ON CONFLICT (currency_code) DO UPDATE 
    SET rate_to_usd = EXCLUDED.rate_to_usd, updated_at = CURRENT_TIMESTAMP;

-- Seed initial cost metrics
INSERT INTO cost_metrics (metric_date, fixed_costs_monthly, variable_cost_per_check, 
    manual_review_cost, data_feed_cost, target_margin, notes)
VALUES 
    (CURRENT_DATE, 10000.00, 0.05, 0.02, 0.01, 0.65, 'Initial cost metrics baseline')
ON CONFLICT (metric_date) DO NOTHING;

