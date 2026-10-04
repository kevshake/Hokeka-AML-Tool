-- Link a control-plane assessment to the edge assessment that decided it.
-- edge_assessment_id already exists (V239). This only adds the uniqueness the adopt path needs.
ALTER TABLE assessments ADD COLUMN IF NOT EXISTS edge_assessment_id VARCHAR(128);
CREATE UNIQUE INDEX IF NOT EXISTS uq_assessments_edge_assessment_id
    ON assessments (edge_assessment_id);

-- Manifest of rule_version_ids published with an edge bundle. The hash is the bundle content id.
CREATE TABLE IF NOT EXISTS edge_bundles (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL,
    manifest_hash VARCHAR(80) NOT NULL,
    manifest JSONB NOT NULL,
    rule_count INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_edge_bundles_psp_hash UNIQUE (psp_id, manifest_hash)
);
