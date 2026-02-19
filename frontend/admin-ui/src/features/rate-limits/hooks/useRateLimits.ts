// React Query hooks для Rate Limit политик (Story 5.4)
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { message } from 'antd'
import * as rateLimitsApi from '../api/rateLimitsApi'
import type {
  CreateRateLimitRequest,
  UpdateRateLimitRequest,
} from '../types/rateLimit.types'

/**
 * Ключи для React Query кэша.
 */
const QUERY_KEYS = {
  rateLimits: ['rateLimits'] as const,
  rateLimit: (id: string) => ['rateLimits', id] as const,
  rateLimitRoutes: (id: string) => ['rateLimits', id, 'routes'] as const,
}

/**
 * Hook для получения списка Rate Limit политик.
 *
 * Поддерживает пагинацию через offset и limit.
 */
export function useRateLimits(params?: { offset?: number; limit?: number }) {
  return useQuery({
    queryKey: [...QUERY_KEYS.rateLimits, params],
    queryFn: () => rateLimitsApi.getRateLimits(params),
  })
}

/**
 * Hook для получения одной Rate Limit политики.
 */
export function useRateLimit(id: string | undefined) {
  return useQuery({
    // SAFETY: id гарантированно определён благодаря enabled: !!id
    queryKey: QUERY_KEYS.rateLimit(id!),
    queryFn: () => rateLimitsApi.getRateLimit(id!),
    enabled: !!id,
  })
}

/**
 * Hook для создания новой Rate Limit политики.
 *
 * После успешного создания инвалидирует кэш списка.
 */
export function useCreateRateLimit() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: CreateRateLimitRequest) => rateLimitsApi.createRateLimit(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.rateLimits, refetchType: 'all' })
      message.success('Policy created')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Failed to create policy')
    },
  })
}

/**
 * Hook для обновления Rate Limit политики.
 *
 * После успешного обновления инвалидирует кэш.
 */
export function useUpdateRateLimit() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateRateLimitRequest }) =>
      rateLimitsApi.updateRateLimit(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.rateLimits, refetchType: 'all' })
      message.success('Policy updated')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Failed to update policy')
    },
  })
}

/**
 * Интерфейс ошибки с деталями от API.
 */
interface ApiError extends Error {
  response?: {
    data?: {
      detail?: string
    }
  }
}

/**
 * Hook для удаления Rate Limit политики.
 *
 * Обрабатывает 409 Conflict — политика используется маршрутами.
 */
export function useDeleteRateLimit() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => rateLimitsApi.deleteRateLimit(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.rateLimits, refetchType: 'all' })
      message.success('Policy deleted')
    },
    onError: (error: ApiError) => {
      // Обработка 409 Conflict (AC6) — политика используется маршрутами
      const detail = error.response?.data?.detail
      if (detail) {
        // Backend возвращает сообщение в формате AC6
        message.error(detail)
      } else {
        message.error('Failed to delete policy')
      }
    },
  })
}

/**
 * Hook для получения маршрутов, использующих Rate Limit политику.
 *
 * Используется для AC7 — просмотр использующих маршрутов.
 */
export function useRoutesByRateLimitId(id: string | undefined) {
  return useQuery({
    // SAFETY: id гарантированно определён благодаря enabled: !!id
    queryKey: QUERY_KEYS.rateLimitRoutes(id!),
    queryFn: () => rateLimitsApi.getRoutesByRateLimitId(id!),
    enabled: !!id,
  })
}
