ALTER TABLE beneficial_owners
    ALTER COLUMN passport_number TYPE TEXT,
    ALTER COLUMN national_id TYPE TEXT,
    ADD COLUMN IF NOT EXISTS passport_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS national_id_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_beneficial_owners_passport_hash
    ON beneficial_owners (passport_hash) WHERE passport_hash IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_beneficial_owners_national_id_hash
    ON beneficial_owners (national_id_hash) WHERE national_id_hash IS NOT NULL;

ALTER TABLE edd_requests
    ADD COLUMN IF NOT EXISTS site_visit_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS initiated_by VARCHAR(200),
    ADD COLUMN IF NOT EXISTS last_updated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_updated_by VARCHAR(200),
    ADD COLUMN IF NOT EXISTS completed_by VARCHAR(200);

CREATE TABLE IF NOT EXISTS edd_evidence_events (
    id BIGSERIAL PRIMARY KEY,
    edd_request_id BIGINT NOT NULL REFERENCES edd_requests(id) ON DELETE CASCADE,
    merchant_id BIGINT NOT NULL,
    item_code VARCHAR(50) NOT NULL,
    previous_value BOOLEAN,
    new_value BOOLEAN NOT NULL,
    performed_by VARCHAR(200) NOT NULL,
    notes TEXT,
    occurred_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_edd_evidence_request
    ON edd_evidence_events (edd_request_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_edd_evidence_merchant
    ON edd_evidence_events (merchant_id, occurred_at DESC);
