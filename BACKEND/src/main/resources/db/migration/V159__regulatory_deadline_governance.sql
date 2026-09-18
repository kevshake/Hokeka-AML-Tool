CREATE TABLE regulatory_deadline_policies (
    id BIGSERIAL PRIMARY KEY,
    policy_code VARCHAR(80) NOT NULL UNIQUE,
    jurisdiction VARCHAR(8) NOT NULL,
    report_type VARCHAR(40) NOT NULL,
    deadline_amount INTEGER NOT NULL CHECK (deadline_amount > 0),
    deadline_unit VARCHAR(24) NOT NULL CHECK (deadline_unit IN ('HOURS', 'CALENDAR_DAYS', 'BUSINESS_DAYS')),
    warning_hours INTEGER NOT NULL DEFAULT 24 CHECK (warning_hours >= 0),
    psp_id BIGINT REFERENCES psps(psp_id),
    effective_from DATE NOT NULL,
    effective_to DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    legal_reference TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX idx_deadline_policy_resolution
    ON regulatory_deadline_policies(report_type, jurisdiction, psp_id, effective_from DESC)
    WHERE active = TRUE;

INSERT INTO regulatory_deadline_policies (
    policy_code, jurisdiction, report_type, deadline_amount, deadline_unit,
    warning_hours, psp_id, effective_from, legal_reference
) VALUES
    ('KE-POCAMLA-STR-2D', 'KE', 'SAR_STR', 2, 'CALENDAR_DAYS', 24, NULL,
     DATE '2023-11-17',
     'POCAMLA Regulations 2023 regulation 38 and POCAMLA section 44: report within two days after suspicion arose'),
    ('DEFAULT-SAR-30D', 'DEFAULT', 'SAR_STR', 30, 'CALENDAR_DAYS', 72, NULL,
     DATE '2000-01-01',
     'Fallback policy; deployments must configure the applicable jurisdiction-specific legal requirement')
ON CONFLICT (policy_code) DO NOTHING;

ALTER TABLE suspicious_activity_reports
    ADD COLUMN suspicion_arose_at TIMESTAMP,
    ADD COLUMN suspicion_timestamp_source VARCHAR(32),
    ADD COLUMN deadline_policy_id BIGINT REFERENCES regulatory_deadline_policies(id),
    ADD COLUMN deadline_policy_code VARCHAR(80),
    ADD COLUMN deadline_calculated_at TIMESTAMP,
    ADD COLUMN deadline_warning_at TIMESTAMP,
    ADD COLUMN deadline_warning_sent_at TIMESTAMP,
    ADD COLUMN deadline_breach_notified_at TIMESTAMP,
    ADD COLUMN deadline_breached BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE suspicious_activity_reports
SET suspicion_arose_at = COALESCE(suspicion_arose_at, created_at),
    suspicion_timestamp_source = COALESCE(suspicion_timestamp_source, 'MIGRATED_CREATED_AT')
WHERE suspicion_arose_at IS NULL;

ALTER TABLE suspicious_activity_reports
    ALTER COLUMN suspicion_arose_at SET NOT NULL,
    ALTER COLUMN suspicion_timestamp_source SET NOT NULL;

CREATE INDEX idx_sar_regulatory_deadline
    ON suspicious_activity_reports(status, filing_deadline)
    WHERE status IN ('DRAFT', 'PENDING_REVIEW', 'APPROVED');

ALTER TABLE suspicious_activity_reports_aud
    ADD COLUMN IF NOT EXISTS suspicion_arose_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS suspicion_timestamp_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS deadline_policy_id BIGINT,
    ADD COLUMN IF NOT EXISTS deadline_policy_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS deadline_calculated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deadline_warning_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deadline_warning_sent_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deadline_breach_notified_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS deadline_breached BOOLEAN;

ALTER TABLE compliance_deadlines
    ADD COLUMN source_type VARCHAR(40),
    ADD COLUMN source_id BIGINT;

CREATE UNIQUE INDEX uq_compliance_deadline_source
    ON compliance_deadlines(source_type, source_id)
    WHERE source_type IS NOT NULL AND source_id IS NOT NULL;

ALTER TABLE compliance_deadlines_aud
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS source_id BIGINT;
