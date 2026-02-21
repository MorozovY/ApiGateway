// React Query hook для Health Check (Story 8.1)
import { useQuery } from '@tanstack/react-query'
import * as healthApi from '../api/healthApi'
import { HEALTH_REFRESH_INTERVAL, METRICS_STALE_TIME } from '../config/metricsConfig'

/**
 * Ключи для React Query кэша.
 */
const QUERY_KEYS = {
  servicesHealth: ['health', 'services'] as const,
}

/**
 * Hook для получения статуса здоровья всех сервисов.
 *
 * AC3: Автоматически обновляется каждые 30 секунд.
 * Возвращает функцию refetch для ручного обновления.
 */
export function useHealth() {
  const query = useQuery({
    queryKey: QUERY_KEYS.servicesHealth,
    queryFn: healthApi.getServicesHealth,
    refetchInterval: HEALTH_REFRESH_INTERVAL, // 30 секунд auto-refresh (AC3)
    staleTime: METRICS_STALE_TIME,
  })

  return {
    ...query,
    // Возвращаем refetch напрямую для удобства использования (AC3)
    refresh: query.refetch,
  }
}
