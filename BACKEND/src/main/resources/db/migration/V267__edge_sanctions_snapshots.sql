-- R10b (docs/architecture/EDGE_DATA_RESIDENCY_PLAN.md, "Conflicts and resolutions" item 1;
-- EDGE_DATA_RESIDENCY_CONTRACT.md section 6.4): the sanctions/PEP list snapshot distributed to Edge
-- Nodes as a sealed, versioned list document, so the Edge screens counterparties locally and never
-- sends a name to the cloud.
--
-- A snapshot is built while the OpenSanctions download streams (SanctionsListDownloadService) and
-- becomes READY once every chunk is stored. Chunks are gzip-compressed JSON lines, one public list
-- entry per line (docs/contracts/sanctions-list-entry.schema.json), at most 5 000 entries each.
-- list_hash is the SHA-256 over the chunk lines '<index>:<sha256>:<entryCount>\n' and is what the
-- Edge config's sanctions.listHash announces.

CREATE TABLE IF NOT EXISTS edge_sanctions_snapshots (
    snapshot_id        BIGSERIAL    PRIMARY KEY,
    list_version       VARCHAR(64)  NOT NULL,
    list_hash          CHAR(64),
    published_at       TIMESTAMPTZ,
    entry_count        INTEGER      NOT NULL DEFAULT 0,
    chunk_count        INTEGER      NOT NULL DEFAULT 0,
    sanctions_list_id  INTEGER      REFERENCES sanctions_lists (list_id) ON DELETE SET NULL,
    status             VARCHAR(16)  NOT NULL DEFAULT 'BUILDING',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_edge_sanctions_snapshots_status CHECK (status IN ('BUILDING', 'READY')),
    CONSTRAINT ck_edge_sanctions_snapshots_ready
        CHECK (status = 'BUILDING' OR (list_hash IS NOT NULL AND published_at IS NOT NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_edge_sanctions_snapshots_hash
    ON edge_sanctions_snapshots (list_hash) WHERE list_hash IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_edge_sanctions_snapshots_ready
    ON edge_sanctions_snapshots (published_at DESC, snapshot_id DESC) WHERE status = 'READY';

COMMENT ON TABLE edge_sanctions_snapshots IS
    'Sanctions/PEP list snapshots published to Edge Nodes (R10b, contract 6.4); public list data only';

CREATE TABLE IF NOT EXISTS edge_sanctions_snapshot_chunks (
    snapshot_id   BIGINT   NOT NULL REFERENCES edge_sanctions_snapshots (snapshot_id) ON DELETE CASCADE,
    chunk_index   INTEGER  NOT NULL,
    chunk_sha256  CHAR(64) NOT NULL,
    entry_count   INTEGER  NOT NULL,
    content_gzip  BYTEA    NOT NULL,
    PRIMARY KEY (snapshot_id, chunk_index),
    CONSTRAINT ck_edge_sanctions_snapshot_chunks_index CHECK (chunk_index BETWEEN 0 AND 4095),
    CONSTRAINT ck_edge_sanctions_snapshot_chunks_count CHECK (entry_count BETWEEN 1 AND 10000)
);

COMMENT ON TABLE edge_sanctions_snapshot_chunks IS
    'gzip JSON-lines chunks of an Edge sanctions/PEP list snapshot; chunk_sha256 is over the uncompressed bytes';
