// React Query hooks для работы с согласованиями маршрутов (Story 10.2 — auto-refresh)
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { App } from 'antd'
import { useAuth } from '@features/auth'
import { canApprove } from '@shared/utils'
import * as approvalsApi from '../api/approvalsApi'

/**
 * Ключ для React Query кэша pending маршрутов.
 */
export const PENDING_ROUTES_QUERY_KEY = 'pendingRoutes'

/**
 * Интервал автообновления pending маршрутов (5 секунд, AC1).
 */
export const APPROVALS_REFRESH_INTERVAL = 5000

/**
 * Время, после которого данные считаются устаревшими (2 секунды).
 */
export const APPROVALS_STALE_TIME = 2000

/**
 * Hook для получения списка pending маршрутов.
 *
 * Автоматически обновляется каждые 5 секунд (AC1, AC4).
 * Polling не выполняется когда вкладка скрыта (AC4).
 */
export function usePendingRoutes() {
  return useQuery({
    queryKey: [PENDING_ROUTES_QUERY_KEY],
    queryFn: approvalsApi.fetchPendingRoutes,
    refetchInterval: APPROVALS_REFRESH_INTERVAL, // 5 секунд auto-refresh (AC1)
    refetchIntervalInBackground: false, // не polling когда tab скрыт (AC4)
    staleTime: APPROVALS_STALE_TIME,
  })
}

/**
 * Hook для получения количества pending маршрутов.
 *
 * Запрос выполняется только для ролей security и admin (enabled=false для developer).
 * React Query кэш используется совместно с usePendingRoutes.
 * Автоматически обновляется каждые 5 секунд для обновления badge в sidebar (AC2).
 */
export function usePendingRoutesCount() {
  const { user } = useAuth()

  const { data } = useQuery({
    queryKey: [PENDING_ROUTES_QUERY_KEY],
    queryFn: approvalsApi.fetchPendingRoutes,
    // Запрос только для security и admin — предотвращает 403 для developer
    // Story 11.6: используем централизованный helper canApprove
    enabled: canApprove(user ?? undefined),
    select: (data) => data.length,
    refetchInterval: APPROVALS_REFRESH_INTERVAL, // 5 секунд auto-refresh для badge (AC2)
    refetchIntervalInBackground: false, // не polling когда tab скрыт
    staleTime: APPROVALS_STALE_TIME,
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
  const { message } = App.useApp()

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
  const { message } = App.useApp()

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
