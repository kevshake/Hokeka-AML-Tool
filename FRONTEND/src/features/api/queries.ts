import { useQuery } from "@tanstack/react-query";
import { apiClient } from "../../lib/apiClient";
import type {
  Case,
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
  return useQuery({
    queryKey: ["case", caseId, "timeline"],
    queryFn: () => apiClient.get(`cases/${caseId}/timeline`),
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
export const useRiskHeatmap = (type: "customer" | "merchant") => {
  return useQuery<Record<string, number>>({
    queryKey: ["risk", "heatmap", type],
    queryFn: () => apiClient.get<Record<string, number>>(`analytics/risk/heatmap/${type}`).catch(() => ({})),
  });
};

interface RiskTrends {
  labels?: string[];
  data?: number[];
  [key: string]: unknown;
}

export const useRiskTrends = (days: number = 30) => {
  return useQuery<RiskTrends>({
    queryKey: ["risk", "trends", days],
    queryFn: () => apiClient.get<RiskTrends>(`analytics/risk/trends?days=${days}`).catch(() => ({})),
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
    queryKey: ["monitoring", "dashboard-stats"],
    queryFn: () => apiClient.get<Record<string, unknown>>("monitoring/dashboard/stats").catch(() => ({})),
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
    queryFn: () => apiClient.get<any>(`reporting/regulatory/${reportType.toUpperCase()}`).catch(() => null),
  });
};

export const useAllPsps = () => {
  return useQuery<Psp[]>({
    queryKey: ["psps"],
    queryFn: () => apiClient.get<Psp[]>("psps").catch(() => []),
  });
};
