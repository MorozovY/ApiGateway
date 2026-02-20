// React Query hook для истории изменений маршрута (Story 7.6, AC1)
import { useQuery } from '@tanstack/react-query'
import { fetchRouteHistory } from '@features/routes/api/routesApi'

/**
 * Ключ для React Query кэша истории маршрута.
 */
export const ROUTE_HISTORY_QUERY_KEY = 'route-history'

/**
 * Hook для получения истории изменений маршрута.
 *
 * Загружает историю только если routeId передан (enabled: !!routeId).
 * Возвращает хронологический список изменений (newest first).
 *
 * Query key: ['routes', routeId, 'history']
 */
export function useRouteHistory(routeId: string | undefined) {
  return useQuery({
    queryKey: ['routes', routeId, 'history'],
    queryFn: () => fetchRouteHistory(routeId!),
    enabled: !!routeId,
  })
}
