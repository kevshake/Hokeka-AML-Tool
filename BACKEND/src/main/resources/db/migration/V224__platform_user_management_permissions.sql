INSERT INTO role_permissions_dynamic (role_id, permission)
SELECT r.id, p.permission
FROM roles r
CROSS JOIN (VALUES ('MANAGE_USERS'), ('MANAGE_ROLES')) p(permission)
WHERE r.name = 'PLATFORM_ADMIN' AND r.psp_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions_dynamic existing
      WHERE existing.role_id = r.id AND existing.permission = p.permission
  );

DELETE FROM role_permissions_dynamic permission
USING roles role
WHERE permission.role_id = role.id
  AND role.name = 'PSP_ADMIN'
  AND role.psp_id IS NULL
  AND permission.permission = 'MANAGE_USERS';
