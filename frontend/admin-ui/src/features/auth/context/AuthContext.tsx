// Контекст аутентификации
import { createContext, useState, useCallback, useEffect, useRef, type ReactNode } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { Spin } from 'antd'
import { loginApi, logoutApi, checkSessionApi } from '../api/authApi'
import { authEvents } from '@shared/utils/axios'
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
  const [isInitializing, setIsInitializing] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const navigate = useNavigate()
  const location = useLocation()

  // Проверка сессии при инициализации
  useEffect(() => {
    const initSession = async () => {
      try {
        const result = await checkSessionApi()
        if (result.user) {
          setUser(result.user)
        } else if (result.networkError) {
          // AC4: показываем сообщение об ошибке сети
          setError('Ошибка сети. Проверьте подключение к интернету')
        }
        // Если user === null и networkError === false — просто не залогинен
      } finally {
        setIsInitializing(false)
      }
    }
    initSession()
  }, [])

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

  // Выполнить выход при истечении сессии (AC2)
  // Показывает сообщение "Сессия истекла" на странице login
  const handleSessionExpired = useCallback(() => {
    setUser(null)
    setError('Сессия истекла, войдите снова')
    navigate('/login', { replace: true })
  }, [navigate])

  // Регистрируем callback для 401 ошибок из axios interceptor
  // Используем useRef чтобы избежать бесконечного цикла в useEffect
  const handleSessionExpiredRef = useRef(handleSessionExpired)
  handleSessionExpiredRef.current = handleSessionExpired

  useEffect(() => {
    authEvents.onUnauthorized = () => handleSessionExpiredRef.current()
    return () => {
      authEvents.onUnauthorized = () => {}
    }
  }, [])

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

  // Показываем loading пока проверяем сессию (AC3)
  if (isInitializing) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
        <Spin size="large" />
      </div>
    )
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
