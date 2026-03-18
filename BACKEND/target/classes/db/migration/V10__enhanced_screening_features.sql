-- V10__enhanced_screening_features.sql
-- Adds tables for enhanced screening features

-- Fix: Add missing columns to alerts table
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS disposed_at TIMESTAMP;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS disposition VARCHAR(50);

-- Screening Whitelist Table
CREATE TABLE IF NOT EXISTS screening_whitelist (
    id BIGSERIAL PRIMARY KEY,
    entity_id VARCHAR(255) NOT NULL,
    entity_type VARCHAR(50) NOT NULL, -- MERCHANT, COUNTERPARTY, etc.
    reason TEXT,
    created_by BIGINT,
    expires_at TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_whitelist_entity UNIQUE (entity_id, entity_type)
);

CREATE INDEX IF NOT EXISTS idx_whitelist_entity ON screening_whitelist(entity_id, entity_type);
CREATE INDEX IF NOT EXISTS idx_whitelist_active ON screening_whitelist(active);

-- Screening Override Table
CREATE TABLE IF NOT EXISTS screening_overrides (
    id BIGSERIAL PRIMARY KEY,
    entity_id VARCHAR(255) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    override_reason TEXT NOT NULL,
    justification TEXT NOT NULL,
    created_by BIGINT NOT NULL,
    approved_by BIGINT,
    approved_at TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED, EXPIRED
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_overrides_entity ON screening_overrides(entity_id, entity_type);
CREATE INDEX IF NOT EXISTS idx_overrides_status ON screening_overrides(status);
CREATE INDEX IF NOT EXISTS idx_overrides_created_by ON screening_overrides(created_by);

-- Watchlist Update Tracking Table
CREATE TABLE IF NOT EXISTS watchlist_updates (
    id BIGSERIAL PRIMARY KEY,
    list_name VARCHAR(100) NOT NULL, -- OFAC, UN, EU, etc.
    list_type VARCHAR(50) NOT NULL, -- SANCTIONS, PEP, ADVERSE_MEDIA
    update_date DATE NOT NULL,
    record_count BIGINT,
    source_url VARCHAR(500),
    checksum VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, PROCESSING, COMPLETED, FAILED
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_watchlist_updates_list ON watchlist_updates(list_name, list_type);
CREATE INDEX IF NOT EXISTS idx_watchlist_updates_date ON watchlist_updates(update_date DESC);
CREATE INDEX IF NOT EXISTS idx_watchlist_updates_status ON watchlist_updates(status);

-- Custom Watchlist Table
CREATE TABLE IF NOT EXISTS custom_watchlists (
    id BIGSERIAL PRIMARY KEY,
    watchlist_name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    list_type VARCHAR(50) NOT NULL, -- INTERNAL, EXTERNAL
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, INACTIVE
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_custom_watchlists_name ON custom_watchlists(watchlist_name);
CREATE INDEX IF NOT EXISTS idx_custom_watchlists_status ON custom_watchlists(status);

-- Custom Watchlist Entries Table
CREATE TABLE IF NOT EXISTS custom_watchlist_entries (
    id BIGSERIAL PRIMARY KEY,
    watchlist_id BIGINT NOT NULL,
    entity_name VARCHAR(255) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    match_reason TEXT,
    risk_level VARCHAR(50), -- LOW, MEDIUM, HIGH, CRITICAL
    added_by BIGINT NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_custom_watchlist FOREIGN KEY (watchlist_id) REFERENCES custom_watchlists(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_custom_entries_watchlist ON custom_watchlist_entries(watchlist_id);
CREATE INDEX IF NOT EXISTS idx_custom_entries_name ON custom_watchlist_entries(entity_name);
CREATE INDEX IF NOT EXISTS idx_custom_entries_type ON custom_watchlist_entries(entity_type);

-- False Positive Feedback Table
CREATE TABLE IF NOT EXISTS false_positive_feedback (
    id BIGSERIAL PRIMARY KEY,
    alert_id BIGINT NOT NULL,
    rule_name VARCHAR(255),
    reason TEXT,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_feedback_alert FOREIGN KEY (alert_id) REFERENCES alerts(alert_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_feedback_alert ON false_positive_feedback(alert_id);
CREATE INDEX IF NOT EXISTS idx_feedback_rule ON false_positive_feedback(rule_name);

-- Alert Tuning Recommendations Table
CREATE TABLE IF NOT EXISTS alert_tuning_recommendations (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(255) NOT NULL,
    false_positive_rate FLOAT,
    recommendation TEXT,
    priority VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    applied_by BIGINT,
    applied_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tuning_rule ON alert_tuning_recommendations(rule_name);
CREATE INDEX IF NOT EXISTS idx_tuning_status ON alert_tuning_recommendations(status);

-- AML Policy Documents Table
CREATE TABLE IF NOT EXISTS aml_policies (
    id BIGSERIAL PRIMARY KEY,
    policy_name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    description TEXT,
    content TEXT NOT NULL,
    document_path VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL,
    approved_by BIGINT,
    approved_at TIMESTAMP,
    effective_date TIMESTAMP,
    review_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_policy_version UNIQUE (policy_name, version)
);

CREATE INDEX IF NOT EXISTS idx_policy_name ON aml_policies(policy_name);
CREATE INDEX IF NOT EXISTS idx_policy_version ON aml_policies(policy_name, version);
CREATE INDEX IF NOT EXISTS idx_policy_active ON aml_policies(is_active);

-- Policy Acknowledgment Table
CREATE TABLE IF NOT EXISTS policy_acknowledgments (
    id BIGSERIAL PRIMARY KEY,
    policy_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    acknowledged_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45),
    CONSTRAINT fk_ack_policy FOREIGN KEY (policy_id) REFERENCES aml_policies(id) ON DELETE CASCADE,
    CONSTRAINT uk_ack_policy_user UNIQUE (policy_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_ack_policy_user ON policy_acknowledgments(policy_id, user_id);
CREATE INDEX IF NOT EXISTS idx_ack_user ON policy_acknowledgments(user_id);



