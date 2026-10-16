-- V265__drop_psp_edge_inline_ai_settings.sql
-- Purpose: work package R10c of docs/architecture/EDGE_DATA_RESIDENCY_PLAN.md.
--
-- psps.ai_inline_mode and psps.ai_inline_budget_ms (V233, comments V235) told an Edge Node to wait,
-- up to the budget, for a Hokeka AI recommendation on a borderline pre-auth decision. That
-- recommendation came from POST /api/v1/edge/decision, which received the Edge's full feature bag
-- (pan_hash, amount, merchant, ...): class-T transaction data leaving the Edge, forbidden by
-- docs/EDGE_DATA_RESIDENCY_CONTRACT.md §2 and removed in R10c together with its Edge client. With
-- the endpoint gone nothing reads either column, so they are dropped rather than left as settings
-- that look live but do nothing. The PSP-facing AI shadow advisory (ai_engine_settings,
-- advisory_only) is unaffected.
--
-- ai_decision_audit.edge_id is deliberately KEPT: it is no longer written (the Edge no longer asks
-- for AI advisory, so no AI decision has an Edge caller), but existing rows are audit history and
-- audit history is never deleted by a migration. The column stays nullable and unmapped.

ALTER TABLE psps DROP COLUMN IF EXISTS ai_inline_mode;
ALTER TABLE psps DROP COLUMN IF EXISTS ai_inline_budget_ms;
