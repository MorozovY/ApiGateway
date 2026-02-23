// React Query hook для маршрутов по upstream (Story 11.1)
import { useQuery } from '@tanstack/react-query'
import { fetchRoutes } from '@features/routes/api/routesApi'

/**
 * Hook для получения маршрутов по upstream host.
 *
 * Используется в expandable rows на странице Integrations.
 * Query выполняется только когда host передан.
 *
 * @param host - Upstream host для фильтрации маршрутов (partial match)
 *
 * Query key: ['upstreams', 'routes', host]
 */
export function useUpstreamRoutes(host: string | null) {
  return useQuery({
    queryKey: ['upstreams', 'routes', host],
    queryFn: () => fetchRoutes({ upstream: host! }),
    enabled: !!host,
  })
}
