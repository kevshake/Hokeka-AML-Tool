import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../../lib/apiClient";
import type {
  Case,
  CaseTimeline,
  SarReport,
  Alert,
  Transaction,
  Merchant,
  AuditLog,
  DashboardStats,
  User,
  Psp,
  PageResponse,
} from "../../types";
import type { User as UserManagementUser, Role as UserManagementRole } from "../../types/userManagement";
import type { RevenueSummary, Invoice, Subscription, UsageSummary, PricingTier } from "../../types/billing";
import type { Customer360, MultiAssetCustomer, PageResult } from "../../types/multiAsset";
import type { RuleVersion } from "../../types/rules";
import type { MarketOrder, MarketSurveillanceSignal } from "../../types/marketSurveillance";
import type { MobileMoneyContext, MobileMoneyEntityType, MobileMoneyNetwork, MobileMoneyRiskProfile } from "../../types/mobileMoney";
import type { CryptoTransactionSummary, CryptoWalletProfile, RegulatorGrant, TravelRulePolicy, TravelRuleTransfer, VaspEntry, VaspScreeningRecord, WalletScreeningRecord } from "../../types/virtualAssets";

// Dashboard
export const useDashboardStats = () => {
  return useQuery<DashboardStats>({
    queryKey: ["dashboard", "stats"],
    queryFn: () => apiClient.get<DashboardStats>("reporting/summary"),
  });
};

export const useDashboardGlobalStats = () => {
  return useQuery({
    queryKey: ["dashboard", "global-stats"],
    queryFn: () => apiClient.get("dashboard/stats"),
  });
};

export const useTransactionVolume = (days: number = 30) => {
  return useQuery({
    queryKey: ["dashboard", "transaction-volume", days],
    queryFn: () => apiClient.get(`dashboard/transaction-volume?days=${days}`),
  });
};

export const useRiskDistribution = () => {
  return useQuery({
    queryKey: ["dashboard", "risk-distribution"],
    queryFn: () => apiClient.get("dashboard/risk-distribution"),
  });
};

export const useLiveAlerts = (limit: number = 5) => {
  return useQuery<Alert[]>({
    queryKey: ["dashboard", "live-alerts", limit],
    queryFn: () => apiClient.get<Alert[]>(`dashboard/live-alerts?limit=${limit}`).catch(() => []),
  });
};

export const useRecentTransactions = (limit: number = 5) => {
  return useQuery<Transaction[]>({
    queryKey: ["dashboard", "recent-transactions", limit],
    queryFn: () => apiClient.get<Transaction[]>(`dashboard/recent-transactions?limit=${limit}`).catch(() => []),
  });
};

// Cases
// Cases
export interface CaseQueryParams {
  page?: number;
  size?: number;
  status?: string;
}

