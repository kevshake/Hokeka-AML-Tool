-- V220__backfill_orphaned_active_versionless_rules.sql
-- Purpose (W18-4): V160's baseline-version backfill (`WHERE created_by IS NOT NULL` in its
--          `snapshots` CTE) skips any rule_definitions row whose created_by can't be resolved to
--          a platform_users id through any of its three fallbacks (direct id match, legacy
--          psp_users email match, or MIN(id) across all platform_users) -- which only happens if
--          platform_users was completely empty at the moment V160 ran. Such rows were left with
--          lifecycle_status='ACTIVE' (set unconditionally by V160's ADD COLUMN ... DEFAULT
--          'ACTIVE') but current_version_id still NULL: "active but versionless", with no
--          rule_versions audit trail backing their active status at all. Never edits V160
--          (already applied) -- retroactively creates the missing baseline version now, using
--          the identical MIN(platform_users.id) fallback V160 itself uses, now that
--          platform_users presumably has at least one row.
-- Tables affected: rule_versions, rule_definitions
-- Audited entities: no
-- Idempotent: only targets rows still lifecycle_status='ACTIVE' AND current_version_id IS NULL;
--             re-running finds nothing left once the first run succeeds. If platform_users is
--             STILL empty when this runs, the INSERT's WHERE clause below again excludes those
--             rows and they remain exactly as before -- no worse off, safe to re-run later.

WITH orphaned AS (
    SELECT r.id AS rule_id, r.psp_id,
           COALESCE(r.created_at, CURRENT_TIMESTAMP) AS created_at,
           (SELECT MIN(id) FROM platform_users) AS fallback_user_id,
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
           ) AS snapshot
    FROM rule_definitions r
    WHERE r.lifecycle_status = 'ACTIVE' AND r.current_version_id IS NULL
), snapshots AS (
    SELECT rule_id, psp_id, created_at, fallback_user_id, snapshot
    FROM orphaned
    WHERE fallback_user_id IS NOT NULL
), inserted AS (
    INSERT INTO rule_versions (
        rule_id, version_number, lifecycle_status, change_type, snapshot,
        content_hash, change_summary, psp_id, created_by, created_at,
        submitted_at, effective_from, reviewed_by, reviewed_at, activated_at
    )
    SELECT rule_id, 1, 'ACTIVE', 'CREATE', snapshot,
           encode(sha256(convert_to(snapshot::text, 'UTF8')), 'hex'),
           'Baseline version backfilled retroactively (V220 -- platform_users was empty when V160 ran)',
           psp_id, fallback_user_id, created_at, created_at, created_at, fallback_user_id, created_at, created_at
    FROM snapshots
    RETURNING id, rule_id
)
UPDATE rule_definitions r
SET current_version_number = 1,
    current_version_id = inserted.id
FROM inserted
WHERE r.id = inserted.rule_id;
