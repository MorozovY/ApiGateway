// API для управления пользователями (Story 2.6; Story 8.3 — поиск)
import axios from '@shared/utils/axios'
import type {
  User,
  UserListResponse,
  UserListParams,
  CreateUserRequest,
  UpdateUserRequest,
  UserOptionsResponse,
} from '../types/user.types'

/**
 * Получение списка пользователей с пагинацией и поиском.
 *
 * GET /api/v1/users?offset=0&limit=20&search=john
 *
 * search — поиск по username или email (case-insensitive)
 */
export async function fetchUsers(params: UserListParams = {}): Promise<UserListResponse> {
  const { offset = 0, limit = 20, search } = params
  const response = await axios.get<UserListResponse>('/api/v1/users', {
    // Не отправляем пустую строку search — undefined исключается из params
    params: { offset, limit, search: search || undefined },
  })
  return response.data
}

/**
 * Получение пользователя по ID.
 *
 * GET /api/v1/users/{id}
 */
export async function fetchUserById(id: string): Promise<User> {
  const response = await axios.get<User>(`/api/v1/users/${id}`)
  return response.data
}

/**
 * Создание нового пользователя.
 *
 * POST /api/v1/users
 */
export async function createUser(data: CreateUserRequest): Promise<User> {
  const response = await axios.post<User>('/api/v1/users', data)
  return response.data
}

/**
 * Обновление пользователя.
 *
 * PUT /api/v1/users/{id}
 */
export async function updateUser(id: string, data: UpdateUserRequest): Promise<User> {
  const response = await axios.put<User>(`/api/v1/users/${id}`, data)
  return response.data
}

/**
 * Деактивация пользователя (soft delete).
 *
 * DELETE /api/v1/users/{id}
 */
export async function deactivateUser(id: string): Promise<void> {
  await axios.delete(`/api/v1/users/${id}`)
}

/**
 * Получение списка пользователей для dropdowns (минимальные данные).
 *
 * GET /api/v1/users/options
 *
 * Доступен для security и admin ролей.
 * Возвращает только id и username активных пользователей, отсортированных по алфавиту.
 */
export async function fetchUserOptions(): Promise<UserOptionsResponse> {
  const response = await axios.get<UserOptionsResponse>('/api/v1/users/options')
  return response.data
}
