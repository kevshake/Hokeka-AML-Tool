-- Expand varchar(255) columns in audit_logs_enhanced to TEXT.
-- These columns store full entity JSON representations which easily exceed 255 chars.
-- Without this fix, any write operation (SAR create, case create, etc.) triggers a
-- DATABASE_UNAVAILABLE 503 because the audit log INSERT fails after the main write,
-- causing an UnexpectedRollbackException that the error handler wraps as ERR_DATABASE_001.
ALTER TABLE audit_logs_enhanced ALTER COLUMN after_value TYPE TEXT;
ALTER TABLE audit_logs_enhanced ALTER COLUMN before_value TYPE TEXT;
ALTER TABLE audit_logs_enhanced ALTER COLUMN error_message TYPE TEXT;
ALTER TABLE audit_logs_enhanced ALTER COLUMN reason TYPE TEXT;
