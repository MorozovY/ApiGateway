// React Query hooks для метрик (Story 6.5)
import { useQuery } from '@tanstack/react-query'
import * as metricsApi from '../api/metricsApi'
import { METRICS_REFRESH_INTERVAL, METRICS_STALE_TIME } from '../config/metricsConfig'
import type { MetricsPeriod, MetricsSortBy } from '../types/metrics.types'

/**
 * Ключи для React Query кэша.
 */
const QUERY_KEYS = {
  summary: (period: MetricsPeriod) => ['metrics', 'summary', period] as const,
  topRoutes: (sortBy: MetricsSortBy, limit: number, period: MetricsPeriod) =>
    ['metrics', 'top-routes', sortBy, limit, period] as const,
  routeMetrics: (routeId: string, period: MetricsPeriod) =>
    ['metrics', 'routes', routeId, period] as const,
}

/**
 * Hook для получения сводки метрик.
 *
 * Автоматически обновляется каждые 10 секунд (AC2).
 */
export function useMetricsSummary(period: MetricsPeriod = '5m') {
  return useQuery({
    queryKey: QUERY_KEYS.summary(period),
    queryFn: () => metricsApi.getSummary(period),
    refetchInterval: METRICS_REFRESH_INTERVAL, // 10 секунд auto-refresh (AC2)
    staleTime: METRICS_STALE_TIME,
  })
}

/**
 * Hook для получения топ-маршрутов.
 *
 * Автоматически обновляется каждые 10 секунд.
 * Story 10.10: Добавлен параметр period для фильтрации по time range.
 */
export function useTopRoutes(
  sortBy: MetricsSortBy = 'requests',
  limit: number = 10,
  period: MetricsPeriod = '5m'
) {
  return useQuery({
    queryKey: QUERY_KEYS.topRoutes(sortBy, limit, period),
    queryFn: () => metricsApi.getTopRoutes(sortBy, limit, period),
    refetchInterval: METRICS_REFRESH_INTERVAL, // 10 секунд auto-refresh (AC2)
    staleTime: METRICS_STALE_TIME,
  })
}

/**
 * Hook для получения детальных метрик маршрута.
 */
export function useRouteMetrics(routeId: string | undefined, period: MetricsPeriod = '5m') {
  return useQuery({
    // Query выполняется только если routeId определён (enabled: !!routeId)
    queryKey: QUERY_KEYS.routeMetrics(routeId ?? '', period),
    queryFn: () => {
      if (!routeId) {
        throw new Error('routeId is required')
      }
      return metricsApi.getRouteMetrics(routeId, period)
    },
    enabled: !!routeId,
    refetchInterval: METRICS_REFRESH_INTERVAL,
    staleTime: METRICS_STALE_TIME,
  })
}
