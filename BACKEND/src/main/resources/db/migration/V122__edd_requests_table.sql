-- ============================================================
-- V122: edd_requests
--
-- Backs com.posgateway.aml.entity.edd.EnhancedDueDiligenceRequest.
-- Replaces the previous in-memory ConcurrentHashMap that lived
-- inside EnhancedDueDiligenceService ("In-memory store for demo
-- purposes" — now removed).
--
-- One row per merchant currently or previously in EDD.
-- merchant_id is unique (one active EDD per merchant); soft FK
-- to merchants(merchant_id) — the convention elsewhere on
-- merchant-scoped tables (V121 merchant_documents) is to keep
-- this loose.
--
-- Entity is NOT @Audited; no edd_requests_aud counterpart.
-- ============================================================

CREATE TABLE IF NOT EXISTS edd_requests (
    id                            BIGSERIAL    PRIMARY KEY,
    merchant_id                   BIGINT       NOT NULL UNIQUE,
    status                        VARCHAR(32)  NOT NULL DEFAULT 'IN_PROGRESS',
    source_of_funds_verified      BOOLEAN      NOT NULL DEFAULT FALSE,
    source_of_wealth_verified     BOOLEAN      NOT NULL DEFAULT FALSE,
    site_visit_completed          BOOLEAN      NOT NULL DEFAULT FALSE,
    senior_management_approval    BOOLEAN      NOT NULL DEFAULT FALSE,
    family_associate_checks       BOOLEAN      NOT NULL DEFAULT FALSE,
    transaction_purpose_review    BOOLEAN      NOT NULL DEFAULT FALSE,
    initiated_at                  TIMESTAMP    NOT NULL,
    completed_at                  TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_edd_requests_merchant
    ON edd_requests (merchant_id);
CREATE INDEX IF NOT EXISTS idx_edd_requests_status
    ON edd_requests (status);

COMMENT ON TABLE  edd_requests IS 'Enhanced Due Diligence per-merchant tracking (EnhancedDueDiligenceRequest entity)';
COMMENT ON COLUMN edd_requests.merchant_id IS 'Soft FK -> merchants(merchant_id); one active EDD per merchant';
