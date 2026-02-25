// Настройка axios instance для API запросов
// Story 12.2: Добавлена поддержка Bearer token для Keycloak OIDC
// Story 12.9.1: Legacy cookie auth удалён — всегда используется Bearer token

import axios from 'axios'

// Функция получения access token — устанавливается из AuthContext
let getAccessToken: (() => string | undefined) | null = null

/**
 * Устанавливает функцию получения access token для Bearer auth.
 */
export const setTokenGetter = (getter: () => string | undefined) => {
  getAccessToken = getter
}

const instance = axios.create({
  // Используем Bearer token (Keycloak), withCredentials не нужен
  withCredentials: false,
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

// Request interceptor — добавляем Bearer token
instance.interceptors.request.use((config) => {
  if (getAccessToken) {
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
      // Code Review Fix: M4 - защита от infinite loop при Keycloak token refresh failure
      const url = error.config?.url || ''
      const isAuthEndpoint =
        url.endsWith('/auth/login') ||
        url.endsWith('/auth/me') ||
        url.includes('/callback') ||
        url.includes('/protocol/openid-connect/token') // Keycloak token endpoint

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
