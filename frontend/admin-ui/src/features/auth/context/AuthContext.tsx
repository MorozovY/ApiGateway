// Контекст аутентификации с поддержкой cookie-auth и Keycloak Direct Access Grants
// Story 12.2: Admin UI — Keycloak Auth Migration

import { createContext, useState, useCallback, useEffect, useRef, type ReactNode } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { Spin } from 'antd'
import { loginApi, logoutApi, checkSessionApi } from '../api/authApi'
import { keycloakLogin, keycloakLogout, keycloakRefreshToken, type KeycloakTokenResponse } from '../api/keycloakApi'
import { authEvents, setTokenGetter } from '@shared/utils/axios'
import { isKeycloakEnabled, mapKeycloakRoles, extractKeycloakRoles, decodeJwtPayload } from '../config/oidcConfig'
import type { User, AuthContextType } from '../types/auth.types'

// Storage keys для токенов Keycloak
const TOKEN_STORAGE_KEY = 'keycloak_tokens'

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

// ========================================
// COOKIE-BASED AUTH PROVIDER (Legacy)
// ========================================

/**
 * Cookie-based AuthProvider (Legacy).
 * Используется когда VITE_USE_KEYCLOAK=false.
 */
function CookieAuthProvider({ children }: AuthProviderProps) {
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
          setError('Ошибка сети. Проверьте подключение к интернету')
        }
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
      // Игнорируем ошибки API при logout
    } finally {
      setUser(null)
      navigate('/login', { replace: true })
    }
  }, [navigate])

  // Обработка 401 ошибок
  const handleSessionExpired = useCallback(() => {
    setUser(null)
    setError('Сессия истекла, войдите снова')
    navigate('/login', { replace: true })
  }, [navigate])

  const handleSessionExpiredRef = useRef(handleSessionExpired)
  handleSessionExpiredRef.current = handleSessionExpired

  useEffect(() => {
    authEvents.onUnauthorized = () => handleSessionExpiredRef.current()
    return () => {
      authEvents.onUnauthorized = () => {}
    }
  }, [])

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

  if (isInitializing) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
        <Spin size="large" />
      </div>
    )
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// ========================================
// KEYCLOAK DIRECT ACCESS GRANTS PROVIDER
// ========================================

/**
 * Сохраняет токены в sessionStorage.
 */
function saveTokens(tokens: KeycloakTokenResponse): void {
  sessionStorage.setItem(TOKEN_STORAGE_KEY, JSON.stringify({
    ...tokens,
    saved_at: Date.now(),
  }))
}

/**
 * Загружает токены из sessionStorage.
 */
function loadTokens(): (KeycloakTokenResponse & { saved_at: number }) | null {
  const stored = sessionStorage.getItem(TOKEN_STORAGE_KEY)
  if (!stored) return null
  try {
    return JSON.parse(stored)
  } catch {
    return null
  }
}

/**
 * Удаляет токены из sessionStorage.
 */
function clearTokens(): void {
  sessionStorage.removeItem(TOKEN_STORAGE_KEY)
}

/**
 * Проверяет, истёк ли access token.
 */
function isTokenExpired(tokens: KeycloakTokenResponse & { saved_at: number }): boolean {
  const expiresAt = tokens.saved_at + tokens.expires_in * 1000
  // Считаем истёкшим за 30 секунд до реального истечения
  return Date.now() > expiresAt - 30000
}

/**
 * Проверяет, истёк ли refresh token.
 */
function isRefreshTokenExpired(tokens: KeycloakTokenResponse & { saved_at: number }): boolean {
  const expiresAt = tokens.saved_at + tokens.refresh_expires_in * 1000
  return Date.now() > expiresAt
}

/**
 * Keycloak AuthProvider с Direct Access Grants.
 * Использует нашу форму логина, аутентифицирует через Keycloak API.
 */
