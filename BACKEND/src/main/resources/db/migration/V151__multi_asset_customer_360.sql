CREATE TABLE multi_asset_customers (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL,
    external_customer_id VARCHAR(128) NOT NULL,
    customer_type VARCHAR(24) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    country_code VARCHAR(3),
    risk_tier VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    kyc_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    last_reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_multi_asset_customer UNIQUE (psp_id, external_customer_id)
);

CREATE TABLE asset_accounts (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES multi_asset_customers(id) ON DELETE CASCADE,
    psp_id BIGINT NOT NULL,
    external_account_id VARCHAR(160) NOT NULL,
    asset_class VARCHAR(24) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    provider_name VARCHAR(128),
    currency VARCHAR(12),
    country_code VARCHAR(3),
    public_address VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_asset_account UNIQUE (psp_id, external_account_id)
);

CREATE TABLE multi_asset_transactions (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL,
    external_transaction_id VARCHAR(160) NOT NULL,
    customer_id BIGINT NOT NULL REFERENCES multi_asset_customers(id),
    source_account_id BIGINT REFERENCES asset_accounts(id),
    destination_account_id BIGINT REFERENCES asset_accounts(id),
    asset_class VARCHAR(24) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    amount NUMERIC(24, 8) NOT NULL,
    fiat_equivalent_usd NUMERIC(24, 8),
    currency VARCHAR(12) NOT NULL,
    asset_symbol VARCHAR(32),
    quantity NUMERIC(30, 12),
    unit_price NUMERIC(24, 8),
    executed_at TIMESTAMP NOT NULL,
    country_code VARCHAR(3),
    device_fingerprint VARCHAR(255),
    ip_address VARCHAR(64),
    counterparty_reference VARCHAR(255),
    funding_party_customer_id VARCHAR(128),
    source_network VARCHAR(64),
    destination_network VARCHAR(64),
    originator_name VARCHAR(255),
    originator_account VARCHAR(255),
    beneficiary_name VARCHAR(255),
    beneficiary_account VARCHAR(255),
    merchant_category VARCHAR(128),
    travel_rule_required BOOLEAN NOT NULL DEFAULT FALSE,
    travel_rule_status VARCHAR(24) NOT NULL DEFAULT 'NOT_REQUIRED',
    risk_score INTEGER NOT NULL DEFAULT 0,
    decision VARCHAR(20) NOT NULL DEFAULT 'ALLOW',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_multi_asset_transaction UNIQUE (psp_id, external_transaction_id)
);

CREATE TABLE multi_asset_risk_signals (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL REFERENCES multi_asset_transactions(id) ON DELETE CASCADE,
    customer_id BIGINT NOT NULL REFERENCES multi_asset_customers(id) ON DELETE CASCADE,
    psp_id BIGINT NOT NULL,
    signal_code VARCHAR(80) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    score_impact INTEGER NOT NULL,
    description TEXT NOT NULL,
    evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_multi_asset_customer_psp_name ON multi_asset_customers(psp_id, display_name);
CREATE INDEX idx_asset_account_customer ON asset_accounts(customer_id, asset_class);
CREATE INDEX idx_multi_asset_tx_customer_time ON multi_asset_transactions(customer_id, executed_at DESC);
CREATE INDEX idx_multi_asset_tx_psp_asset_time ON multi_asset_transactions(psp_id, asset_class, executed_at DESC);
CREATE INDEX idx_multi_asset_tx_counterparty ON multi_asset_transactions(psp_id, counterparty_reference);
CREATE INDEX idx_multi_asset_signal_customer_time ON multi_asset_risk_signals(customer_id, created_at DESC);
CREATE INDEX idx_multi_asset_signal_psp_severity ON multi_asset_risk_signals(psp_id, severity, created_at DESC);
