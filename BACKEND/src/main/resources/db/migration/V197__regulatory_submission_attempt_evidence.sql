ALTER TABLE regulatory_submission_attempts
    ADD COLUMN IF NOT EXISTS request_sha256 VARCHAR(64),
    ADD COLUMN IF NOT EXISTS response_sha256 VARCHAR(64),
    ADD COLUMN IF NOT EXISTS regulator_status VARCHAR(64),
    ADD COLUMN IF NOT EXISTS error_message TEXT;

CREATE INDEX IF NOT EXISTS idx_rsa_status
    ON regulatory_submission_attempts(regulator, regulator_status, submitted_at DESC);

COMMENT ON COLUMN regulatory_submission_attempts.request_sha256 IS
    'SHA-256 of the complete outbound evidence representation before bounded body storage.';
COMMENT ON COLUMN regulatory_submission_attempts.response_sha256 IS
    'SHA-256 of the complete upstream response before bounded body storage.';
COMMENT ON COLUMN regulatory_submission_attempts.regulator_status IS
    'Status returned by the upstream regulator transport.';
COMMENT ON COLUMN regulatory_submission_attempts.error_message IS
    'Transport or validation failure retained for replay investigation.';
