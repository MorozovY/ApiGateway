// Главный компонент приложения с конфигурацией роутинга
import { Routes, Route, Navigate } from 'react-router-dom'
import AuthLayout from '@layouts/AuthLayout'
import MainLayout from '@layouts/MainLayout'
import { LoginPage, ProtectedRoute, CallbackPage } from '@features/auth'
import { DashboardPage } from '@features/dashboard'
import { RoutesPage, RouteFormPage, RouteDetailsPage } from '@features/routes'
import { UsersPage } from '@features/users'
import { ApprovalsPage } from '@features/approval'
import { RateLimitsPage } from '@features/rate-limits'
import { MetricsPage } from '@features/metrics'
import { AuditPage, IntegrationsPage } from '@features/audit'
import { TestPage } from '@features/test'

function App() {
  return (
    <Routes>
      {/* Auth routes - используют AuthLayout */}
      <Route element={<AuthLayout />}>
        <Route path="/login" element={<LoginPage />} />
        {/* OIDC callback для Keycloak (Story 12.2) */}
        <Route path="/callback" element={<CallbackPage />} />
      </Route>

      {/* Protected routes - используют MainLayout и требуют аутентификации */}
      <Route
        element={
          <ProtectedRoute>
            <MainLayout />
          </ProtectedRoute>
        }
      >
        {/* Dashboard - главная страница после логина */}
        <Route path="/dashboard" element={<DashboardPage />} />
        {/* Routes Management */}
        <Route path="/routes" element={<RoutesPage />} />
        <Route path="/routes/new" element={<RouteFormPage />} />
        <Route path="/routes/:id/edit" element={<RouteFormPage />} />
        <Route path="/routes/:id" element={<RouteDetailsPage />} />
        {/* User Management (Story 2.6) — только для admin, защита и на клиенте */}
        <Route
          path="/users"
          element={
            <ProtectedRoute requiredRole="admin">
              <UsersPage />
            </ProtectedRoute>
          }
        />
        {/* Rate Limits Management (Story 5.4, Story 9.3 AC4) — только для admin */}
        <Route
          path="/rate-limits"
          element={
            <ProtectedRoute requiredRole="admin">
              <RateLimitsPage />
            </ProtectedRoute>
          }
        />
        {/* Согласование маршрутов (Story 4.6) — только для security и admin */}
        <Route
          path="/approvals"
          element={
            <ProtectedRoute requiredRole={['security', 'admin']}>
              <ApprovalsPage />
            </ProtectedRoute>
          }
        />
        {/* Аудит-логи (Story 7.5) — только для security и admin */}
        <Route
          path="/audit"
          element={
            <ProtectedRoute requiredRole={['security', 'admin']}>
              <AuditPage />
            </ProtectedRoute>
          }
        />
        {/* Integrations Report (Story 7.6, AC6) — только для security и admin */}
        <Route
          path="/audit/integrations"
          element={
            <ProtectedRoute requiredRole={['security', 'admin']}>
              <IntegrationsPage />
            </ProtectedRoute>
          }
        />
        {/* Метрики (Story 6.5) — доступ для всех аутентифицированных пользователей */}
        <Route path="/metrics" element={<MetricsPage />} />
        {/* Test Load Generator (Story 8.9, Story 9.3 AC4) — только для admin */}
        <Route
          path="/test"
          element={
            <ProtectedRoute requiredRole="admin">
              <TestPage />
            </ProtectedRoute>
          }
        />
      </Route>

      {/* Редирект с корня на dashboard */}
      <Route path="/" element={<Navigate to="/dashboard" replace />} />

      {/* Fallback - неизвестные пути редиректят на dashboard */}
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}

export default App
