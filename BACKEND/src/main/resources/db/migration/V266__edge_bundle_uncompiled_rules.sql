-- V266 (R10a): rules a published Edge bundle could not carry, with the reason.
--
-- EdgeRuleCompiler now compiles the SpEL typology catalog to Edge Rule IR v2. A rule that
-- genuinely cannot run on the Edge (screening owned by R10b, cloud-only dispute data, merchant
-- master data the Edge does not hold, ...) is listed in the bundle's "uncompiled" section and
-- here, [{ruleId, name, reason}], so the Console and operators can see which cloud detections an
-- Edge does not perform. Rule names and reasons only: no transaction data.
ALTER TABLE edge_bundles
    ADD COLUMN IF NOT EXISTS uncompiled_rules JSONB NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN edge_bundles.uncompiled_rules IS
    'Rules not compiled to Edge IR for this bundle: [{ruleId, name, reason}] (R10a)';
