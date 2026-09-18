ALTER TABLE vasp_directory_entries
    ADD COLUMN IF NOT EXISTS sanctions_provider VARCHAR(128),
    ADD COLUMN IF NOT EXISTS sanctions_match_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS sanctions_next_screening_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE vasp_screening_records (
    id BIGSERIAL PRIMARY KEY,
    psp_id BIGINT NOT NULL,
    vasp_id BIGINT NOT NULL REFERENCES vasp_directory_entries(id) ON DELETE RESTRICT,
    subject_name VARCHAR(255) NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    provider VARCHAR(128) NOT NULL,
    available BOOLEAN NOT NULL,
    status VARCHAR(24) NOT NULL,
    match_count INTEGER NOT NULL DEFAULT 0,
    matches JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence JSONB NOT NULL DEFAULT '{}'::jsonb,
    screened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retain_until DATE NOT NULL,
    CONSTRAINT chk_vasp_screening_match_count CHECK (match_count >= 0)
);

CREATE INDEX idx_vasp_screening_history ON vasp_screening_records(vasp_id, screened_at DESC);
CREATE INDEX idx_vasp_screening_psp_time ON vasp_screening_records(psp_id, screened_at DESC);
CREATE INDEX idx_vasp_screening_due ON vasp_directory_entries(psp_id, sanctions_next_screening_at);

CREATE TRIGGER trg_vasp_screening_immutable
    BEFORE UPDATE OR DELETE ON vasp_screening_records
    FOR EACH ROW EXECUTE FUNCTION prevent_virtual_asset_evidence_mutation();

COMMENT ON TABLE vasp_screening_records IS
    'Append-only sanctions/PEP screening evidence for VASP names and named beneficial owners';
