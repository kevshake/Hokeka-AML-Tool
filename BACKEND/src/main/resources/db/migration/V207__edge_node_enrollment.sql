-- ============================================================
-- Edge node enrollment & authorisation registry
-- Migration: V207__edge_node_enrollment.sql
-- Entity:    com.posgateway.aml.entity.edge.EdgeNode  (@Audited)
-- Contract:  docs/architecture/edge-channel-contract.md §7
--
-- One row per PSP on-prem edge engine. This is the authorisation
-- record that gates rule-bundle distribution:
--   PENDING -> APPROVED -> ACTIVE -> SUSPENDED / REVOKED
-- and only ACTIVE is ever served a bundle.
--
-- Security notes baked into the schema:
--   * enrollment_code_hash holds a SHA-256 HEX DIGEST of the
--     single-use enrollment code. The raw code is NEVER stored;
--     it is returned once at request time. The digest is set to
--     NULL the moment the code is consumed / the node is
--     rejected or revoked — that is what makes it single-use.
--   * edge_x25519_public_key / edge_ed25519_public_key are the
--     edge's PINNED public keys (base64 of the raw 32 bytes).
--     Trust-on-first-activation: once written they are never
--     overwritten with different values.
--   * edge_id is globally unique but NULL until activation
--     (the node asserts its machine identity at first boot).
--     Postgres treats NULLs as distinct in a UNIQUE constraint,
--     so many un-activated rows coexist happily.
-- ============================================================

