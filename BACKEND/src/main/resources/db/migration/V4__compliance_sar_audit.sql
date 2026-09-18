-- Compliance Cases (Fixed for schema compatibility)
-- Note: compliance_cases table is created in a later migration with case_id as PK
-- This migration creates supporting tables for case management

-- Case Relationships
CREATE TABLE IF NOT EXISTS case_relationships (
    case_id BIGINT NOT NULL,
    related_case_id BIGINT NOT NULL,
    PRIMARY KEY (case_id, related_case_id)
);

-- Case Notes
CREATE TABLE IF NOT EXISTS case_notes (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    internal BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_case_notes_author FOREIGN KEY (author_id) REFERENCES psp_users(user_id)
);

-- Case Evidence
CREATE TABLE IF NOT EXISTS case_evidence (
    id BIGSERIAL PRIMARY KEY,
    case_id BIGINT NOT NULL,
    uploaded_by_id BIGINT NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    file_type VARCHAR(100) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    description TEXT,
    uploaded_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_case_evidence_uploader FOREIGN KEY (uploaded_by_id) REFERENCES psp_users(user_id)
);

-- Suspicious Activity Reports
CREATE TABLE IF NOT EXISTS suspicious_activity_reports (
    id BIGSERIAL PRIMARY KEY,
    sar_reference VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL,
    created_by_user_id BIGINT,
    reviewed_by_user_id BIGINT,
    approved_by_user_id BIGINT,
    approved_at TIMESTAMP,
    filing_reference_number VARCHAR(255),
    filed_at TIMESTAMP,
    filed_by_user_id BIGINT,
    filing_receipt TEXT,
    sar_type VARCHAR(50) NOT NULL,
    jurisdiction VARCHAR(50) NOT NULL,
    filing_deadline TIMESTAMP,
    suspicious_activity_type VARCHAR(255) NOT NULL,
    narrative TEXT NOT NULL,
    total_suspicious_amount NUMERIC(19,2),
    case_id BIGINT,
    amends_sar_id BIGINT,
    amendment_reason TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_sar_created_by FOREIGN KEY (created_by_user_id) REFERENCES psp_users(user_id),
    CONSTRAINT fk_sar_reviewed_by FOREIGN KEY (reviewed_by_user_id) REFERENCES psp_users(user_id),
    CONSTRAINT fk_sar_approved_by FOREIGN KEY (approved_by_user_id) REFERENCES psp_users(user_id),
    CONSTRAINT fk_sar_filed_by FOREIGN KEY (filed_by_user_id) REFERENCES psp_users(user_id),
    CONSTRAINT fk_sar_amends FOREIGN KEY (amends_sar_id) REFERENCES suspicious_activity_reports(id)
);

CREATE TABLE IF NOT EXISTS sar_transactions (
    sar_id BIGINT NOT NULL,
    txn_id BIGINT NOT NULL,
    PRIMARY KEY (sar_id, txn_id),
    CONSTRAINT fk_sar_txn_sar FOREIGN KEY (sar_id) REFERENCES suspicious_activity_reports(id),
    CONSTRAINT fk_sar_txn_txn FOREIGN KEY (txn_id) REFERENCES transactions(txn_id)
);

-- Audit Logs
CREATE TABLE IF NOT EXISTS audit_logs_enhanced (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    user_role VARCHAR(100),
    action_type VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    before_value TEXT,
    after_value TEXT,
    timestamp TIMESTAMP NOT NULL,
    ip_address VARCHAR(100),
    session_id VARCHAR(255),
    user_agent VARCHAR(500),
    reason TEXT,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message TEXT,
    checksum VARCHAR(255)
);
