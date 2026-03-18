CREATE TABLE IF NOT EXISTS case_alerts (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL,
    alert_type VARCHAR(100),
    description TEXT,
    severity VARCHAR(20),
    score DECIMAL(10, 2),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_case_alerts_case FOREIGN KEY (case_id) REFERENCES compliance_cases(case_id)
);

CREATE INDEX IF NOT EXISTS idx_case_alerts_case_id ON case_alerts(case_id);
