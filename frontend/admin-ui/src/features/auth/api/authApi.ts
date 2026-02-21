// API клиент для аутентификации
import axios from '@shared/utils/axios'
import type { User } from '../types/auth.types'

/**
 * Выполняет вход пользователя
 * Backend устанавливает auth_token cookie при успехе
 */
export async function loginApi(username: string, password: string): Promise<User> {
  const response = await axios.post<User>('/api/v1/auth/login', {
    username,
    password,
  })
  return response.data
}

/**
 * Выполняет выход пользователя
 * Backend очищает auth_token cookie
 */
export async function logoutApi(): Promise<void> {
  await axios.post('/api/v1/auth/logout')
}

/**
 * Проверяет текущую сессию пользователя.
 * Используется при инициализации приложения для восстановления сессии.
 *
 * @returns User если сессия валидна, null если нет
 */
export async function checkSessionApi(): Promise<User | null> {
  try {
    const response = await axios.get<User>('/api/v1/auth/me')
    return response.data
  } catch {
    return null
  }
}
