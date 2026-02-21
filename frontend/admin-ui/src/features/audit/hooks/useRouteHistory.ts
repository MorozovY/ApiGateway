// React Query hook для истории изменений маршрута (Story 7.6, AC1)
import { useQuery } from '@tanstack/react-query'
import { fetchRouteHistory } from '@features/routes/api/routesApi'

/**
 * Query key factory для истории маршрута.
 *
 * Использование:
 * - routeHistoryKeys.all — для инвалидации всех историй
 * - routeHistoryKeys.byRoute(routeId) — для конкретного маршрута
 */
export const routeHistoryKeys = {
  all: ['routes', 'history'] as const,
  byRoute: (routeId: string) => ['routes', routeId, 'history'] as const,
}

/**
 * @deprecated Используйте routeHistoryKeys вместо этой константы
 */
export const ROUTE_HISTORY_QUERY_KEY = 'routes'

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
    queryKey: routeHistoryKeys.byRoute(routeId!),
    queryFn: () => fetchRouteHistory(routeId!),
    enabled: !!routeId,
  })
}
