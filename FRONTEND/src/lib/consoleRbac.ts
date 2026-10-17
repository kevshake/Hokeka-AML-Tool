import type { LucideIcon } from "lucide-react";
import {
  Activity,
  BarChart3,
  Bell,
  Briefcase,
  Building2,
  Calendar,
  CandlestickChart,
  Eye,
  ClipboardList,
  ContactRound,
  FileSliders,
  FileText,
  Gauge,
  Landmark,
  LayoutDashboard,
  Mail,
  Scale,
  Server,
  Settings,
  Share2,
  ShieldCheck,
  Smartphone,
  Store,
  UserCheck,
  Users,
  WalletCards,
} from "lucide-react";
import { Permission } from "../types/userManagement";
import { isPlatformAdmin, normalizeRole, type UserAccessContext } from "./userAccess";
import { templatePermissionsForRole } from "./rolePermissionTemplates";

/** Console personas exercised in client testing and documented in docs/CONSOLE_ROLE_MATRIX.md. */
export const CONSOLE_TEST_ROLES = [
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
] as const;

export type ConsoleTestRole = (typeof CONSOLE_TEST_ROLES)[number];

export type ConsoleAction =
  | "alert.updateStatus"
  | "alert.resolveDisposition"
  | "alert.export"
  | "sar.approve"
  | "sar.file"
  | "sar.create"
  | "rules.manage"
  | "users.manage"
  | "roles.manage"
  | "psp.manage"
  | "screening.ingest"
  | "screening.override"
  | "billing.platform"
  | "runtimeErrors.view"
  | "ai.settings";

export interface ConsoleNavItem {
  label: string;
  icon: LucideIcon;
  to: string;
  badge?: number;
}

export interface ConsoleNavGroup {
  label: string;
  items: ConsoleNavItem[];
}

type RouteRule = {
  /** Path prefix matched against the current location (lowercase, no query). */
  prefix: string;
  /** User needs at least one listed permission; empty means any authenticated user. */
  anyOf: Permission[];
  /** When true, only platform operators (HOKEKA_PLATFORM / pspId 0 admin roles). */
  platformOperatorOnly?: boolean;
  /** When true, tenant users with pspId > 0 only (excludes cross-tenant platform console routes). */
  tenantOnly?: boolean;
};

