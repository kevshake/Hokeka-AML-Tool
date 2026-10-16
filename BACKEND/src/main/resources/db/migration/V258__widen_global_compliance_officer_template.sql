-- V258__widen_global_compliance_officer_template.sql
-- Purpose: the global (psp_id IS NULL) COMPLIANCE_OFFICER template was seeded by
--          RoleService.initDefaultRoles() with only VIEW_CASES, VIEW_SCREENING_RESULTS,
--          ASSIGN_CASES, FILE_SAR, ADD_CASE_EVIDENCE, ESCALATE_CASES and MANAGE_WATCHLISTS.
--          A PSP registered after the V127 demo seed has no per-PSP COMPLIANCE_OFFICER row, so
--          its compliance officers resolve to this template and could neither create a case
--          (CaseWorkflowService: CREATE_CASES), nor create/see a SAR (SarWorkflowController:
--          CREATE_SAR; /exports/sars.csv: VIEW_SAR) -- found by the PSP simulator (L2/L3).
--          Widens the template to the per-PSP COMPLIANCE_OFFICER definition of V127 ("owns SAR
--          workflow and case decisions"). SAR maker/checker (maker cannot approve own SAR) is
--          enforced in SarWorkflowService independently of APPROVE_SAR.
-- Tables affected: role_permissions_dynamic
-- Audited entities: no
-- Idempotent: guarded by NOT EXISTS per permission.

INSERT INTO role_permissions_dynamic (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (VALUES
    ('VIEW_CASES'),('CREATE_CASES'),('ASSIGN_CASES'),('CLOSE_CASES'),
    ('ESCALATE_CASES'),('REOPEN_CASES'),('ADD_CASE_NOTES'),('ADD_CASE_EVIDENCE'),
    ('VIEW_SAR'),('CREATE_SAR'),('APPROVE_SAR'),('FILE_SAR'),('AMEND_SAR'),
    ('VIEW_PII'),('VIEW_TRANSACTION_DETAILS'),('VIEW_SCREENING_RESULTS'),
    ('MANAGE_WATCHLISTS'),('WHITELIST_ENTITY'),('MERCHANT_VIEW'),('REPORT_VIEW')
) p(permission)
WHERE r.name = 'COMPLIANCE_OFFICER' AND r.psp_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions_dynamic rpd
      WHERE rpd.role_id = r.id AND rpd.permission = p.permission
  );
