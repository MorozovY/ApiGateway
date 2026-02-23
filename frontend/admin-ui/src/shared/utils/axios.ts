// Настройка axios instance для API запросов
// Story 12.2: Добавлена поддержка Bearer token для Keycloak OIDC

import axios from 'axios'
import { isKeycloakEnabled } from '@features/auth/config/oidcConfig'

// Функция получения access token — устанавливается из AuthContext при Keycloak mode
let getAccessToken: (() => string | undefined) | null = null

/**
 * Устанавливает функцию получения access token для Bearer auth.
 * Используется только при VITE_USE_KEYCLOAK=true.
 */
export const setTokenGetter = (getter: () => string | undefined) => {
  getAccessToken = getter
}

const instance = axios.create({
  // Cookie credentials нужны только для cookie-auth mode
  // При Keycloak используем Bearer token
  withCredentials: !isKeycloakEnabled(),
  headers: {
    'Content-Type': 'application/json',
  },
})

/**
 * Callback для обработки 401 ошибок (сессия истекла).
 * Устанавливается из AuthProvider для вызова logout.
 */
export const authEvents = {
  onUnauthorized: () => {},
}

// Request interceptor — добавляем Bearer token при Keycloak mode
instance.interceptors.request.use((config) => {
  if (isKeycloakEnabled() && getAccessToken) {
    const token = getAccessToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
  }
  return config
})

// Response interceptor для обработки ошибок
instance.interceptors.response.use(
  (response) => response,
  (error) => {
    // Обработка сетевых ошибок (нет ответа от сервера)
    if (!error.response) {
      const isNetworkError = error.message === 'Network Error' || error.code === 'ERR_NETWORK'
      const message = isNetworkError
        ? 'Ошибка сети. Проверьте подключение к интернету'
        : 'Сервер недоступен. Попробуйте позже'
      return Promise.reject(new Error(message))
    }

    if (error.response.status === 401) {
      // Исключаем auth endpoints из автоматического logout
      const url = error.config?.url || ''
      const isAuthEndpoint =
        url.endsWith('/auth/login') || url.endsWith('/auth/me') || url.includes('/callback')

      if (!isAuthEndpoint) {
        authEvents.onUnauthorized()
      }

      const detail = error.response.data?.detail || 'Неверные учётные данные'
      return Promise.reject(new Error(detail))
    }

    // Для других ошибок также пробуем извлечь detail из RFC 7807 ответа
    const message = error.response.data?.detail || error.message || 'Произошла ошибка'
    return Promise.reject(new Error(message))
  }
)

export default instance
