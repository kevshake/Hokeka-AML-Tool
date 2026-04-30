-- ============================================================
-- AML Reporting System Schema
-- Migration: V108__reporting_system_schema.sql
-- Description: Core tables for comprehensive AML reporting
-- ============================================================

-- ============================================================
-- 1. CORE REPORT TABLES
-- ============================================================

-- Master Report Registry
CREATE TABLE IF NOT EXISTS reports (
    id BIGSERIAL PRIMARY KEY,
    report_code VARCHAR(50) NOT NULL UNIQUE,
    report_name VARCHAR(255) NOT NULL,
    report_category VARCHAR(50) NOT NULL,
    description TEXT,
    report_type VARCHAR(20) NOT NULL DEFAULT 'DYNAMIC',
    base_entity VARCHAR(100),
    requires_approval BOOLEAN DEFAULT FALSE,
    regulatory_template VARCHAR(50),
    retention_days INTEGER DEFAULT 2555,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reports_category ON reports(report_category);
CREATE INDEX IF NOT EXISTS idx_reports_type ON reports(report_type);
CREATE INDEX IF NOT EXISTS idx_reports_enabled ON reports(enabled);

COMMENT ON TABLE reports IS 'Master registry of all available reports';
COMMENT ON COLUMN reports.report_code IS 'Unique report identifier (e.g., SAR_001, CTR_001)';
COMMENT ON COLUMN reports.report_category IS 'Report category (AML_FRAUD, CURRENCY_THRESHOLD, etc.)';
COMMENT ON COLUMN reports.report_type IS 'STATIC, DYNAMIC, or REGULATORY';
COMMENT ON COLUMN reports.retention_days IS 'Default 2555 days (7 years) for compliance';

-- Report Definitions (Versioned Query Definitions)
CREATE TABLE IF NOT EXISTS report_definitions (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    version INTEGER NOT NULL DEFAULT 1,
    sql_query TEXT NOT NULL,
    count_query TEXT,
    parameters JSONB,
    filters JSONB,
    columns JSONB NOT NULL,
    aggregations JSONB,
    group_by_fields JSONB,
    order_by_default VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_by BIGINT REFERENCES platform_users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(report_id, version)
);

CREATE INDEX IF NOT EXISTS idx_report_defs_report ON report_definitions(report_id);
CREATE INDEX IF NOT EXISTS idx_report_defs_active ON report_definitions(is_active);

COMMENT ON TABLE report_definitions IS 'Versioned SQL definitions for each report';

-- ============================================================
-- 2. REPORT EXECUTION & STORAGE
-- ============================================================

-- Report Execution Tracking
CREATE TABLE IF NOT EXISTS report_executions (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES reports(id),
    execution_id VARCHAR(100) UNIQUE NOT NULL,
    psp_id BIGINT,
    triggered_by BIGINT REFERENCES platform_users(id),
    trigger_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    parameters JSONB,
    date_from TIMESTAMP,
    date_to TIMESTAMP,
    filters_applied JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    progress_percent INTEGER DEFAULT 0,
    total_records BIGINT,
    file_path VARCHAR(500),
    file_formats JSONB,
    file_sizes JSONB,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    execution_time_ms INTEGER,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_report_exec_report ON report_executions(report_id);
CREATE INDEX IF NOT EXISTS idx_report_exec_psp ON report_executions(psp_id);
CREATE INDEX IF NOT EXISTS idx_report_exec_status ON report_executions(status);
CREATE INDEX IF NOT EXISTS idx_report_exec_dates ON report_executions(date_from, date_to);
CREATE INDEX IF NOT EXISTS idx_report_exec_created ON report_executions(created_at);

COMMENT ON TABLE report_executions IS 'Tracks all report executions and their status';
COMMENT ON COLUMN report_executions.execution_id IS 'UUID for tracking async executions';
COMMENT ON COLUMN report_executions.trigger_type IS 'MANUAL, SCHEDULED, or API';

-- Report Results Cache (for pagination and re-export)
CREATE TABLE IF NOT EXISTS report_results (
    id BIGSERIAL PRIMARY KEY,
    execution_id BIGINT NOT NULL REFERENCES report_executions(id) ON DELETE CASCADE,
    row_number INTEGER NOT NULL,
    data JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_report_results_exec ON report_results(execution_id);
CREATE INDEX IF NOT EXISTS idx_report_results_row ON report_results(execution_id, row_number);

COMMENT ON TABLE report_results IS 'Cached report data for pagination and re-export';

-- ============================================================
-- 3. REPORT SCHEDULING
-- ============================================================

-- Report Schedules
CREATE TABLE IF NOT EXISTS report_schedules (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT NOT NULL REFERENCES reports(id),
    psp_id BIGINT,
    schedule_name VARCHAR(255) NOT NULL,
    frequency VARCHAR(20) NOT NULL,
    cron_expression VARCHAR(100),
    time_of_day TIME,
    day_of_week INTEGER,
    day_of_month INTEGER,
    timezone VARCHAR(50) DEFAULT 'UTC',
    default_parameters JSONB,
    default_filters JSONB,
    date_range_type VARCHAR(20),
    email_recipients JSONB,
    email_subject VARCHAR(255),
    email_body TEXT,
    export_formats JSONB DEFAULT '["PDF", "CSV"]',
    is_active BOOLEAN DEFAULT TRUE,
    last_run_at TIMESTAMP,
    next_run_at TIMESTAMP,
    run_count INTEGER DEFAULT 0,
    fail_count INTEGER DEFAULT 0,
    created_by BIGINT REFERENCES platform_users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_report_sched_report ON report_schedules(report_id);
CREATE INDEX IF NOT EXISTS idx_report_sched_psp ON report_schedules(psp_id);
CREATE INDEX IF NOT EXISTS idx_report_sched_active ON report_schedules(is_active);
CREATE INDEX IF NOT EXISTS idx_report_sched_next ON report_schedules(next_run_at);

COMMENT ON TABLE report_schedules IS 'Scheduled report configurations';
COMMENT ON COLUMN report_schedules.frequency IS 'DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY';
COMMENT ON COLUMN report_schedules.date_range_type IS 'PREVIOUS_DAY, PREVIOUS_WEEK, PREVIOUS_MONTH, CUSTOM';

-- Report Schedule Execution History
CREATE TABLE IF NOT EXISTS report_schedule_history (
    id BIGSERIAL PRIMARY KEY,
    schedule_id BIGINT NOT NULL REFERENCES report_schedules(id),
    execution_id BIGINT REFERENCES report_executions(id),
    scheduled_for TIMESTAMP NOT NULL,
    executed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_report_sched_hist_schedule ON report_schedule_history(schedule_id);
CREATE INDEX IF NOT EXISTS idx_report_sched_hist_status ON report_schedule_history(status);

COMMENT ON TABLE report_schedule_history IS 'Historical record of scheduled report executions';

-- ============================================================
-- 4. REGULATORY SUBMISSION TRACKING
-- ============================================================

-- Regulatory Submissions
CREATE TABLE IF NOT EXISTS regulatory_submissions (
    id BIGSERIAL PRIMARY KEY,
    submission_reference VARCHAR(100) UNIQUE NOT NULL,
    report_id BIGINT NOT NULL REFERENCES reports(id),
    execution_id BIGINT REFERENCES report_executions(id),
    regulator_code VARCHAR(50) NOT NULL,
    submission_type VARCHAR(50) NOT NULL,
    jurisdiction VARCHAR(50) NOT NULL,
    filing_period_start DATE NOT NULL,
    filing_period_end DATE NOT NULL,
    filing_deadline DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    prepared_by BIGINT REFERENCES platform_users(id),
    prepared_at TIMESTAMP,
    reviewed_by BIGINT REFERENCES platform_users(id),
    reviewed_at TIMESTAMP,
    approved_by BIGINT REFERENCES platform_users(id),
    approved_at TIMESTAMP,
    filed_by BIGINT REFERENCES platform_users(id),
    filed_at TIMESTAMP,
    regulator_reference VARCHAR(100),
    filing_receipt TEXT,
    rejection_reason TEXT,
    amended_submission_id BIGINT REFERENCES regulatory_submissions(id),
    amendment_reason TEXT,
    submitted_data JSONB,
    attachment_paths JSONB,
    psp_id BIGINT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reg_sub_psp ON regulatory_submissions(psp_id);
CREATE INDEX IF NOT EXISTS idx_reg_sub_status ON regulatory_submissions(status);
CREATE INDEX IF NOT EXISTS idx_reg_sub_regulator ON regulatory_submissions(regulator_code);
CREATE INDEX IF NOT EXISTS idx_reg_sub_type ON regulatory_submissions(submission_type);
CREATE INDEX IF NOT EXISTS idx_reg_sub_period ON regulatory_submissions(filing_period_start, filing_period_end);
CREATE INDEX IF NOT EXISTS idx_reg_sub_deadline ON regulatory_submissions(filing_deadline);

COMMENT ON TABLE regulatory_submissions IS 'Tracks regulatory filing submissions and their status';
COMMENT ON COLUMN regulatory_submissions.submission_reference IS 'Internal reference (e.g., SUB-2024-001)';
COMMENT ON COLUMN regulatory_submissions.status IS 'DRAFT, PENDING_REVIEW, APPROVED, FILED, REJECTED, AMENDED';

-- Regulatory Templates
CREATE TABLE IF NOT EXISTS regulatory_templates (
    id BIGSERIAL PRIMARY KEY,
    template_code VARCHAR(50) UNIQUE NOT NULL,
    template_name VARCHAR(255) NOT NULL,
    regulator_code VARCHAR(50) NOT NULL,
    jurisdiction VARCHAR(50) NOT NULL,
    submission_type VARCHAR(50) NOT NULL,
    schema_definition JSONB NOT NULL,
    validation_rules JSONB,
    required_fields JSONB,
    xml_template TEXT,
    csv_mapping JSONB,
    pdf_template_path VARCHAR(500),
    version INTEGER DEFAULT 1,
    effective_date DATE,
    expiry_date DATE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reg_templates_regulator ON regulatory_templates(regulator_code);
CREATE INDEX IF NOT EXISTS idx_reg_templates_active ON regulatory_templates(is_active);

COMMENT ON TABLE regulatory_templates IS 'Templates for generating regulatory filings';

-- ============================================================
-- 5. USER PREFERENCES
-- ============================================================

-- Report Favorites
CREATE TABLE IF NOT EXISTS report_favorites (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
    report_id BIGINT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    psp_id BIGINT,
    display_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, report_id, psp_id)
);

CREATE INDEX IF NOT EXISTS idx_report_fav_user ON report_favorites(user_id);

COMMENT ON TABLE report_favorites IS 'User favorite reports';

-- Saved Filters
CREATE TABLE IF NOT EXISTS report_saved_filters (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES platform_users(id) ON DELETE CASCADE,
    report_id BIGINT NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    psp_id BIGINT,
    filter_name VARCHAR(255) NOT NULL,
    filter_criteria JSONB NOT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_report_filters_user ON report_saved_filters(user_id);
CREATE INDEX IF NOT EXISTS idx_report_filters_report ON report_saved_filters(report_id);

COMMENT ON TABLE report_saved_filters IS 'User saved filter configurations per report';

-- ============================================================
-- 6. DATA QUALITY TRACKING
-- ============================================================

-- Data Quality Issues
CREATE TABLE IF NOT EXISTS data_quality_issues (
    id BIGSERIAL PRIMARY KEY,
    issue_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    psp_id BIGINT,
    field_name VARCHAR(100),
    expected_format VARCHAR(255),
    actual_value TEXT,
    severity VARCHAR(20) DEFAULT 'WARNING',
    status VARCHAR(20) DEFAULT 'OPEN',
    resolved_by BIGINT REFERENCES platform_users(id),
    resolved_at TIMESTAMP,
    resolution_notes TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dq_issues_type ON data_quality_issues(issue_type);
CREATE INDEX IF NOT EXISTS idx_dq_issues_entity ON data_quality_issues(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_dq_issues_psp ON data_quality_issues(psp_id);
CREATE INDEX IF NOT EXISTS idx_dq_issues_status ON data_quality_issues(status);
CREATE INDEX IF NOT EXISTS idx_dq_issues_severity ON data_quality_issues(severity);
CREATE INDEX IF NOT EXISTS idx_dq_issues_created ON data_quality_issues(created_at);

COMMENT ON TABLE data_quality_issues IS 'Tracks data quality issues for reporting';
COMMENT ON COLUMN data_quality_issues.issue_type IS 'MISSING_CUSTOMER_DATA, INVALID_ID, DUPLICATE_RECORDS, etc.';
COMMENT ON COLUMN data_quality_issues.severity IS 'INFO, WARNING, ERROR, CRITICAL';

-- ============================================================
-- 7. REPORT AUDIT LOG
-- ============================================================

-- Report Audit Log
CREATE TABLE IF NOT EXISTS report_audit_log (
    id BIGSERIAL PRIMARY KEY,
    report_id BIGINT REFERENCES reports(id),
    execution_id BIGINT REFERENCES report_executions(id),
    user_id BIGINT REFERENCES platform_users(id),
    action VARCHAR(50) NOT NULL,
    action_details JSONB,
    ip_address VARCHAR(100),
    user_agent VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_report_audit_report ON report_audit_log(report_id);
CREATE INDEX IF NOT EXISTS idx_report_audit_user ON report_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_report_audit_action ON report_audit_log(action);
CREATE INDEX IF NOT EXISTS idx_report_audit_created ON report_audit_log(created_at);

COMMENT ON TABLE report_audit_log IS 'Audit trail for report access and actions';

-- ============================================================
-- 8. UPDATE TRIGGERS
-- ============================================================

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply triggers
CREATE TRIGGER update_reports_updated_at BEFORE UPDATE ON reports
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_report_definitions_updated_at BEFORE UPDATE ON report_definitions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_report_schedules_updated_at BEFORE UPDATE ON report_schedules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_regulatory_submissions_updated_at BEFORE UPDATE ON regulatory_submissions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_regulatory_templates_updated_at BEFORE UPDATE ON regulatory_templates
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_report_saved_filters_updated_at BEFORE UPDATE ON report_saved_filters
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- 9. RETENTION POLICY FUNCTION
-- ============================================================

-- Function to purge old report data based on retention policy
CREATE OR REPLACE FUNCTION purge_expired_report_data()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER := 0;
    report_record RECORD;
BEGIN
    FOR report_record IN 
        SELECT id, retention_days FROM reports WHERE retention_days IS NOT NULL
    LOOP
        DELETE FROM report_executions 
        WHERE report_id = report_record.id 
        AND created_at < NOW() - INTERVAL '1 day' * report_record.retention_days;
        
        GET DIAGNOSTICS deleted_count = deleted_count + ROW_COUNT;
    END LOOP;
    
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION purge_expired_report_data() IS 'Purges report execution data older than retention period';