export const useCases = (params?: string | CaseQueryParams) => {
  // Support backward compatibility: if string is provided, use it as status with default pagination
  const queryParams = typeof params === 'string' 
    ? { page: 0, size: 25, status: params } 
    : params || {};

  // Convert object to query string
  const queryString = Object.entries(queryParams)
    .filter(([_, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${key}=${encodeURIComponent(String(value))}`)
    .join('&');

  return useQuery<PageResponse<Case>>({
    queryKey: ["cases", queryParams],
    queryFn: () =>
      apiClient.get<PageResponse<Case>>(
        `compliance/cases${queryString ? `?${queryString}` : ""}`
      ),
  });
};

export const useCase = (id: number) => {
  return useQuery<Case>({
    queryKey: ["case", id],
    queryFn: () => apiClient.get<Case>(`compliance/cases/${id}`),
    enabled: !!id,
  });
};

export const useCaseTimeline = (caseId: number) => {
  return useQuery<CaseTimeline>({
    queryKey: ["case", caseId, "timeline"],
    queryFn: () => apiClient.get<CaseTimeline>(`cases/${caseId}/timeline`),
    enabled: !!caseId,
  });
};

export const useCaseNetwork = (caseId: number) => {
  return useQuery({
    queryKey: ["case", caseId, "network"],
    queryFn: () => apiClient.get(`cases/${caseId}/network`),
    enabled: !!caseId,
  });
};

// SAR Reports
export const useSarReports = (status?: string) => {
  return useQuery<SarReport[]>({
    queryKey: ["sar", status],
    queryFn: () =>
      apiClient.get<SarReport[]>(
        `compliance/sar${status ? `?status=${status}` : ""}`
      ),
  });
};

// Alerts
export interface AlertQueryParams {
  page?: number;
  size?: number;
  status?: string;
}

export const useAlerts = (params?: AlertQueryParams) => {
  const queryParams = params || {};

  // Convert object to query string
  const queryString = Object.entries(queryParams)
    .filter(([_, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${key}=${encodeURIComponent(String(value))}`)
    .join('&');

  return useQuery<PageResponse<Alert>>({
    queryKey: ["alerts", queryParams],
    queryFn: () =>
      apiClient.get<PageResponse<Alert>>(`alerts${queryString ? `?${queryString}` : ""}`),
  });
};

// Transactions
export interface TransactionQueryParams {
  page?: number;
  size?: number;
  pspId?: number;
}

export const useTransactions = (params?: number | TransactionQueryParams) => {
  // Support backward compatibility: if number is provided, use it as page size with page 0
  const queryParams = typeof params === 'number' 
    ? { page: 0, size: params } 
    : params || {};

  // Convert object to query string
  const queryString = Object.entries(queryParams)
    .filter(([_, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${key}=${encodeURIComponent(String(value))}`)
    .join('&');

  return useQuery<PageResponse<Transaction>>({
    queryKey: ["transactions", queryParams],
    queryFn: () =>
      apiClient.get<PageResponse<Transaction>>(
        `transactions${queryString ? `?${queryString}` : ""}`
      ),
  });
};

// Merchants
// Merchants
export interface MerchantQueryParams {
  page?: number;
  size?: number;
}

export const useMerchants = (params?: MerchantQueryParams) => {
  const queryParams = params || {};

  // Convert object to query string
  const queryString = Object.entries(queryParams)
    .filter(([_, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${key}=${encodeURIComponent(String(value))}`)
    .join('&');

  return useQuery<PageResponse<Merchant>>({
    queryKey: ["merchants", queryParams],
    queryFn: () =>
      apiClient.get<PageResponse<Merchant>>(`merchants${queryString ? `?${queryString}` : ""}`),
  });
};

// Users
export interface UserQueryParams {
  page?: number;
  size?: number;
  pspId?: number;
}

export const useUsers = (params?: UserQueryParams) => {
  const queryParams = params || {};

  // Convert object to query string
  const queryString = Object.entries(queryParams)
    .filter(([_, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${key}=${encodeURIComponent(String(value))}`)
    .join('&');

  return useQuery<PageResponse<UserManagementUser>>({
    queryKey: ["users", queryParams],
    queryFn: () =>
      apiClient.get<PageResponse<UserManagementUser>>(`users${queryString ? `?${queryString}` : ""}`),
  });
};

// Roles
export const useRoles = () => {
  return useQuery<UserManagementRole[]>({
    queryKey: ["roles"],
    queryFn: () => apiClient.get<UserManagementRole[]>("roles"),
  });
};

// Audit Logs
export interface AuditLogQueryParams {
  page?: number;
  size?: number;
  username?: string;
  actionType?: string;
  entityType?: string;
  entityId?: string;
  success?: boolean;
  pspId?: number;
  start?: string;
  end?: string;
}

export const useAuditLogs = (params?: number | AuditLogQueryParams) => {
  const queryParams = typeof params === 'number' ? { size: params } : params || {};

  // Convert object to query string
  const queryString = Object.entries(queryParams)
    .filter(([_, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${key}=${encodeURIComponent(String(value))}`)
    .join('&');

  return useQuery<PageResponse<AuditLog>>({
    queryKey: ["audit", queryParams],
    queryFn: () =>
      apiClient.get<PageResponse<AuditLog>>(`audit/logs${queryString ? `?${queryString}` : ""}`),
  });
};

// Runtime Errors (W31-1: this hook plus RuntimeErrorsPage are the previously-missing admin
// viewer for GET /admin/runtime-errors -- the endpoint existed with no frontend caller at all)
export interface RuntimeError {
  id: number;
  errorCode?: string;
  message?: string;
  stackTrace?: string;
  userId?: number;
  pspId?: number;
  occurredAt?: string;
}

export const useRuntimeErrors = (page: number, size: number) => {
  return useQuery<PageResponse<RuntimeError>>({
    queryKey: ["admin", "runtime-errors", page, size],
    queryFn: () => apiClient.get<PageResponse<RuntimeError>>(`admin/runtime-errors?page=${page}&size=${size}`),
  });
};

// Current User
export const useCurrentUser = () => {
  return useQuery<User>({
    queryKey: ["user", "me"],
    queryFn: () => apiClient.get<User>("users/me"),
  });
};

// Grafana Dashboards
export interface GrafanaDashboard {
  menu: string;
  uid: string;
  path: string;
  systemOnly: boolean;
}

export const useGrafanaDashboards = () => {
  return useQuery<GrafanaDashboard[]>({
    queryKey: ["grafana", "dashboards"],
    queryFn: () => apiClient.get<GrafanaDashboard[]>("grafana/dashboards"),
  });
};

// Risk Analytics
export interface RiskHeatmapCell {
  id: string;
  type: string;
  caseCount: number;
  averageRiskScore: number;
}

/**
 * Heatmap query — backend returns Map<String, RiskHeatmapCell>. We project each
 * cell down to a 0-100 score so the existing UI can render it as a number.
 */
export const useRiskHeatmap = (type: "customer" | "merchant") => {
  return useQuery<Record<string, number>>({
    queryKey: ["risk", "heatmap", type],
    queryFn: async () => {
      const raw = await apiClient
        .get<Record<string, RiskHeatmapCell>>(`analytics/risk/heatmap/${type}`)
        .catch(() => ({} as Record<string, RiskHeatmapCell>));
      const projected: Record<string, number> = {};
      for (const [k, v] of Object.entries(raw)) {
        // averageRiskScore is in [0,1]; surface as 0-100 for the UI bands
        projected[k] = Math.round((v?.averageRiskScore ?? 0) * 100);
      }
      return projected;
    },
  });
};

interface RiskTrendBackend {
  trendDirection?: string;
  weeklyTrends?: Record<string, number>;
  totalCases?: number;
  highRiskCases?: number;
}

interface RiskTrends {
  labels?: string[];
  data?: number[];
  trendDirection?: string;
  totalCases?: number;
  highRiskCases?: number;
}

/**
 * Trends query — backend accepts startDate/endDate; we compute a window from `days`
 * client-side so the existing UI's "last N days" selector works without a backend
 * contract change. Response is projected from {weeklyTrends:{label:count}} → {labels, data}.
 */
export const useRiskTrends = (days: number = 30) => {
  return useQuery<RiskTrends>({
    queryKey: ["risk", "trends", days],
    queryFn: async () => {
      const endDate = new Date();
      const startDate = new Date(endDate.getTime() - days * 24 * 60 * 60 * 1000);
      const raw = await apiClient
        .get<RiskTrendBackend>(
          `analytics/risk/trends?startDate=${startDate.toISOString()}&endDate=${endDate.toISOString()}`,
        )
        .catch(() => ({} as RiskTrendBackend));
      const labels = raw.weeklyTrends ? Object.keys(raw.weeklyTrends) : [];
      const data = raw.weeklyTrends ? Object.values(raw.weeklyTrends).map((n) => Number(n) || 0) : [];
      return {
        labels,
        data,
        trendDirection: raw.trendDirection,
        totalCases: raw.totalCases,
        highRiskCases: raw.highRiskCases,
      };
    },
  });
};

// Compliance Calendar
export const useUpcomingDeadlines = () => {
  return useQuery({
    queryKey: ["compliance", "calendar", "upcoming"],
    queryFn: () => apiClient.get("compliance/calendar/upcoming"),
  });
};

export const useOverdueDeadlines = () => {
  return useQuery({
    queryKey: ["compliance", "calendar", "overdue"],
    queryFn: () => apiClient.get("compliance/calendar/overdue"),
  });
};

// Transaction Monitoring
export const useMonitoringDashboardStats = () => {
  return useQuery<Record<string, unknown>>({
    // W14-11 fix: this used to share ["monitoring", "dashboard-stats"] with useTransactionStats
    // below, even though both hit the same endpoint with different declared response types
    // (Record<string, unknown> vs the typed TransactionStats) and different query configs
    // (this one polls every 30s, that one doesn't) -- React Query treats a shared key as one
    // cache entry, so whichever hook mounted first silently won and the other's refetchInterval
    // was ignored. Distinct key so each hook owns its own cache entry and config.
    queryKey: ["monitoring", "dashboard-stats-raw"],
    queryFn: () => apiClient.get<Record<string, unknown>>("monitoring/dashboard/stats").catch(() => ({})),
    refetchInterval: 30_000,
  });
};

export const useMonitoringRiskDistribution = () => {
  return useQuery<Record<string, number>>({
    queryKey: ["monitoring", "risk-distribution"],
    queryFn: () => apiClient.get<Record<string, number>>("monitoring/risk-distribution").catch(() => ({})),
  });
};

export const useMonitoringRiskIndicators = () => {
  return useQuery<any[]>({
    queryKey: ["monitoring", "risk-indicators"],
    queryFn: () => apiClient.get<any[]>("monitoring/risk-indicators").catch(() => []),
  });
};

export const useMonitoringRecentActivity = () => {
  return useQuery<any[]>({
    queryKey: ["monitoring", "recent-activity"],
    queryFn: () => apiClient.get<any[]>("monitoring/recent-activity").catch(() => []),
    refetchInterval: 30_000,
  });
};

export interface MonitoringTransactionQueryParams {
  page?: number;
  size?: number;
  riskLevel?: string;
  decision?: string;
}

export const useMonitoringTransactions = (params?: MonitoringTransactionQueryParams) => {
  const queryParams = params || {};

  // Convert object to query string
  const queryString = Object.entries(queryParams)
    .filter(([_, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${key}=${encodeURIComponent(String(value))}`)
    .join('&');

  return useQuery<PageResponse<any>>({
    queryKey: ["monitoring", "transactions", queryParams],
    queryFn: () => apiClient.get<PageResponse<any>>(`monitoring/transactions${queryString ? `?${queryString}` : ""}`),
    refetchInterval: 30_000,
  });
};

// Rules Management
export const useAmlRules = () => {
  return useQuery({
    queryKey: ["rules", "aml"],
    queryFn: async () => {
      const rules = await apiClient.get<any[]>("rules").catch(() => []);
      // Enrich rules with creator info if available
      return rules.map((rule) => ({
        ...rule,
        isSuperAdmin: !rule.pspId || rule.createdByUser?.role?.name === "ADMIN" || rule.createdByUser?.role?.name === "SUPER_ADMIN",
      }));
    },
  });
};

export const useAmlRule = (id: number) => {
  return useQuery({
    queryKey: ["rule", id],
    queryFn: () => apiClient.get(`rules/${id}`).catch(() => null),
    enabled: !!id,
  });
};

export const useRuleEffectiveness = (id: number) => {
  return useQuery({
    queryKey: ["rule", id, "effectiveness"],
    queryFn: () => apiClient.get(`rules/${id}/effectiveness`).catch(() => null),
    enabled: !!id && id > 0,
  });
};

export const useChargebackDisputes = (merchantId?: number) => {
  return useQuery({
    queryKey: ["chargeback", "disputes", merchantId ?? "all"],
    queryFn: () =>
      apiClient
        .get<any[]>(`chargeback/disputes${merchantId ? `?merchantId=${merchantId}` : ""}`)
        .catch(() => []),
    refetchInterval: 60_000,
  });
};

export interface MonitoringAlertRow {
  alertId: number;
  merchantId?: number;
  alertType: string;
  alertSeverity: string;
  alertDetails?: Record<string, unknown>;
  acknowledged?: boolean;
  createdAt?: string;
}

export const useMonitoringAlerts = (page = 0, size = 25) => {
  return useQuery({
    queryKey: ["monitoring", "alerts", page, size],
    queryFn: () =>
      apiClient
        .get<{ content: MonitoringAlertRow[]; totalElements: number }>(
          `monitoring/alerts?page=${page}&size=${size}`
        )
        .catch(() => ({ content: [], totalElements: 0 })),
    refetchInterval: 60_000,
  });
};

export interface SanctionsListVersion {
  listId: number;
  listName: string;
  listSource: string;
  version?: string;
  downloadedAt?: string;
  recordCount?: number;
}

export const useSanctionsListVersions = (listName?: string) => {
  return useQuery({
    queryKey: ["sanctions", "lists", listName ?? "all"],
    queryFn: () =>
      apiClient
        .get<SanctionsListVersion[]>(
          `sanctions/lists${listName ? `?listName=${encodeURIComponent(listName)}` : ""}`
        )
        .catch(() => []),
    staleTime: 5 * 60_000,
  });
};

export const useVelocityRules = () => {
  return useQuery({
    queryKey: ["rules", "velocity"],
    queryFn: async () => {
      const rules = await apiClient.get<any[]>("limits/velocity-rules");
      // Enrich rules with creator info if available
      return rules.map((rule) => ({
        ...rule,
        isSuperAdmin: !rule.pspId || rule.createdByUser?.role?.name === "ADMIN" || rule.createdByUser?.role?.name === "SUPER_ADMIN",
      }));
    },
  });
};

export const useRiskThresholds = () => {
  return useQuery({
    queryKey: ["rules", "risk-thresholds"],
    queryFn: async () => {
      const thresholds = await apiClient.get<any[]>("limits/risk-thresholds");
      // Enrich thresholds with creator info if available
      return thresholds.map((threshold) => ({
        ...threshold,
        isSuperAdmin: !threshold.pspId || threshold.createdByUser?.role?.name === "ADMIN" || threshold.createdByUser?.role?.name === "SUPER_ADMIN",
      }));
    },
  });
};

// PSP Information
export const usePsp = (pspId: number) => {
  return useQuery({
    queryKey: ["psp", pspId],
    queryFn: () => apiClient.get(`psps/${pspId}`),
    enabled: !!pspId,
  });
};

export const useRegulatoryReport = (reportType: string) => {
  return useQuery<any>({
    queryKey: ["regulatory-report", reportType],
    queryFn: () => apiClient.get<any>(`reporting/regulatory/${reportType.toLowerCase()}`).catch(() => null),
  });
};

export const useAllPsps = () => {
  return useQuery<Psp[]>({
    queryKey: ["psps"],
    queryFn: () => apiClient.get<Psp[]>("psps").catch(() => []),
  });
};

// ─────────────────────────────────────────────
// PSP CBK config (separate endpoint, returns env + allowLive + liveEffective)
// ─────────────────────────────────────────────

export interface PspCbkConfig {
  pspId: number;
  pspCode: string;
  legalName: string;
  cbkInstitutionCode: string | null;
  cbkReportingEnabled: boolean | null;
  cbkEnvironment: "preprod" | "live" | null;
  cbkAllowLive: boolean | null;
  cbkClientId: string | null;
  hasClientSecret: boolean;
  liveEffective: boolean;
}

export const usePspCbkConfig = (pspId: number | string) =>
  useQuery<PspCbkConfig | null>({
    queryKey: ["psp", pspId, "cbk-config"],
    queryFn: () => apiClient.get<PspCbkConfig>(`psps/${pspId}/cbk-config`).catch(() => null),
    enabled: !!pspId,
  });

// ─────────────────────────────────────────────
// PSP child-entity queries
// ─────────────────────────────────────────────

export const usePspDirectors = (pspId: number | string) =>
  useQuery<any[]>({
    queryKey: ["psp", pspId, "directors"],
    queryFn: () => apiClient.get<any[]>(`psps/${pspId}/cbk/directors`).catch(() => []),
    enabled: !!pspId,
  });

export const usePspShareholders = (pspId: number | string) =>
  useQuery<any[]>({
    queryKey: ["psp", pspId, "shareholders"],
    queryFn: () => apiClient.get<any[]>(`psps/${pspId}/cbk/shareholders`).catch(() => []),
    enabled: !!pspId,
  });

export const usePspTrustees = (pspId: number | string) =>
  useQuery<any[]>({
    queryKey: ["psp", pspId, "trustees"],
    queryFn: () => apiClient.get<any[]>(`psps/${pspId}/cbk/trustees`).catch(() => []),
    enabled: !!pspId,
  });

export const usePspSeniorManagement = (pspId: number | string) =>
  useQuery<any[]>({
    queryKey: ["psp", pspId, "senior-management"],
    queryFn: () => apiClient.get<any[]>(`psps/${pspId}/cbk/senior-management`).catch(() => []),
    enabled: !!pspId,
  });

export const usePspProducts = (pspId: number | string) =>
  useQuery<any[]>({
    queryKey: ["psp", pspId, "products"],
    queryFn: () => apiClient.get<any[]>(`psps/${pspId}/cbk/products`).catch(() => []),
    enabled: !!pspId,
  });

export const usePspTrustAccounts = (pspId: number | string) =>
  useQuery<any[]>({
    queryKey: ["psp", pspId, "trust-accounts"],
    queryFn: () => apiClient.get<any[]>(`psps/${pspId}/cbk/trust-accounts`).catch(() => []),
    enabled: !!pspId,
  });

export const usePspTariffs = (pspId: number | string) =>
  useQuery<any[]>({
    queryKey: ["psp", pspId, "tariffs"],
    queryFn: () => apiClient.get<any[]>(`psps/${pspId}/cbk/tariffs`).catch(() => []),
    enabled: !!pspId,
  });

export const usePspCyberIncidents = (pspId: number | string) =>
  useQuery<any[]>({
    queryKey: ["psp", pspId, "cyber-incidents"],
    queryFn: () => apiClient.get<any[]>(`psps/${pspId}/cbk/cyber-incidents`).catch(() => []),
    enabled: !!pspId,
  });

export const usePspSystemInterruptions = (pspId: number | string) =>
  useQuery<any[]>({
    queryKey: ["psp", pspId, "system-interruptions"],
    queryFn: () => apiClient.get<any[]>(`psps/${pspId}/cbk/system-interruptions`).catch(() => []),
    enabled: !!pspId,
  });

export const usePspComplaints = (pspId: number | string) =>
  useQuery<any[]>({
    queryKey: ["psp", pspId, "complaints"],
    queryFn: () => apiClient.get<any[]>(`psps/${pspId}/cbk/customer-complaints`).catch(() => []),
    enabled: !!pspId,
  });

export const usePspFraudIncidents = (pspId: number | string) =>
  useQuery<any[]>({
    queryKey: ["psp", pspId, "fraud-incidents"],
    queryFn: () => apiClient.get<any[]>(`psps/${pspId}/cbk/fraud-incidents`).catch(() => []),
    enabled: !!pspId,
  });

// ─────────────────────────────────────────────
// CBK Submissions history
// ─────────────────────────────────────────────

export interface CbkSubmissionQueryParams {
  pspId?: number | string;
  status?: string;
  endpoint?: string;
  page?: number;
  size?: number;
}

export const useCbkSubmissions = (params: CbkSubmissionQueryParams = {}) => {
  const { pspId, status, endpoint, page = 0, size = 25 } = params;
  const qs = [
    `page=${page}`,
    `size=${size}`,
    pspId !== undefined ? `pspId=${pspId}` : "",
    status ? `status=${status}` : "",
    endpoint ? `endpoint=${endpoint}` : "",
  ]
    .filter(Boolean)
    .join("&");
  return useQuery<PageResponse<any>>({
    queryKey: ["cbk-submissions", params],
    queryFn: () =>
      apiClient
        .get<PageResponse<any>>(`compliance/cbk/submissions?${qs}`)
        .catch(() => ({ content: [], totalElements: 0, totalPages: 0, size, number: page })),
  });
};

// ─── Billing ─────────────────────────────────────────────────────────────────

export function useRevenueSummary() {
  return useQuery<RevenueSummary>({
    queryKey: ['billing', 'revenue-summary'],
    queryFn: () => apiClient.get<RevenueSummary>('billing/revenue/summary'),
  });
}

export interface InvoiceQueryParams {
  pspId?: number;
  page?: number;
  size?: number;
}

export function useInvoices(params?: InvoiceQueryParams) {
  const qs = params
    ? Object.entries(params)
        .filter(([, v]) => v !== undefined && v !== null)
        .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
        .join('&')
    : '';
  return useQuery<PageResponse<Invoice>>({
    queryKey: ['billing', 'invoices', params],
    queryFn: () => apiClient.get<PageResponse<Invoice>>(`billing/invoices${qs ? `?${qs}` : ''}`),
  });
}

export function useInvoice(id: number | null) {
  return useQuery<Invoice>({
    queryKey: ['billing', 'invoices', id],
    queryFn: () => apiClient.get<Invoice>(`billing/invoices/${id}`),
    enabled: id != null,
  });
}

export function useOverdueInvoices() {
  return useQuery<Invoice[]>({
    queryKey: ['billing', 'invoices', 'overdue'],
    queryFn: () => apiClient.get<Invoice[]>('billing/invoices/overdue').catch(() => []),
  });
}

export function useSubscriptions() {
  return useQuery<Subscription[]>({
    queryKey: ['billing', 'subscriptions'],
    queryFn: () => apiClient.get<Subscription[]>('subscriptions').catch(() => []),
  });
}

export function useSubscription(id: number | null) {
  return useQuery<Subscription>({
    queryKey: ['billing', 'subscriptions', id],
    queryFn: () => apiClient.get<Subscription>(`subscriptions/${id}`),
    enabled: id != null,
  });
}

export function usePspSubscription(pspId: number | null) {
  return useQuery<Subscription>({
    queryKey: ['billing', 'subscriptions', 'psp', pspId],
    queryFn: () => apiClient.get<Subscription>(`subscriptions/psp/${pspId}`),
    enabled: pspId != null,
  });
}

export function usePricingTiers() {
  return useQuery<PricingTier[]>({
    queryKey: ['billing', 'pricing-tiers'],
    queryFn: () => apiClient.get<PricingTier[]>('pricing/tiers').catch(() => []),
  });
}

export function useUsageSummary(pspId: number | null, month?: string) {
  const qs = month ? `?month=${encodeURIComponent(month)}` : '';
  return useQuery<UsageSummary>({
    queryKey: ['billing', 'usage', pspId, month],
    queryFn: () => apiClient.get<UsageSummary>(`billing/usage/${pspId}${qs}`),
    enabled: pspId != null,
  });
}

export function useCurrentUsage(pspId: number | null) {
  return useQuery<UsageSummary>({
    queryKey: ['billing', 'usage', pspId, 'current'],
    queryFn: () => apiClient.get<UsageSummary>(`billing/usage/${pspId}/current`),
    enabled: pspId != null,
  });
}

// ─── Analytics ────────────────────────────────────────────────────────────────

export interface FraudMetrics {
  precision: string;
  recall: string;
  f1: string;
  falsePositives: string;
}

export const useFraudMetrics = () => {
  return useQuery<FraudMetrics>({
    queryKey: ["analytics", "fraud-metrics"],
    queryFn: () => apiClient.get<FraudMetrics>("dashboard/fraud-metrics").catch(() => ({
      precision: "—", recall: "—", f1: "—", falsePositives: "—",
    })),
  });
};

export interface ModelMetrics {
  id: number;
  date: string;
  auc: number | null;
  precisionAt100: number | null;
  avgLatencyMs: number | null;
  driftScore: number | null;
}

export type ModelMetricsEntry = ModelMetrics;

export const useModelMetricsLatest = () => {
  return useQuery<ModelMetrics | null>({
    queryKey: ["analytics", "model-metrics", "latest"],
    queryFn: () => apiClient.get<ModelMetrics>("monitoring/metrics/latest").catch(() => null),
  });
};

export const useModelMetricsRange = (startDate: string, endDate: string) => {
  return useQuery<ModelMetrics[]>({
    queryKey: ["analytics", "model-metrics", "range", startDate, endDate],
    queryFn: () =>
      apiClient
        .get<ModelMetrics[]>(`monitoring/metrics/range?startDate=${startDate}&endDate=${endDate}`)
        .catch(() => []),
    enabled: !!startDate && !!endDate,
  });
};

export interface AlertTrendsEntry {
  date: string;
  open: number;
  resolved: number;
  escalated: number;
  total: number;
}

export const useAlertTrends = (days: number = 30) => {
  return useQuery<AlertTrendsEntry[]>({
    queryKey: ["analytics", "alert-trends", days],
    queryFn: () =>
      apiClient
        .get<AlertTrendsEntry[]>(`analytics/alert-trends?days=${days}`)
        .catch(() => []),
  });
};

export interface TransactionStats {
  totalCount: number;
  approvedCount: number;
  declinedCount: number;
  manualReviewCount: number;
  highRiskCount: number;
  mediumRiskCount: number;
  lowRiskCount: number;
  totalAmountCents: number;
  averageAmountCents: number;
  fraudAlertCount: number;
}

export const useTransactionStats = () => {
  return useQuery<TransactionStats>({
    queryKey: ["monitoring", "dashboard-stats"],
    queryFn: () => apiClient.get<TransactionStats>("monitoring/dashboard/stats"),
  });
};

export const useMyPsp = (enabled = true) => {
  return useQuery<Psp>({
    queryKey: ["psp", "me"],
    queryFn: () => apiClient.get<Psp>("psps/me"),
    enabled,
  });
};

export const usePendingRuleVersions = () => {
  return useQuery<RuleVersion[]>({
    queryKey: ["rules", "governance", "pending"],
    queryFn: () => apiClient.get<RuleVersion[]>("rules/governance/pending"),
    refetchInterval: 30_000,
  });
};

export const useRuleVersions = (id: number) => {
  return useQuery<RuleVersion[]>({
    queryKey: ["rule", id, "versions"],
    queryFn: () => apiClient.get<RuleVersion[]>(`rules/${id}/versions`),
    enabled: id > 0,
  });
};

// Multi-asset customer intelligence
export const useMultiAssetCustomers = (search = "", page = 0, size = 25) => {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (search.trim()) query.set("search", search.trim());
  return useQuery<PageResult<MultiAssetCustomer>>({
    queryKey: ["multi-asset", "customers", search, page, size],
    queryFn: () => apiClient.get<PageResult<MultiAssetCustomer>>(`multi-asset/customers?${query}`),
  });
};

export const useCustomer360 = (customerId: number | null) =>
  useQuery<Customer360>({
    queryKey: ["multi-asset", "customers", customerId, "360"],
    queryFn: () => apiClient.get<Customer360>(`multi-asset/customers/${customerId}/360`),
    enabled: customerId !== null,
  });

export const useMarketOrders = (page = 0, size = 50) => {
  return useQuery<PageResult<MarketOrder>>({
    queryKey: ["market-surveillance", "orders", page, size],
    queryFn: () => apiClient.get<PageResult<MarketOrder>>(`market-surveillance/orders?page=${page}&size=${size}`),
    refetchInterval: 15_000,
  });
};

export const useMarketSignals = (page = 0, size = 50) => {
  return useQuery<PageResult<MarketSurveillanceSignal>>({
    queryKey: ["market-surveillance", "signals", page, size],
    queryFn: () => apiClient.get<PageResult<MarketSurveillanceSignal>>(`market-surveillance/signals?page=${page}&size=${size}`),
    refetchInterval: 15_000,
  });
};

export const useMobileMoneyTransactions = (page = 0, size = 50) =>
  useQuery<PageResult<MobileMoneyContext>>({
    queryKey: ["mobile-money", "transactions", page, size],
    queryFn: () => apiClient.get<PageResult<MobileMoneyContext>>(`mobile-money/transactions?page=${page}&size=${size}`),
    refetchInterval: 15_000,
  });

export const useMobileMoneyRiskProfiles = (page = 0, size = 50) =>
  useQuery<PageResult<MobileMoneyRiskProfile>>({
    queryKey: ["mobile-money", "risk-profiles", page, size],
    queryFn: () => apiClient.get<PageResult<MobileMoneyRiskProfile>>(`mobile-money/risk-profiles?page=${page}&size=${size}`),
    refetchInterval: 15_000,
  });

export const useMobileMoneyNetwork = (entityType: MobileMoneyEntityType | null, entityReference: string | null) =>
  useQuery<MobileMoneyNetwork>({
    queryKey: ["mobile-money", "network", entityType, entityReference],
    queryFn: () => apiClient.get<MobileMoneyNetwork>(
      `mobile-money/network?entityType=${entityType}&entityReference=${encodeURIComponent(entityReference || "")}`),
    enabled: entityType !== null && entityReference !== null,
  });

export const useVaspDirectory = (search = "", page = 0, size = 50) => {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (search.trim()) params.set("search", search.trim());
  return useQuery<PageResult<VaspEntry>>({ queryKey: ["virtual-assets", "vasps", search, page, size],
    queryFn: () => apiClient.get<PageResult<VaspEntry>>(`virtual-assets/vasps?${params}`) });
};
export const useVaspScreeningHistory = (vaspId?: number, page = 0, size = 50) => {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (vaspId) params.set("vaspId", String(vaspId));
  return useQuery<PageResult<VaspScreeningRecord>>({ queryKey: ["virtual-assets", "vasp-screenings", vaspId, page, size],
    queryFn: () => apiClient.get<PageResult<VaspScreeningRecord>>(`virtual-assets/vasp-screenings?${params}`) });
};
export const useCryptoWalletProfiles = (page = 0, size = 50) =>
  useQuery<PageResult<CryptoWalletProfile>>({ queryKey: ["virtual-assets", "wallets", page, size],
    queryFn: () => apiClient.get<PageResult<CryptoWalletProfile>>(`virtual-assets/wallets?page=${page}&size=${size}`), refetchInterval: 30_000 });
export const useWalletScreeningHistory = (walletId?: number, page = 0, size = 50) => {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (walletId) params.set("walletId", String(walletId));
  return useQuery<PageResult<WalletScreeningRecord>>({ queryKey: ["virtual-assets", "screenings", walletId, page, size],
    queryFn: () => apiClient.get<PageResult<WalletScreeningRecord>>(`virtual-assets/wallet-screenings?${params}`) });
};
export const useCryptoTransactions = (page = 0, size = 100) =>
  useQuery<PageResult<CryptoTransactionSummary>>({ queryKey: ["virtual-assets", "transactions", page, size],
    queryFn: () => apiClient.get<PageResult<CryptoTransactionSummary>>(`virtual-assets/transactions?page=${page}&size=${size}`) });
export const useTravelRulePolicies = () => useQuery<TravelRulePolicy[]>({ queryKey: ["virtual-assets", "travel-rule", "policies"],
  queryFn: () => apiClient.get<TravelRulePolicy[]>("virtual-assets/travel-rule/policies") });
export const useTravelRuleTransfers = (page = 0, size = 50) => useQuery<PageResult<TravelRuleTransfer>>({
  queryKey: ["virtual-assets", "travel-rule", "transfers", page, size],
  queryFn: () => apiClient.get<PageResult<TravelRuleTransfer>>(`virtual-assets/travel-rule/transfers?page=${page}&size=${size}`), refetchInterval: 30_000 });
export const useRegulatorGrants = () => useQuery<RegulatorGrant[]>({ queryKey: ["virtual-assets", "regulator-grants"],
  queryFn: () => apiClient.get<RegulatorGrant[]>("virtual-assets/regulator-grants") });
