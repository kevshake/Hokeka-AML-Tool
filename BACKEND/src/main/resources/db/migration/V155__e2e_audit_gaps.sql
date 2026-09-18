-- V147: E2E audit gaps — Verifi decisions, rule auto-case config, payment blacklist, report favorites by code

-- Renumbered to resolve a merged Flyway version collision.
CREATE TABLE IF NOT EXISTS verifi_decisions (
    id                  BIGSERIAL PRIMARY KEY,
    case_id             VARCHAR(128),
    psp_transaction_id  VARCHAR(128),
    merchant_id         BIGINT,
    decision            VARCHAR(16)  NOT NULL,
    reason              TEXT,
    request_payload     TEXT,
    response_payload    TEXT,
    latency_ms          INTEGER,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_verifi_decisions_case ON verifi_decisions(case_id);
CREATE INDEX IF NOT EXISTS idx_verifi_decisions_txn ON verifi_decisions(psp_transaction_id);
CREATE INDEX IF NOT EXISTS idx_verifi_decisions_created ON verifi_decisions(created_at DESC);

COMMENT ON TABLE verifi_decisions IS 'Synchronous Verifi RDR decision API audit log';

CREATE TABLE IF NOT EXISTS payment_blacklist_entries (
    id           BIGSERIAL PRIMARY KEY,
    entry_type   VARCHAR(32)  NOT NULL,
    entry_value  VARCHAR(255) NOT NULL,
    reason       TEXT,
    active       BOOLEAN      DEFAULT TRUE,
    created_by   BIGINT,
    created_at   TIMESTAMP    DEFAULT NOW(),
    UNIQUE(entry_type, entry_value)
);

CREATE INDEX IF NOT EXISTS idx_payment_blacklist_type_value
    ON payment_blacklist_entries(entry_type, entry_value) WHERE active = TRUE;

COMMENT ON TABLE payment_blacklist_entries IS 'Persistent PAN/terminal/IP blacklist entries synced to Redis cache';

INSERT INTO model_config (config_key, value, description, updated_by)
VALUES ('rules.auto-create-cases', 'true', 'Auto-create compliance cases when rules trigger BLOCK/HOLD/ALERT', 'system')
ON CONFLICT (config_key) DO NOTHING;

ALTER TABLE report_favorites ADD COLUMN IF NOT EXISTS report_code VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_report_fav_code ON report_favorites(report_code);

ALTER TABLE report_favorites ALTER COLUMN report_id DROP NOT NULL;
