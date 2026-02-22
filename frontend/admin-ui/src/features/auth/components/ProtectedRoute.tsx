// Компонент защиты роутов от неаутентифицированных пользователей и недостаточных прав
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import type { ReactNode } from 'react'
import type { User } from '../types/auth.types'

interface ProtectedRouteProps {
  children: ReactNode
  /**
   * Требуемая роль для доступа к роуту.
   * Если указана — проверяет роль пользователя, иначе только аутентификацию.
   * Допустимые значения: 'developer', 'security', 'admin' или массив ролей.
   */
  requiredRole?: User['role'] | User['role'][]
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
  if (requiredRole) {
    const allowedRoles = Array.isArray(requiredRole) ? requiredRole : [requiredRole]
    if (!user?.role || !allowedRoles.includes(user.role)) {
      return <Navigate to="/dashboard" replace />
    }
  }

  return <>{children}</>
}
