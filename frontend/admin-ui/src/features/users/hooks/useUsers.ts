// React Query hooks для управления пользователями (Story 2.6)
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { message } from 'antd'
import * as usersApi from '../api/usersApi'
import type {
  UserListParams,
  CreateUserRequest,
  UpdateUserRequest,
} from '../types/user.types'

/**
 * Ключи для React Query кэша.
 */
const USERS_QUERY_KEY = 'users'

/**
 * Hook для получения списка пользователей.
 *
 * Поддерживает пагинацию через offset и limit.
 */
export function useUsers(params: UserListParams = {}) {
  return useQuery({
    queryKey: [USERS_QUERY_KEY, params],
    queryFn: () => usersApi.fetchUsers(params),
  })
}

/**
 * Hook для создания нового пользователя.
 *
 * После успешного создания инвалидирует кэш списка.
 */
export function useCreateUser() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: CreateUserRequest) => usersApi.createUser(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [USERS_QUERY_KEY], refetchType: 'all' })
      message.success('Пользователь создан')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Ошибка при создании пользователя')
    },
  })
}

/**
 * Hook для обновления пользователя.
 *
 * После успешного обновления инвалидирует кэш списка.
 */
export function useUpdateUser() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateUserRequest }) =>
      usersApi.updateUser(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [USERS_QUERY_KEY], refetchType: 'all' })
      message.success('Пользователь обновлён')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Ошибка при обновлении пользователя')
    },
  })
}

/**
 * Hook для деактивации пользователя.
 *
 * После успешной деактивации инвалидирует кэш списка.
 */
export function useDeactivateUser() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => usersApi.deactivateUser(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [USERS_QUERY_KEY], refetchType: 'all' })
      message.success('Пользователь деактивирован')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Ошибка при деактивации пользователя')
    },
  })
}
