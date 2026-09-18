CREATE TABLE market_orders (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL REFERENCES psps(psp_id),
    external_order_id VARCHAR(128) NOT NULL,
    customer_id BIGINT NOT NULL REFERENCES multi_asset_customers(id),
    account_reference VARCHAR(128) NOT NULL,
    beneficial_owner_reference VARCHAR(128),
    instrument_id VARCHAR(128) NOT NULL,
    symbol VARCHAR(64),
    side VARCHAR(8) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    order_type VARCHAR(24) NOT NULL,
    quantity NUMERIC(28, 10) NOT NULL CHECK (quantity > 0),
    executed_quantity NUMERIC(28, 10) NOT NULL DEFAULT 0 CHECK (executed_quantity >= 0),
    limit_price NUMERIC(28, 10),
    currency VARCHAR(3) NOT NULL,
    venue VARCHAR(64),
    status VARCHAR(24) NOT NULL,
    placed_at TIMESTAMP NOT NULL,
    cancelled_at TIMESTAMP,
    market_close_at TIMESTAMP,
    reference_price NUMERIC(28, 10),
    device_fingerprint VARCHAR(255),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_market_order_external UNIQUE (psp_id, external_order_id),
    CHECK (executed_quantity <= quantity)
);

CREATE INDEX idx_market_order_customer_time ON market_orders(psp_id, customer_id, placed_at DESC);
CREATE INDEX idx_market_order_instrument_time ON market_orders(psp_id, instrument_id, placed_at DESC);
CREATE INDEX idx_market_order_open_book ON market_orders(psp_id, instrument_id, side, status, placed_at DESC);

CREATE TABLE market_executions (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL REFERENCES psps(psp_id),
    external_execution_id VARCHAR(128) NOT NULL,
    order_id BIGINT NOT NULL REFERENCES market_orders(id),
    customer_id BIGINT NOT NULL REFERENCES multi_asset_customers(id),
    instrument_id VARCHAR(128) NOT NULL,
    side VARCHAR(8) NOT NULL,
    quantity NUMERIC(28, 10) NOT NULL CHECK (quantity > 0),
    price NUMERIC(28, 10) NOT NULL CHECK (price > 0),
    currency VARCHAR(3) NOT NULL,
    executed_at TIMESTAMP NOT NULL,
    counterparty_customer_id BIGINT REFERENCES multi_asset_customers(id),
    counterparty_reference VARCHAR(255),
    counterparty_beneficial_owner_reference VARCHAR(128),
    venue VARCHAR(64),
    market_close_at TIMESTAMP,
    reference_price NUMERIC(28, 10),
    settlement_account_reference VARCHAR(255),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_market_execution_external UNIQUE (psp_id, external_execution_id)
);

CREATE INDEX idx_market_execution_customer_time ON market_executions(psp_id, customer_id, executed_at DESC);
CREATE INDEX idx_market_execution_counterparty ON market_executions(psp_id, customer_id, instrument_id, counterparty_reference, executed_at DESC);

CREATE TABLE market_surveillance_signals (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL REFERENCES psps(psp_id),
    customer_id BIGINT NOT NULL REFERENCES multi_asset_customers(id),
    order_id BIGINT REFERENCES market_orders(id),
    execution_id BIGINT REFERENCES market_executions(id),
    scenario_code VARCHAR(64) NOT NULL,
    signal_type VARCHAR(32) NOT NULL DEFAULT 'MARKET_ABUSE',
    severity VARCHAR(16) NOT NULL,
    score INTEGER NOT NULL CHECK (score BETWEEN 0 AND 100),
    description TEXT NOT NULL,
    evidence JSONB NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    reviewed_by BIGINT REFERENCES platform_users(id),
    review_notes TEXT
);

CREATE INDEX idx_market_signal_queue ON market_surveillance_signals(psp_id, status, created_at DESC);
CREATE INDEX idx_market_signal_customer ON market_surveillance_signals(psp_id, customer_id, created_at DESC);

COMMENT ON TABLE market_surveillance_signals IS
    'Market-integrity signals kept distinct from AML signals and correlated through customer, alerts, and cases';
