import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { apiClient } from '../lib/apiClient'
import {
  fetchDashboardSparklines,
  useDashboardStats,
  type DashboardStats,
} from './useDashboard'

export interface NavBadgeCounts {
  alertCount: number | undefined
  caseCount: number | undefined
  isLoading: boolean
}

/**
 * Sidebar / header badge counts from live dashboard stats.
 * Prefetches the dashboard KPI bundle once so the Dashboard route
 * paints from warm cache instead of a cold waterfall.
 */
export function useNavBadges(): NavBadgeCounts {
  const queryClient = useQueryClient()
  const stats = useDashboardStats()

  useEffect(() => {
    void queryClient.prefetchQuery({
      queryKey: ['dashboard', 'sparklines', 7],
      queryFn: () => fetchDashboardSparklines(queryClient, 7),
      staleTime: 45_000,
    })
    void queryClient.prefetchQuery({
      queryKey: ['dashboard', 'live-alerts', 12],
      queryFn: () => apiClient.get('dashboard/live-alerts?limit=12'),
      staleTime: 20_000,
    })
  }, [queryClient])

  const data = stats.data as DashboardStats | undefined
  const isLoading = stats.isLoading && !data

  return {
    alertCount: isLoading ? undefined : (data?.openAlertsCount ?? 0),
    caseCount: isLoading ? undefined : (data?.openCases ?? 0),
    isLoading,
  }
}
