CREATE TABLE event_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_key VARCHAR(200) NOT NULL UNIQUE,
    topic VARCHAR(200) NOT NULL,
    partition_key VARCHAR(200),
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_event_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED'))
);

CREATE INDEX idx_event_outbox_ready
    ON event_outbox (status, next_attempt_at, created_at);

CREATE TABLE reporting_event_receipts (
    consumer_name VARCHAR(100) NOT NULL,
    event_key VARCHAR(200) NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (consumer_name, event_key)
);