CREATE TABLE IF NOT EXISTS edge_nodes (
    id                        BIGSERIAL PRIMARY KEY,
    psp_id                    BIGINT        NOT NULL,
    edge_id                   VARCHAR(128),
    display_name              VARCHAR(255)  NOT NULL,
    status                    VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    enrollment_code_hash      VARCHAR(64),
    enrollment_code_expires_at TIMESTAMP WITH TIME ZONE,
    edge_x25519_public_key    VARCHAR(128),
    edge_ed25519_public_key   VARCHAR(128),
    last_seen_at              TIMESTAMP WITH TIME ZONE,
    last_bundle_version       BIGINT,
    activated_at              TIMESTAMP WITH TIME ZONE,
    revoked_at                TIMESTAMP WITH TIME ZONE,
    suspended_at              TIMESTAMP WITH TIME ZONE,
    created_by                VARCHAR(128),
    approved_by               VARCHAR(128),
    hostname                  VARCHAR(255),
    agent_version             VARCHAR(64),
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_edge_nodes_edge_id') THEN
        ALTER TABLE edge_nodes ADD CONSTRAINT uq_edge_nodes_edge_id UNIQUE (edge_id);
    END IF;
END $$;

-- Tenant-scoped listing (a PSP user may only ever see its own nodes).
CREATE INDEX IF NOT EXISTS idx_edge_nodes_psp_id       ON edge_nodes (psp_id);
-- Fleet dashboards / "who is ACTIVE" authorisation sweeps.
CREATE INDEX IF NOT EXISTS idx_edge_nodes_status       ON edge_nodes (status);
-- Activation path: single indexed lookup on the code digest.
CREATE INDEX IF NOT EXISTS idx_edge_nodes_code_hash    ON edge_nodes (enrollment_code_hash);

COMMENT ON TABLE  edge_nodes                             IS 'PSP on-prem edge engine registry; gates rule-bundle distribution (edge-channel-contract §7). Envers-audited.';
COMMENT ON COLUMN edge_nodes.psp_id                      IS 'Owning tenant; every PSP-scoped read MUST filter by psp_id';
COMMENT ON COLUMN edge_nodes.edge_id                     IS 'Stable machine identity asserted at activation and carried in the mTLS client-cert CN; NULL until activated';
COMMENT ON COLUMN edge_nodes.status                      IS 'PENDING | APPROVED | ACTIVE | SUSPENDED | REVOKED | REJECTED — only ACTIVE receives bundles';
COMMENT ON COLUMN edge_nodes.enrollment_code_hash        IS 'SHA-256 hex digest of the single-use enrollment code. The raw code is never persisted; NULL once consumed';
COMMENT ON COLUMN edge_nodes.enrollment_code_expires_at  IS 'Enrollment code TTL (24 h from issue)';
COMMENT ON COLUMN edge_nodes.edge_x25519_public_key      IS 'Base64 of the raw 32-byte X25519 public key. PINNED at first activation — bundles are HSE-1 sealed to it';
COMMENT ON COLUMN edge_nodes.edge_ed25519_public_key     IS 'Base64 of the raw 32-byte Ed25519 public key. PINNED at first activation';
COMMENT ON COLUMN edge_nodes.last_bundle_version         IS 'Bundle version last served to this node (drift / tamper attestation)';

-- ============================================================
-- Envers audit mirror (edge_nodes_aud)
-- Conventions match V117 / V119 / V120:
--   rev     INTEGER NOT NULL (FK -> revinfo.rev)
--   revtype SMALLINT
--   PK      (id, rev), all entity columns NULLABLE
-- ============================================================
CREATE TABLE IF NOT EXISTS edge_nodes_aud (
    id                        BIGINT       NOT NULL,
    rev                       INTEGER      NOT NULL,
    revtype                   SMALLINT,
    psp_id                    BIGINT,
    edge_id                   VARCHAR(128),
    display_name              VARCHAR(255),
    status                    VARCHAR(16),
    enrollment_code_hash      VARCHAR(64),
    enrollment_code_expires_at TIMESTAMP WITH TIME ZONE,
    edge_x25519_public_key    VARCHAR(128),
    edge_ed25519_public_key   VARCHAR(128),
    last_seen_at              TIMESTAMP WITH TIME ZONE,
    last_bundle_version       BIGINT,
    activated_at              TIMESTAMP WITH TIME ZONE,
    revoked_at                TIMESTAMP WITH TIME ZONE,
    suspended_at              TIMESTAMP WITH TIME ZONE,
    created_by                VARCHAR(128),
    approved_by               VARCHAR(128),
    hostname                  VARCHAR(255),
    agent_version             VARCHAR(64),
    created_at                TIMESTAMP WITH TIME ZONE,
    updated_at                TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_edge_nodes_aud PRIMARY KEY (id, rev)
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'revinfo')
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_edge_nodes_aud_rev') THEN
        ALTER TABLE edge_nodes_aud
            ADD CONSTRAINT fk_edge_nodes_aud_rev FOREIGN KEY (rev) REFERENCES revinfo (rev);
    END IF;
END $$;

COMMENT ON TABLE edge_nodes_aud IS 'Hibernate Envers audit mirror of edge_nodes — full history of every approve/activate/suspend/revoke';

-- ============================================================
-- Aggregate metrics uploaded by edge nodes (contract §6).
-- COUNTS AND OUTCOMES ONLY. There is deliberately no column
-- here that could hold a PAN, PAN hash, customer id, IP,
-- amount or merchant id — the schema itself is the last line
-- of the privacy guarantee.
-- ============================================================
CREATE TABLE IF NOT EXISTS edge_metrics_reports (
    id                     BIGSERIAL PRIMARY KEY,
    edge_node_id           BIGINT       NOT NULL REFERENCES edge_nodes (id),
    psp_id                 BIGINT       NOT NULL,
    edge_id                VARCHAR(128) NOT NULL,
    window_start           TIMESTAMP WITH TIME ZONE NOT NULL,
    window_end             TIMESTAMP WITH TIME ZONE NOT NULL,
    rule_bundle_version    BIGINT       NOT NULL DEFAULT 0,
    rule_bundle_hash       VARCHAR(128),
    total_evaluated        BIGINT       NOT NULL DEFAULT 0,
    allowed                BIGINT       NOT NULL DEFAULT 0,
    alerted                BIGINT       NOT NULL DEFAULT 0,
    held                   BIGINT       NOT NULL DEFAULT 0,
    blocked                BIGINT       NOT NULL DEFAULT 0,
    rule_hit_counts_json   TEXT,
    p50_latency_micros     DOUBLE PRECISION NOT NULL DEFAULT 0,
    p95_latency_micros     DOUBLE PRECISION NOT NULL DEFAULT 0,
    p99_latency_micros     DOUBLE PRECISION NOT NULL DEFAULT 0,
    engine_health          VARCHAR(64),
    received_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_edge_metrics_psp_window
    ON edge_metrics_reports (psp_id, window_end DESC);
CREATE INDEX IF NOT EXISTS idx_edge_metrics_node
    ON edge_metrics_reports (edge_node_id, window_end DESC);

COMMENT ON TABLE  edge_metrics_reports                    IS 'Aggregate-only telemetry from PSP edge engines (edge-channel-contract §6). No PII, no per-transaction fields — by schema.';
COMMENT ON COLUMN edge_metrics_reports.rule_hit_counts_json IS 'JSON object {ruleId: count} — counts only, never transaction references';
COMMENT ON COLUMN edge_metrics_reports.rule_bundle_hash   IS 'Hash of the bundle the edge was running — drift / tamper attestation';
