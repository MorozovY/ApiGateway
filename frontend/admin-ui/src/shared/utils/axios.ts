// Настройка axios instance для API запросов
import axios from 'axios'

// Base path из Vite конфига (например '/ApiGateway')
const basePath = (import.meta.env.BASE_URL || '/').replace(/\/$/, '')

const instance = axios.create({
  withCredentials: true, // Для отправки cookies (auth_token)
  headers: {
    'Content-Type': 'application/json',
  },
})

// Request interceptor: добавляем base path к URL
instance.interceptors.request.use((config) => {
  // Если URL начинается с /api, добавляем base path
  if (config.url?.startsWith('/api')) {
    config.url = `${basePath}${config.url}`
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
