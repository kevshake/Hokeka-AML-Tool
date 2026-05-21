-- Model Configuration Table
CREATE TABLE IF NOT EXISTS model_config (
    id SERIAL PRIMARY KEY,
    config_key TEXT UNIQUE NOT NULL,
    value TEXT NOT NULL,
    description TEXT,
    updated_by TEXT,
    updated_at TIMESTAMP DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_config_key ON model_config(config_key);

-- Transactions Table
CREATE TABLE IF NOT EXISTS transactions (
    txn_id BIGSERIAL PRIMARY KEY,
    iso_msg TEXT,
    pan_hash TEXT,
    merchant_id TEXT,
    terminal_id TEXT,
    amount_cents BIGINT,
    currency CHAR(3),
    txn_ts TIMESTAMP,
    emv_tags JSONB,
    acquirer_response TEXT,
    created_at TIMESTAMP DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_txn_merchant ON transactions(merchant_id);
CREATE INDEX IF NOT EXISTS idx_txn_timestamp ON transactions(txn_ts);
CREATE INDEX IF NOT EXISTS idx_txn_pan_hash ON transactions(pan_hash);

-- Transaction Features Table
CREATE TABLE IF NOT EXISTS transaction_features (
    txn_id BIGINT PRIMARY KEY REFERENCES transactions(txn_id),
    feature_json JSONB,
    score FLOAT,
    action_taken TEXT,
    label SMALLINT,
    scored_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_features_label ON transaction_features(label);
CREATE INDEX IF NOT EXISTS idx_features_scored_at ON transaction_features(scored_at);

-- Alerts Table
CREATE TABLE IF NOT EXISTS alerts (
    alert_id BIGSERIAL PRIMARY KEY,
    txn_id BIGINT REFERENCES transactions(txn_id),
    score FLOAT,
    action TEXT,
    reason TEXT,
    created_at TIMESTAMP DEFAULT now(),
    status TEXT DEFAULT 'open',
    investigator TEXT,
    notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_alert_status ON alerts(status);
CREATE INDEX IF NOT EXISTS idx_alert_created ON alerts(created_at);
CREATE INDEX IF NOT EXISTS idx_alert_txn ON alerts(txn_id);

-- Model Metrics Table
CREATE TABLE IF NOT EXISTS model_metrics (
    id SERIAL PRIMARY KEY,
    date DATE,
    auc FLOAT,
    precision_at_100 FLOAT,
    avg_latency_ms FLOAT,
    drift_score FLOAT,
    created_at TIMESTAMP DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_metrics_date ON model_metrics(date);

-- Clients Table
CREATE TABLE IF NOT EXISTS clients (
    client_id SERIAL PRIMARY KEY,
    client_name TEXT NOT NULL,
    api_key TEXT UNIQUE NOT NULL,
    contact_email TEXT NOT NULL,
    contact_phone TEXT,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now(),
    last_accessed_at TIMESTAMP,
    description TEXT
);

CREATE INDEX IF NOT EXISTS idx_client_api_key ON clients(api_key);
CREATE INDEX IF NOT EXISTS idx_client_status ON clients(status);

-- Insert default configuration values
INSERT INTO model_config (config_key, value, description, updated_by) VALUES
    ('fraud.threshold.block', '0.95', 'Fraud score threshold for blocking transactions', 'system'),
    ('fraud.threshold.hold', '0.7', 'Fraud score threshold for holding transactions for review', 'system'),
    ('fraud.rule.blacklist.enabled', 'true', 'Enable hard rule blacklist checking', 'system'),
    ('aml.high_value_amount_cents', '1000000', 'AML high value transaction threshold in cents', 'system'),
    ('fraud.action.block', 'BLOCK', 'Action code for blocking transactions', 'system'),
    ('fraud.notify.slack', 'false', 'Enable Slack notifications for fraud alerts', 'system')
ON CONFLICT (config_key) DO NOTHING;


