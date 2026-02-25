// React Query hooks для управления consumers (Story 12.9)
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { App } from 'antd'
import * as consumersApi from '../api/consumersApi'
import type {
  ConsumerListParams,
  CreateConsumerRequest,
  ConsumerRateLimitRequest,
} from '../types/consumer.types'

/**
 * Ключи для React Query кэша.
 */
const CONSUMERS_QUERY_KEY = 'consumers'

/**
 * Hook для получения списка consumers.
 *
 * Поддерживает пагинацию и поиск.
 */
export function useConsumers(params: ConsumerListParams = {}) {
  return useQuery({
    queryKey: [CONSUMERS_QUERY_KEY, params],
    queryFn: () => consumersApi.fetchConsumers(params),
  })
}

/**
 * Hook для получения одного consumer.
 */
export function useConsumer(clientId: string) {
  return useQuery({
    queryKey: [CONSUMERS_QUERY_KEY, clientId],
    queryFn: () => consumersApi.fetchConsumer(clientId),
    enabled: !!clientId,
  })
}

/**
 * Hook для создания нового consumer.
 *
 * После успешного создания инвалидирует кэш списка.
 */
export function useCreateConsumer() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  return useMutation({
    mutationFn: (data: CreateConsumerRequest) => consumersApi.createConsumer(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [CONSUMERS_QUERY_KEY], refetchType: 'all' })
      message.success('Consumer создан')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Ошибка при создании consumer')
    },
  })
}

/**
 * Hook для ротации client secret.
 *
 * После успешной ротации инвалидирует кэш конкретного consumer.
 */
export function useRotateSecret() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  return useMutation({
    mutationFn: (clientId: string) => consumersApi.rotateSecret(clientId),
    onSuccess: (_, clientId) => {
      queryClient.invalidateQueries({ queryKey: [CONSUMERS_QUERY_KEY, clientId] })
      message.success('Secret ротирован')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Ошибка при ротации secret')
    },
  })
}

/**
 * Hook для деактивации consumer.
 *
 * После успешной деактивации инвалидирует кэш списка.
 */
export function useDisableConsumer() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  return useMutation({
    mutationFn: (clientId: string) => consumersApi.disableConsumer(clientId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [CONSUMERS_QUERY_KEY], refetchType: 'all' })
      message.success('Consumer деактивирован')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Ошибка при деактивации consumer')
    },
  })
}

/**
 * Hook для активации consumer.
 *
 * После успешной активации инвалидирует кэш списка.
 */
export function useEnableConsumer() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  return useMutation({
    mutationFn: (clientId: string) => consumersApi.enableConsumer(clientId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [CONSUMERS_QUERY_KEY], refetchType: 'all' })
      message.success('Consumer активирован')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Ошибка при активации consumer')
    },
  })
}

// ============ Consumer Rate Limit Hooks ============

/**
 * Hook для получения rate limit consumer.
 */
export function useConsumerRateLimit(consumerId: string) {
  return useQuery({
    queryKey: [CONSUMERS_QUERY_KEY, consumerId, 'rate-limit'],
    queryFn: () => consumersApi.getConsumerRateLimit(consumerId),
    enabled: !!consumerId,
  })
}

/**
 * Hook для установки rate limit (upsert).
 *
 * После успешного обновления инвалидирует кэш consumer и списка.
 */
export function useSetConsumerRateLimit() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  return useMutation({
    mutationFn: ({ consumerId, data }: { consumerId: string; data: ConsumerRateLimitRequest }) =>
      consumersApi.setConsumerRateLimit(consumerId, data),
    onSuccess: (_, { consumerId }) => {
      queryClient.invalidateQueries({ queryKey: [CONSUMERS_QUERY_KEY, consumerId] })
      queryClient.invalidateQueries({ queryKey: [CONSUMERS_QUERY_KEY], refetchType: 'all' })
      message.success('Rate limit установлен')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Ошибка при установке rate limit')
    },
  })
}

/**
 * Hook для удаления rate limit.
 *
 * После успешного удаления инвалидирует кэш consumer и списка.
 */
export function useDeleteConsumerRateLimit() {
  const queryClient = useQueryClient()
  const { message } = App.useApp()

  return useMutation({
    mutationFn: (consumerId: string) => consumersApi.deleteConsumerRateLimit(consumerId),
    onSuccess: (_, consumerId) => {
      queryClient.invalidateQueries({ queryKey: [CONSUMERS_QUERY_KEY, consumerId] })
      queryClient.invalidateQueries({ queryKey: [CONSUMERS_QUERY_KEY], refetchType: 'all' })
      message.success('Rate limit удалён')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Ошибка при удалении rate limit')
    },
  })
}
