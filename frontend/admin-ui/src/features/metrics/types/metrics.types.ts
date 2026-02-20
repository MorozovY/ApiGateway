// Типы для Metrics API (Story 6.5)

/**
 * Допустимые значения периода для метрик.
 */
export type MetricsPeriod = '5m' | '15m' | '1h' | '6h' | '24h'

/**
 * Допустимые значения сортировки для топ-маршрутов.
 */
export type MetricsSortBy = 'requests' | 'latency' | 'errors'

/**
 * Сводка метрик за период.
 * Ответ от GET /api/v1/metrics/summary
 */
export interface MetricsSummary {
  period: string
  totalRequests: number
  requestsPerSecond: number
  avgLatencyMs: number
  p95LatencyMs: number
  p99LatencyMs: number
  errorRate: number
  errorCount: number
  activeRoutes: number
}

/**
 * Метрики отдельного маршрута.
 * Ответ от GET /api/v1/metrics/top-routes
 */
export interface TopRoute {
  routeId: string
  path: string
  requestsPerSecond: number
  avgLatencyMs: number
  errorRate: number
}

/**
 * Детальные метрики маршрута.
 * Ответ от GET /api/v1/metrics/routes/{routeId}
 */
export interface RouteMetrics {
  routeId: string
  path: string
  period: string
  requestsPerSecond: number
  avgLatencyMs: number
  p95LatencyMs: number
  errorRate: number
  statusBreakdown: {
    '2xx': number
    '4xx': number
    '5xx': number
  }
}
