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
 * Результат проверки сессии.
 * Позволяет различать "не залогинен" и "ошибка сети" (AC4).
 */
export interface SessionCheckResult {
  user: User | null
  networkError: boolean
}

/**
 * Запрос на смену пароля (Story 9.4).
 */
export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

/**
 * Смена пароля текущего пользователя (Story 9.4).
 *
 * @throws Error с status 401 если текущий пароль неверный
 */
export async function changePasswordApi(request: ChangePasswordRequest): Promise<void> {
  await axios.post('/api/v1/auth/change-password', request)
}

/**
 * Проверяет текущую сессию пользователя.
 * Используется при инициализации приложения для восстановления сессии.
 *
 * @returns SessionCheckResult с user и флагом networkError
 */
export async function checkSessionApi(): Promise<SessionCheckResult> {
  try {
    const response = await axios.get<User>('/api/v1/auth/me')
    return { user: response.data, networkError: false }
  } catch (error) {
    // Проверяем, это ошибка сети или просто 401 (не залогинен)
    const isNetworkError =
      error instanceof Error &&
      (error.message.includes('Ошибка сети') || error.message.includes('Сервер недоступен'))
    return { user: null, networkError: isNetworkError }
  }
}
