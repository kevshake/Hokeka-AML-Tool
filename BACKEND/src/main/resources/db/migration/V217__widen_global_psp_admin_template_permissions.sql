-- V217__widen_global_psp_admin_template_permissions.sql
-- Purpose (W19-5): RoleService.initDefaultRoles() only creates the global (psp_id IS NULL)
--          PSP_ADMIN template role if it doesn't already exist -- it never updates an
--          already-persisted row's permissions. In any environment where this app has already
--          run, that role was created with the old narrow permission set (VIEW_CASES,
--          VIEW_TRANSACTION_DETAILS, VIEW_SCREENING_RESULTS, VIEW_SAR, MANAGE_PSP_THEME -- no
--          MANAGE_USERS, no MANAGE_RULES). Every PSP registered outside the one-time V127 demo
--          seed has no per-PSP PSP_ADMIN row of its own, so createPspUser's role lookup
--          (findByNameAndPsp -> falls back to findByNameAndPspIsNull) resolves to this
--          under-permissioned global template -- a real PSP's "administrator" could not manage
--          their own PSP's users or rules. Widens the existing row to match V127's richer
--          "full control within their PSP" definition (the RoleService Java-side fix already
--          covers brand-new deployments where the role doesn't exist yet; this covers existing
--          already-provisioned databases).
-- Tables affected: role_permissions_dynamic
-- Audited entities: no
-- Idempotent: guarded by NOT EXISTS per permission, like every other role_permissions_dynamic seed.

INSERT INTO role_permissions_dynamic (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (VALUES
    ('VIEW_CASES'),('CREATE_CASES'),('ASSIGN_CASES'),('CLOSE_CASES'),
    ('ESCALATE_CASES'),('REOPEN_CASES'),('ADD_CASE_NOTES'),('ADD_CASE_EVIDENCE'),
    ('VIEW_SAR'),('CREATE_SAR'),('APPROVE_SAR'),('FILE_SAR'),('AMEND_SAR'),
    ('VIEW_PII'),('EXPORT_DATA'),
    ('VIEW_TRANSACTION_DETAILS'),('VIEW_SCREENING_RESULTS'),
    ('MANAGE_WATCHLISTS'),('WHITELIST_ENTITY'),
    ('MANAGE_USERS'),('MANAGE_RULES'),('MANAGE_PSP_THEME'),
    ('PSP_SETTINGS_VIEW'),('PSP_SETTINGS_EDIT'),('PSP_UI_EDIT'),
    ('MERCHANT_VIEW'),('MERCHANT_EDIT'),('REPORT_VIEW')
) p(permission)
WHERE r.name = 'PSP_ADMIN' AND r.psp_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions_dynamic rpd
      WHERE rpd.role_id = r.id AND rpd.permission = p.permission
  );
