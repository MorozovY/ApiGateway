// API функции для метрик (Story 6.5)
import axios from '@/shared/utils/axios'
import type {
  MetricsSummary,
  TopRoute,
  RouteMetrics,
  MetricsPeriod,
  MetricsSortBy,
} from '../types/metrics.types'

const BASE_URL = '/api/v1/metrics'

/**
 * Получение сводки метрик за период.
 *
 * GET /api/v1/metrics/summary?period=5m
 */
export async function getSummary(period: MetricsPeriod = '5m'): Promise<MetricsSummary> {
  const { data } = await axios.get<MetricsSummary>(`${BASE_URL}/summary`, {
    params: { period },
  })
  return data
}

/**
 * Получение метрик для конкретного маршрута.
 *
 * GET /api/v1/metrics/routes/{routeId}?period=5m
 */
export async function getRouteMetrics(
  routeId: string,
  period: MetricsPeriod = '5m'
): Promise<RouteMetrics> {
  const { data } = await axios.get<RouteMetrics>(`${BASE_URL}/routes/${routeId}`, {
    params: { period },
  })
  return data
}

/**
 * Получение топ-маршрутов по указанному критерию.
 *
 * GET /api/v1/metrics/top-routes?by=requests&limit=10
 */
export async function getTopRoutes(
  sortBy: MetricsSortBy = 'requests',
  limit: number = 10
): Promise<TopRoute[]> {
  const { data } = await axios.get<TopRoute[]>(`${BASE_URL}/top-routes`, {
    params: { by: sortBy, limit },
  })
  return data
}
