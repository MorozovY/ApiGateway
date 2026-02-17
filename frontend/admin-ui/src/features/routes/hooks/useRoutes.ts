// React Query hooks для управления маршрутами (Story 3.4)
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { message } from 'antd'
import * as routesApi from '../api/routesApi'
import type {
  RouteListParams,
  CreateRouteRequest,
  UpdateRouteRequest,
} from '../types/route.types'

/**
 * Ключи для React Query кэша.
 */
export const ROUTES_QUERY_KEY = 'routes'

/**
 * Hook для получения списка маршрутов.
 *
 * Поддерживает пагинацию, поиск и фильтрацию по статусу.
 */
export function useRoutes(params: RouteListParams = {}) {
  return useQuery({
    queryKey: [ROUTES_QUERY_KEY, params],
    queryFn: () => routesApi.fetchRoutes(params),
  })
}

/**
 * Hook для получения маршрута по ID.
 *
 * Запрос выполняется только если id передан.
 */
export function useRoute(id: string | undefined) {
  return useQuery({
    queryKey: [ROUTES_QUERY_KEY, id],
    queryFn: () => routesApi.fetchRouteById(id!),
    enabled: !!id,
  })
}

/**
 * Hook для создания нового маршрута.
 *
 * После успешного создания инвалидирует кэш списка.
 */
export function useCreateRoute() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: CreateRouteRequest) => routesApi.createRoute(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [ROUTES_QUERY_KEY], refetchType: 'all' })
      message.success('Маршрут создан')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Ошибка при создании маршрута')
    },
  })
}

/**
 * Hook для обновления маршрута.
 *
 * После успешного обновления инвалидирует кэш списка.
 */
export function useUpdateRoute() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateRouteRequest }) =>
      routesApi.updateRoute(id, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [ROUTES_QUERY_KEY], refetchType: 'all' })
      message.success('Маршрут обновлён')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Ошибка при обновлении маршрута')
    },
  })
}

/**
 * Hook для удаления маршрута.
 *
 * После успешного удаления инвалидирует кэш списка.
 */
export function useDeleteRoute() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => routesApi.deleteRoute(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [ROUTES_QUERY_KEY], refetchType: 'all' })
      message.success('Маршрут удалён')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Ошибка при удалении маршрута')
    },
  })
}

/**
 * Hook для клонирования маршрута.
 *
 * После успешного клонирования инвалидирует кэш списка.
 */
export function useCloneRoute() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => routesApi.cloneRoute(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [ROUTES_QUERY_KEY], refetchType: 'all' })
      message.success('Маршрут клонирован')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Ошибка при клонировании маршрута')
    },
  })
}
