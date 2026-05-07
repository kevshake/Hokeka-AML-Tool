-- High-Performance Partitioned Schema for AML System
-- PostgreSQL 15+ with declarative partitioning

-- ============================================================
-- SEQUENCE for transaction IDs
-- ============================================================
CREATE SEQUENCE IF NOT EXISTS transaction_seq
    START WITH 1
    INCREMENT BY 100
    CACHE 100;

-- ============================================================
-- PARTITIONED TRANSACTIONS TABLE
-- ============================================================
-- Range partitioned by txn_ts for efficient time-series queries
-- Partitions: Monthly (can be adjusted to daily for high volume)

CREATE TABLE transactions (
    txn_id BIGINT NOT NULL,
    txn_reference VARCHAR(64) NOT NULL UNIQUE,
    customer_id VARCHAR(64) NOT NULL,
    pan_hash VARCHAR(64),
    merchant_id VARCHAR(32),
    psp_id BIGINT,
    terminal_id VARCHAR(16),
    amount_cents BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'KES',
    txn_ts TIMESTAMP NOT NULL,
    country_code VARCHAR(2),
    channel VARCHAR(16),
    mcc VARCHAR(4),
    decision VARCHAR(16),
    risk_score INTEGER,
    rules_triggered TEXT,
    processing_time_ms INTEGER,
    features_version BIGINT,
    iso_msg TEXT,
    emv_tags JSONB,
    ip_address VARCHAR(45),
    device_fingerprint VARCHAR(128),
    user_agent TEXT,
    acquirer_response VARCHAR(4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ingestion_ts TIMESTAMP,
    decision_ts TIMESTAMP,
    
    PRIMARY KEY (txn_id, txn_ts) -- Include partition key in PK
) PARTITION BY RANGE (txn_ts);

-- Create monthly partitions (adjust as needed)
-- Current month + 3 months ahead

CREATE TABLE transactions_2026_03 PARTITION OF transactions
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');

CREATE TABLE transactions_2026_04 PARTITION OF transactions
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');

CREATE TABLE transactions_2026_05 PARTITION OF transactions
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE TABLE transactions_2026_06 PARTITION OF transactions
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

-- Default partition for overflow (optional but recommended)
-- CREATE TABLE transactions_default PARTITION OF transactions DEFAULT;

-- ============================================================
-- INDEXES for PARTITIONED TABLE
-- ============================================================
-- These are created on the parent table and inherited by partitions

-- Primary access pattern: customer + time range
CREATE INDEX idx_txn_customer_ts ON transactions (customer_id, txn_ts DESC);

-- Time-based queries
CREATE INDEX idx_txn_timestamp ON transactions (txn_ts);

-- Merchant analysis
CREATE INDEX idx_txn_merchant ON transactions (merchant_id);

-- Card-based tracking
CREATE INDEX idx_txn_pan_hash ON transactions (pan_hash);

-- Decision queries
CREATE INDEX idx_txn_decision ON transactions (decision);

-- Geographic analysis
CREATE INDEX idx_txn_country ON transactions (country_code);

-- Composite index for common queries
CREATE INDEX idx_txn_merchant_ts ON transactions (merchant_id, txn_ts DESC);

-- ============================================================
-- CUSTOMER FEATURES TABLE (Hot Path)
-- ============================================================
-- Pre-computed features for O(1) rule evaluation
-- Updated asynchronously via Kafka

CREATE TABLE customer_features (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL UNIQUE,
    
    -- Velocity Features (Rolling Windows)
    tx_count_1h INTEGER DEFAULT 0,
    tx_count_24h INTEGER DEFAULT 0,
    tx_count_7d INTEGER DEFAULT 0,
    tx_count_30d INTEGER DEFAULT 0,
    
    tx_volume_1h BIGINT DEFAULT 0,
    tx_volume_24h BIGINT DEFAULT 0,
    tx_volume_7d BIGINT DEFAULT 0,
    tx_volume_30d BIGINT DEFAULT 0,
    
    -- Behavioral Baselines
    avg_tx_amount DECIMAL(19,2) DEFAULT 0.0,
    max_tx_amount DECIMAL(19,2) DEFAULT 0.0,
    min_tx_amount DECIMAL(19,2) DEFAULT 0.0,
    usual_hours_start INTEGER DEFAULT 0,
    usual_hours_end INTEGER DEFAULT 23,
    home_country VARCHAR(2),
    usual_merchant_ids TEXT, -- JSON array
    
    -- Risk Indicators
    risk_score INTEGER DEFAULT 0,
    countries_last_24h TEXT, -- JSON array
    countries_last_7d TEXT, -- JSON array
    unique_countries_24h INTEGER DEFAULT 0,
    unique_countries_7d INTEGER DEFAULT 0,
    channels_used_24h TEXT, -- JSON array
    
    -- Device & Security
    unique_devices_24h INTEGER DEFAULT 0,
    unique_ips_24h INTEGER DEFAULT 0,
    failed_tx_count_24h INTEGER DEFAULT 0,
    
    -- Temporal Features
    last_tx_timestamp TIMESTAMP,
    last_tx_amount DECIMAL(19,2) DEFAULT 0.0,
    last_tx_country VARCHAR(2),
    time_since_last_tx_minutes INTEGER DEFAULT 0,
    
    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0
);

-- Indexes for customer features
CREATE INDEX idx_cf_customer ON customer_features (customer_id);
CREATE INDEX idx_cf_updated ON customer_features (updated_at);
CREATE INDEX idx_cf_risk ON customer_features (risk_score);

-- ============================================================
-- ALERTS TABLE (Case Management)
-- ============================================================
-- Not in hot path - standard table is fine

CREATE TABLE alerts (
    alert_id BIGSERIAL PRIMARY KEY,
    txn_id BIGINT,
    customer_id VARCHAR(64),
    score DECIMAL(5,2),
    action VARCHAR(16),
    reason TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'open',
    severity VARCHAR(16),
    merchant_id VARCHAR(32),
    investigator VARCHAR(64),
    notes TEXT,
    disposition VARCHAR(32),
    disposition_reason TEXT,
    disposed_by VARCHAR(64),
    disposed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_alert_status ON alerts (status);
CREATE INDEX idx_alert_customer ON alerts (customer_id);
CREATE INDEX idx_alert_created ON alerts (created_at);
CREATE INDEX idx_alert_severity ON alerts (severity);
CREATE INDEX idx_alert_txn ON alerts (txn_id);

-- ============================================================
-- AUDIT LOG TABLE (Partitioned)
-- ============================================================
-- Immutable audit trail - partitioned by month

CREATE TABLE audit_logs (
    log_id BIGSERIAL,
    event_type VARCHAR(32) NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64),
    action VARCHAR(32) NOT NULL,
    old_value JSONB,
    new_value JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (log_id, timestamp)
) PARTITION BY RANGE (timestamp);

-- Audit partitions
CREATE TABLE audit_logs_2026_03 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');

CREATE TABLE audit_logs_2026_04 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');

CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX idx_audit_timestamp ON audit_logs (timestamp);
CREATE INDEX idx_audit_user ON audit_logs (user_id);

-- ============================================================
-- BLACKLIST TABLES
-- ============================================================

CREATE TABLE blacklist_entries (
    id BIGSERIAL PRIMARY KEY,
    entry_type VARCHAR(32) NOT NULL, -- 'merchant', 'country', 'card', 'customer'
    entry_value VARCHAR(64) NOT NULL,
    reason TEXT,
    severity VARCHAR(16) DEFAULT 'HIGH',
    expires_at TIMESTAMP,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE (entry_type, entry_value)
);

CREATE INDEX idx_blacklist_type ON blacklist_entries (entry_type);
CREATE INDEX idx_blacklist_value ON blacklist_entries (entry_value);

-- ============================================================
-- MERCHANT RISK PROFILES
-- ============================================================

CREATE TABLE merchant_risk_profiles (
    merchant_id VARCHAR(32) PRIMARY KEY,
    mcc VARCHAR(4),
    risk_category VARCHAR(16) DEFAULT 'MEDIUM',
    avg_tx_amount DECIMAL(19,2) DEFAULT 0.0,
    tx_volume_30d BIGINT DEFAULT 0,
    chargeback_rate DECIMAL(5,4) DEFAULT 0.0,
    is_high_risk BOOLEAN DEFAULT FALSE,
    country_code VARCHAR(2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- PARTITION MAINTENANCE FUNCTIONS
-- ============================================================

-- Function to create next month's partition
CREATE OR REPLACE FUNCTION create_next_partition()
RETURNS void AS $$
DECLARE
    next_month DATE;
    partition_name TEXT;
    start_date DATE;
    end_date DATE;
BEGIN
    next_month := DATE_TRUNC('month', CURRENT_DATE + INTERVAL '1 month');
    partition_name := 'transactions_' || TO_CHAR(next_month, 'YYYY_MM');
    start_date := next_month;
    end_date := next_month + INTERVAL '1 month';
    
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF transactions FOR VALUES FROM (%L) TO (%L)',
        partition_name, start_date, end_date
    );
END;
$$ LANGUAGE plpgsql;

-- Function to drop old partitions (data retention)
CREATE OR REPLACE FUNCTION drop_old_partitions(retention_months INT)
RETURNS void AS $$
DECLARE
    partition RECORD;
    cutoff_date DATE;
BEGIN
    cutoff_date := CURRENT_DATE - (retention_months || ' months')::INTERVAL;
    
    FOR partition IN
        SELECT tablename 
        FROM pg_tables 
        WHERE tablename LIKE 'transactions_20%'
        AND tablename < 'transactions_' || TO_CHAR(cutoff_date, 'YYYY_MM')
    LOOP
        EXECUTE format('DROP TABLE IF EXISTS %I', partition.tablename);
        RAISE NOTICE 'Dropped partition: %', partition.tablename;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- TRIGGER for updated_at
-- ============================================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to tables with updated_at
CREATE TRIGGER update_customer_features_updated_at
    BEFORE UPDATE ON customer_features
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_alerts_updated_at
    BEFORE UPDATE ON alerts
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_blacklist_entries_updated_at
    BEFORE UPDATE ON blacklist_entries
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_merchant_risk_profiles_updated_at
    BEFORE UPDATE ON merchant_risk_profiles
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- GRANTS (adjust as needed)
-- ============================================================
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO fraud_user;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO fraud_user;
