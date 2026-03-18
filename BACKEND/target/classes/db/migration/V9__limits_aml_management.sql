-- Limits & AML Management Migration
-- Creates tables for comprehensive limits and AML configuration management

-- Merchant Transaction Limits Table
CREATE TABLE IF NOT EXISTS merchant_transaction_limits (
    id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    daily_limit DECIMAL(19,2),
    weekly_limit DECIMAL(19,2),
    monthly_limit DECIMAL(19,2),
    per_transaction_limit DECIMAL(19,2),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT fk_merchant_limits_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(merchant_id) ON DELETE CASCADE,
    CONSTRAINT fk_merchant_limits_created_by FOREIGN KEY (created_by) REFERENCES psp_users(user_id),
    CONSTRAINT fk_merchant_limits_updated_by FOREIGN KEY (updated_by) REFERENCES psp_users(user_id),
    CONSTRAINT unique_merchant_limit UNIQUE (merchant_id)
);

CREATE INDEX IF NOT EXISTS idx_merchant_limits_status ON merchant_transaction_limits(status);
CREATE INDEX IF NOT EXISTS idx_merchant_limits_merchant ON merchant_transaction_limits(merchant_id);

-- Global Limits Table
CREATE TABLE IF NOT EXISTS global_limits (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    limit_type VARCHAR(50) NOT NULL, -- VOLUME, COUNT, VELOCITY
    limit_value DECIMAL(19,2) NOT NULL,
    period VARCHAR(20) NOT NULL, -- DAY, HOUR, MINUTE, WEEK, MONTH
    current_usage DECIMAL(19,2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT fk_global_limits_created_by FOREIGN KEY (created_by) REFERENCES psp_users(user_id),
    CONSTRAINT fk_global_limits_updated_by FOREIGN KEY (updated_by) REFERENCES psp_users(user_id),
    CONSTRAINT unique_global_limit_name UNIQUE (name)
);

CREATE INDEX IF NOT EXISTS idx_global_limits_type ON global_limits(limit_type);
CREATE INDEX IF NOT EXISTS idx_global_limits_status ON global_limits(status);

-- Risk Thresholds Table
CREATE TABLE IF NOT EXISTS risk_thresholds (
    id BIGSERIAL PRIMARY KEY,
    risk_level VARCHAR(20) NOT NULL, -- LOW, MEDIUM, HIGH, CRITICAL
    description TEXT,
    daily_limit DECIMAL(19,2) NOT NULL,
    per_transaction_limit DECIMAL(19,2) NOT NULL,
    velocity_limit INT, -- transactions per hour
    merchant_count INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT fk_risk_thresholds_created_by FOREIGN KEY (created_by) REFERENCES psp_users(user_id),
    CONSTRAINT fk_risk_thresholds_updated_by FOREIGN KEY (updated_by) REFERENCES psp_users(user_id),
    CONSTRAINT unique_risk_level UNIQUE (risk_level)
);

CREATE INDEX IF NOT EXISTS idx_risk_thresholds_level ON risk_thresholds(risk_level);
CREATE INDEX IF NOT EXISTS idx_risk_thresholds_status ON risk_thresholds(status);

-- Velocity Rules Table
CREATE TABLE IF NOT EXISTS velocity_rules (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(200) NOT NULL,
    description TEXT,
    max_transactions INT NOT NULL,
    max_amount DECIMAL(19,2) NOT NULL,
    time_window_minutes INT NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    trigger_count INT DEFAULT 0,
    last_triggered_at TIMESTAMP,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT fk_velocity_rules_created_by FOREIGN KEY (created_by) REFERENCES psp_users(user_id),
    CONSTRAINT fk_velocity_rules_updated_by FOREIGN KEY (updated_by) REFERENCES psp_users(user_id),
    CONSTRAINT unique_velocity_rule_name UNIQUE (rule_name)
);

CREATE INDEX IF NOT EXISTS idx_velocity_rules_status ON velocity_rules(status);
CREATE INDEX IF NOT EXISTS idx_velocity_rules_risk_level ON velocity_rules(risk_level);

-- Country Compliance Rules Table
CREATE TABLE IF NOT EXISTS country_compliance_rules (
    id BIGSERIAL PRIMARY KEY,
    country_code VARCHAR(3) NOT NULL,
    country_name VARCHAR(100) NOT NULL,
    compliance_requirements TEXT, -- JSON for flexible requirements
    transaction_restrictions TEXT, -- JSON for restrictions
    required_documentation TEXT, -- JSON for required docs
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT fk_country_compliance_created_by FOREIGN KEY (created_by) REFERENCES psp_users(user_id),
    CONSTRAINT fk_country_compliance_updated_by FOREIGN KEY (updated_by) REFERENCES psp_users(user_id),
    CONSTRAINT unique_country_code UNIQUE (country_code)
);

CREATE INDEX IF NOT EXISTS idx_country_compliance_status ON country_compliance_rules(status);
CREATE INDEX IF NOT EXISTS idx_country_compliance_country ON country_compliance_rules(country_code);

COMMENT ON TABLE merchant_transaction_limits IS 'Transaction limits configured per merchant';
COMMENT ON TABLE global_limits IS 'System-wide transaction limits';
COMMENT ON TABLE risk_thresholds IS 'Risk-based transaction limits';
COMMENT ON TABLE velocity_rules IS 'Velocity monitoring rules for transaction patterns';
COMMENT ON TABLE country_compliance_rules IS 'Country-specific compliance rules and restrictions';


