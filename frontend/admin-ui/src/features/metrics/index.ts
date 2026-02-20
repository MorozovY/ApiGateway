// Публичный API feature metrics (Story 6.5)

// Компоненты
export { default as MetricsWidget } from './components/MetricsWidget'
export { default as MetricsPage } from './components/MetricsPage'
export { default as TopRoutesTable } from './components/TopRoutesTable'

// Хуки
export { useMetricsSummary, useTopRoutes, useRouteMetrics } from './hooks/useMetrics'

// Типы
export type {
  MetricsSummary,
  TopRoute,
  RouteMetrics,
  MetricsPeriod,
  MetricsSortBy,
} from './types/metrics.types'
