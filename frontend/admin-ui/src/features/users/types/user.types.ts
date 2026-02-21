// Типы для управления пользователями (Story 2.6)

/**
 * Роль пользователя в системе.
 *
 * developer — базовая роль для разработчиков
 * security — роль для команды безопасности
 * admin — полный доступ к администрированию
 */
export type UserRole = 'developer' | 'security' | 'admin'

/**
 * Данные пользователя (без passwordHash).
 *
 * Используется для отображения в таблице и деталях.
 */
export interface User {
  id: string
  username: string
  email: string
  role: UserRole
  isActive: boolean
  createdAt: string
}

/**
 * Запрос на создание нового пользователя.
 *
 * Все поля обязательны.
 */
export interface CreateUserRequest {
  username: string
  email: string
  password: string
  role: UserRole
}

/**
 * Запрос на обновление пользователя.
 *
 * Все поля опциональны — обновляются только переданные.
 */
export interface UpdateUserRequest {
  email?: string
  role?: UserRole
  isActive?: boolean
}

/**
 * Пагинированный ответ со списком пользователей.
 */
export interface UserListResponse {
  items: User[]
  total: number
  offset: number
  limit: number
}

/**
 * Параметры для получения списка пользователей.
 *
 * search — поиск по username или email (case-insensitive)
 */
export interface UserListParams {
  offset?: number
  limit?: number
  search?: string
}

/**
 * Минимальные данные пользователя для dropdowns и фильтров.
 *
 * Используется для GET /api/v1/users/options.
 * Не содержит чувствительную информацию (email, role, isActive).
 */
export interface UserOption {
  id: string
  username: string
}

/**
 * Ответ со списком пользователей для dropdowns.
 */
export interface UserOptionsResponse {
  items: UserOption[]
}
