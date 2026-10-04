-- Shadow ledger. Existing transaction.decision stays the live decision.
CREATE TABLE assessments (
    id UUID PRIMARY KEY,
    psp_id BIGINT,
    txn_id BIGINT,
    trigger VARCHAR(32) NOT NULL,
    trigger_ref VARCHAR(128),
    parent_assessment_id UUID,
    edge_assessment_id VARCHAR(128),
    versions JSONB,
    context_hash VARCHAR(128),
    decision VARCHAR(32),
    latency_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_assessments_txn ON assessments (txn_id);
CREATE INDEX idx_assessments_psp ON assessments (psp_id, created_at);

CREATE TABLE findings (
    id UUID PRIMARY KEY,
    assessment_id UUID NOT NULL REFERENCES assessments(id),
    txn_id BIGINT,
    party_id VARCHAR(128),
    counterparty_party_id VARCHAR(128),
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(128),
    source_version VARCHAR(64),
    phase VARCHAR(16) NOT NULL,
    shadow BOOLEAN NOT NULL DEFAULT TRUE,
    nature VARCHAR(32),
    severity VARCHAR(16),
    triggered BOOLEAN NOT NULL,
    score DOUBLE PRECISION,
    proposed_action VARCHAR(16),
    evidence JSONB,
    explanation TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_findings_assessment ON findings (assessment_id);
CREATE INDEX idx_findings_txn ON findings (txn_id);
CREATE INDEX idx_findings_source ON findings (source_type, source_id);

ALTER TABLE rule_execution_logs ADD COLUMN IF NOT EXISTS rule_version_id BIGINT;
ALTER TABLE rule_execution_logs ADD COLUMN IF NOT EXISTS assessment_id UUID;
