-- ============================================================================
-- V257: PSP operators (PSP_USER) lose SAR visibility (tipping-off risk).
--
-- Suspicious-activity reports may only be seen by compliance roles (MLRO /
-- COMPLIANCE_OFFICER and platform administrators). V127 granted the per-PSP
-- PSP_USER roles VIEW_SAR, and /compliance/sar admitted PSP_USER/PSP_ADMIN at
-- the HTTP layer; ComplianceReportingController is now restricted to
-- compliance roles, and this migration removes the SAR permissions from every
-- PSP_USER role so the Console no longer offers the SAR screens to them.
-- ============================================================================
DELETE FROM role_permissions_dynamic
WHERE permission IN ('VIEW_SAR', 'CREATE_SAR', 'APPROVE_SAR', 'FILE_SAR', 'REJECT_SAR', 'AMEND_SAR')
  AND role_id IN (SELECT id FROM roles WHERE name = 'PSP_USER');
