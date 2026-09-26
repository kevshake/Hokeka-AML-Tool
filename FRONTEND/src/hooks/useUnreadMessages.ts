import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../lib/apiClient'

export function useUnreadMessages() {
  return useQuery({
    queryKey: ['messages', 'unread-count'],
    queryFn: async () => {
      const res = await apiClient.get<{ count: number }>('messages/unread/count')
      return res.count ?? 0
    },
    staleTime: 30_000,
    refetchInterval: 60_000,
  })
}
