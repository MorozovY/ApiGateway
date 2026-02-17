// Контекст аутентификации
import { createContext, useState, useCallback, type ReactNode } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { loginApi, logoutApi } from '../api/authApi'
import type { User, AuthContextType } from '../types/auth.types'

// Начальное состояние контекста
const defaultContext: AuthContextType = {
  user: null,
  isAuthenticated: false,
  isLoading: false,
  error: null,
  login: async () => {},
  logout: async () => {},
  clearError: () => {},
}

export const AuthContext = createContext<AuthContextType>(defaultContext)

interface AuthProviderProps {
  children: ReactNode
}

export function AuthProvider({ children }: AuthProviderProps) {
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const navigate = useNavigate()
  const location = useLocation()

  // Выполнить вход
  const login = useCallback(
    async (username: string, password: string) => {
      setIsLoading(true)
      setError(null)
      try {
        const userData = await loginApi(username, password)
        setUser(userData)
        // Редирект на returnUrl или dashboard
        const state = location.state as { returnUrl?: string } | null
        const returnUrl = state?.returnUrl || '/dashboard'
        navigate(returnUrl, { replace: true })
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Login failed'
        setError(message)
      } finally {
        setIsLoading(false)
      }
    },
    [navigate, location.state]
  )

  // Выполнить выход
  const logout = useCallback(async () => {
    try {
      await logoutApi()
    } catch {
      // Игнорируем ошибки API при logout — пользователь всё равно выходит
    } finally {
      setUser(null)
      navigate('/login', { replace: true })
    }
  }, [navigate])

  // Очистить ошибку
  const clearError = useCallback(() => {
    setError(null)
  }, [])

  const value: AuthContextType = {
    user,
    isAuthenticated: user !== null,
    isLoading,
    error,
    login,
    logout,
    clearError,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
