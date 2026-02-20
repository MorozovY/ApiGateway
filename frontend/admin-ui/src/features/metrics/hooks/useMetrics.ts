// React Query hooks для метрик (Story 6.5)
import { useQuery } from '@tanstack/react-query'
import * as metricsApi from '../api/metricsApi'
import type { MetricsPeriod, MetricsSortBy } from '../types/metrics.types'

/**
 * Ключи для React Query кэша.
 */
const QUERY_KEYS = {
  summary: (period: MetricsPeriod) => ['metrics', 'summary', period] as const,
  topRoutes: (sortBy: MetricsSortBy, limit: number) =>
    ['metrics', 'top-routes', sortBy, limit] as const,
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
    refetchInterval: 10000, // 10 секунд auto-refresh (AC2)
    staleTime: 5000,
  })
}

/**
 * Hook для получения топ-маршрутов.
 *
 * Автоматически обновляется каждые 10 секунд.
 */
export function useTopRoutes(sortBy: MetricsSortBy = 'requests', limit: number = 10) {
  return useQuery({
    queryKey: QUERY_KEYS.topRoutes(sortBy, limit),
    queryFn: () => metricsApi.getTopRoutes(sortBy, limit),
    refetchInterval: 10000, // 10 секунд auto-refresh (AC2)
    staleTime: 5000,
  })
}

/**
 * Hook для получения детальных метрик маршрута.
 */
export function useRouteMetrics(routeId: string | undefined, period: MetricsPeriod = '5m') {
  return useQuery({
    // SAFETY: routeId гарантированно определён благодаря enabled: !!routeId
    queryKey: QUERY_KEYS.routeMetrics(routeId!, period),
    queryFn: () => metricsApi.getRouteMetrics(routeId!, period),
    enabled: !!routeId,
    refetchInterval: 10000,
    staleTime: 5000,
  })
}