/** Maps Console routes to Control Plane permissions (mirrors @PreAuthorize + V127/V128 seeds). */
export const CONSOLE_ROUTE_RULES: RouteRule[] = [
  { prefix: "/dashboard", anyOf: [] },
  { prefix: "/profile", anyOf: [] },
  { prefix: "/messages", anyOf: [] },
  { prefix: "/transaction-monitoring", anyOf: [Permission.VIEW_TRANSACTION_DETAILS] },
  { prefix: "/customer-360", anyOf: [Permission.VIEW_PII, Permission.MERCHANT_VIEW] },
  { prefix: "/market-surveillance", anyOf: [Permission.REPORT_VIEW, Permission.VIEW_TRANSACTION_DETAILS] },
  { prefix: "/mobile-money", anyOf: [Permission.VIEW_TRANSACTION_DETAILS] },
  { prefix: "/wallet-intelligence", anyOf: [Permission.VIEW_TRANSACTION_DETAILS] },
  { prefix: "/records", anyOf: [Permission.VIEW_TRANSACTION_DETAILS, Permission.VIEW_CASES] },
  { prefix: "/alerts", anyOf: [Permission.VIEW_CASES] },
  { prefix: "/cases", anyOf: [Permission.VIEW_CASES] },
  { prefix: "/network", anyOf: [Permission.VIEW_CASES] },
  { prefix: "/kyc-documents", anyOf: [Permission.MERCHANT_VIEW] },
  { prefix: "/screening", anyOf: [Permission.VIEW_SCREENING_RESULTS] },
  { prefix: "/rules-generation", anyOf: [Permission.MANAGE_RULES] },
  { prefix: "/limits-aml", anyOf: [Permission.MANAGE_RULES, Permission.CONFIGURE_SYSTEM] },
  { prefix: "/regulatory-reports", anyOf: [Permission.VIEW_SAR, Permission.REPORT_VIEW] },
  { prefix: "/risk-analytics", anyOf: [Permission.REPORT_VIEW] },
  { prefix: "/reports", anyOf: [Permission.REPORT_VIEW] },
  { prefix: "/chargebacks", anyOf: [Permission.VIEW_CASES, Permission.MERCHANT_VIEW] },
  { prefix: "/compliance-calendar", anyOf: [Permission.VIEW_SAR, Permission.REPORT_VIEW] },
  { prefix: "/analytics", anyOf: [Permission.REPORT_VIEW] },
  { prefix: "/audit", anyOf: [Permission.VIEW_AUDIT_LOGS] },
  { prefix: "/data-access-log", anyOf: [Permission.VIEW_AUDIT_LOGS] },
  { prefix: "/merchants", anyOf: [Permission.MERCHANT_VIEW], platformOperatorOnly: true },
  { prefix: "/psps", anyOf: [Permission.MANAGE_PSP], platformOperatorOnly: true },
  { prefix: "/edge-nodes", anyOf: [Permission.MANAGE_PSP, Permission.PSP_SETTINGS_VIEW] },
  { prefix: "/users", anyOf: [Permission.MANAGE_USERS] },
  { prefix: "/settings", anyOf: [Permission.PSP_SETTINGS_VIEW, Permission.CONFIGURE_SYSTEM, Permission.MANAGE_PSP] },
  { prefix: "/billing", anyOf: [Permission.MANAGE_PSP], platformOperatorOnly: true },
  { prefix: "/runtime-errors", anyOf: [Permission.CONFIGURE_SYSTEM], platformOperatorOnly: true },
  { prefix: "/organization", anyOf: [Permission.PSP_SETTINGS_VIEW], tenantOnly: true },
  { prefix: "/psps/", anyOf: [Permission.MANAGE_PSP], platformOperatorOnly: true },
];

/** Resolves effective permission strings for RBAC checks (session grants, then role template). */
export function effectivePermissions(user: UserAccessContext): Set<string> {
  const granted = user?.role?.permissions ?? [];
  if (granted.includes("*")) {
    return new Set(Object.values(Permission));
  }
  if (granted.length > 0) {
    return new Set(granted);
  }
  return new Set(templatePermissionsForRole(user?.role?.name));
}

/** Returns true when the user holds a specific Control Plane permission. */
export function hasPermission(user: UserAccessContext, permission: Permission): boolean {
  return effectivePermissions(user).has(permission);
}

/** Returns true when the user holds any permission from the list. */
export function hasAnyPermission(user: UserAccessContext, permissions: Permission[]): boolean {
  if (!permissions.length) return true;
  const set = effectivePermissions(user);
  return permissions.some((p) => set.has(p));
}

/** Normalizes a pathname for route-rule matching (strips query/hash, ensures leading slash). */
export function normalizePathname(pathname: string): string {
  const base = pathname.split("?")[0]?.split("#")[0] ?? "/";
  if (!base.startsWith("/")) return `/${base}`;
  return base.toLowerCase();
}

/** Finds the most specific route rule for a Console path. */
export function routeRuleForPath(pathname: string): RouteRule | undefined {
  const path = normalizePathname(pathname);
  const matches = CONSOLE_ROUTE_RULES.filter(
    (rule) => path === rule.prefix || path.startsWith(`${rule.prefix}/`),
  );
  if (!matches.length) return undefined;
  return matches.sort((a, b) => b.prefix.length - a.prefix.length)[0];
}

