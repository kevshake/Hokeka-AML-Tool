-- ============================================================
-- V18__missing_tables_bridge.sql
-- Bridge migration: creates all tables that V100+ migrations
-- assume to exist but that V1-V17 never created.
--
-- Background
-- ----------
-- V18-V99 are absent from this repository. The V100+ migrations
-- were authored against a database that had already run those
-- missing files. On a fresh database (V1-V17 only) several
-- V100+ migrations fail because they ALTER or INSERT INTO tables
-- that do not yet exist.
--
-- This bridge creates those tables so that V100+ can run
-- without modification. Every statement uses IF NOT EXISTS /
-- ADD COLUMN IF NOT EXISTS so it is safe to re-run and does
-- not conflict with any legacy database that already has the
-- tables (e.g. from Hibernate ddl-auto=update).
--
-- Tables created here (grouped by the first V100+ migration
-- that references them):
--
--  compliance_deadlines          -- ALTER'd by V118
--  compliance_deadlines_aud      -- ALTER'd by V118
--  role_permissions_dynamic      -- INSERT'd by V127, V128
--
-- Additional columns on existing tables that V100+ migrations
-- expect to exist on a fresh DB are added here via
-- ADD COLUMN IF NOT EXISTS:
--
--  compliance_cases.case_reference  (expected by V120 _aud DDL / V112)
--  compliance_cases.status          (expected by V110 conditional index)
--  transactions.psp_id              (expected by V110 conditional index)
--  merchants.psp_id                 (expected by V110 conditional index)
--  merchants.risk_level             (expected by V110 conditional index)
--  transactions.ip_address          (expected by V135 partial index)
--  transactions.device_fingerprint  (expected by V135 partial index)
--  transactions.direction           (expected by V135 composite index)
--  transactions.merchant_country    (expected by V135 composite index)
--  transactions.psp_id              (expected by multiple V100+ migrations)
--
-- All column additions are guarded by IF NOT EXISTS so they are
-- safe against any partial state left by ddl-auto=update.
-- ============================================================

-- ============================================================
-- 1. compliance_deadlines  (entity: ComplianceDeadline, @Audited)
--
-- V118 does:
--   ALTER TABLE compliance_deadlines ADD COLUMN IF NOT EXISTS psp_id BIGINT NULL;
--   ALTER TABLE compliance_deadlines_aud ADD COLUMN IF NOT EXISTS psp_id BIGINT NULL;
-- Both tables must therefore exist before V118 runs.
--
-- V121 also creates this table (CREATE TABLE IF NOT EXISTS) so
-- the definition below is the canonical schema. The V121
-- statement is a no-op once we create it here.
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

COMMENT ON TABLE compliance_deadlines IS
    'Regulatory compliance deadlines per PSP (ComplianceDeadline entity; @Audited)';

-- ============================================================
-- 2. compliance_deadlines_aud  (Envers audit mirror)
--
-- V118 does ALTER TABLE compliance_deadlines_aud ADD COLUMN IF NOT EXISTS psp_id ...
-- so the audit table must exist before V118 runs.
--
-- V120 also creates it (CREATE TABLE IF NOT EXISTS), referencing
-- revinfo(rev). revinfo is created by V120 itself, so the FK on
-- the aud table below is omitted here (revinfo does not exist yet
-- at V18 time). V120 will either create the table fresh (no-op if
-- already here) or add the FK when it runs.
--
-- Note: we deliberately omit the FK constraint to revinfo here
-- because revinfo does not exist at V18 execution time.
-- V120 uses CREATE TABLE IF NOT EXISTS for compliance_deadlines_aud
-- too, so the FK is handled there if the table is newly created;
-- if the table already exists from this migration, V120's
-- CREATE TABLE IF NOT EXISTS is a no-op and the FK is never added —
-- which is acceptable because compliance_deadlines_aud is an
-- append-only audit table that Hibernate Envers manages directly.
-- ============================================================
CREATE TABLE IF NOT EXISTS compliance_deadlines_aud (
    id             BIGINT       NOT NULL,
    rev            INTEGER      NOT NULL,
    revtype        SMALLINT,
    deadline_type  VARCHAR(255),
    deadline_date  TIMESTAMP,
    description    TEXT,
    jurisdiction   VARCHAR(255),
    psp_id         BIGINT,
    completed      BOOLEAN,
    completed_at   TIMESTAMP,
    created_at     TIMESTAMP,
    PRIMARY KEY (id, rev)
);

CREATE INDEX IF NOT EXISTS idx_compliance_deadlines_aud_rev
    ON compliance_deadlines_aud (rev);

CREATE INDEX IF NOT EXISTS idx_compliance_deadlines_aud_id
    ON compliance_deadlines_aud (id);

COMMENT ON TABLE compliance_deadlines_aud IS
    'Hibernate Envers audit mirror for compliance_deadlines';

