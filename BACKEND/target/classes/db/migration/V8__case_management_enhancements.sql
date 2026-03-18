-- Case Management Enhancements Migration
-- Adds tables for case activities, queues, escalation rules, and other missing features

-- Case Activities Table
CREATE TABLE IF NOT EXISTS case_activities (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    description TEXT,
    details TEXT, -- JSON for structured data
    performed_by BIGINT NOT NULL,
    performed_at TIMESTAMP NOT NULL,
    related_entity_id BIGINT,
    related_entity_type VARCHAR(50),
    CONSTRAINT fk_case_activities_case FOREIGN KEY (case_id) REFERENCES compliance_cases(case_id) ON DELETE CASCADE,
    CONSTRAINT fk_case_activities_user FOREIGN KEY (performed_by) REFERENCES psp_users(user_id)
);

CREATE INDEX IF NOT EXISTS idx_case_activities_case ON case_activities(case_id, performed_at DESC);
CREATE INDEX IF NOT EXISTS idx_case_activities_type ON case_activities(activity_type);
CREATE INDEX IF NOT EXISTS idx_case_activities_user ON case_activities(performed_by);

-- Case Queues Table
CREATE TABLE IF NOT EXISTS case_queues (
    id BIGSERIAL PRIMARY KEY,
    queue_name VARCHAR(100) NOT NULL UNIQUE,
    target_role VARCHAR(50),
    min_priority VARCHAR(20),
    max_queue_size INT,
    auto_assign BOOLEAN DEFAULT FALSE,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add queue reference to compliance_cases
ALTER TABLE compliance_cases ADD COLUMN IF NOT EXISTS queue_id BIGINT;
ALTER TABLE compliance_cases DROP CONSTRAINT IF EXISTS fk_cases_queue;
ALTER TABLE compliance_cases ADD CONSTRAINT fk_cases_queue FOREIGN KEY (queue_id) REFERENCES case_queues(id);

-- Case Mentions Table
CREATE TABLE IF NOT EXISTS case_mentions (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL,
    mentioned_user_id BIGINT NOT NULL,
    mentioned_by_user_id BIGINT,
    mentioned_at TIMESTAMP NOT NULL,
    read BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_case_mentions_case FOREIGN KEY (case_id) REFERENCES compliance_cases(case_id) ON DELETE CASCADE,
    CONSTRAINT fk_case_mentions_mentioned FOREIGN KEY (mentioned_user_id) REFERENCES psp_users(user_id),
    CONSTRAINT fk_case_mentions_by FOREIGN KEY (mentioned_by_user_id) REFERENCES psp_users(user_id)
);

CREATE INDEX IF NOT EXISTS idx_case_mentions_user ON case_mentions(mentioned_user_id, read);
CREATE INDEX IF NOT EXISTS idx_case_mentions_case ON case_mentions(case_id);

-- Escalation Rules Table
CREATE TABLE IF NOT EXISTS escalation_rules (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    min_priority VARCHAR(20),
    min_risk_score DOUBLE PRECISION,
    min_amount DECIMAL(19,2),
    days_open INT,
    escalate_to_role VARCHAR(50),
    escalate_to_user_id BIGINT,
    reason_template TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_escalation_rules_user FOREIGN KEY (escalate_to_user_id) REFERENCES psp_users(user_id)
);

-- Case Transactions (for timeline)
CREATE TABLE IF NOT EXISTS case_transactions (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL,
    transaction_id BIGINT NOT NULL,
    relationship_type VARCHAR(50), -- PRIMARY, RELATED, SUSPICIOUS_PATTERN
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    added_by BIGINT,
    CONSTRAINT fk_case_transactions_case FOREIGN KEY (case_id) REFERENCES compliance_cases(case_id) ON DELETE CASCADE,
    CONSTRAINT fk_case_transactions_txn FOREIGN KEY (transaction_id) REFERENCES transactions(txn_id),
    CONSTRAINT fk_case_transactions_user FOREIGN KEY (added_by) REFERENCES psp_users(user_id)
);

CREATE INDEX IF NOT EXISTS idx_case_transactions_case ON case_transactions(case_id);
CREATE INDEX IF NOT EXISTS idx_case_transactions_txn ON case_transactions(transaction_id);

-- Update case_notes to support threading
ALTER TABLE case_notes ADD COLUMN IF NOT EXISTS parent_note_id BIGINT;
ALTER TABLE case_notes DROP CONSTRAINT IF EXISTS fk_case_notes_parent;
ALTER TABLE case_notes ADD CONSTRAINT fk_case_notes_parent FOREIGN KEY (parent_note_id) REFERENCES case_notes(id);
ALTER TABLE case_notes ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- Update case_notes to support mentions
CREATE TABLE IF NOT EXISTS case_note_mentions (
    note_id BIGINT NOT NULL,
    mentioned_user_id BIGINT NOT NULL,
    PRIMARY KEY (note_id, mentioned_user_id),
    CONSTRAINT fk_note_mentions_note FOREIGN KEY (note_id) REFERENCES case_notes(id) ON DELETE CASCADE,
    CONSTRAINT fk_note_mentions_user FOREIGN KEY (mentioned_user_id) REFERENCES psp_users(user_id)
);

-- Update case_evidence to add more fields
ALTER TABLE case_evidence ADD COLUMN IF NOT EXISTS evidence_type VARCHAR(50); -- DOCUMENT, SCREENSHOT, EMAIL, etc.
ALTER TABLE case_evidence ADD COLUMN IF NOT EXISTS file_size BIGINT;
ALTER TABLE case_evidence ADD COLUMN IF NOT EXISTS checksum VARCHAR(255); -- For integrity verification

-- Add methods to ComplianceCaseRepository (will be added via JPA)
-- These will be handled by Spring Data JPA automatically

-- Add methods to UserRepository for case assignment
-- These will be handled by Spring Data JPA automatically

COMMENT ON TABLE case_activities IS 'Activity feed for compliance cases';
COMMENT ON TABLE case_queues IS 'Case queues for automatic assignment';
COMMENT ON TABLE case_mentions IS 'User mentions in case notes and activities';
COMMENT ON TABLE escalation_rules IS 'Rules for automatic case escalation';
COMMENT ON TABLE case_transactions IS 'Transactions linked to cases for timeline view';


