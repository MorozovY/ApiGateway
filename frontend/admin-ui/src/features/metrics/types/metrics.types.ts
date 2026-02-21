// Типы для Metrics API (Story 6.5, 8.1)

/**
 * Допустимые значения периода для метрик.
 */
export type MetricsPeriod = '5m' | '15m' | '1h' | '6h' | '24h'

/**
 * Статус сервиса: UP или DOWN.
 */
export type ServiceStatus = 'UP' | 'DOWN'

/**
 * Статус здоровья отдельного сервиса.
 * Story 8.1: Health Check на странице Metrics
 */
export interface ServiceHealth {
  name: string
  status: ServiceStatus
  lastCheck: string // ISO timestamp
  details?: string | null
}

/**
 * Ответ API со статусами всех сервисов.
 * Ответ от GET /api/v1/health/services
 */
export interface HealthResponse {
  services: ServiceHealth[]
  timestamp: string // ISO timestamp
}

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
 * Метрики отдельного маршрута в топе.
 * Ответ от GET /api/v1/metrics/top-routes
 *
 * Story 7.0: API возвращает value (значение метрики) и metric (тип метрики).
 * Значение зависит от параметра sortBy:
 * - requests: total requests (не RPS)
 * - latency: avg latency в секундах
 * - errors: total errors
 */
export interface TopRoute {
  routeId: string
  path: string
  value: number
  metric: MetricsSortBy
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
