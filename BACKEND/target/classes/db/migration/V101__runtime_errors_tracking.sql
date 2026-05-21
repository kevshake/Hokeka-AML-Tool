CREATE TABLE IF NOT EXISTS runtime_errors (
    id BIGSERIAL PRIMARY KEY,
    error_code VARCHAR(50),
    message TEXT,
    stack_trace TEXT,
    user_id BIGINT,
    psp_id BIGINT REFERENCES psps(psp_id),
    occurred_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_runtime_errors_psp ON runtime_errors(psp_id);
CREATE INDEX IF NOT EXISTS idx_runtime_errors_occurred ON runtime_errors(occurred_at DESC);
