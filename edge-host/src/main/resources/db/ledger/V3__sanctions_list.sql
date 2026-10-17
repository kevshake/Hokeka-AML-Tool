-- Edge Ledger, schema version 3 (EDGE_DATA_RESIDENCY_CONTRACT.md §6.4, §6.5; plan work package R10b).
-- The sanctions/PEP list snapshot the Edge screens counterparties with, kept on the site ledger so a
-- restart (or a second node of the site) screens with it without reaching the Control Plane. Public
-- list data only. Never edit this file once released: add V4+.

-- One row per list snapshot (manifest as received, after verification). complete = every chunk is
-- stored and the whole list re-verified against list_hash.
CREATE TABLE edge_sanctions_lists (
    list_hash      char(64)     PRIMARY KEY,
    list_version   text         NOT NULL,
    published_at   timestamptz  NOT NULL,
    entry_count    integer      NOT NULL,
    chunk_count    integer      NOT NULL,
    manifest       jsonb        NOT NULL,
    complete       boolean      NOT NULL DEFAULT false,
    fetched_at     timestamptz  NOT NULL DEFAULT now(),
    completed_at   timestamptz,
    CONSTRAINT ck_edge_sanctions_lists_hash CHECK (list_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_edge_sanctions_lists_counts CHECK (entry_count >= 0 AND chunk_count BETWEEN 0 AND 4096)
);

-- The gzip JSON-lines chunks exactly as received (chunk_sha256 is over the uncompressed bytes).
CREATE TABLE edge_sanctions_list_chunks (
    list_hash      char(64)  NOT NULL REFERENCES edge_sanctions_lists (list_hash) ON DELETE CASCADE,
    chunk_index    integer   NOT NULL,
    chunk_sha256   char(64)  NOT NULL,
    entry_count    integer   NOT NULL,
    content_gzip   bytea     NOT NULL,
    PRIMARY KEY (list_hash, chunk_index),
    CONSTRAINT ck_edge_sanctions_list_chunks_index CHECK (chunk_index BETWEEN 0 AND 4095)
);
