-- Console on-demand list exports (async jobs) and list/filter performance indexes.

CREATE TABLE IF NOT EXISTS console_list_export_jobs (
    id              BIGSERIAL PRIMARY KEY,
    job_uuid        VARCHAR(64) NOT NULL UNIQUE,
    view_type       VARCHAR(64) NOT NULL,
    export_format   VARCHAR(16) NOT NULL,
    status          VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    psp_id          BIGINT,
    user_id         BIGINT,
    username        VARCHAR(255),
    filters_json    JSONB,
    include_correlated BOOLEAN NOT NULL DEFAULT FALSE,
    row_count       BIGINT,
    file_path       TEXT,
    file_name       VARCHAR(512),
    error_message   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_console_list_export_jobs_user_created
    ON console_list_export_jobs (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_console_list_export_jobs_status_created
    ON console_list_export_jobs (status, created_at DESC);

-- SAR list filters (PSP + status + created_at sort)
CREATE INDEX IF NOT EXISTS idx_sar_psp_status_created
    ON suspicious_activity_reports (psp_id, status, created_at DESC);

-- Audit log range queries with PSP filter (entity maps to audit_logs_enhanced; see V4/V106)
CREATE INDEX IF NOT EXISTS idx_audit_logs_enhanced_psp_timestamp
    ON audit_logs_enhanced (psp_id, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_enhanced_action_timestamp
    ON audit_logs_enhanced (action_type, timestamp DESC);

-- Transaction monitoring list (PSP + time ordering)
CREATE INDEX IF NOT EXISTS idx_transactions_psp_txn_ts
    ON transactions (psp_id, txn_ts DESC);

-- Screening activity ledger
CREATE INDEX IF NOT EXISTS idx_screening_activities_psp_created
    ON screening_activities (psp_id, created_at DESC);

-- Chargeback disputes list
CREATE INDEX IF NOT EXISTS idx_chargeback_disputes_psp_created
    ON chargeback_disputes (psp_id, created_at DESC);

ANALYZE suspicious_activity_reports;
ANALYZE audit_logs_enhanced;
ANALYZE transactions;
ANALYZE screening_activities;
ANALYZE chargeback_disputes;
