CREATE TABLE mobile_money_transaction_contexts (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL,
    transaction_id BIGINT NOT NULL REFERENCES multi_asset_transactions(id) ON DELETE CASCADE,
    wallet_account_id BIGINT REFERENCES asset_accounts(id),
    wallet_reference VARCHAR(160) NOT NULL,
    counterparty_wallet_reference VARCHAR(160),
    event_type VARCHAR(32) NOT NULL,
    device_fingerprint VARCHAR(255),
    agent_device_fingerprint VARCHAR(255),
    sim_identifier_hash VARCHAR(255),
    agent_reference VARCHAR(160),
    merchant_reference VARCHAR(160),
    latitude NUMERIC(10, 7),
    longitude NUMERIC(10, 7),
    agent_latitude NUMERIC(10, 7),
    agent_longitude NUMERIC(10, 7),
    cell_id VARCHAR(128),
    sim_changed_at TIMESTAMP,
    device_registered_at TIMESTAMP,
    wallet_previous_activity_at TIMESTAMP,
    agent_float_before NUMERIC(24, 8),
    agent_float_after NUMERIC(24, 8),
    cross_border BOOLEAN NOT NULL DEFAULT FALSE,
    executed_at TIMESTAMP NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_mobile_money_transaction_context UNIQUE (psp_id, transaction_id),
    CONSTRAINT chk_mobile_money_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_mobile_money_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_mobile_money_agent_latitude CHECK (agent_latitude IS NULL OR agent_latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_mobile_money_agent_longitude CHECK (agent_longitude IS NULL OR agent_longitude BETWEEN -180 AND 180)
);

CREATE TABLE mobile_money_network_edges (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL,
    from_entity_type VARCHAR(32) NOT NULL,
    from_entity_reference VARCHAR(255) NOT NULL,
    to_entity_type VARCHAR(32) NOT NULL,
    to_entity_reference VARCHAR(255) NOT NULL,
    relationship_type VARCHAR(40) NOT NULL,
    occurrence_count BIGINT NOT NULL DEFAULT 1,
    cumulative_amount NUMERIC(24, 8) NOT NULL DEFAULT 0,
    currency VARCHAR(12),
    first_seen_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NOT NULL,
    latest_transaction_id BIGINT REFERENCES multi_asset_transactions(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_mobile_money_network_edge UNIQUE (
        psp_id, from_entity_type, from_entity_reference,
        to_entity_type, to_entity_reference, relationship_type
    )
);

CREATE TABLE mobile_money_risk_profiles (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_reference VARCHAR(255) NOT NULL,
    risk_score INTEGER NOT NULL DEFAULT 0,
    risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW',
    signal_count BIGINT NOT NULL DEFAULT 0,
    first_seen_at TIMESTAMP NOT NULL,
    last_seen_at TIMESTAMP NOT NULL,
    last_assessed_at TIMESTAMP NOT NULL,
    latest_transaction_id BIGINT REFERENCES multi_asset_transactions(id),
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_mobile_money_risk_profile UNIQUE (psp_id, entity_type, entity_reference),
    CONSTRAINT chk_mobile_money_risk_score CHECK (risk_score BETWEEN 0 AND 100)
);

CREATE INDEX idx_mobile_money_context_psp_time
    ON mobile_money_transaction_contexts(psp_id, executed_at DESC);
CREATE INDEX idx_mobile_money_context_wallet_time
    ON mobile_money_transaction_contexts(psp_id, wallet_reference, executed_at DESC);
CREATE INDEX idx_mobile_money_context_device
    ON mobile_money_transaction_contexts(psp_id, device_fingerprint, executed_at DESC)
    WHERE device_fingerprint IS NOT NULL;
CREATE INDEX idx_mobile_money_context_sim
    ON mobile_money_transaction_contexts(psp_id, sim_identifier_hash, executed_at DESC)
    WHERE sim_identifier_hash IS NOT NULL;
CREATE INDEX idx_mobile_money_context_agent
    ON mobile_money_transaction_contexts(psp_id, agent_reference, executed_at DESC)
    WHERE agent_reference IS NOT NULL;
CREATE INDEX idx_mobile_money_edge_from
    ON mobile_money_network_edges(psp_id, from_entity_type, from_entity_reference, last_seen_at DESC);
CREATE INDEX idx_mobile_money_edge_to
    ON mobile_money_network_edges(psp_id, to_entity_type, to_entity_reference, last_seen_at DESC);
CREATE INDEX idx_mobile_money_profile_risk
    ON mobile_money_risk_profiles(psp_id, risk_score DESC, last_assessed_at DESC);

COMMENT ON COLUMN mobile_money_transaction_contexts.sim_identifier_hash IS
    'One-way or keyed hash supplied by the PSP; raw IMSI, ICCID, or MSISDN values must not be stored here';
COMMENT ON TABLE mobile_money_network_edges IS
    'Durable investigation graph edges derived only from observed PSP transaction telemetry';
