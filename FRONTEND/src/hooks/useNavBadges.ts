import { useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useLocation } from 'react-router-dom'
import { apiClient } from '../lib/apiClient'
import { useDashboardStats } from './useDashboard'

const BADGE_STALE = 45_000

export interface NavBadgeCounts {
  alertCount: number | undefined
  caseCount: number | undefined
  isLoading: boolean
}

/** Sidebar / header badge counts from live backend stats. */
export function useNavBadges(): NavBadgeCounts {
  const location = useLocation()
  const queryClient = useQueryClient()
  const stats = useDashboardStats()

  const alerts = useQuery({
    queryKey: ['alerts', 'count', 'active'],
    queryFn: () => apiClient.get<{ count?: number }>('alerts/count/active'),
    staleTime: BADGE_STALE,
    refetchInterval: BADGE_STALE,
  })

  const cases = useQuery({
    queryKey: ['cases', 'count', 'active'],
    queryFn: () => apiClient.get<{ count?: number }>('compliance/cases/count/active'),
    staleTime: BADGE_STALE,
    refetchInterval: BADGE_STALE,
  })

  useEffect(() => {
    void queryClient.invalidateQueries({ queryKey: ['alerts', 'count', 'active'] })
    void queryClient.invalidateQueries({ queryKey: ['cases', 'count', 'active'] })
    void queryClient.invalidateQueries({ queryKey: ['dashboard', 'stats'] })
  }, [location.pathname, queryClient])

  const isLoading = stats.isLoading || alerts.isLoading || cases.isLoading

  return {
    alertCount: isLoading ? undefined : (alerts.data?.count ?? 0),
    caseCount: isLoading ? undefined : (cases.data?.count ?? stats.data?.openCases ?? 0),
    isLoading,
  }
}
