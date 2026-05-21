-- =====================================================================
-- V139: Enhanced Rules Table for System-Owned Default Rules + PSP Duplication
-- =====================================================================
-- Supports:
-- - System-owned default rules (cannot be deleted, only disabled)
-- - PSP duplication of system rules
-- - Efficient lookup for rule engine (via Aerospike sync)
-- =====================================================================

-- Drop old rules table if it exists (safe for new environments)
-- In production you would migrate data instead.

CREATE TABLE IF NOT EXISTS rules (
    id BIGSERIAL PRIMARY KEY,
    rule_code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    rule_type VARCHAR(50) NOT NULL,           -- TRANSACTION, USER, SCREENING, etc.
    typology VARCHAR(100),
    default_action VARCHAR(50),               -- FLAG, SUSPEND, BLOCK, REVIEW
    is_system_rule BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    owner_type VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',  -- SYSTEM or PSP
    psp_id BIGINT,
    parent_rule_id BIGINT,                    -- Reference to original system rule when duplicated
    parameters JSONB,                         -- Configurable values (x, t, y, threshold, etc.)
    checks_for TEXT[],                        -- Array of fields checked (e.g. ['amount', 'country'])
    priority INTEGER DEFAULT 100,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),

    CONSTRAINT uq_rule_code_psp UNIQUE (rule_code, psp_id),
    CONSTRAINT fk_rules_parent FOREIGN KEY (parent_rule_id) REFERENCES rules(id)
);

CREATE INDEX idx_rules_system_active ON rules (is_system_rule, is_active) WHERE is_system_rule = true;
CREATE INDEX idx_rules_psp_active ON rules (psp_id, is_active);
CREATE INDEX idx_rules_parent ON rules (parent_rule_id);

COMMENT ON COLUMN rules.is_system_rule IS 'True for platform default rules that cannot be deleted';
COMMENT ON COLUMN rules.parent_rule_id IS 'Links duplicated PSP rule back to the original system rule';