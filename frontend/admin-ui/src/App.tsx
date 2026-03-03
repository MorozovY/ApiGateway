// Главный компонент приложения с конфигурацией роутинга
// Использует React.lazy для code splitting и Suspense для loading states
import { Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import AuthLayout from '@layouts/AuthLayout'
import MainLayout from '@layouts/MainLayout'
// Auth компоненты остаются синхронными — нужны сразу при запуске
import { LoginPage, ProtectedRoute, CallbackPage } from '@features/auth'
import { LoadingFallback } from '@shared/components/LoadingFallback'
import { LazyErrorBoundary } from '@shared/components/LazyErrorBoundary'
import {
  LazyDashboardPage,
  LazyRoutesPage,
  LazyRouteFormPage,
  LazyRouteDetailsPage,
  LazyUsersPage,
  LazyConsumersPage,
  LazyRateLimitsPage,
  LazyApprovalsPage,
  LazyAuditPage,
  LazyIntegrationsPage,
  LazyMetricsPage,
  LazyTestPage,
} from '@shared/components/LazyComponents'

function App() {
  return (
    <LazyErrorBoundary>
      <Suspense fallback={<LoadingFallback />}>
        <Routes>
          {/* Auth routes — синхронные, используют AuthLayout */}
          <Route element={<AuthLayout />}>
            <Route path="/login" element={<LoginPage />} />
            {/* OIDC callback для Keycloak (Story 12.2) */}
            <Route path="/callback" element={<CallbackPage />} />
          </Route>

          {/* Protected routes — lazy loading, требуют аутентификации */}
          <Route
            element={
              <ProtectedRoute>
                <MainLayout />
              </ProtectedRoute>
            }
          >
            {/* Dashboard — главная страница после логина */}
            <Route path="/dashboard" element={<LazyDashboardPage />} />
            {/* Routes Management */}
            <Route path="/routes" element={<LazyRoutesPage />} />
            <Route path="/routes/new" element={<LazyRouteFormPage />} />
            <Route path="/routes/:id/edit" element={<LazyRouteFormPage />} />
            <Route path="/routes/:id" element={<LazyRouteDetailsPage />} />
            {/* User Management (Story 2.6) — только для admin, защита и на клиенте */}
            <Route
              path="/users"
              element={
                <ProtectedRoute requiredRole="admin">
                  <LazyUsersPage />
                </ProtectedRoute>
              }
            />
            {/* Consumer Management (Story 12.9) — только для admin */}
            <Route
              path="/consumers"
              element={
                <ProtectedRoute requiredRole="admin">
                  <LazyConsumersPage />
                </ProtectedRoute>
              }
            />
            {/* Rate Limits Management (Story 5.4, Story 9.3 AC4) — только для admin */}
            <Route
              path="/rate-limits"
              element={
                <ProtectedRoute requiredRole="admin">
                  <LazyRateLimitsPage />
                </ProtectedRoute>
              }
            />
            {/* Согласование маршрутов (Story 4.6) — только для security и admin */}
            <Route
              path="/approvals"
              element={
                <ProtectedRoute requiredRole={['security', 'admin']}>
                  <LazyApprovalsPage />
                </ProtectedRoute>
              }
            />
            {/* Аудит-логи (Story 7.5) — только для security и admin */}
            <Route
              path="/audit"
              element={
                <ProtectedRoute requiredRole={['security', 'admin']}>
                  <LazyAuditPage />
                </ProtectedRoute>
              }
            />
            {/* Integrations Report (Story 7.6, AC6) — только для security и admin */}
            <Route
              path="/audit/integrations"
              element={
                <ProtectedRoute requiredRole={['security', 'admin']}>
                  <LazyIntegrationsPage />
                </ProtectedRoute>
              }
            />
            {/* Метрики (Story 6.5) — доступ для всех аутентифицированных пользователей */}
            <Route path="/metrics" element={<LazyMetricsPage />} />
            {/* Test Load Generator (Story 8.9, Story 9.3 AC4) — только для admin */}
            <Route
              path="/test"
              element={
                <ProtectedRoute requiredRole="admin">
                  <LazyTestPage />
                </ProtectedRoute>
              }
            />
          </Route>

          {/* Редирект с корня на dashboard */}
          <Route path="/" element={<Navigate to="/dashboard" replace />} />

          {/* Fallback — неизвестные пути редиректят на dashboard */}
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </Suspense>
    </LazyErrorBoundary>
  )
}

export default App
