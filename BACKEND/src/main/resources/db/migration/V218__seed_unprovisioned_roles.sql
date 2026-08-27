-- V218__seed_unprovisioned_roles.sql
-- Purpose (W19-2): SCREENING_ANALYST and PSP_ANALYST are documented, real values in the
--          UserRole Java enum and are referenced across 17+ @PreAuthorize annotations and
--          permission checks throughout the codebase, but no Role row with either name was ever
--          seeded by any prior migration -- every one of those branches was permanently dead,
--          since role assignment only offers roles that actually exist in the roles table.
--          APP_CONTROLLER is a machine/service-account role referenced by raw role-name string
--          comparison (GrafanaUserContextController, PspReportingConfigService,
--          PspIsolationService), also never seeded. RoleService.initDefaultRoles() now creates
--          all three going forward (covers brand-new deployments); this covers existing
--          already-provisioned databases where that @PostConstruct already ran without them.
-- Tables affected: roles, role_permissions_dynamic
-- Audited entities: no
-- Idempotent: guarded by NOT EXISTS, same pattern as every other role/permission seed.

INSERT INTO roles (name, description, psp_id)
SELECT 'SCREENING_ANALYST', 'Sanctions Screening Specialist', NULL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'SCREENING_ANALYST' AND psp_id IS NULL);

INSERT INTO roles (name, description, psp_id)
SELECT 'PSP_ANALYST', 'PSP Case Analyst', NULL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'PSP_ANALYST' AND psp_id IS NULL);

INSERT INTO roles (name, description, psp_id)
SELECT 'APP_CONTROLLER', 'Application Service Account', NULL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'APP_CONTROLLER' AND psp_id IS NULL);

INSERT INTO role_permissions_dynamic (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (VALUES
    ('VIEW_CASES'),('VIEW_SAR'),('VIEW_SCREENING_RESULTS'),
    ('MANAGE_WATCHLISTS'),('WHITELIST_ENTITY'),('OVERRIDE_SCREENING_MATCH'),
    ('VIEW_TRANSACTION_DETAILS'),('MERCHANT_VIEW')
) p(permission)
WHERE r.name = 'SCREENING_ANALYST' AND r.psp_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions_dynamic rpd
      WHERE rpd.role_id = r.id AND rpd.permission = p.permission
  );

INSERT INTO role_permissions_dynamic (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (VALUES
    ('VIEW_CASES'),('ASSIGN_CASES'),('ADD_CASE_NOTES'),('ADD_CASE_EVIDENCE'),
    ('VIEW_SAR'),('VIEW_TRANSACTION_DETAILS'),('VIEW_SCREENING_RESULTS'),
    ('MERCHANT_VIEW'),('REPORT_VIEW')
) p(permission)
WHERE r.name = 'PSP_ANALYST' AND r.psp_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions_dynamic rpd
      WHERE rpd.role_id = r.id AND rpd.permission = p.permission
  );

-- APP_CONTROLLER deliberately gets no Permission grants -- see RoleService.initDefaultRoles.
