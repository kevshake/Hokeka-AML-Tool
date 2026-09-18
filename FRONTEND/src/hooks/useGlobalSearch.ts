import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../lib/apiClient'

export interface GlobalSearchHit {
  entityType: string
  entityId: string
  title: string
  subtitle?: string | null
  status?: string | null
  recordPath?: string | null
}

export interface GlobalSearchResponse {
  query: string
  totalHits: number
  hits: GlobalSearchHit[]
}

export function useGlobalSearch(query: string, enabled: boolean) {
  const trimmed = query.trim()
  return useQuery({
    queryKey: ['global-search', trimmed],
    enabled: enabled && trimmed.length >= 2,
    queryFn: async () => {
      const params = new URLSearchParams({ q: trimmed, page: '0', size: '8' })
      return apiClient.get<GlobalSearchResponse>(`/search?${params.toString()}`)
    },
    staleTime: 30_000,
  })
}
