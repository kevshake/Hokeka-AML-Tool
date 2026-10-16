-- Link a control-plane assessment to the edge assessment that decided it.
-- assessments.edge_assessment_id already exists as UUID (V238); this only adds the uniqueness
-- the adopt path needs. Edge assessment ids are UUIDs.
CREATE UNIQUE INDEX IF NOT EXISTS uq_assessments_edge_assessment_id
    ON assessments (edge_assessment_id);

-- Edge bundle manifest hashes are 'sha256:' + 64 hex chars (71) and are stored as context_hash
-- on edge-adopted assessments; V238 sized the column at 64.
ALTER TABLE assessments ALTER COLUMN context_hash TYPE VARCHAR(128);

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
