// API для управления consumers (Story 12.9)
import axios from '@shared/utils/axios'
import type {
  Consumer,
  ConsumerListResponse,
  CreateConsumerRequest,
  CreateConsumerResponse,
  RotateSecretResponse,
  ConsumerRateLimitRequest,
  ConsumerRateLimit,
  ConsumerListParams,
} from '../types/consumer.types'


/**
 * Получение списка consumers с пагинацией и поиском.
 *
 * GET /api/v1/consumers?offset=0&limit=100&search=consumer
 */
export async function fetchConsumers(params: ConsumerListParams = {}): Promise<ConsumerListResponse> {
  const { offset = 0, limit = 100, search } = params
  const response = await axios.get<ConsumerListResponse>('/api/v1/consumers', {
    params: { offset, limit, search: search || undefined },
  })
  return response.data
}

/**
 * Получение consumer по client ID.
 *
 * GET /api/v1/consumers/{clientId}
 */
export async function fetchConsumer(clientId: string): Promise<Consumer> {
  const response = await axios.get<Consumer>(`/api/v1/consumers/${clientId}`)
  return response.data
}

/**
 * Создание нового consumer.
 *
 * POST /api/v1/consumers
 *
 * ВАЖНО: Secret показывается только один раз!
 */
export async function createConsumer(data: CreateConsumerRequest): Promise<CreateConsumerResponse> {
  const response = await axios.post<CreateConsumerResponse>('/api/v1/consumers', data)
  return response.data
}

/**
 * Ротация client secret.
 *
 * POST /api/v1/consumers/{clientId}/rotate-secret
 */
export async function rotateSecret(clientId: string): Promise<RotateSecretResponse> {
  const response = await axios.post<RotateSecretResponse>(`/api/v1/consumers/${clientId}/rotate-secret`)
  return response.data
}

/**
 * Деактивация consumer.
 *
 * POST /api/v1/consumers/{clientId}/disable
 */
export async function disableConsumer(clientId: string): Promise<void> {
  await axios.post(`/api/v1/consumers/${clientId}/disable`)
}

/**
 * Активация consumer.
 *
 * POST /api/v1/consumers/{clientId}/enable
 */
export async function enableConsumer(clientId: string): Promise<void> {
  await axios.post(`/api/v1/consumers/${clientId}/enable`)
}

// ============ Consumer Rate Limit API (reuse from Story 12.8) ============

/**
 * Установить rate limit для consumer (create or update).
 *
 * PUT /api/v1/consumers/{consumerId}/rate-limit
 */
export async function setConsumerRateLimit(
  consumerId: string,
  data: ConsumerRateLimitRequest
): Promise<ConsumerRateLimit> {
  const response = await axios.put<ConsumerRateLimit>(`/api/v1/consumers/${consumerId}/rate-limit`, data)
  return response.data
}

/**
 * Получить rate limit для consumer.
 *
 * GET /api/v1/consumers/{consumerId}/rate-limit
 */
export async function getConsumerRateLimit(consumerId: string): Promise<ConsumerRateLimit> {
  const response = await axios.get<ConsumerRateLimit>(`/api/v1/consumers/${consumerId}/rate-limit`)
  return response.data
}

/**
 * Удалить rate limit для consumer.
 *
 * DELETE /api/v1/consumers/{consumerId}/rate-limit
 */
export async function deleteConsumerRateLimit(consumerId: string): Promise<void> {
  await axios.delete(`/api/v1/consumers/${consumerId}/rate-limit`)
}
