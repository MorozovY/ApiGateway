// Контекст аутентификации — Keycloak Direct Access Grants
// Story 12.2: Admin UI — Keycloak Auth Migration
// Story 12.9.1: Legacy cookie auth удалён

import { createContext, useState, useCallback, useEffect, useRef, type ReactNode } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { Spin } from 'antd'
import { keycloakLogin, keycloakLogout, keycloakRefreshToken, type KeycloakTokenResponse } from '../api/keycloakApi'
import { authEvents, setTokenGetter } from '@shared/utils/axios'
import { mapKeycloakRoles, extractKeycloakRoles, decodeJwtPayload } from '../config/oidcConfig'
import type { User, AuthContextType } from '../types/auth.types'

// Storage keys для токенов Keycloak
// Code Review Fix: H3 - Security Trade-off Documentation
// Токены хранятся в sessionStorage (не в httpOnly cookies) для упрощения архитектуры.
// SECURITY CONSIDERATION: sessionStorage доступен через JavaScript (видим в DevTools).
// - Плюсы: простая архитектура, не требуется backend proxy для токенов
// - Минусы: уязвим к XSS атакам и malicious browser extensions
// - Mitigation: sessionStorage очищается при закрытии вкладки (короткоживущие сессии)
// Для production рассмотреть: httpOnly cookie proxy pattern или token encryption
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
// KEYCLOAK DIRECT ACCESS GRANTS PROVIDER
// Story 12.9.1: Legacy cookie auth удалён
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
  const refreshInProgressRef = useRef<Promise<boolean> | null>(null) // Code Review Fix: H2 - prevent race condition

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
   * Code Review Fix: H2 - предотвращает concurrent refresh attempts через promise caching.
   */
  const refreshTokens = useCallback(async (): Promise<boolean> => {
    // Если refresh уже в процессе, возвращаем существующий promise
    if (refreshInProgressRef.current) {
      return refreshInProgressRef.current
    }

    const stored = loadTokens()
    if (!stored || isRefreshTokenExpired(stored)) {
      return false
    }

    // Создаём promise и сохраняем в ref
    const refreshPromise = (async () => {
      try {
        const newTokens = await keycloakRefreshToken(stored.refresh_token)
        setAuthState(newTokens)
        return true
      } catch (error) {
        // Code Review Fix: M2 - логируем ошибки refresh
        console.error('Token refresh failed:', error)
        return false
      } finally {
        // Очищаем ref после завершения
        refreshInProgressRef.current = null
      }
    })()

    refreshInProgressRef.current = refreshPromise
    return refreshPromise
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
// MAIN AUTH PROVIDER
// ========================================

/**
 * AuthProvider — Keycloak Direct Access Grants.
 * Story 12.9.1: Legacy cookie auth удалён.
 */
export function AuthProvider({ children }: AuthProviderProps) {
  return <KeycloakDirectGrantsProvider>{children}</KeycloakDirectGrantsProvider>
}
