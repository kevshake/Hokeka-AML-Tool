/**
 * Canonical Console routes referenced by docs/features/README.md and legacy bookmarks.
 * Legacy aliases are registered in App.tsx as Navigate redirects.
 */
export const LEGACY_CONSOLE_ROUTE_ALIASES: ReadonlyArray<{
  from: string;
  to: string;
}> = [
  { from: "/monitoring", to: "/transaction-monitoring" },
  { from: "/audit-logs", to: "/audit" },
  { from: "/rules", to: "/rules-generation" },
];

export const DOCUMENTED_PRIMARY_CONSOLE_ROUTES: readonly string[] = [
  "/dashboard",
  "/transaction-monitoring",
  "/customer-360",
  "/market-surveillance",
  "/mobile-money",
  "/wallet-intelligence",
  "/records/:recordType/:recordId",
  "/alerts",
  "/cases",
  "/kyc-documents",
  "/screening",
  "/rules-generation",
  "/limits-aml",
  "/regulatory-reports",
  "/risk-analytics",
  "/reports",
  "/billing",
  "/psps",
  "/users",
  "/settings",
  "/messages",
  "/compliance-calendar",
  "/audit",
  "/profile",
  "/analytics",
  "/edge-nodes",
  "/chargebacks",
  "/organization",
  "/runtime-errors",
];
