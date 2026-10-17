-- V269__regulatory_filed_documents_and_legal_holds.sql
-- Purpose: work package R9 of docs/architecture/EDGE_DATA_RESIDENCY_PLAN.md (SAR/CTR filing from
-- Edge pulls, the filed-document store, legal hold notifications).
--
-- 1. regulatory_filed_documents: the filed SAR/STR/CTR document, the regulatory record. This is
--    the ONLY cloud table allowed to hold class-T transaction data
--    (docs/EDGE_DATA_RESIDENCY_CONTRACT.md §2.3): the data pulled by filing.extract at filing time,
--    inside the rendered document. The document is stored encrypted at rest with the platform field
--    key (AES-256-GCM, 'enc:v1:' prefix, VersionedAesGcmStringConverter) and its SHA-256 over the
--    plaintext bytes. Rows are immutable: a trigger refuses every UPDATE, and refuses DELETE while
--    retain_until (filing date + the PSP's retention_years at filing, §8.4) is in the future. One
--    document per workflow record (unique workflow_type + workflow_id). Not an Envers entity: the
--    document must not be copied into an audit mirror.
--
-- 2. legal_hold_workflows / legal_holds / legal_hold_items / legal_hold_site_acks: the cloud is the
--    source of truth for legal holds (§7.10, §8.3). A workflow record (SAR, escalated case, filed
--    CTR) owns a set of holds; each hold covers at most 1000 decision record ids (the hold.place
--    scope limit) and is delivered to every Edge Site of the PSP. legal_hold_site_acks tracks, per
--    hold and site, the last notify sent and the hold.ack received; unconfirmed rows are re-sent
--    after every hello.ack of that site. Hold ids and references are class C (§2.2): no transaction
--    data here, only decision record ids.
--
-- 3. Decision record purge (§8.4): DecisionRecordPurgeService now also keeps records covered by an
--    active hold; the lookup index is idx_legal_hold_items_edge_assessment_id.
--
-- Tables affected: regulatory_filed_documents, legal_hold_workflows, legal_holds, legal_hold_items,
--                  legal_hold_site_acks (all new)
-- Audited entities: none (filed documents and holds are audited through audit_logs entries).

CREATE TABLE IF NOT EXISTS regulatory_filed_documents (
    id                   BIGSERIAL     PRIMARY KEY,
    psp_id               BIGINT        NOT NULL REFERENCES psps (psp_id),
    document_kind        VARCHAR(8)    NOT NULL,
    workflow_type        VARCHAR(24)   NOT NULL,
    workflow_id          BIGINT        NOT NULL,
    filing_ref           VARCHAR(64)   NOT NULL,
    document_format      VARCHAR(24)   NOT NULL,
    content_type         VARCHAR(64)   NOT NULL,
    file_name            VARCHAR(128)  NOT NULL,
    content_ciphertext   TEXT          NOT NULL,
    content_sha256       CHAR(64)      NOT NULL,
    content_length       BIGINT        NOT NULL,
    transaction_count    INTEGER       NOT NULL,
    data_source          VARCHAR(16)   NOT NULL,
    edge_audit_ids       TEXT,
    regulator_reference  VARCHAR(128),
    filed_by_user_id     BIGINT        NOT NULL,
    filed_by_username    VARCHAR(255),
    filed_at             TIMESTAMPTZ   NOT NULL,
    retain_until         TIMESTAMPTZ   NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_regulatory_filed_documents_workflow UNIQUE (workflow_type, workflow_id),
    CONSTRAINT ck_regulatory_filed_documents_kind CHECK (document_kind IN ('SAR', 'STR', 'CTR')),
    CONSTRAINT ck_regulatory_filed_documents_workflow CHECK (workflow_type IN ('SAR', 'CTR_DEADLINE')),
    CONSTRAINT ck_regulatory_filed_documents_source CHECK (data_source IN ('EDGE', 'CLOUD_LEGACY', 'NONE')),
    CONSTRAINT ck_regulatory_filed_documents_encrypted CHECK (content_ciphertext LIKE 'enc:v1:%'),
    CONSTRAINT ck_regulatory_filed_documents_sha CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_regulatory_filed_documents_retention CHECK (retain_until > filed_at)
);

CREATE INDEX IF NOT EXISTS idx_regulatory_filed_documents_psp_filed
    ON regulatory_filed_documents (psp_id, filed_at DESC);

COMMENT ON TABLE regulatory_filed_documents IS
    'Filed SAR/STR/CTR documents: the regulatory record and the only cloud store of class-T data '
    '(EDGE_DATA_RESIDENCY_CONTRACT.md 2.3). Encrypted at rest, SHA-256 sealed, immutable.';

-- Immutability: no UPDATE ever; DELETE only once the retention floor has passed.
CREATE OR REPLACE FUNCTION regulatory_filed_documents_immutable() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'regulatory_filed_documents rows are immutable (document %)', OLD.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF OLD.retain_until > NOW() THEN
        RAISE EXCEPTION 'regulatory_filed_documents row % is retained until %', OLD.id, OLD.retain_until
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_regulatory_filed_documents_immutable ON regulatory_filed_documents;
CREATE TRIGGER trg_regulatory_filed_documents_immutable
    BEFORE UPDATE OR DELETE ON regulatory_filed_documents
    FOR EACH ROW EXECUTE FUNCTION regulatory_filed_documents_immutable();

CREATE TABLE IF NOT EXISTS legal_hold_workflows (
    id             BIGSERIAL     PRIMARY KEY,
    psp_id         BIGINT        NOT NULL REFERENCES psps (psp_id),
    workflow_type  VARCHAR(24)   NOT NULL,
    workflow_id    BIGINT        NOT NULL,
    reason_code    VARCHAR(24)   NOT NULL,
    reference      VARCHAR(64)   NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_legal_hold_workflows_workflow UNIQUE (workflow_type, workflow_id),
    CONSTRAINT ck_legal_hold_workflows_type CHECK (workflow_type IN ('SAR', 'CASE', 'CTR_DEADLINE')),
    CONSTRAINT ck_legal_hold_workflows_reason
        CHECK (reason_code IN ('CASE', 'SAR', 'STR', 'CTR', 'REGULATOR_REQUEST', 'LITIGATION'))
);

CREATE TABLE IF NOT EXISTS legal_holds (
    hold_id           UUID          PRIMARY KEY,
    workflow_ref_id   BIGINT        NOT NULL REFERENCES legal_hold_workflows (id),
    psp_id            BIGINT        NOT NULL REFERENCES psps (psp_id),
    reason_code       VARCHAR(24)   NOT NULL,
    reference         VARCHAR(64)   NOT NULL,
    state             VARCHAR(16)   NOT NULL,
    placed_at         TIMESTAMPTZ   NOT NULL,
    placed_by         VARCHAR(128)  NOT NULL,
    item_count        INTEGER       NOT NULL,
    release_basis_at  TIMESTAMPTZ,
    released_at       TIMESTAMPTZ,
    released_by       VARCHAR(128),
    release_reason    VARCHAR(32),
    CONSTRAINT ck_legal_holds_state CHECK (state IN ('ACTIVE', 'RELEASED')),
    CONSTRAINT ck_legal_holds_reason
        CHECK (reason_code IN ('CASE', 'SAR', 'STR', 'CTR', 'REGULATOR_REQUEST', 'LITIGATION')),
    CONSTRAINT ck_legal_holds_release_reason CHECK (release_reason IS NULL OR release_reason IN
        ('CASE_CLOSED', 'FILING_RETENTION_ELAPSED', 'REGULATOR_RELEASED', 'PLACED_IN_ERROR')),
    CONSTRAINT ck_legal_holds_released
        CHECK ((state = 'RELEASED') = (released_at IS NOT NULL AND release_reason IS NOT NULL)),
    CONSTRAINT ck_legal_holds_items CHECK (item_count BETWEEN 1 AND 1000)
);

CREATE INDEX IF NOT EXISTS idx_legal_holds_psp_state ON legal_holds (psp_id, state);
CREATE INDEX IF NOT EXISTS idx_legal_holds_workflow ON legal_holds (workflow_ref_id);

CREATE TABLE IF NOT EXISTS legal_hold_items (
    hold_id             UUID  NOT NULL REFERENCES legal_holds (hold_id),
    edge_assessment_id  UUID  NOT NULL,
    PRIMARY KEY (hold_id, edge_assessment_id)
);

CREATE INDEX IF NOT EXISTS idx_legal_hold_items_edge_assessment_id ON legal_hold_items (edge_assessment_id);

CREATE TABLE IF NOT EXISTS legal_hold_site_acks (
    id                  BIGSERIAL     PRIMARY KEY,
    hold_id             UUID          NOT NULL REFERENCES legal_holds (hold_id),
    site_id             VARCHAR(64)   NOT NULL,
    notify_type         VARCHAR(16)   NOT NULL,
    notify_id           VARCHAR(64),
    sent_at             TIMESTAMPTZ,
    send_count          INTEGER       NOT NULL DEFAULT 0,
    ack_state           VARCHAR(16),
    acked_at            TIMESTAMPTZ,
    matched_rows        BIGINT,
    active_hold_digest  CHAR(64),
    digest_matches      BOOLEAN,
    CONSTRAINT uq_legal_hold_site_acks UNIQUE (hold_id, site_id),
    CONSTRAINT ck_legal_hold_site_acks_type CHECK (notify_type IN ('hold.place', 'hold.release')),
    CONSTRAINT ck_legal_hold_site_acks_state
        CHECK (ack_state IS NULL OR ack_state IN ('APPLIED', 'RELEASED', 'UNKNOWN_HOLD'))
);

CREATE INDEX IF NOT EXISTS idx_legal_hold_site_acks_site ON legal_hold_site_acks (site_id);

COMMENT ON TABLE legal_holds IS
    'Legal holds (EDGE_DATA_RESIDENCY_CONTRACT.md 7.10/8.3); the cloud is their source of truth.';
COMMENT ON TABLE legal_hold_site_acks IS
    'Per hold and Edge Site: last hold.place/hold.release notify sent and the hold.ack received.';
