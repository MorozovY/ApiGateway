// React Query hooks для работы с согласованиями маршрутов
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { message } from 'antd'
import { useAuth } from '@features/auth'
import * as approvalsApi from '../api/approvalsApi'

/**
 * Ключ для React Query кэша pending маршрутов.
 */
export const PENDING_ROUTES_QUERY_KEY = 'pendingRoutes'

/**
 * Hook для получения списка pending маршрутов.
 */
export function usePendingRoutes() {
  return useQuery({
    queryKey: [PENDING_ROUTES_QUERY_KEY],
    queryFn: approvalsApi.fetchPendingRoutes,
  })
}

/**
 * Hook для получения количества pending маршрутов.
 *
 * Запрос выполняется только для ролей security и admin (enabled=false для developer).
 * React Query кэш используется совместно с usePendingRoutes.
 */
export function usePendingRoutesCount() {
  const { user } = useAuth()

  const { data } = useQuery({
    queryKey: [PENDING_ROUTES_QUERY_KEY],
    queryFn: approvalsApi.fetchPendingRoutes,
    // Запрос только для security и admin — предотвращает 403 для developer
    enabled: user?.role === 'security' || user?.role === 'admin',
    select: (data) => data.length,
  })

  return data ?? 0
}

/**
 * Hook для одобрения маршрута.
 *
 * После успеха инвалидирует кэш pending маршрутов и списка routes.
 */
export function useApproveRoute() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => approvalsApi.approveRoute(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [PENDING_ROUTES_QUERY_KEY] })
      // Инвалидируем routes т.к. статус маршрута изменился
      queryClient.invalidateQueries({ queryKey: ['routes'], refetchType: 'all' })
      message.success('Маршрут одобрен и опубликован')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Ошибка при одобрении маршрута')
    },
  })
}

/**
 * Hook для отклонения маршрута.
 *
 * После успеха инвалидирует кэш pending маршрутов.
 */
export function useRejectRoute() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      approvalsApi.rejectRoute(id, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [PENDING_ROUTES_QUERY_KEY] })
      queryClient.invalidateQueries({ queryKey: ['routes'], refetchType: 'all' })
      message.success('Маршрут отклонён')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Ошибка при отклонении маршрута')
    },
  })
}