/** Returns whether the authenticated user may open a Console route. */
export function canAccessRoute(user: UserAccessContext, pathname: string): boolean {
  if (!user) return false;
  const rule = routeRuleForPath(pathname);
  if (!rule) return true;
  if (rule.platformOperatorOnly && !isPlatformAdmin(user)) return false;
  const tenantId = user.pspId ?? 0;
  if (rule.tenantOnly && !(tenantId > 0)) return false;
  return hasAnyPermission(user, rule.anyOf);
}

/** Maps UI actions to backend authority (roles + permissions). */
export function canPerformAction(user: UserAccessContext, action: ConsoleAction): boolean {
  if (!user) return false;
  const role = normalizeRole(user.role?.name);
  switch (action) {
    case "alert.updateStatus":
    case "alert.resolveDisposition":
      return (
        role === "SUPER_ADMIN" ||
        role === "ADMIN" ||
        role === "COMPLIANCE_OFFICER" ||
        role === "INVESTIGATOR" ||
        role === "MLRO" ||
        role === "SENIOR_ANALYST"
      );
    case "alert.export":
      return hasPermission(user, Permission.EXPORT_DATA) || hasPermission(user, Permission.VIEW_CASES);
    case "sar.approve":
      return hasPermission(user, Permission.APPROVE_SAR);
    case "sar.file":
      return hasPermission(user, Permission.FILE_SAR);
    case "sar.create":
      return hasPermission(user, Permission.CREATE_SAR);
    case "rules.manage":
      return hasPermission(user, Permission.MANAGE_RULES);
    case "users.manage":
      return hasPermission(user, Permission.MANAGE_USERS);
    case "roles.manage":
      return hasPermission(user, Permission.MANAGE_ROLES);
    case "psp.manage":
      return hasPermission(user, Permission.MANAGE_PSP);
    case "screening.ingest":
      return (
        role === "SUPER_ADMIN" ||
        role === "ADMIN" ||
        role === "COMPLIANCE_OFFICER" ||
        role === "MLRO"
      );
    case "screening.override":
      return hasPermission(user, Permission.OVERRIDE_SCREENING_MATCH);
    case "billing.platform":
      return isPlatformAdmin(user) && hasPermission(user, Permission.MANAGE_PSP);
    case "runtimeErrors.view":
      return isPlatformAdmin(user) && hasPermission(user, Permission.CONFIGURE_SYSTEM);
    case "ai.settings":
      return isPlatformAdmin(user);
    default:
      return false;
  }
}

export type NavBadgeCounts = {
  alertCount?: number;
  caseCount?: number;
  messageUnreadCount?: number;
};

