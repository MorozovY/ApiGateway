// Публичный API feature metrics (Story 6.5)

// Компоненты
export { default as MetricsWidget } from './components/MetricsWidget'
export { default as MetricsPage } from './components/MetricsPage'
export { default as TopRoutesTable } from './components/TopRoutesTable'

// Хуки
export { useMetricsSummary, useTopRoutes, useRouteMetrics } from './hooks/useMetrics'

// Утилиты
export {
  getErrorRateStatus,
  getErrorRateColor,
  getErrorRateTagColor,
  ERROR_RATE_THRESHOLDS,
  ERROR_RATE_COLORS,
  ERROR_RATE_TAG_COLORS,
} from './utils/errorRateUtils'
export type { ErrorRateStatus } from './utils/errorRateUtils'

// Конфигурация
export {
  GRAFANA_URL,
  GRAFANA_DASHBOARD_URL,
  GRAFANA_DASHBOARD_ID,
  METRICS_REFRESH_INTERVAL,
  METRICS_STALE_TIME,
  TREND_HISTORY_SIZE,
} from './config/metricsConfig'

// Типы
export type {
  MetricsSummary,
  TopRoute,
  RouteMetrics,
  MetricsPeriod,
  MetricsSortBy,
} from './types/metrics.types'
