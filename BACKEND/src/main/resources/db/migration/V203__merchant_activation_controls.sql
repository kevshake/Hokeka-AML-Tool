-- Progressive-activation controls applied to a merchant after underwriting.
-- Records the graduated state, controls in force, the reduced daily limit actually
-- applied during the heightened-monitoring window, and when that window ends.

CREATE TABLE IF NOT EXISTS merchant_activation_controls (
    id                   BIGSERIAL   PRIMARY KEY,
    merchant_id          BIGINT      NOT NULL,
    state                VARCHAR(24) NOT NULL,
    controls             TEXT,
    original_daily_limit NUMERIC(19, 2),
    reduced_daily_limit  NUMERIC(19, 2),
    monitoring_until     TIMESTAMP,
    active               BOOLEAN     DEFAULT TRUE,
    created_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mac_merchant ON merchant_activation_controls (merchant_id);
