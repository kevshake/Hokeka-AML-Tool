-- ============================================================
-- Rule Definitions Table
-- Migration: V116__rule_definitions_table.sql
-- Description: Source-of-truth table for AML rule definitions
--              consumed by the Rules Microservice (HOK-77) and
--              the existing AML monolith.
-- ADR: docs/architecture/ADR/ADR-001-Transaction-Rules.md
-- ============================================================

CREATE TABLE IF NOT EXISTS rule_definitions (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(255) NOT NULL UNIQUE,
    description       VARCHAR(255),
    rule_json         TEXT,
    drl_content       TEXT,
    rule_type         VARCHAR(50),
    rule_expression   TEXT,
    score_impact      INTEGER,
    action_type       VARCHAR(50),
    priority          INTEGER,
    enabled           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    psp_id            BIGINT,
    created_by        BIGINT,
    updated_by        BIGINT
);

-- Hot-path index: GET /v1/rules/active (enabled, priority ASC)
CREATE INDEX IF NOT EXISTS idx_rule_definitions_enabled_priority
    ON rule_definitions (enabled, priority)
    WHERE enabled = TRUE;

-- Watcher index: SELECT max(updated_at) FROM rule_definitions
CREATE INDEX IF NOT EXISTS idx_rule_definitions_updated_at
    ON rule_definitions (updated_at);

-- PSP isolation lookups
CREATE INDEX IF NOT EXISTS idx_rule_definitions_psp_id
    ON rule_definitions (psp_id);

-- Rule type filtering
CREATE INDEX IF NOT EXISTS idx_rule_definitions_rule_type
    ON rule_definitions (rule_type);

COMMENT ON TABLE  rule_definitions                    IS 'AML rule definitions (source of truth for Rules Microservice and monolith); cached in Aerospike namespace `aml`, set `rules`';
COMMENT ON COLUMN rule_definitions.name               IS 'Globally unique rule identifier';
COMMENT ON COLUMN rule_definitions.rule_json          IS 'Structured rule definition (conditions/actions) in JSON';
COMMENT ON COLUMN rule_definitions.drl_content        IS 'Compiled DRL (Drools) content when rule_type=DROOLS_DRL';
COMMENT ON COLUMN rule_definitions.rule_type          IS 'Engine flavour: SPEL | DROOLS_DRL | JAVA_BEAN';
COMMENT ON COLUMN rule_definitions.rule_expression    IS 'Raw expression body authored by the rule designer';
COMMENT ON COLUMN rule_definitions.score_impact       IS 'Score delta applied when this rule fires (positive=risk, negative=trust)';
COMMENT ON COLUMN rule_definitions.action_type        IS 'BLOCK | HOLD | ALERT | ALLOW';
COMMENT ON COLUMN rule_definitions.priority           IS 'Lower number = higher priority during evaluation';
COMMENT ON COLUMN rule_definitions.psp_id             IS 'NULL for super-admin/global rules; set for PSP-specific rules (PSP isolation)';

-- Note on Envers (`rule_definitions_aud`):
-- The RuleDefinition entity is @Audited; Hibernate Envers manages the _aud
-- mirror table under the project's existing ddl-auto=update convention
-- (see V100 fix-up pattern). When ddl-auto is moved to `none`, an explicit
-- `rule_definitions_aud` migration must be authored to mirror this schema
-- plus the standard Envers columns (rev BIGINT, revtype SMALLINT).
