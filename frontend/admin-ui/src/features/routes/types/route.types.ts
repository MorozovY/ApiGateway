// Типы для управления маршрутами (Story 3.4, расширено в Story 5.5)

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
 * Краткая информация о политике rate limit (из API response).
 * Соответствует RateLimitInfo DTO на backend (Story 5.2).
 */
export interface RateLimitInfo {
  id: string
  name: string
  requestsPerSecond: number
  burstSize: number
}

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
  /** Полная информация о rate limit политике (Story 5.5) */
  rateLimit?: RateLimitInfo | null
  // Поля rejection/approval (Story 4.5) — nullable, т.к. большинство маршрутов их не имеют
  rejectionReason?: string | null
  rejectorUsername?: string | null
  rejectedAt?: string | null
  approverUsername?: string | null
  approvedAt?: string | null
  // JWT Authentication fields (Story 12.7)
  /** Требуется ли JWT аутентификация для маршрута */
  authRequired: boolean
  /** Whitelist consumer IDs (null = все разрешены) */
  allowedConsumers: string[] | null
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
  /** Фильтр по upstream host (Story 7.6, AC4) — partial match (ILIKE) */
  upstream?: string
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
  /** ID политики rate limit (Story 5.5) */
  rateLimitId?: string | null
  /** Требуется ли JWT аутентификация (Story 12.7). Default: true */
  authRequired?: boolean
  /** Whitelist consumer IDs (Story 12.7). Null = все разрешены */
  allowedConsumers?: string[] | null
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
  /** ID политики rate limit (Story 5.5) */
  rateLimitId?: string | null
  /** Требуется ли JWT аутентификация (Story 12.7) */
  authRequired?: boolean
  /** Whitelist consumer IDs (Story 12.7) */
  allowedConsumers?: string[] | null
}

// ========================================
// Route History Types (Story 7.6, AC1, AC2)
// ========================================

/**
 * Действия в истории маршрута.
 *
 * Подмножество AuditAction, относящееся к маршрутам.
 */
export type RouteHistoryAction =
  | 'created'
  | 'updated'
  | 'deleted'
  | 'approved'
  | 'rejected'
  | 'submitted'
  | 'published'

/**
 * Информация о пользователе в истории маршрута.
 */
export interface RouteHistoryUser {
  id: string
  username: string
}

/**
 * Структура изменений в истории маршрута.
 */
export interface RouteHistoryChanges {
  before?: Record<string, unknown> | null
  after?: Record<string, unknown> | null
}

/**
 * Запись истории изменений маршрута.
 *
 * Соответствует RouteHistoryEntry DTO на backend (Story 7.3).
 */
export interface RouteHistoryEntry {
  /** Временная метка события (ISO 8601) */
  timestamp: string
  /** Тип действия */
  action: RouteHistoryAction
  /** Пользователь, выполнивший действие */
  user: RouteHistoryUser
  /** Изменения: before/after для updated, после для created, до для deleted */
  changes: RouteHistoryChanges | null
}

/**
 * Ответ с историей изменений маршрута.
 *
 * Соответствует RouteHistoryResponse DTO на backend (Story 7.3).
 */
export interface RouteHistoryResponse {
  /** ID маршрута */
  routeId: string
  /** Текущий path маршрута */
  currentPath: string
  /** Хронологический список событий (newest first) */
  history: RouteHistoryEntry[]
}
