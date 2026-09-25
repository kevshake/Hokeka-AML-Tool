interface JevRoleUser {
  role?: { name?: string; permissions?: string[] };
}

/** Roles allowed to view JEV advisory verdicts in the Console. */
const JEV_VERDICT_ROLES = new Set([
  "SUPER_ADMIN",
  "PLATFORM_ADMIN",
  "ADMIN",
  "COMPLIANCE_OFFICER",
  "MLRO",
  "INVESTIGATOR",
  "ANALYST",
  "SCREENING_ANALYST",
  "PSP_ADMIN",
  "PSP_USER",
]);

export function canViewJevVerdict(user: JevRoleUser | null | undefined): boolean {
  const role = user?.role?.name?.toUpperCase() || "";
  return JEV_VERDICT_ROLES.has(role);
}
