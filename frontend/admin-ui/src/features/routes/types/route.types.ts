// Типы для управления маршрутами (Story 3.4)

/**
 * Статус маршрута в системе.
 *
 * draft — черновик, доступен только автору
 * pending — ожидает одобрения
 * published — опубликован, активен в gateway
 * rejected — отклонён
 */
export type RouteStatus = 'draft' | 'pending' | 'published' | 'rejected'

/**
 * Данные маршрута.
 *
 * Соответствует API response из Story 3.2.
 */
export interface Route {
  id: string
  path: string
  upstreamUrl: string
  methods: string[]
  description: string | null
  status: RouteStatus
  createdBy: string
  creatorUsername?: string
  createdAt: string
  updatedAt: string
  rateLimitId: string | null
}

/**
 * Параметры для получения списка маршрутов.
 *
 * Все параметры опциональны.
 */
export interface RouteListParams {
  offset?: number
  limit?: number
  status?: RouteStatus
  search?: string
  createdBy?: string
}

/**
 * Пагинированный ответ со списком маршрутов.
 */
export interface RouteListResponse {
  items: Route[]
  total: number
  offset: number
  limit: number
}

/**
 * Запрос на создание нового маршрута.
 */
export interface CreateRouteRequest {
  path: string
  upstreamUrl: string
  methods: string[]
  description?: string
}

/**
 * Запрос на обновление маршрута.
 *
 * Все поля опциональны — обновляются только переданные.
 */
export interface UpdateRouteRequest {
  path?: string
  upstreamUrl?: string
  methods?: string[]
  description?: string
}
