// Типы для генератора нагрузки (Story 8.9)

/**
 * Конфигурация генератора нагрузки.
 */
export interface LoadGeneratorConfig {
  routeId: string
  routePath: string
  requestsPerSecond: number
  durationSeconds: number | null // null = until stopped
}

/**
 * Состояние генератора нагрузки.
 */
export interface LoadGeneratorState {
  status: 'idle' | 'running' | 'stopped'
  startTime: number | null
  sentCount: number
  successCount: number
  errorCount: number
  lastError: string | null
  averageResponseTime: number | null
}

/**
 * Summary после остановки генерации.
 */
export interface LoadGeneratorSummary {
  totalRequests: number
  successCount: number
  errorCount: number
  durationMs: number
  successRate: number
  averageResponseTime: number | null
}

/**
 * Маршрут для выбора в dropdown.
 */
export interface RouteOption {
  id: string
  path: string
  name: string
}
