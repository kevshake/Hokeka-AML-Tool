CREATE TABLE IF NOT EXISTS fix_message_events (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL REFERENCES psps(psp_id),
    session_id VARCHAR(255) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    message_type VARCHAR(16) NOT NULL,
    message_sequence_number INTEGER NOT NULL,
    sending_time TIMESTAMP,
    business_reference VARCHAR(160),
    message_hash VARCHAR(64) NOT NULL,
    sanitized_fields JSONB NOT NULL DEFAULT '{}'::jsonb,
    outcome VARCHAR(24) NOT NULL,
    error_code VARCHAR(80),
    error_message TEXT,
    market_order_id BIGINT REFERENCES market_orders(id),
    market_execution_id BIGINT REFERENCES market_executions(id),
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    CONSTRAINT uq_fix_message_session_sequence_direction
        UNIQUE (session_id, message_sequence_number, direction),
    CONSTRAINT ck_fix_message_direction CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT ck_fix_message_outcome CHECK (outcome IN ('RECEIVED', 'ACCEPTED', 'REJECTED', 'IGNORED'))
);

CREATE INDEX IF NOT EXISTS idx_fix_message_psp_received
    ON fix_message_events(psp_id, received_at DESC);
CREATE INDEX IF NOT EXISTS idx_fix_message_session_sequence
    ON fix_message_events(session_id, message_sequence_number);
CREATE INDEX IF NOT EXISTS idx_fix_message_business_reference
    ON fix_message_events(psp_id, business_reference);
CREATE INDEX IF NOT EXISTS idx_fix_message_market_order
    ON fix_message_events(market_order_id) WHERE market_order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_fix_message_market_execution
    ON fix_message_events(market_execution_id) WHERE market_execution_id IS NOT NULL;

INSERT INTO reports (
    report_code, report_name, report_category, description, report_type,
    base_entity, requires_approval, enabled
)
VALUES (
    'MKT_003', 'FIX Market Feed Evidence', 'MARKET_SURVEILLANCE',
    'Sanitized and hash-verifiable FIX message receipts with session, sequence, processing outcome, and linked market records.',
    'DYNAMIC', 'fix_message_events', FALSE, TRUE
)
ON CONFLICT (report_code) DO NOTHING;

INSERT INTO report_definitions (
    report_id, version, sql_query, count_query, parameters, filters, columns,
    order_by_default, is_active, created_by, created_at
)
SELECT r.id, 1,
    'SELECT f.id AS fix_message_event_id, f.session_id, f.direction, f.message_type,
            f.message_sequence_number, f.sending_time, f.business_reference,
            f.message_hash, f.sanitized_fields, f.outcome, f.error_code, f.error_message,
            f.market_order_id, f.market_execution_id, f.received_at, f.processed_at
       FROM fix_message_events f
      WHERE f.psp_id = :pspId
        AND f.received_at BETWEEN :dateFrom AND :dateTo
      ORDER BY f.received_at DESC',
    'SELECT COUNT(*) FROM fix_message_events f
      WHERE f.psp_id = :pspId AND f.received_at BETWEEN :dateFrom AND :dateTo',
    '[{"name":"pspId","type":"LONG","required":true},{"name":"dateFrom","type":"DATETIME","required":true},{"name":"dateTo","type":"DATETIME","required":true}]'::jsonb,
    '[{"field":"session_id","type":"STRING"},{"field":"message_type","type":"STRING"},{"field":"outcome","type":"ENUM","options":["RECEIVED","ACCEPTED","REJECTED","IGNORED"]}]'::jsonb,
    '[{"name":"fix_message_event_id","type":"LONG","label":"Receipt ID"},{"name":"session_id","type":"STRING","label":"Session"},{"name":"message_type","type":"STRING","label":"Message Type"},{"name":"message_sequence_number","type":"INTEGER","label":"Sequence"},{"name":"business_reference","type":"STRING","label":"Business Reference"},{"name":"outcome","type":"STRING","label":"Outcome"},{"name":"market_order_id","type":"LONG","label":"Order ID"},{"name":"market_execution_id","type":"LONG","label":"Execution ID"},{"name":"received_at","type":"DATETIME","label":"Received"},{"name":"processed_at","type":"DATETIME","label":"Processed"},{"name":"message_hash","type":"STRING","label":"Message Hash"}]'::jsonb,
    'received_at DESC', TRUE, NULL, CURRENT_TIMESTAMP
FROM reports r WHERE r.report_code = 'MKT_003'
ON CONFLICT (report_id, version) DO NOTHING;
