-- V263__edge_data_access_log.sql
-- Purpose: work package R4 (Edge Tunnel server) of docs/architecture/EDGE_DATA_RESIDENCY_PLAN.md.
--
-- 1. edge_data_access_log: the cloud half of the tunnel access audit
--    (docs/EDGE_DATA_RESIDENCY_CONTRACT.md §7.15). One row per tunnel request, written before the
--    request is sent and completed on the terminal frame; it shares audit_id with the Edge-local
--    edge_access_log row. It holds who/why/what-shape only: params_summary is restricted to time
--    range, merchantId, decisions, ruleId, minScore, flags, id COUNT, link types and depth, never
--    txnRef values or backfill rows; no response item is ever stored (§7.16). Retained for at
--    least the PSP's retention_years (§8.4); PSP-visible through GET /api/v1/edge-data/access-log;
--    not deletable by PSP or platform users (no delete API exists).
--
-- 2. edge_nodes.site_id (+ Envers mirror): the Edge Site a node belongs to (§1). The tunnel
--    handshake requires hello.siteId to equal the node's registered site (§7.2) and the registry
--    routes per site (§7.12). NULL = single-node site whose id is the node's edge_id.
--
-- Tables affected: edge_data_access_log (new), edge_nodes, edge_nodes_aud
-- Audited entities: EdgeNode (new nullable column on the audit mirror)
-- Idempotent: IF NOT EXISTS throughout. V261/V262 belong to R3/R7 and are not touched here.

CREATE TABLE IF NOT EXISTS edge_data_access_log (
    id                BIGSERIAL     PRIMARY KEY,
    audit_id          UUID          NOT NULL,
    psp_id            BIGINT        NOT NULL,
    actor_kind        VARCHAR(16)   NOT NULL,
    actor_user_id     VARCHAR(128)  NOT NULL,
    actor_role        VARCHAR(64)   NOT NULL,
    purpose           VARCHAR(32)   NOT NULL,
    case_id           VARCHAR(64),
    alert_id          VARCHAR(64),
    filing_ref        VARCHAR(64),
    support_access    BOOLEAN       NOT NULL DEFAULT FALSE,
    support_reason    VARCHAR(500),
    op                VARCHAR(32)   NOT NULL,
    params_summary    TEXT          NOT NULL,
    params_digest     CHAR(64)      NOT NULL,
    sites_contacted   VARCHAR(4096),
    sites_answered    VARCHAR(4096),
    item_count        INTEGER       NOT NULL DEFAULT 0,
    bytes             BIGINT        NOT NULL DEFAULT 0,
    outcome           VARCHAR(32),
    requested_at      TIMESTAMPTZ   NOT NULL,
    completed_at      TIMESTAMPTZ,
    CONSTRAINT uq_edge_data_access_log_audit_id UNIQUE (audit_id),
    CONSTRAINT ck_edge_data_access_log_actor_kind
        CHECK (actor_kind IN ('PSP_USER', 'PLATFORM_USER', 'SYSTEM')),
    CONSTRAINT ck_edge_data_access_log_support
        CHECK (support_access = FALSE OR support_reason IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_edge_data_access_log_psp_requested
    ON edge_data_access_log (psp_id, requested_at DESC);

CREATE INDEX IF NOT EXISTS idx_edge_data_access_log_support
    ON edge_data_access_log (psp_id, requested_at DESC) WHERE support_access;

COMMENT ON TABLE edge_data_access_log IS
    'Cloud tunnel access audit (EDGE_DATA_RESIDENCY_CONTRACT.md 7.15). Never stores response items.';

ALTER TABLE edge_nodes ADD COLUMN IF NOT EXISTS site_id VARCHAR(64);
ALTER TABLE edge_nodes_aud ADD COLUMN IF NOT EXISTS site_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_edge_nodes_psp_site ON edge_nodes (psp_id, site_id);
