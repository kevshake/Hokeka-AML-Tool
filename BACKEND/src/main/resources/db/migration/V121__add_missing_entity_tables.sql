-- ============================================================
-- Backfill CREATE TABLE migrations for entities that previously
-- relied on Hibernate ddl-auto=update to materialise their
-- schema. With ddl-auto=validate now in force, every entity
-- needs an explicit Flyway migration.
--
-- Migration: V121__add_missing_entity_tables.sql
--
-- Tables created (entity -> table):
--   CaseAuditLog            -> case_audit_logs
--   CaseDecision            -> case_decisions
--   CaseEntity              -> case_entities
--   ComplianceDeadline      -> compliance_deadlines
--   DocumentAccessLog       -> document_access_logs
--   EvidenceChainOfCustody  -> evidence_chain_of_custody
--   MerchantDocument        -> merchant_documents
--   PasswordResetToken      -> password_reset_tokens
--   PspReportConfig         -> psp_report_configs
--   RolePermissionMapping   -> role_permission_mappings
--   RuleAbTest              -> rule_ab_tests
--   RuleAbTestResult        -> rule_ab_test_results
--   WebhookSubscription     -> webhook_subscriptions
--
-- All statements use CREATE TABLE IF NOT EXISTS so that any
-- table already materialised by the legacy ddl-auto=update
-- path is left untouched. No seed/sample data; pure DDL.
--
-- FK targets verified against existing migrations:
--   compliance_cases(case_id) - V2
--   case_evidence(id)         - V4
--   merchants(merchant_id)    - V2  (soft FK; kept loose for
--                               legacy data parity, see notes)
--   platform_users(id)        - V14
--   psps(psp_id)              - V3
--
-- Notes:
--   * case_audit_logs.user_id and case_decisions.decided_by point
--     to platform_users(id) but are deliberately created without
--     a hard FK because the legacy schema referenced both
--     `psp_users(user_id)` (V103) and `platform_users(id)`
--     (V14) inconsistently. Index-only is safer until the
--     auth-domain consolidation lands.
--   * Two columns on suspicious_activity_reports/compliance_cases
--     use TEXT for human-typed reference IDs; we mirror that.
-- ============================================================