function KeycloakDirectGrantsProvider({ children }: AuthProviderProps) {
  const [user, setUser] = useState<User | null>(null)
  const [tokens, setTokens] = useState<KeycloakTokenResponse | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [isInitializing, setIsInitializing] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const navigate = useNavigate()
  const location = useLocation()
  const refreshTimerRef = useRef<number | null>(null)

  /**
   * Извлекает User из access_token.
   * Использует безопасный decodeJwtPayload для обработки malformed JWT.
   */
  const extractUserFromToken = useCallback((accessToken: string): User => {
    const roles = extractKeycloakRoles(accessToken)
    const payload = decodeJwtPayload(accessToken)
    return {
      userId: (payload.sub as string) ?? '',
      username: (payload.preferred_username as string) ?? (payload.email as string) ?? 'unknown',
      role: mapKeycloakRoles(roles),
    }
  }, [])

  /**
   * Устанавливает токены и пользователя.
   */
  const setAuthState = useCallback((newTokens: KeycloakTokenResponse) => {
    setTokens(newTokens)
    saveTokens(newTokens)
    setUser(extractUserFromToken(newTokens.access_token))
    setTokenGetter(() => newTokens.access_token)
  }, [extractUserFromToken])

  /**
   * Очищает состояние аутентификации.
   */
  const clearAuthState = useCallback(() => {
    setTokens(null)
    setUser(null)
    clearTokens()
    setTokenGetter(() => undefined)
    if (refreshTimerRef.current) {
      clearTimeout(refreshTimerRef.current)
      refreshTimerRef.current = null
    }
  }, [])

  /**
   * Обновляет токены.
   */
  const refreshTokens = useCallback(async (): Promise<boolean> => {
    const stored = loadTokens()
    if (!stored || isRefreshTokenExpired(stored)) {
      return false
    }

    try {
      const newTokens = await keycloakRefreshToken(stored.refresh_token)
      setAuthState(newTokens)
      return true
    } catch {
      return false
    }
  }, [setAuthState])

  /**
   * Планирует автоматическое обновление токена.
   */
  const scheduleTokenRefresh = useCallback((expiresIn: number) => {
    if (refreshTimerRef.current) {
      clearTimeout(refreshTimerRef.current)
    }
    // Обновляем за 60 секунд до истечения
    const refreshIn = (expiresIn - 60) * 1000
    if (refreshIn > 0) {
      refreshTimerRef.current = window.setTimeout(async () => {
        const success = await refreshTokens()
        if (!success) {
          clearAuthState()
          setError('Сессия истекла, войдите снова')
          navigate('/login', { replace: true })
        }
      }, refreshIn)
    }
  }, [refreshTokens, clearAuthState, navigate])

  // Инициализация: проверяем сохранённые токены
  useEffect(() => {
    const init = async () => {
      const stored = loadTokens()
      if (stored) {
        if (isRefreshTokenExpired(stored)) {
          // Refresh token истёк — нужен новый логин
          clearTokens()
        } else if (isTokenExpired(stored)) {
          // Access token истёк — пробуем обновить
          const success = await refreshTokens()
          if (success) {
            const newStored = loadTokens()
            if (newStored) {
              scheduleTokenRefresh(newStored.expires_in)
            }
          }
        } else {
          // Токены валидны
          setAuthState(stored)
          scheduleTokenRefresh(stored.expires_in)
        }
      }
      setIsInitializing(false)
    }
    init()

    return () => {
      if (refreshTimerRef.current) {
        clearTimeout(refreshTimerRef.current)
      }
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // Login через Keycloak Direct Access Grants
  const login = useCallback(
    async (username: string, password: string) => {
      setIsLoading(true)
      setError(null)
      try {
        const newTokens = await keycloakLogin(username, password)
        setAuthState(newTokens)
        scheduleTokenRefresh(newTokens.expires_in)

        const state = location.state as { returnUrl?: string } | null
        const returnUrl = state?.returnUrl || '/dashboard'
        navigate(returnUrl, { replace: true })
      } catch (err) {
        const message = err instanceof Error ? err.message : 'Ошибка входа'
        setError(message)
      } finally {
        setIsLoading(false)
      }
    },
    [setAuthState, scheduleTokenRefresh, navigate, location.state]
  )

  // Logout
  const logout = useCallback(async () => {
    const stored = loadTokens()
    if (stored) {
      await keycloakLogout(stored.refresh_token)
    }
    clearAuthState()
    navigate('/login', { replace: true })
  }, [clearAuthState, navigate])

  // Обработка 401 ошибок
  const handleSessionExpired = useCallback(async () => {
    // Пробуем обновить токен
    const success = await refreshTokens()
    if (!success) {
      clearAuthState()
      setError('Сессия истекла, войдите снова')
      navigate('/login', { replace: true })
    }
  }, [refreshTokens, clearAuthState, navigate])

  const handleSessionExpiredRef = useRef(handleSessionExpired)
  handleSessionExpiredRef.current = handleSessionExpired

  useEffect(() => {
    authEvents.onUnauthorized = () => handleSessionExpiredRef.current()
    return () => {
      authEvents.onUnauthorized = () => {}
    }
  }, [])

  const clearError = useCallback(() => {
    setError(null)
  }, [])

  const value: AuthContextType = {
    user,
    isAuthenticated: user !== null && tokens !== null,
    isLoading,
    error,
    login,
    logout,
    clearError,
  }

  if (isInitializing) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
        <Spin size="large" />
      </div>
    )
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// ========================================
// MAIN AUTH PROVIDER WITH FEATURE FLAG
// ========================================

/**
 * AuthProvider с feature flag для переключения между cookie-auth и Keycloak.
 *
 * Feature flag: VITE_USE_KEYCLOAK
 * - false (default): Cookie-based authentication (backend API)
 * - true: Keycloak Direct Access Grants (Keycloak API, наша форма логина)
 */
export function AuthProvider({ children }: AuthProviderProps) {
  if (isKeycloakEnabled()) {
    return <KeycloakDirectGrantsProvider>{children}</KeycloakDirectGrantsProvider>
  }

  return <CookieAuthProvider>{children}</CookieAuthProvider>
}
