// React Query hook для upstream сервисов (Story 7.6, AC3)
import { useQuery } from '@tanstack/react-query'
import { fetchUpstreams } from '../api/upstreamsApi'

/**
 * Ключ для React Query кэша upstream сервисов.
 */
export const UPSTREAMS_QUERY_KEY = 'upstreams'

/**
 * Hook для получения списка upstream сервисов.
 *
 * Возвращает список уникальных upstream хостов с количеством маршрутов.
 * Используется на странице Integrations Report.
 *
 * Query key: ['upstreams']
 */
export function useUpstreams() {
  return useQuery({
    queryKey: [UPSTREAMS_QUERY_KEY],
    queryFn: fetchUpstreams,
  })
}
