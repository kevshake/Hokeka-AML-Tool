ALTER TABLE report_executions ADD COLUMN IF NOT EXISTS source_context JSONB;
ALTER TABLE report_executions ADD COLUMN IF NOT EXISTS calculation_summary JSONB;
ALTER TABLE report_executions ADD COLUMN IF NOT EXISTS record_links JSONB;

CREATE INDEX IF NOT EXISTS idx_report_execution_source_context
    ON report_executions USING GIN (source_context);

