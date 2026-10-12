import { templatePermissionsForRole } from "../lib/rolePermissionTemplates";
import type { ConsoleTestRole } from "../lib/consoleRbac";

/** Builds a dev mock session user for screenshot and Playwright role matrices. */
export function buildMockUserForRole(role: ConsoleTestRole) {
  const permissions = templatePermissionsForRole(role).map((p) => p as string);
  const tenantRoles = new Set([
    "PSP_ADMIN",
    "PSP_USER",
    "COMPLIANCE_OFFICER",
    "MLRO",
    "SENIOR_ANALYST",
    "INVESTIGATOR",
    "SCREENING_ANALYST",
    "AUDITOR",
  ]);
  const pspId = tenantRoles.has(role) ? 2 : 0;
  return {
    id: 100,
    username: `${role.toLowerCase()}.demo`,
    email: `${role.toLowerCase()}@demo.hokeka.com`,
    firstName: role.replace(/_/g, " "),
    lastName: "Demo",
    role: {
      id: 1,
      name: role,
      permissions: role === "SUPER_ADMIN" ? ["*"] : permissions,
    },
    pspId,
    psp: pspId
      ? { pspId: 2, code: "DEMO_VELOCITY", name: "Velocity Pay", id: 2 }
      : null,
    enabled: true,
    createdAt: new Date().toISOString(),
  };
}

/** Lists roles available to dev mock via sessionStorage devMockUserRole. */
export const DEV_MOCK_ROLES: ConsoleTestRole[] = [
  "SUPER_ADMIN",
  "PLATFORM_ADMIN",
  "PSP_ADMIN",
  "COMPLIANCE_OFFICER",
  "MLRO",
  "SENIOR_ANALYST",
  "INVESTIGATOR",
  "SCREENING_ANALYST",
  "AUDITOR",
  "PSP_USER",
];
