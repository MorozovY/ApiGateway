// Компонент защиты роутов от неаутентифицированных пользователей и недостаточных прав
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import type { ReactNode } from 'react'

interface ProtectedRouteProps {
  children: ReactNode
  /** Если указана — проверяет роль пользователя, иначе только аутентификацию */
  requiredRole?: string
}

/**
 * Компонент для защиты роутов.
 * Если пользователь не аутентифицирован, перенаправляет на /login.
 * Если указана requiredRole и роль не совпадает, перенаправляет на /dashboard.
 */
export function ProtectedRoute({ children, requiredRole }: ProtectedRouteProps) {
  const { isAuthenticated, user } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    // Сохраняем текущий путь для редиректа после логина
    return (
      <Navigate
        to="/login"
        state={{ returnUrl: location.pathname }}
        replace
      />
    )
  }

  // Проверка роли — только если requiredRole указана
  if (requiredRole && user?.role !== requiredRole) {
    return <Navigate to="/dashboard" replace />
  }

  return <>{children}</>
}
