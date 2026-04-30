-- ============================================================
-- CBK Regulatory Submissions Table (HOK-CBK)
-- Migration: V117__cbk_submissions_table.sql
-- Description: Persists CBK regulatory report submissions made
--              from the Reports Center -> CBK Submission Panel.
--              Audited via Hibernate Envers (cbk_submissions_aud).
-- ============================================================

CREATE TABLE IF NOT EXISTS cbk_submissions (
    id                  BIGSERIAL PRIMARY KEY,
    psp_id              BIGINT       NOT NULL,
    report_type         VARCHAR(64)  NOT NULL,
    period              VARCHAR(32)  NOT NULL,
    period_from         VARCHAR(32),
    period_to           VARCHAR(32),
    reference_number    VARCHAR(64)  NOT NULL UNIQUE,
    status              VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    submitted_at        TIMESTAMP WITH TIME ZONE,
    submitted_by        BIGINT,
    payload_json        TEXT,
    regulator_response  TEXT,
    error_message       VARCHAR(1024)
);

-- Hot-path index: list-by-period within a PSP
CREATE INDEX IF NOT EXISTS idx_cbk_submissions_psp_period
    ON cbk_submissions (psp_id, period);

-- Status dashboards / status filters within a PSP
CREATE INDEX IF NOT EXISTS idx_cbk_submissions_psp_status
    ON cbk_submissions (psp_id, status);

-- Direct lookup by regulator-facing reference number
CREATE INDEX IF NOT EXISTS idx_cbk_submissions_reference
    ON cbk_submissions (reference_number);

COMMENT ON TABLE  cbk_submissions                    IS 'CBK regulatory report submissions (PSP-isolated, Envers-audited)';
COMMENT ON COLUMN cbk_submissions.psp_id             IS 'Tenant scope; every read MUST filter by psp_id';
COMMENT ON COLUMN cbk_submissions.report_type        IS 'Report template id (e.g. cbk-returns, CTR, STR)';
COMMENT ON COLUMN cbk_submissions.period             IS 'Period bucket: daily | weekly | monthly | quarterly | semi-annual | annual | YYYY-Qn';
COMMENT ON COLUMN cbk_submissions.reference_number   IS 'Generated CBK-<year>-<uuid8> reference returned to the FE';
COMMENT ON COLUMN cbk_submissions.status             IS 'DRAFT | SUBMITTED | ACCEPTED | REJECTED';
COMMENT ON COLUMN cbk_submissions.regulator_response IS 'Raw response payload from CBK once the API integration is wired (HOK-CBK-API)';

-- ============================================================
-- Envers audit mirror (cbk_submissions_aud)
-- ============================================================
CREATE TABLE IF NOT EXISTS cbk_submissions_aud (
    id                  BIGINT       NOT NULL,
    rev                 INTEGER      NOT NULL,
    revtype             SMALLINT,
    psp_id              BIGINT,
    report_type         VARCHAR(64),
    period              VARCHAR(32),
    period_from         VARCHAR(32),
    period_to           VARCHAR(32),
    reference_number    VARCHAR(64),
    status              VARCHAR(16),
    submitted_at        TIMESTAMP WITH TIME ZONE,
    submitted_by        BIGINT,
    payload_json        TEXT,
    regulator_response  TEXT,
    error_message       VARCHAR(1024),
    PRIMARY KEY (id, rev)
);

CREATE INDEX IF NOT EXISTS idx_cbk_submissions_aud_rev
    ON cbk_submissions_aud (rev);