/** Builds sidebar navigation groups filtered by effective permissions for the signed-in user. */
export function buildConsoleNavGroups(
  user: UserAccessContext,
  badges: NavBadgeCounts = {},
): ConsoleNavGroup[] {
  const { alertCount, caseCount, messageUnreadCount } = badges;
  const tenantUser = (user?.pspId ?? 0) > 0;
  const platform = isPlatformAdmin(user);

  const intelligence: ConsoleNavItem[] = [
    { label: "Dashboard", icon: LayoutDashboard, to: "/dashboard" },
    { label: "Live Monitoring", icon: Activity, to: "/transaction-monitoring" },
    { label: "Customer 360", icon: ContactRound, to: "/customer-360" },
    { label: "Market Surveillance", icon: CandlestickChart, to: "/market-surveillance" },
    { label: "Mobile Money", icon: Smartphone, to: "/mobile-money" },
    { label: "Wallet Intelligence", icon: WalletCards, to: "/wallet-intelligence" },
    {
      label: "Alerts",
      icon: Bell,
      to: "/alerts",
      badge: alertCount && alertCount > 0 ? alertCount : undefined,
    },
    {
      label: "Cases",
      icon: Briefcase,
      to: "/cases",
      badge: caseCount && caseCount > 0 ? caseCount : undefined,
    },
    {
      label: "Messages",
      icon: Mail,
      to: "/messages",
      badge: messageUnreadCount && messageUnreadCount > 0 ? messageUnreadCount : undefined,
    },
    { label: "Network", icon: Share2, to: "/network" },
  ];

  const compliance: ConsoleNavItem[] = [
    { label: "KYC", icon: UserCheck, to: "/kyc-documents" },
    { label: "Screening", icon: ShieldCheck, to: "/screening" },
    { label: "Risk Rules", icon: FileSliders, to: "/rules-generation" },
    { label: "Transaction Limits", icon: Gauge, to: "/limits-aml" },
    { label: "Regulatory Reports", icon: Landmark, to: "/regulatory-reports" },
    { label: "Chargebacks", icon: Scale, to: "/chargebacks" },
    { label: "Compliance Calendar", icon: Calendar, to: "/compliance-calendar" },
  ];

  const analytics: ConsoleNavItem[] = [
    { label: "Risk Analytics", icon: BarChart3, to: "/risk-analytics" },
    { label: "Reports", icon: FileText, to: "/reports" },
    { label: "Analytics", icon: BarChart3, to: "/analytics" },
    { label: "Audit Logs", icon: ClipboardList, to: "/audit" },
    { label: "Data Access Log", icon: Eye, to: "/data-access-log" },
  ];

  const adminPlatform: ConsoleNavItem[] = [
    { label: "Merchants", icon: Store, to: "/merchants" },
    { label: "PSPs", icon: Building2, to: "/psps" },
    { label: "Edge Nodes", icon: Server, to: "/edge-nodes" },
    { label: "Users", icon: Users, to: "/users" },
    { label: "Runtime Errors", icon: ClipboardList, to: "/runtime-errors" },
    { label: "Settings", icon: Settings, to: "/settings" },
  ];

  const adminTenant: ConsoleNavItem[] = [
    { label: "My Organization", icon: Building2, to: "/organization" },
    ...(hasPermission(user, Permission.MANAGE_USERS)
      ? [{ label: "Users", icon: Users, to: "/users" }]
      : []),
    ...(hasPermission(user, Permission.PSP_SETTINGS_VIEW)
      ? [{ label: "Edge Nodes", icon: Server, to: "/edge-nodes" }]
      : []),
    { label: "Settings", icon: Settings, to: "/settings" },
  ];

  const groups: ConsoleNavGroup[] = [
    {
      label: "INTELLIGENCE",
      items: intelligence.filter((item) => canAccessRoute(user, item.to)),
    },
    {
      label: "COMPLIANCE",
      items: compliance.filter((item) => canAccessRoute(user, item.to)),
    },
    {
      label: "ANALYTICS",
      items: analytics.filter((item) => canAccessRoute(user, item.to)),
    },
    {
      label: "ADMINISTRATION",
      items: (tenantUser && !platform ? adminTenant : adminPlatform).filter((item) =>
        canAccessRoute(user, item.to),
      ),
    },
  ];

  return groups.filter((group) => group.items.length > 0);
}

/** Returns a synthetic user context for matrix tests (tenant vs platform by role name). */
export function mockUserContextForRole(role: ConsoleTestRole): UserAccessContext {
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
  return {
    role: { name: role, permissions: [] },
    pspId: tenantRoles.has(role) ? 2 : 0,
  };
}

/** Lists primary Console routes used for screenshot matrices per role. */
export function routesForRoleMatrix(role: ConsoleTestRole): string[] {
  const mockUser = mockUserContextForRole(role);
  const candidates = [
    "/dashboard",
    "/transaction-monitoring/live",
    "/alerts",
    "/cases/all",
    "/screening",
    "/reports",
    "/regulatory-reports",
    "/risk-analytics",
    "/kyc-documents",
    "/users/list",
    "/settings",
    "/audit",
    "/edge-nodes",
    "/merchants",
    "/psps",
    "/billing",
    "/chargebacks",
    "/rules-generation",
    "/network",
    "/messages",
  ];
  return candidates.filter((path) => canAccessRoute(mockUser, path));
}
