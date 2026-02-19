// Типы данных для Rate Limit политик (Story 5.4)

/**
 * Rate Limit политика.
 *
 * Содержит настройки лимитирования запросов для маршрутов.
 */
export interface RateLimit {
  id: string
  name: string
  description: string | null
  requestsPerSecond: number
  burstSize: number
  usageCount: number
  createdBy: string
  createdAt: string
  updatedAt: string
}

/**
 * Ответ списка Rate Limit политик с пагинацией.
 */
export interface RateLimitListResponse {
  items: RateLimit[]
  total: number
  offset: number
  limit: number
}

/**
 * Запрос на создание Rate Limit политики.
 */
export interface CreateRateLimitRequest {
  name: string
  description?: string
  requestsPerSecond: number
  burstSize: number
}

/**
 * Запрос на обновление Rate Limit политики.
 */
export interface UpdateRateLimitRequest {
  name?: string
  description?: string
  requestsPerSecond?: number
  burstSize?: number
}
