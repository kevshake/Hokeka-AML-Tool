import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../lib/apiClient'
import type { Alert } from '../types'

const STALE = 30_000

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
  urgentCases?: number
  flaggedToday?: number
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

// ---- hooks ----

export const useDashboardStats = () =>
  useQuery<DashboardStats>({
    queryKey: ['dashboard', 'stats'],
    queryFn: () => apiClient.get<DashboardStats>('dashboard/stats'),
    staleTime: STALE,
  })

export const useLiveAlerts = (limit = 6) =>
  useQuery<Alert[]>({
    queryKey: ['dashboard', 'live-alerts', limit],
    queryFn: () =>
      apiClient.get<Alert[]>(`dashboard/live-alerts?limit=${limit}`).catch(() => [] as Alert[]),
    staleTime: STALE,
  })

export const useRiskDistribution = () =>
  useQuery<RiskDistribution>({
    queryKey: ['dashboard', 'risk-distribution'],
    queryFn: () => apiClient.get<RiskDistribution>('dashboard/risk-distribution'),
    staleTime: STALE,
  })

export const useSanctionsStatus = () =>
  useQuery<SanctionsStatus>({
    queryKey: ['dashboard', 'sanctions-status'],
    queryFn: () => apiClient.get<SanctionsStatus>('dashboard/sanctions/status'),
    staleTime: STALE,
  })

export const useFraudMetrics = () =>
  useQuery<FraudMetrics>({
    queryKey: ['dashboard', 'fraud-metrics'],
    queryFn: () => apiClient.get<FraudMetrics>('dashboard/fraud-metrics'),
    staleTime: STALE,
  })

export const useTransactionVolume = (days = 7) =>
  useQuery<TransactionVolume>({
    queryKey: ['dashboard', 'transaction-volume', days],
    queryFn: () =>
      apiClient.get<TransactionVolume>(`dashboard/transaction-volume?days=${days}`),
    staleTime: STALE,
  })

export const useCasesPriority = () =>
  useQuery<CasesPriority>({
    queryKey: ['dashboard', 'cases-priority'],
    queryFn: () => apiClient.get<CasesPriority>('dashboard/cases/priority'),
    staleTime: STALE,
  })

// Backend-sorted top-risk merchants — `/dashboard/merchants/top-risk?limit=N`
// returns merchants ranked by stored krs (and risk-level ordinal) without
// loading every merchant client-side.
export const useTopRiskMerchants = (limit = 5) =>
  useQuery<TopRiskMerchant[]>({
    queryKey: ['dashboard', 'top-risk-merchants', limit],
    queryFn: () =>
      apiClient
        .get<TopRiskMerchant[]>(`dashboard/merchants/top-risk?limit=${limit}`)
        .catch(() => [] as TopRiskMerchant[]),
    staleTime: STALE,
  })

export const useRiskHeatmap = () =>
  useQuery<CountryRisk[]>({
    queryKey: ['dashboard', 'risk-heatmap'],
    queryFn: () =>
      apiClient
        .get<CountryRisk[]>('dashboard/risk-heatmap')
        .catch(() => [] as CountryRisk[]),
    staleTime: STALE,
  })

export const useCasesClosedRecent = () =>
  useQuery<CasesClosedRecent>({
    queryKey: ['dashboard', 'cases-closed-recent'],
    queryFn: () => apiClient.get<CasesClosedRecent>('dashboard/cases/closed-recent'),
    staleTime: STALE,
  })

export const useScreeningResultsToday = () =>
  useQuery<ScreeningResultsToday>({
    queryKey: ['dashboard', 'screening-results-today'],
    queryFn: () =>
      apiClient.get<ScreeningResultsToday>('dashboard/screening/results-today'),
    staleTime: STALE,
  })

export const useComplianceHealth = () =>
  useQuery<ComplianceHealth>({
    queryKey: ['dashboard', 'compliance-health'],
    queryFn: () => apiClient.get<ComplianceHealth>('dashboard/compliance/health'),
    staleTime: STALE,
  })

export const useAlertTrends = (days = 7) =>
  useQuery<AlertTrendsResponse>({
    queryKey: ['dashboard', 'alert-trends', days],
    queryFn: () =>
      apiClient.get<AlertTrendsResponse>(`dashboard/alerts/trends?days=${days}`),
    staleTime: STALE,
  })
