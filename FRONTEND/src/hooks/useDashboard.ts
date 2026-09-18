import { useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { apiClient } from '../lib/apiClient'
import type { Alert } from '../types'

/** Fresh enough for KPI tiles; avoids refetch storms on every focus/nav. */
const STALE = 45_000
const REFETCH = 60_000
/** Live queue / alerts can refresh a bit more often. */
const LIVE_STALE = 20_000
const LIVE_REFETCH = 30_000

// ---- response shapes (mirrored from BACKEND DashboardController) ----

export interface DashboardTrends {
  totalTransactionsDelta?: number
  flaggedDelta?: number
  openCasesDelta?: number
  highRiskCustomersDelta?: number
  screeningMatchesDelta?: number
  complianceHealthDelta?: number
}

export interface DashboardStats {
  totalMerchants?: number
  activeMerchants?: number
  pendingScreening?: number
  openCases?: number
  openAlertsCount?: number
  urgentCases?: number
  flaggedToday?: number
  transactionsMonitoredToday?: number
  highRiskCustomerCount?: number
  complianceHealthScore?: number
  trends?: DashboardTrends
}

export interface CountryRisk {
  countryCode: string
  countryName: string
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH'
  transactionCount: number
  alertCount: number
}

export interface CasesClosedRecent {
  resolved: number
  falsePositive: number
  closureRate: number
  closureRateTrend: number
  sparkline: number[]
}

export interface ScreeningResultsToday {
  pepMatches: number
  sanctionsMatches: number
  adverseMediaHits: number
  watchlistMatches: number
}

export interface TopRiskMerchant {
  rank: number
  merchantId: number
  name?: string | null
  riskScore?: number | null
  riskLevel: string
}

export interface ComplianceHealth {
  kycCompletion: number
  cddReviews: number
  eddReviews: number
  sarFilingSla: number
}

export interface AlertTrendsResponse {
  labels: string[]
  data: number[]
  asOf?: string
}

export interface RiskDistribution {
  [riskLevel: string]: number
}

export interface SanctionsStatus {
  lastRun?: string
  status?: string
  merchantsProcessed?: number
  hitsFound?: number
}

export interface FraudMetrics {
  auc?: string
  precisionAt100?: string
  driftScore?: string
  avgLatencyMs?: string
  modelDate?: string
  precision?: string
  recall?: string
  f1?: string
  falsePositiveRate?: string
  truePositiveCount?: number
  falsePositiveCount?: number
  reviewedAlertCount?: number
  totalAlertCount?: number
}

export interface TransactionVolume {
  labels: string[]
  data: number[]
  pspId?: number | null
}

export interface DailyTrendResponse {
  labels: string[]
  data: number[]
}

export interface CasesPriority {
  [priority: string]: number
}

export interface MerchantsPage<T = unknown> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface MerchantListItem {
  merchantId?: number | string
  legalName?: string
  tradingName?: string
  riskLevel?: string
  status?: string
}

/** Bundled sparkline payload — one round-trip for all KPI series. */
export interface DashboardSparklines {
  transactionVolume: TransactionVolume
  alertTrends: AlertTrendsResponse
  caseTrends: DailyTrendResponse
  screeningMatchTrends: DailyTrendResponse
  highRiskTrends: DailyTrendResponse
}

function keepPrevious<T>(prev: T | undefined) {
  return prev
}

/** Fetch sparklines and seed per-series caches (shared by hook + layout prefetch). */
export async function fetchDashboardSparklines(
  queryClient: QueryClient,
  days = 7,
): Promise<DashboardSparklines> {
  const data = await apiClient.get<DashboardSparklines>(`dashboard/sparklines?days=${days}`)
  queryClient.setQueryData(['dashboard', 'transaction-volume', days], data.transactionVolume)
  queryClient.setQueryData(['dashboard', 'alert-trends', days], data.alertTrends)
  queryClient.setQueryData(['dashboard', 'case-trends', days], data.caseTrends)
  queryClient.setQueryData(['dashboard', 'screening-match-trends', days], data.screeningMatchTrends)
  queryClient.setQueryData(['dashboard', 'high-risk-trends', days], data.highRiskTrends)
  return data
}

// ---- hooks ----

export const useDashboardStats = () =>
  useQuery<DashboardStats>({
    queryKey: ['dashboard', 'stats'],
    queryFn: () => apiClient.get<DashboardStats>('dashboard/stats'),
    staleTime: STALE,
    refetchInterval: REFETCH,
    placeholderData: keepPrevious,
  })

/**
 * Single request for all KPI sparkline series. Seeds the individual
 * query caches so child widgets (AlertTrends, etc.) reuse the data
 * without extra HTTP calls.
 */
export const useDashboardSparklines = (days = 7) => {
  const queryClient = useQueryClient()
  return useQuery<DashboardSparklines>({
    queryKey: ['dashboard', 'sparklines', days],
    queryFn: () => fetchDashboardSparklines(queryClient, days),
    staleTime: STALE,
    refetchInterval: REFETCH,
    placeholderData: keepPrevious,
  })
}

export const useLiveAlerts = (limit = 6) =>
  useQuery<Alert[]>({
    queryKey: ['dashboard', 'live-alerts', limit],
    queryFn: () => apiClient.get<Alert[]>(`dashboard/live-alerts?limit=${limit}`),
    staleTime: LIVE_STALE,
    refetchInterval: LIVE_REFETCH,
    placeholderData: keepPrevious,
  })

export const useRiskDistribution = () =>
  useQuery<RiskDistribution>({
    queryKey: ['dashboard', 'risk-distribution'],
    queryFn: () => apiClient.get<RiskDistribution>('dashboard/risk-distribution'),
    staleTime: STALE,
    refetchInterval: REFETCH,
    placeholderData: keepPrevious,
  })

export const useSanctionsStatus = () =>
  useQuery<SanctionsStatus>({
    queryKey: ['dashboard', 'sanctions-status'],
    queryFn: () => apiClient.get<SanctionsStatus>('dashboard/sanctions/status'),
    staleTime: STALE,
    refetchInterval: REFETCH,
    placeholderData: keepPrevious,
  })

export const useFraudMetrics = () =>
  useQuery<FraudMetrics>({
    queryKey: ['dashboard', 'fraud-metrics'],
    queryFn: () => apiClient.get<FraudMetrics>('dashboard/fraud-metrics'),
    staleTime: STALE,
    refetchInterval: REFETCH,
    placeholderData: keepPrevious,
  })

export const useTransactionVolume = (days = 7) =>
  useQuery<TransactionVolume>({
    queryKey: ['dashboard', 'transaction-volume', days],
    queryFn: () =>
      apiClient.get<TransactionVolume>(`dashboard/transaction-volume?days=${days}`),
    staleTime: STALE,
    refetchInterval: REFETCH,
    placeholderData: keepPrevious,
  })

export const useCasesPriority = () =>
  useQuery<CasesPriority>({
    queryKey: ['dashboard', 'cases-priority'],
    queryFn: () => apiClient.get<CasesPriority>('dashboard/cases/priority'),
    staleTime: STALE,
    refetchInterval: REFETCH,
    placeholderData: keepPrevious,
  })

// Backend-sorted top-risk merchants — `/dashboard/merchants/top-risk?limit=N`
export const useTopRiskMerchants = (limit = 5) =>
  useQuery<TopRiskMerchant[]>({
    queryKey: ['dashboard', 'top-risk-merchants', limit],
    queryFn: () =>
      apiClient.get<TopRiskMerchant[]>(`dashboard/merchants/top-risk?limit=${limit}`),
    staleTime: STALE,
    refetchInterval: REFETCH,
    placeholderData: keepPrevious,
  })

export const useRiskHeatmap = () =>
  useQuery<CountryRisk[]>({
    queryKey: ['dashboard', 'risk-heatmap'],
    queryFn: () => apiClient.get<CountryRisk[]>('dashboard/risk-heatmap'),
    staleTime: STALE,
    refetchInterval: REFETCH,
    placeholderData: keepPrevious,
  })

export const useCasesClosedRecent = () =>
  useQuery<CasesClosedRecent>({
    queryKey: ['dashboard', 'cases-closed-recent'],
    queryFn: () => apiClient.get<CasesClosedRecent>('dashboard/cases/closed-recent'),
    staleTime: STALE,
    refetchInterval: REFETCH,
    placeholderData: keepPrevious,
  })

export const useScreeningResultsToday = () =>
  useQuery<ScreeningResultsToday>({
    queryKey: ['dashboard', 'screening-results-today'],
    queryFn: () =>
      apiClient.get<ScreeningResultsToday>('dashboard/screening/results-today'),
    staleTime: STALE,
    refetchInterval: REFETCH,
    placeholderData: keepPrevious,
  })

export const useComplianceHealth = () =>
  useQuery<ComplianceHealth>({
    queryKey: ['dashboard', 'compliance-health'],
    queryFn: () => apiClient.get<ComplianceHealth>('dashboard/compliance/health'),
    staleTime: STALE,
    refetchInterval: REFETCH,
    placeholderData: keepPrevious,
  })

export const useAlertTrends = (days = 7) =>
  useQuery<AlertTrendsResponse>({
    queryKey: ['dashboard', 'alert-trends', days],
    queryFn: () =>
      apiClient.get<AlertTrendsResponse>(`dashboard/alerts/trends?days=${days}`),
    staleTime: STALE,
    refetchInterval: REFETCH,
    placeholderData: keepPrevious,
  })

export const useCaseTrends = (days = 7) =>
  useQuery<DailyTrendResponse>({
    queryKey: ['dashboard', 'case-trends', days],
    queryFn: () => apiClient.get<DailyTrendResponse>(`dashboard/cases/trends?days=${days}`),
    staleTime: STALE,
    refetchInterval: REFETCH,
    placeholderData: keepPrevious,
  })

export const useScreeningMatchTrends = (days = 7) =>
  useQuery<DailyTrendResponse>({
    queryKey: ['dashboard', 'screening-match-trends', days],
    queryFn: () =>
      apiClient.get<DailyTrendResponse>(`dashboard/screening/matches-trends?days=${days}`),
    staleTime: STALE,
    refetchInterval: REFETCH,
    placeholderData: keepPrevious,
  })

export const useHighRiskTrends = (days = 7) =>
  useQuery<DailyTrendResponse>({
    queryKey: ['dashboard', 'high-risk-trends', days],
    queryFn: () =>
      apiClient.get<DailyTrendResponse>(`dashboard/merchants/high-risk-trends?days=${days}`),
    staleTime: STALE,
    refetchInterval: REFETCH,
    placeholderData: keepPrevious,
  })
