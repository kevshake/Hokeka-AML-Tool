-- Neutral naming for Hokeka AI (Laya) persistence — rename legacy jev_* / openrouter_* identifiers.

ALTER TABLE IF EXISTS jev_decision_audit RENAME TO ai_decision_audit;
ALTER TABLE IF EXISTS jev_engine_settings RENAME TO ai_engine_settings;
ALTER TABLE IF EXISTS jev_daily_spend RENAME TO ai_daily_spend;
ALTER TABLE IF EXISTS jev_bands RENAME TO ai_bands;

ALTER INDEX IF EXISTS idx_jev_audit_psp_created RENAME TO idx_ai_audit_psp_created;
ALTER INDEX IF EXISTS idx_jev_audit_engine_created RENAME TO idx_ai_audit_engine_created;
ALTER INDEX IF EXISTS idx_jev_audit_transaction RENAME TO idx_ai_audit_transaction;
ALTER INDEX IF EXISTS idx_jev_audit_alert RENAME TO idx_ai_audit_alert;
ALTER INDEX IF EXISTS idx_jev_audit_case RENAME TO idx_ai_audit_case;
ALTER INDEX IF EXISTS idx_jev_audit_branch_created RENAME TO idx_ai_audit_branch_created;

ALTER TABLE ai_decision_audit
    RENAME COLUMN openrouter_request_id TO provider_request_id;

COMMENT ON TABLE ai_bands IS 'Per-PSP provisional AI band thresholds; replace with labeled-sample tuning.';
COMMENT ON COLUMN ai_decision_audit.shadow_mode IS 'When true, AI output was logged only and did not mutate decisions.';
COMMENT ON COLUMN psps.ai_inline_mode IS 'When true, edge waits for Hokeka AI verdict up to ai_inline_budget_ms on borderline pre-auth decisions';
COMMENT ON COLUMN psps.ai_inline_budget_ms IS 'Max milliseconds edge waits for inline Hokeka AI verdict (default 500ms)';
