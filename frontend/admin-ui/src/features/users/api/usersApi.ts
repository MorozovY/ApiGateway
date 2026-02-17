// API для управления пользователями (Story 2.6)
import axios from '@shared/utils/axios'
import type {
  User,
  UserListResponse,
  UserListParams,
  CreateUserRequest,
  UpdateUserRequest,
} from '../types/user.types'

/**
 * Получение списка пользователей с пагинацией.
 *
 * GET /api/v1/users?offset=0&limit=20
 */
export async function fetchUsers(params: UserListParams = {}): Promise<UserListResponse> {
  const { offset = 0, limit = 20 } = params
  const response = await axios.get<UserListResponse>('/api/v1/users', {
    params: { offset, limit },
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
