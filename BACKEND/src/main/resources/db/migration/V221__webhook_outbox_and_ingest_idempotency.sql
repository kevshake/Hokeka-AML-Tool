-- V221: Durable webhook delivery via event_outbox channel discriminator + transaction ingest idempotency.

ALTER TABLE event_outbox ADD COLUMN IF NOT EXISTS channel VARCHAR(20) NOT NULL DEFAULT 'KAFKA';
ALTER TABLE event_outbox ADD COLUMN IF NOT EXISTS destination VARCHAR(1000);
ALTER TABLE event_outbox ADD COLUMN IF NOT EXISTS subscription_id BIGINT;

ALTER TABLE event_outbox DROP CONSTRAINT IF EXISTS chk_event_outbox_status;
ALTER TABLE event_outbox ADD CONSTRAINT chk_event_outbox_status
    CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'));

DROP INDEX IF EXISTS idx_event_outbox_ready;
CREATE INDEX idx_event_outbox_ready
    ON event_outbox (channel, status, next_attempt_at, created_at);

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS client_reference VARCHAR(200);

CREATE UNIQUE INDEX IF NOT EXISTS idx_txn_psp_client_ref
    ON transactions (psp_id, client_reference)
    WHERE client_reference IS NOT NULL;
