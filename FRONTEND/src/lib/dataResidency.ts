import { normalizeRole, type UserAccessContext } from "./userAccess";
import type { DataSetting, DataSettingChange } from "../features/api/dataResidency";

/** Tenant roles that may propose a data residency change (backend PspDataSettingsAccess.MAKER_ROLES). */
export const DATA_SETTINGS_MAKER_ROLES = new Set(["PSP_ADMIN", "ADMIN"]);

/** Tenant roles that may approve or reject someone else's change (backend CHECKER_ROLES). */
export const DATA_SETTINGS_CHECKER_ROLES = new Set(["PSP_ADMIN", "ADMIN", "MLRO", "COMPLIANCE_OFFICER"]);

/** True for a user bound to a real PSP tenant (not the platform). */
function isTenantUser(user: UserAccessContext): boolean {
  return !!user && (user.pspId ?? 0) > 0;
}

/** Whether the Data Retention and Support & Data Sharing settings tabs are shown. */
export function canViewDataSettings(user: UserAccessContext): boolean {
  if (!isTenantUser(user)) return false;
  const role = normalizeRole(user?.role?.name);
  return DATA_SETTINGS_CHECKER_ROLES.has(role);
}

/** Whether the user may propose a change (PSP admins of their own PSP only). */
export function canProposeDataSettings(user: UserAccessContext): boolean {
  return isTenantUser(user) && DATA_SETTINGS_MAKER_ROLES.has(normalizeRole(user?.role?.name));
}

/**
 * Whether the user may approve or reject {@code change}: a tenant reviewer who is not the person
 * who proposed it (maker-checker). The server enforces the same rule.
 */
export function canReviewChange(
  user: (UserAccessContext & { username?: string }) | null | undefined,
  change: Pick<DataSettingChange, "status" | "requestedBy">,
): boolean {
  if (!isTenantUser(user) || change.status !== "PENDING_APPROVAL") return false;
  if (!DATA_SETTINGS_CHECKER_ROLES.has(normalizeRole(user?.role?.name))) return false;
  const me = (user?.username ?? "").toLowerCase();
  return me.length > 0 && me !== (change.requestedBy ?? "").toLowerCase();
}

/**
 * Validation message for a proposed retention value, or null when it is acceptable. Proposing the
 * value in force is allowed only while a shortening is waiting: approving it cancels that shortening.
 */
export function retentionError(
  years: number,
  current: number,
  min = 5,
  max = 25,
  reductionWaiting = false,
): string | null {
  if (!Number.isInteger(years)) return "Enter a whole number of years.";
  if (years < min) return `Retention cannot be shorter than ${min} years.`;
  if (years > max) return `Retention cannot be longer than ${max} years.`;
  if (years === current && !reductionWaiting) return `Retention is already ${current} years.`;
  return null;
}

/** Explains when a retention change will take effect once approved. */
export function retentionChangeNotice(years: number, current: number, delayDays = 30): string {
  if (years === current) {
    return "Keeping the current period cancels the shortening that is waiting to take effect.";
  }
  if (years < current) {
    return `Shortening retention takes effect ${delayDays} days after approval. Data already past the new period is then deleted at the next daily purge and cannot be recovered.`;
  }
  return "Extending retention takes effect as soon as a second reviewer approves it. It cannot restore data that was already deleted.";
}

/** Human label of a setting. */
export function settingLabel(setting: DataSetting): string {
  switch (setting) {
    case "RETENTION_YEARS":
      return "Data retention";
    case "SUPPORT_ACCESS":
      return "Hokeka support access";
    case "CROSS_PSP_OPT_IN":
      return "Cross-PSP intelligence";
  }
}

/** Human description of a requested value. */
export function describeValue(setting: DataSetting, value: string): string {
  if (setting === "RETENTION_YEARS") return `${value} years`;
  if (setting === "SUPPORT_ACCESS") return value === "true" ? "Allowed" : "Not allowed";
  return value === "true" ? "Opted in" : "Opted out";
}

/** Short date-time for display, tolerant of missing values. */
export function formatWhen(value: string | null | undefined): string {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}
