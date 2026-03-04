// React Query hooks для метрик (Story 6.5, Story 16.8 — dynamic refetchInterval)
import { useQuery } from '@tanstack/react-query'
import * as metricsApi from '../api/metricsApi'
import { METRICS_STALE_TIME } from '../config/metricsConfig'
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
 * Опции для настройки auto-refresh (Story 16.8).
 */
export interface MetricsQueryOptions {
  /** Интервал обновления в мс, или false для отключения (Story 16.8) */
  refetchInterval?: number | false
}

/**
 * Hook для получения сводки метрик.
 *
 * Story 16.8: refetchInterval теперь настраивается через options.
 * По умолчанию auto-refresh ВЫКЛЮЧЕН (AC1).
 *
 * React Query автоматически приостанавливает refresh при неактивной вкладке
 * через refetchIntervalInBackground: false (по умолчанию) — это реализует AC3.
 *
 * @param period - период времени для метрик
 * @param options - опции, включая refetchInterval
 */
export function useMetricsSummary(period: MetricsPeriod = '5m', options?: MetricsQueryOptions) {
  return useQuery({
    queryKey: QUERY_KEYS.summary(period),
    queryFn: () => metricsApi.getSummary(period),
    // Story 16.8: динамический refetchInterval (AC2, AC3)
    // refetchIntervalInBackground: false (default) — паузит при неактивной вкладке
    refetchInterval: options?.refetchInterval ?? false,
    staleTime: METRICS_STALE_TIME,
  })
}

/**
 * Hook для получения топ-маршрутов.
 *
 * Story 10.10: Добавлен параметр period для фильтрации по time range.
 * Story 16.8: refetchInterval настраивается через options.
 *
 * @param sortBy - критерий сортировки
 * @param limit - количество маршрутов
 * @param period - период времени
 * @param options - опции, включая refetchInterval
 */
export function useTopRoutes(
  sortBy: MetricsSortBy = 'requests',
  limit: number = 10,
  period: MetricsPeriod = '5m',
  options?: MetricsQueryOptions
) {
  return useQuery({
    queryKey: QUERY_KEYS.topRoutes(sortBy, limit, period),
    queryFn: () => metricsApi.getTopRoutes(sortBy, limit, period),
    // Story 16.8: динамический refetchInterval (AC2, AC3)
    refetchInterval: options?.refetchInterval ?? false,
    staleTime: METRICS_STALE_TIME,
  })
}

/**
 * Hook для получения детальных метрик маршрута.
 *
 * Story 16.8: refetchInterval настраивается через options.
 *
 * @param routeId - ID маршрута
 * @param period - период времени
 * @param options - опции, включая refetchInterval
 */
export function useRouteMetrics(
  routeId: string | undefined,
  period: MetricsPeriod = '5m',
  options?: MetricsQueryOptions
) {
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
    // Story 16.8: динамический refetchInterval (AC2, AC3)
    refetchInterval: options?.refetchInterval ?? false,
    staleTime: METRICS_STALE_TIME,
  })
}
