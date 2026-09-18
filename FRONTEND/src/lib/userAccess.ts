/** Role helpers aligned with backend SecurityConfig + RoleController scoping. */

export const PLATFORM_ADMIN_ROLES = new Set(["SUPER_ADMIN", "ADMIN", "PLATFORM_ADMIN"]);

export const USER_MANAGEMENT_ROLES = new Set([
  ...PLATFORM_ADMIN_ROLES,
  "PSP_ADMIN",
]);

export const ROLE_MANAGEMENT_ROLES = new Set([
  ...PLATFORM_ADMIN_ROLES,
  "PSP_ADMIN",
]);

export function normalizeRole(role?: string | null): string {
  return (role ?? "").replaceAll(" ", "_").toUpperCase();
}

type UserAccessContext = {
  role?: { name?: string; permissions?: string[] };
  pspId?: number;
  psp?: { pspCode?: string; code?: string } | null;
} | null | undefined;

export function isPlatformAdmin(user: UserAccessContext): boolean {
  if (!user) return false;
  const role = normalizeRole(user.role?.name);
  if (role === "APP_CONTROLLER") return true;
  if (!PLATFORM_ADMIN_ROLES.has(role)) return false;
  const pspId = user.pspId ?? 0;
  const pspCode = user.psp?.pspCode ?? user.psp?.code ?? "";
  return pspId === 0 || pspCode === "HOKEKA_PLATFORM";
}

export function isPspAdmin(
  user: { role?: { name?: string } } | null | undefined,
): boolean {
  return normalizeRole(user?.role?.name) === "PSP_ADMIN";
}

export function canManageUsers(user: UserAccessContext): boolean {
  if (!user) return false;
  if (user.role?.permissions?.includes("MANAGE_USERS")) return true;
  return USER_MANAGEMENT_ROLES.has(normalizeRole(user.role?.name));
}

export function canManageRoles(
  user: { role?: { name?: string } } | null | undefined,
): boolean {
  if (!user) return false;
  return ROLE_MANAGEMENT_ROLES.has(normalizeRole(user.role?.name));
}

/** PSP admins are locked to their tenant; platform admins may pick any PSP. */
export function lockedPspId(user: UserAccessContext): number | null {
  if (!user || isPlatformAdmin(user)) return null;
  const id = user.pspId ?? 0;
  return id > 0 ? id : null;
}

export function filterPspsForUser<T extends { id?: number; pspId?: number }>(
  user: { pspId?: number } | null | undefined,
  psps: T[] | undefined,
): T[] {
  if (!psps?.length) return [];
  const tenantId = lockedPspId(user);
  if (tenantId == null) return psps;
  return psps.filter((psp) => {
    const id = Number(psp.pspId ?? psp.id ?? 0);
    return id === tenantId;
  });
}

export function pspOptionId(psp: { id?: number; pspId?: number }): number {
  return Number(psp.pspId ?? psp.id ?? 0);
}

export function pspOptionLabel(psp: {
  legalName?: string;
  tradingName?: string;
  name?: string;
  pspCode?: string;
  code?: string;
  id?: number;
  pspId?: number;
}): string {
  const id = pspOptionId(psp);
  return (
    psp.legalName ||
    psp.tradingName ||
    psp.name ||
    psp.pspCode ||
    psp.code ||
    `PSP #${id}`
  );
}
