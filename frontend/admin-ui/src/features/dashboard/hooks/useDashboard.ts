/**
 * React Query hooks для Dashboard (Story 16.2)
 */
import { useQuery } from '@tanstack/react-query'
import * as dashboardApi from '../api/dashboardApi'

// Интервал обновления для Dashboard (30 секунд)
const DASHBOARD_REFRESH_INTERVAL = 30000
const DASHBOARD_STALE_TIME = 10000

/**
 * Ключи для React Query кэша.
 */
const QUERY_KEYS = {
  summary: ['dashboard', 'summary'] as const,
  recentActivity: (limit: number) => ['dashboard', 'recent-activity', limit] as const,
}

/**
 * Hook для получения сводки Dashboard.
 *
 * Автоматически обновляется каждые 30 секунд.
 */
export function useDashboardSummary() {
  const query = useQuery({
    queryKey: QUERY_KEYS.summary,
    queryFn: dashboardApi.getDashboardSummary,
    refetchInterval: DASHBOARD_REFRESH_INTERVAL,
    staleTime: DASHBOARD_STALE_TIME,
  })

  return {
    ...query,
    refresh: query.refetch,
  }
}

/**
 * Hook для получения последней активности.
 *
 * @param limit максимальное количество записей (default: 5)
 */
export function useRecentActivity(limit = 5) {
  const query = useQuery({
    queryKey: QUERY_KEYS.recentActivity(limit),
    queryFn: () => dashboardApi.getRecentActivity(limit),
    refetchInterval: DASHBOARD_REFRESH_INTERVAL,
    staleTime: DASHBOARD_STALE_TIME,
  })

  return {
    ...query,
    refresh: query.refetch,
  }
}
