// Lazy-загружаемые компоненты для code splitting
// Используется React.lazy для динамической загрузки feature модулей
import { lazy } from 'react'

// Dashboard — главная страница после логина (named export)
export const LazyDashboardPage = lazy(() =>
  import('@features/dashboard/components/DashboardPage').then((m) => ({
    default: m.DashboardPage,
  }))
)

// Routes Management — управление маршрутами (named exports)
export const LazyRoutesPage = lazy(() =>
  import('@features/routes/components/RoutesPage').then((m) => ({
    default: m.RoutesPage,
  }))
)

export const LazyRouteFormPage = lazy(() =>
  import('@features/routes/components/RouteFormPage').then((m) => ({
    default: m.RouteFormPage,
  }))
)

export const LazyRouteDetailsPage = lazy(() =>
  import('@features/routes/components/RouteDetailsPage').then((m) => ({
    default: m.RouteDetailsPage,
  }))
)

// Users Management — управление пользователями (default export)
export const LazyUsersPage = lazy(
  () => import('@features/users/components/UsersPage')
)

// Consumers Management — управление consumers (default export)
export const LazyConsumersPage = lazy(
  () => import('@features/consumers/components/ConsumersPage')
)

// Rate Limits — настройка лимитов (default export)
export const LazyRateLimitsPage = lazy(
  () => import('@features/rate-limits/components/RateLimitsPage')
)

// Approvals — согласование маршрутов (named export)
export const LazyApprovalsPage = lazy(() =>
  import('@features/approval/components/ApprovalsPage').then((m) => ({
    default: m.ApprovalsPage,
  }))
)

// Audit — аудит-логи (named export)
export const LazyAuditPage = lazy(() =>
  import('@features/audit/components/AuditPage').then((m) => ({
    default: m.AuditPage,
  }))
)

// Integrations — отчёт по интеграциям (named export)
export const LazyIntegrationsPage = lazy(() =>
  import('@features/audit/components/IntegrationsPage').then((m) => ({
    default: m.IntegrationsPage,
  }))
)

// Metrics — метрики (default export)
export const LazyMetricsPage = lazy(
  () => import('@features/metrics/components/MetricsPage')
)

// Test — генератор нагрузки (default export)
export const LazyTestPage = lazy(
  () => import('@features/test/components/TestPage')
)