-- ============================================================
-- 3. role_permissions_dynamic
--
-- This is the @ElementCollection backing table for Role.permissions
-- (Set<Permission>) declared via:
--   @CollectionTable(name = "role_permissions_dynamic",
--                   joinColumns = @JoinColumn(name = "role_id"))
--   @Column(name = "permission")
--
-- V127 and V128 INSERT into this table. The roles table it
-- references is created by V14.
-- ============================================================
CREATE TABLE IF NOT EXISTS role_permissions_dynamic (
    role_id    INTEGER     NOT NULL,
    permission VARCHAR(128) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

CREATE INDEX IF NOT EXISTS idx_role_permissions_dynamic_role_id
    ON role_permissions_dynamic (role_id);

COMMENT ON TABLE role_permissions_dynamic IS
    'ElementCollection backing table for Role.permissions (Set<Permission>); '
    'one row per (role, permission) tuple';

-- ============================================================
-- 4. Additional columns on compliance_cases
--
-- The ComplianceCase entity uses different column names from
-- those created in V2 (V2 used Hibernate-auto style). Subsequent
-- migrations and the Envers _aud table (V120) reference specific
-- column names that may not exist on a V1-V17-only schema.
-- Adding them here ensures V100+ ALTER / DDL is idempotent.
-- ============================================================

-- case_reference: TEXT unique identifier (e.g. CASE-2023-0001)
-- Added by V112 as ADD COLUMN IF NOT EXISTS, but V120 _aud schema
-- expects it to be present already.
ALTER TABLE compliance_cases ADD COLUMN IF NOT EXISTS case_reference TEXT;

-- status: CaseStatus enum column (entity uses 'status'; V2 used 'case_status')
-- V110 conditionally creates an index on compliance_cases(psp_id, status)
ALTER TABLE compliance_cases ADD COLUMN IF NOT EXISTS status VARCHAR(32);

-- sla_deadline, days_open: SLA tracking fields expected by entity
ALTER TABLE compliance_cases ADD COLUMN IF NOT EXISTS sla_deadline TIMESTAMP;
ALTER TABLE compliance_cases ADD COLUMN IF NOT EXISTS days_open INTEGER;

-- assigned_to_user_id, assigned_by_user_id: assignment tracking
ALTER TABLE compliance_cases ADD COLUMN IF NOT EXISTS assigned_to_user_id BIGINT;
ALTER TABLE compliance_cases ADD COLUMN IF NOT EXISTS assigned_by_user_id BIGINT;
ALTER TABLE compliance_cases ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMP;

-- escalation tracking
ALTER TABLE compliance_cases ADD COLUMN IF NOT EXISTS escalated BOOLEAN DEFAULT FALSE;
ALTER TABLE compliance_cases ADD COLUMN IF NOT EXISTS escalated_to_user_id BIGINT;
ALTER TABLE compliance_cases ADD COLUMN IF NOT EXISTS escalation_reason VARCHAR(1024);
ALTER TABLE compliance_cases ADD COLUMN IF NOT EXISTS escalated_at TIMESTAMP;

-- resolution fields aligned to entity
ALTER TABLE compliance_cases ADD COLUMN IF NOT EXISTS resolution VARCHAR(255);
ALTER TABLE compliance_cases ADD COLUMN IF NOT EXISTS resolution_notes TEXT;

-- archive fields
ALTER TABLE compliance_cases ADD COLUMN IF NOT EXISTS archived BOOLEAN DEFAULT FALSE;
ALTER TABLE compliance_cases ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP;
ALTER TABLE compliance_cases ADD COLUMN IF NOT EXISTS archive_reference VARCHAR(1024);

-- updated_at (entity expects this)
ALTER TABLE compliance_cases ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- ============================================================
-- 5. Additional columns on transactions required by V100+ migrations
--
-- TransactionEntity declares these columns; several V100+
-- migrations create conditional indexes that reference them.
-- Without them, the conditional-index DDL in V110/V130/V135
-- fails if the columns don't exist.
-- ============================================================

-- psp_id: multi-tenancy column (referenced by V110, V130, V135)
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS psp_id BIGINT;

-- ip_address: per-transaction IP (referenced by V135 partial index)
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS ip_address VARCHAR(45);

-- device_fingerprint: device velocity (referenced by V135 partial index)
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS device_fingerprint VARCHAR(255);

-- direction: IN/OUT (referenced by V135 composite index)
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS direction VARCHAR(10);

-- merchant_country: ISO 3166-1 alpha-3 (referenced by V135 composite index)
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS merchant_country VARCHAR(3);

-- ============================================================
-- 6. Additional columns on merchants required by V100+ migrations
--
-- V110 creates conditional indexes on merchants(psp_id, status)
-- and merchants(psp_id, risk_level). psp_id is set via a
-- @ManyToOne @JoinColumn in the entity; risk_level is a
-- denormalised field. Both must exist for V110 to run.
-- ============================================================

-- psp_id: FK to psps (soft join; entity uses @ManyToOne @JoinColumn)
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS psp_id BIGINT;

-- risk_level: denormalised risk classification (LOW/MEDIUM/HIGH/CRITICAL)
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS risk_level VARCHAR(20);

-- kyc_status, contract_status: referenced by entity / V135 comment
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS kyc_status VARCHAR(50);
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS contract_status VARCHAR(50);

-- ============================================================
-- 7. Indexes for the new columns added above (safe to add now
--    before V110 runs its conditional-index logic)
-- ============================================================

-- compliance_cases new columns
CREATE INDEX IF NOT EXISTS idx_case_reference ON compliance_cases (case_reference);
CREATE INDEX IF NOT EXISTS idx_case_status ON compliance_cases (status);
CREATE INDEX IF NOT EXISTS idx_case_updated_at ON compliance_cases (updated_at);

-- transactions new columns
CREATE INDEX IF NOT EXISTS idx_txn_psp_id ON transactions (psp_id);

-- merchants new columns
CREATE INDEX IF NOT EXISTS idx_merchants_psp_id ON merchants (psp_id);
CREATE INDEX IF NOT EXISTS idx_merchants_risk_level ON merchants (risk_level);

-- ============================================================
-- End of V18__missing_tables_bridge.sql
-- ============================================================
