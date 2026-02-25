// API клиент для аутентификации
// Story 12.9.1: Legacy cookie auth API удалён (loginApi, logoutApi, checkSessionApi)
import axios from '@shared/utils/axios'

/**
 * Запрос на смену пароля (Story 9.4).
 */
export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

/**
 * Смена пароля текущего пользователя (Story 9.4).
 * Работает с Keycloak через backend proxy.
 *
 * @throws Error с status 401 если текущий пароль неверный
 */
export async function changePasswordApi(request: ChangePasswordRequest): Promise<void> {
  await axios.post('/api/v1/auth/change-password', request)
}
