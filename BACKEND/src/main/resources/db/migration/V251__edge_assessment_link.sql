-- Edge assessment ids are opaque strings, not necessarily UUIDs.
ALTER TABLE assessments
    ADD COLUMN IF NOT EXISTS edge_assessment_id VARCHAR(128);

ALTER TABLE assessments
    ALTER COLUMN edge_assessment_id TYPE VARCHAR(128)
    USING edge_assessment_id::text;

CREATE UNIQUE INDEX IF NOT EXISTS uq_assessments_edge_assessment_id
    ON assessments (edge_assessment_id);

-- Record each published rule-version manifest under its content hash.
CREATE TABLE IF NOT EXISTS edge_bundles (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL,
    manifest_hash VARCHAR(80) NOT NULL,
    manifest JSONB NOT NULL,
    rule_count INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_edge_bundles_psp_hash UNIQUE (psp_id, manifest_hash)
);