-- ============================================================
-- case_audit_logs  (entity: CaseAuditLog)
-- Immutable per-action audit trail for case operations.
-- ============================================================
CREATE TABLE IF NOT EXISTS case_audit_logs (
    id              BIGSERIAL    PRIMARY KEY,
    case_id         BIGINT       NOT NULL,
    action          VARCHAR(255) NOT NULL,
    details         VARCHAR(255) NOT NULL,
    user_id         BIGINT,
    timestamp       TIMESTAMP    NOT NULL,
    previous_state  VARCHAR(255),
    new_state       VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_case_audit_logs_case_id
    ON case_audit_logs (case_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_case_audit_logs_user_id
    ON case_audit_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_case_audit_logs_action
    ON case_audit_logs (action);

COMMENT ON TABLE  case_audit_logs IS 'Immutable case-action audit trail (CaseAuditLog entity)';
COMMENT ON COLUMN case_audit_logs.user_id IS 'Soft FK -> platform_users(id); not enforced (see V121 header)';


-- ============================================================
-- case_decisions  (entity: CaseDecision, @Audited)
-- Aud mirror lives in V120 (case_decisions_aud).
-- ============================================================
CREATE TABLE IF NOT EXISTS case_decisions (
    id            BIGSERIAL    PRIMARY KEY,
    case_id       BIGINT       NOT NULL,
    decision_type VARCHAR(255) NOT NULL,
    justification TEXT         NOT NULL,
    decided_by    BIGINT,
    decided_at    TIMESTAMP    NOT NULL,
    is_final      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_case_decisions_case
        FOREIGN KEY (case_id) REFERENCES compliance_cases (case_id)
);

CREATE INDEX IF NOT EXISTS idx_case_decisions_case_id
    ON case_decisions (case_id, decided_at DESC);
CREATE INDEX IF NOT EXISTS idx_case_decisions_decided_by
    ON case_decisions (decided_by);
CREATE INDEX IF NOT EXISTS idx_case_decisions_type
    ON case_decisions (decision_type);


-- ============================================================
-- case_entities  (entity: CaseEntity, @Audited)
-- Aud mirror lives in V120 (case_entities_aud).
-- Generic non-transaction entity links (merchant, customer,
-- device, IP, card-hash) attached to a case.
-- ============================================================
CREATE TABLE IF NOT EXISTS case_entities (
    id               BIGSERIAL    PRIMARY KEY,
    case_id          BIGINT       NOT NULL,
    entity_type      VARCHAR(255) NOT NULL,
    entity_reference VARCHAR(255) NOT NULL,
    description      TEXT,
    linked_at        TIMESTAMP    NOT NULL,
    linked_by        BIGINT,
    CONSTRAINT fk_case_entities_case
        FOREIGN KEY (case_id) REFERENCES compliance_cases (case_id)
);

CREATE INDEX IF NOT EXISTS idx_case_entities_case_id
    ON case_entities (case_id);
CREATE INDEX IF NOT EXISTS idx_case_entities_type_reference
    ON case_entities (entity_type, entity_reference);
CREATE INDEX IF NOT EXISTS idx_case_entities_linked_by
    ON case_entities (linked_by);


-- ============================================================
-- compliance_deadlines  (entity: ComplianceDeadline, @Audited)
-- Aud mirror lives in V120 (compliance_deadlines_aud).
-- V118 already ALTERed compliance_deadlines/compliance_deadlines_aud
-- to add psp_id; this CREATE IF NOT EXISTS is a no-op when the
-- table is already present.
-- ============================================================
CREATE TABLE IF NOT EXISTS compliance_deadlines (
    id            BIGSERIAL    PRIMARY KEY,
    deadline_type VARCHAR(255) NOT NULL,
    deadline_date TIMESTAMP    NOT NULL,
    description   TEXT,
    jurisdiction  VARCHAR(255),
    psp_id        BIGINT,
    completed     BOOLEAN      NOT NULL DEFAULT FALSE,
    completed_at  TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_compliance_deadlines_psp_id
    ON compliance_deadlines (psp_id);
CREATE INDEX IF NOT EXISTS idx_compliance_deadlines_psp_date
    ON compliance_deadlines (psp_id, deadline_date)
    WHERE completed = false;
CREATE INDEX IF NOT EXISTS idx_compliance_deadlines_type
    ON compliance_deadlines (deadline_type);


-- ============================================================
-- document_access_logs  (entity: DocumentAccessLog)
-- Per-document access trail (view, download, modify, delete).
-- ============================================================
CREATE TABLE IF NOT EXISTS document_access_logs (
    id          BIGSERIAL    PRIMARY KEY,
    document_id BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    ip_address  VARCHAR(45),
    accessed_at TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_access_document
    ON document_access_logs (document_id);
CREATE INDEX IF NOT EXISTS idx_access_user
    ON document_access_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_access_timestamp
    ON document_access_logs (accessed_at DESC);


-- ============================================================
-- evidence_chain_of_custody  (entity: EvidenceChainOfCustody)
-- Tracks all access/modification of CaseEvidence rows.
-- ============================================================
CREATE TABLE IF NOT EXISTS evidence_chain_of_custody (
    id           BIGSERIAL    PRIMARY KEY,
    evidence_id  BIGINT       NOT NULL,
    action       VARCHAR(50)  NOT NULL,
    user_id      BIGINT       NOT NULL,
    notes        TEXT,
    timestamp    TIMESTAMP    NOT NULL,
    CONSTRAINT fk_evidence_chain_evidence
        FOREIGN KEY (evidence_id) REFERENCES case_evidence (id)
);

CREATE INDEX IF NOT EXISTS idx_custody_evidence
    ON evidence_chain_of_custody (evidence_id);
CREATE INDEX IF NOT EXISTS idx_custody_timestamp
    ON evidence_chain_of_custody (timestamp);


-- ============================================================
-- merchant_documents  (entity: MerchantDocument)
-- KYC document store with simple version-chain semantics.
-- merchant_id is a soft reference; merchants(merchant_id) is
-- BIGSERIAL in V2 so the FK is safe but kept implicit to mirror
-- the existing un-FK'd convention used elsewhere on Merchant.
-- ============================================================
CREATE TABLE IF NOT EXISTS merchant_documents (
    document_id          BIGSERIAL    PRIMARY KEY,
    merchant_id          BIGINT       NOT NULL,
    document_type        VARCHAR(255) NOT NULL,
    file_path            VARCHAR(255) NOT NULL,
    file_name            VARCHAR(255) NOT NULL,
    status               VARCHAR(255) NOT NULL,
    expiry_date          DATE,
    uploaded_at          TIMESTAMP,
    verified_at          TIMESTAMP,
    version              INTEGER      NOT NULL DEFAULT 1,
    previous_version_id  BIGINT,
    is_current_version   BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_merchant_documents_merchant_id
    ON merchant_documents (merchant_id);
CREATE INDEX IF NOT EXISTS idx_merchant_documents_status
    ON merchant_documents (status);
CREATE INDEX IF NOT EXISTS idx_merchant_documents_current
    ON merchant_documents (merchant_id, is_current_version)
    WHERE is_current_version = TRUE;


-- ============================================================
-- password_reset_tokens  (entity: PasswordResetToken)
-- Hashed one-time tokens for password reset flows.
-- token_hash is unique (raw token never stored).
-- ============================================================
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id                    BIGSERIAL     PRIMARY KEY,
    token_hash            VARCHAR(64)   NOT NULL UNIQUE,
    user_id               BIGINT        NOT NULL,
    expires_at            TIMESTAMP     NOT NULL,
    created_at            TIMESTAMP     NOT NULL,
    used_at               TIMESTAMP,
    requested_ip          VARCHAR(64),
    requested_user_agent  VARCHAR(512),
    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id) REFERENCES platform_users (id)
);

CREATE INDEX IF NOT EXISTS idx_prt_user_id
    ON password_reset_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_prt_expires_at
    ON password_reset_tokens (expires_at);


-- ============================================================
-- psp_report_configs  (entity: PspReportConfig)
-- Per-PSP outbound report configuration (one row per PSP).
-- ============================================================
CREATE TABLE IF NOT EXISTS psp_report_configs (
    id              BIGSERIAL    PRIMARY KEY,
    psp_id          BIGINT       NOT NULL UNIQUE,
    report_url      VARCHAR(255) NOT NULL,
    allowed_domains VARCHAR(255),
    allowed_ips     VARCHAR(255),
    port            INTEGER,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at      TIMESTAMP,
    CONSTRAINT fk_psp_report_configs_psp
        FOREIGN KEY (psp_id) REFERENCES psps (psp_id)
);


-- ============================================================
-- role_permission_mappings  (entity: RolePermissionMapping)
-- (UserRole, Permission) tuples persisted for granular RBAC.
-- ============================================================
CREATE TABLE IF NOT EXISTS role_permission_mappings (
    id          BIGSERIAL    PRIMARY KEY,
    user_role   VARCHAR(64)  NOT NULL,
    permission  VARCHAR(128) NOT NULL,
    granted_by  VARCHAR(255),
    granted_at  TIMESTAMP,
    notes       TEXT,
    CONSTRAINT uq_role_permission UNIQUE (user_role, permission)
);

CREATE INDEX IF NOT EXISTS idx_role_permission_role
    ON role_permission_mappings (user_role);


-- ============================================================
-- rule_ab_tests  (entity: RuleAbTest)
-- A/B testing harness for rule configuration changes.
-- ============================================================
CREATE TABLE IF NOT EXISTS rule_ab_tests (
    id                    BIGSERIAL    PRIMARY KEY,
    rule_name             VARCHAR(255) NOT NULL,
    variant_a             TEXT         NOT NULL,
    variant_b             TEXT         NOT NULL,
    traffic_split_percent INTEGER      NOT NULL,
    status                VARCHAR(50)  NOT NULL,
    start_date            TIMESTAMP    NOT NULL,
    end_date              TIMESTAMP,
    created_at            TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ab_test_rule
    ON rule_ab_tests (rule_name);
CREATE INDEX IF NOT EXISTS idx_ab_test_status
    ON rule_ab_tests (status);


-- ============================================================
-- rule_ab_test_results  (entity: RuleAbTestResult)
-- Per-evaluation outcomes recorded against a RuleAbTest.
-- ============================================================
CREATE TABLE IF NOT EXISTS rule_ab_test_results (
    id               BIGSERIAL  PRIMARY KEY,
    test_id          BIGINT     NOT NULL,
    variant          VARCHAR(1) NOT NULL,
    is_true_positive BOOLEAN    NOT NULL,
    recorded_at      TIMESTAMP  NOT NULL,
    CONSTRAINT fk_ab_test_result_test
        FOREIGN KEY (test_id) REFERENCES rule_ab_tests (id)
);

CREATE INDEX IF NOT EXISTS idx_ab_result_test
    ON rule_ab_test_results (test_id);
CREATE INDEX IF NOT EXISTS idx_ab_result_variant
    ON rule_ab_test_results (variant);


-- ============================================================
-- webhook_subscriptions  (entity: WebhookSubscription)
-- Outbound webhook configuration per PSP. Note: psp_id is
-- VARCHAR (entity uses String, not Long), so we cannot FK to
-- psps(psp_id) which is BIGINT. Soft reference only.
-- ============================================================
CREATE TABLE IF NOT EXISTS webhook_subscriptions (
    id            BIGSERIAL     PRIMARY KEY,
    psp_id        VARCHAR(255)  NOT NULL,
    callback_url  VARCHAR(1000) NOT NULL,
    event_type    VARCHAR(255)  NOT NULL,
    secret_key    VARCHAR(255),
    is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP,
    failure_count INTEGER       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_webhook_subscriptions_psp
    ON webhook_subscriptions (psp_id);
CREATE INDEX IF NOT EXISTS idx_webhook_subscriptions_event
    ON webhook_subscriptions (event_type);
CREATE INDEX IF NOT EXISTS idx_webhook_subscriptions_active
    ON webhook_subscriptions (is_active)
    WHERE is_active = TRUE;

COMMENT ON COLUMN webhook_subscriptions.psp_id IS 'String PSP identifier (entity-side type); soft reference to psps(psp_code) - not a hard FK because column types differ';

-- ============================================================
-- End of V121
-- ============================================================
