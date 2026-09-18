ALTER TABLE suspicious_activity_reports
    ADD COLUMN reviewed_at TIMESTAMP,
    ADD COLUMN review_notes TEXT;

ALTER TABLE suspicious_activity_reports_aud
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS review_notes TEXT;

