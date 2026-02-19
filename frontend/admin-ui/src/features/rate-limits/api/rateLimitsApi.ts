// API функции для Rate Limit политик (Story 5.4)
import axios from '@/shared/utils/axios'
import type {
  RateLimit,
  RateLimitListResponse,
  CreateRateLimitRequest,
  UpdateRateLimitRequest,
} from '../types/rateLimit.types'
import type { Route } from '@/features/routes/types/route.types'

const BASE_URL = '/api/v1/rate-limits'

/**
 * Получение списка Rate Limit политик с пагинацией.
 */
export async function getRateLimits(params?: {
  offset?: number
  limit?: number
}): Promise<RateLimitListResponse> {
  const { data } = await axios.get<RateLimitListResponse>(BASE_URL, { params })
  return data
}

/**
 * Получение одной Rate Limit политики по ID.
 */
export async function getRateLimit(id: string): Promise<RateLimit> {
  const { data } = await axios.get<RateLimit>(`${BASE_URL}/${id}`)
  return data
}

/**
 * Создание новой Rate Limit политики.
 */
export async function createRateLimit(
  request: CreateRateLimitRequest
): Promise<RateLimit> {
  const { data } = await axios.post<RateLimit>(BASE_URL, request)
  return data
}

/**
 * Обновление существующей Rate Limit политики.
 */
export async function updateRateLimit(
  id: string,
  request: UpdateRateLimitRequest
): Promise<RateLimit> {
  const { data } = await axios.put<RateLimit>(`${BASE_URL}/${id}`, request)
  return data
}

/**
 * Удаление Rate Limit политики.
 *
 * Возвращает 409 Conflict если политика используется маршрутами.
 */
export async function deleteRateLimit(id: string): Promise<void> {
  await axios.delete(`${BASE_URL}/${id}`)
}

/**
 * Получение маршрутов, использующих указанную Rate Limit политику.
 *
 * Используется для AC7 — просмотр использующих маршрутов.
 */
export async function getRoutesByRateLimitId(rateLimitId: string): Promise<Route[]> {
  const { data } = await axios.get<{ items: Route[] }>('/api/v1/routes', {
    params: { rateLimitId },
  })
  return data.items
}
