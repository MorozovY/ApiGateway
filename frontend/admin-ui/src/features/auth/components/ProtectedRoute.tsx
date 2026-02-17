// Компонент защиты роутов от неаутентифицированных пользователей
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import type { ReactNode } from 'react'

interface ProtectedRouteProps {
  children: ReactNode
}

/**
 * Компонент для защиты роутов
 * Если пользователь не аутентифицирован, перенаправляет на /login
 * с сохранением текущего пути для редиректа после логина
 */
export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { isAuthenticated } = useAuth()
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

  return <>{children}</>
}
