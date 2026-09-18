-- Backfill: give every existing PSP an editable copy of each system-default rule it does not
-- already have a copy of. New PSPs get this at onboarding (PspService.registerPsp); this covers
-- PSPs that existed before per-PSP rule copies were introduced.
--
-- Copies: same expression/parameters/action as the default, psp_id set, is_system_managed = FALSE
-- (editable), external_code NULL (the code is a global catalog reference), and derived_from_rule_id
-- pointing back at the default (so the copy is undeletable). Idempotent via the NOT EXISTS guard.
INSERT INTO rule_definitions (
    name, description, rule_type, rule_expression, score_impact, action_type, priority, enabled,
    created_at, updated_at, psp_id, created_by, is_system_managed, category, rule_subtype,
    applies_to, typology, checks_for, external_code, recommended, sample_use_case, parameters,
    derived_from_rule_id
)
SELECT
    d.name, d.description, d.rule_type, d.rule_expression, d.score_impact, d.action_type, d.priority,
    d.enabled, now(), now(), p.psp_id, d.created_by, FALSE, d.category, d.rule_subtype,
    d.applies_to, d.typology, d.checks_for, NULL, d.recommended, d.sample_use_case, d.parameters,
    d.id
FROM rule_definitions d
CROSS JOIN psps p
WHERE d.is_system_managed = TRUE
  AND NOT EXISTS (
      SELECT 1 FROM rule_definitions c
      WHERE c.psp_id = p.psp_id AND c.derived_from_rule_id = d.id
  );
