// Главный компонент приложения с конфигурацией роутинга
import { Routes, Route, Navigate } from 'react-router-dom'
import AuthLayout from '@layouts/AuthLayout'
import MainLayout from '@layouts/MainLayout'
import { LoginPage, ProtectedRoute } from '@features/auth'
import { DashboardPage } from '@features/dashboard'
import { RoutesPage } from '@features/routes'
import { UsersPage } from '@features/users'

function App() {
  return (
    <Routes>
      {/* Auth routes - используют AuthLayout */}
      <Route element={<AuthLayout />}>
        <Route path="/login" element={<LoginPage />} />
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
        {/* User Management (Story 2.6) — только для admin */}
        <Route path="/users" element={<UsersPage />} />
        {/* Placeholders для будущих stories */}
        <Route path="/rate-limits" element={<div>Rate Limits Management</div>} />
        <Route path="/approvals" element={<div>Approvals</div>} />
        <Route path="/audit" element={<div>Audit Logs</div>} />
      </Route>

      {/* Редирект с корня на dashboard */}
      <Route path="/" element={<Navigate to="/dashboard" replace />} />

      {/* Fallback - неизвестные пути редиректят на dashboard */}
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  )
}

export default App
