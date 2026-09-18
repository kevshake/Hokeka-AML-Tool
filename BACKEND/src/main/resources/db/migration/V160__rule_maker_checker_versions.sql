ALTER TABLE rule_definitions
    ADD COLUMN lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN current_version_number INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN current_version_id BIGINT,
    ADD COLUMN pending_version_id BIGINT;

CREATE TABLE rule_versions (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES rule_definitions(id),
    version_number INTEGER NOT NULL,
    lifecycle_status VARCHAR(32) NOT NULL,
    change_type VARCHAR(24) NOT NULL,
    snapshot JSONB NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    change_summary TEXT NOT NULL,
    psp_id BIGINT REFERENCES psps(psp_id),
    created_by BIGINT NOT NULL REFERENCES platform_users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_from TIMESTAMP,
    reviewed_by BIGINT REFERENCES platform_users(id),
    reviewed_at TIMESTAMP,
    review_reason TEXT,
    activated_at TIMESTAMP,
    supersedes_version_id BIGINT REFERENCES rule_versions(id),
    CONSTRAINT uq_rule_version_number UNIQUE (rule_id, version_number),
    CONSTRAINT ck_rule_version_status CHECK (lifecycle_status IN (
        'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'ACTIVE', 'REJECTED', 'SUPERSEDED', 'RETIRED')),
    CONSTRAINT ck_rule_change_type CHECK (change_type IN (
        'CREATE', 'UPDATE', 'ENABLE', 'DISABLE', 'RETIRE', 'ROLLBACK'))
);

CREATE INDEX idx_rule_versions_pending
    ON rule_versions(lifecycle_status, effective_from, psp_id);
CREATE INDEX idx_rule_versions_rule
    ON rule_versions(rule_id, version_number DESC);

WITH snapshots AS (
    SELECT r.id AS rule_id,
           jsonb_build_object(
               'name', r.name,
               'description', r.description,
               'ruleJson', r.rule_json,
               'drlContent', r.drl_content,
               'ruleType', r.rule_type,
               'ruleExpression', r.rule_expression,
               'score', r.score_impact,
               'action', r.action_type,
               'priority', r.priority,
               'enabled', r.enabled,
               'systemManaged', r.is_system_managed,
               'category', r.category,
               'ruleSubtype', r.rule_subtype,
               'appliesTo', r.applies_to,
               'typology', r.typology,
               'checksFor', r.checks_for,
               'externalCode', r.external_code,
               'recommended', r.recommended,
               'sampleUseCase', r.sample_use_case,
               'parameters', r.parameters
           ) AS snapshot,
           r.psp_id,
           COALESCE(
               (SELECT u.id FROM platform_users u WHERE u.id = r.created_by),
               (SELECT u.id
                FROM platform_users u
                JOIN psp_users legacy_user ON lower(legacy_user.email) = lower(u.email)
                WHERE legacy_user.user_id = r.created_by),
               (SELECT MIN(id) FROM platform_users)
           ) AS created_by,
           COALESCE(r.created_at, CURRENT_TIMESTAMP) AS created_at
    FROM rule_definitions r
), inserted AS (
    INSERT INTO rule_versions (
        rule_id, version_number, lifecycle_status, change_type, snapshot,
        content_hash, change_summary, psp_id, created_by, created_at,
        submitted_at, effective_from, reviewed_by, reviewed_at, activated_at
    )
    SELECT rule_id, 1, 'ACTIVE', 'CREATE', snapshot,
           encode(sha256(convert_to(snapshot::text, 'UTF8')), 'hex'),
           'Baseline migrated active rule', psp_id,
           created_by, created_at, created_at, created_at, created_by, created_at, created_at
    FROM snapshots
    WHERE created_by IS NOT NULL
    RETURNING id, rule_id
)
UPDATE rule_definitions r
SET current_version_number = 1,
    current_version_id = inserted.id,
    lifecycle_status = 'ACTIVE'
FROM inserted
WHERE r.id = inserted.rule_id;

ALTER TABLE rule_definitions
    ADD CONSTRAINT fk_rule_current_version FOREIGN KEY (current_version_id) REFERENCES rule_versions(id),
    ADD CONSTRAINT fk_rule_pending_version FOREIGN KEY (pending_version_id) REFERENCES rule_versions(id);

ALTER TABLE rule_definitions_aud
    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS current_version_number INTEGER,
    ADD COLUMN IF NOT EXISTS current_version_id BIGINT,
    ADD COLUMN IF NOT EXISTS pending_version_id BIGINT;
