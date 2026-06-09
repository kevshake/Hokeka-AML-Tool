-- V144: Chargeback dispute persistence and Verifi RDR webhook idempotency log

CREATE TABLE IF NOT EXISTS chargeback_disputes (
    id                      BIGSERIAL PRIMARY KEY,
    external_event_id       VARCHAR(128),
    deduplication_id        VARCHAR(128),
    notification_type       VARCHAR(64)  NOT NULL,
    rdr_status              VARCHAR(32),
    scheme                  VARCHAR(16)  DEFAULT 'visa',
    case_id                 VARCHAR(128),
    case_date               DATE,
    case_amount             NUMERIC(19, 4),
    case_currency           VARCHAR(3),
    reason_code             VARCHAR(16),
    reason_category         VARCHAR(32),
    acquirer_reference_number VARCHAR(64),
    network_merchant_id     VARCHAR(64),
    network_transaction_id  VARCHAR(128),
    merchant_order_id       VARCHAR(128),
    psp_transaction_id      VARCHAR(128),
    card_bin                VARCHAR(8),
    card_last4              VARCHAR(4),
    refunded                BOOLEAN      DEFAULT FALSE,
    merchant_id             BIGINT,
    psp_id                  BIGINT,
    alert_id                BIGINT,
    compliance_case_id      BIGINT,
    raw_payload             TEXT,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_chargeback_disputes_dedup
    ON chargeback_disputes(deduplication_id)
    WHERE deduplication_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_chargeback_disputes_merchant
    ON chargeback_disputes(merchant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_chargeback_disputes_psp
    ON chargeback_disputes(psp_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_chargeback_disputes_status
    ON chargeback_disputes(rdr_status, notification_type);

COMMENT ON TABLE chargeback_disputes IS 'Visa/Verifi RDR and chargeback webhook events mapped to merchants, alerts, and cases';
