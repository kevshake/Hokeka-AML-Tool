-- Cross-PSP Fraud Intelligence Sharing
-- Tracks confirmed fraudulent merchants/entities across all PSPs so that
-- when one PSP flags a merchant, other PSPs are protected automatically.
CREATE TABLE IF NOT EXISTS cross_psp_fraud_flags (
    id              BIGSERIAL    PRIMARY KEY,

    -- The entity identifier (merchant_id, pan_hash, terminal_id, email, etc.)
    entity_value    TEXT         NOT NULL,
    entity_type     VARCHAR(32)  NOT NULL,  -- MERCHANT_ID, PAN_HASH, TERMINAL, EMAIL, NAME

    -- How many distinct PSPs have flagged this entity
    flag_count      INT          NOT NULL DEFAULT 1,

    -- Source PSP that first flagged it
    source_psp_id   BIGINT       REFERENCES psp(psp_id),
    source_alert_id BIGINT       REFERENCES alerts(alert_id),

    -- Risk metadata
    risk_level      VARCHAR(16)  NOT NULL DEFAULT 'HIGH',
    reason          TEXT,

    -- Timestamps
    first_flagged   TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_flagged    TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),

    -- Unique per entity
    CONSTRAINT uq_cross_psp_entity UNIQUE (entity_value, entity_type)
);

-- Index for fast lookup during transaction screening
CREATE INDEX IF NOT EXISTS idx_cross_psp_entity_value ON cross_psp_fraud_flags (entity_value);
CREATE INDEX IF NOT EXISTS idx_cross_psp_entity_type  ON cross_psp_fraud_flags (entity_type);
CREATE INDEX IF NOT EXISTS idx_cross_psp_risk_level   ON cross_psp_fraud_flags (risk_level);