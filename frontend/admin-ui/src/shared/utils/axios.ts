// Настройка axios instance для API запросов
import axios from 'axios'

const instance = axios.create({
  withCredentials: true, // Для отправки cookies (auth_token)
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
      // Исключаем login и me endpoints из автоматического logout
      // login — чтобы показать ошибку "Неверные учётные данные"
      // me — чтобы не зацикливаться при проверке сессии
      const url = error.config?.url || ''
      // Используем endsWith для точного совпадения (избегаем false positive для /auth/login-history и т.п.)
      const isAuthEndpoint = url.endsWith('/auth/login') || url.endsWith('/auth/me')

      if (!isAuthEndpoint) {
        // Вызываем logout callback (AC2)
        authEvents.onUnauthorized()
      }

      // Извлекаем detail из RFC 7807 ответа
      const detail = error.response.data?.detail || 'Неверные учётные данные'
      return Promise.reject(new Error(detail))
    }

    // Для других ошибок также пробуем извлечь detail
    const message = error.response.data?.detail || error.message || 'Произошла ошибка'
    return Promise.reject(new Error(message))
  }
)

export default instance
