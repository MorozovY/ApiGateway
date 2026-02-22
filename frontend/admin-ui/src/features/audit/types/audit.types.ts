// Типы для аудит-логов (Story 7.5)

/**
 * Типы действий аудит-лога.
 *
 * Соответствует enum AuditAction на backend (Story 7.1).
 * Включает 'published' для полного соответствия backend schema.
 * Story 10.8: Добавлен 'route.rolledback' для rollback операций.
 */
export type AuditAction =
  | 'created'
  | 'updated'
  | 'deleted'
  | 'approved'
  | 'rejected'
  | 'submitted'
  | 'published'
  | 'route.rolledback'

/**
 * Типы сущностей аудит-лога.
 *
 * Соответствует возможным значениям entityType на backend.
 */
export type AuditEntityType = 'route' | 'user' | 'rate_limit'

/**
 * Информация о пользователе, выполнившем действие.
 *
 * Соответствует AuditLogResponse.UserInfo на backend.
 */
export interface AuditUserInfo {
  id: string
  username: string
}

/**
 * Структура изменений для аудит-событий.
 *
 * Для CRUD операций (created, updated, deleted):
 * - before — состояние до изменения (null для created)
 * - after — состояние после изменения (null для deleted)
 *
 * Для approval/status операций (approved, rejected, submitted, route.rolledback):
 * - Generic поля: previousStatus, newStatus, approvedAt, rejectedAt, etc.
 *
 * Story 10.8: Добавлена поддержка generic полей через index signature.
 */
export interface AuditChanges {
  before?: Record<string, unknown> | null
  after?: Record<string, unknown> | null
  // Generic поля для approval/status операций (Story 10.8)
  [key: string]: unknown
}

/**
 * Запись аудит-лога.
 *
 * Соответствует AuditLogResponse DTO на backend.
 */
export interface AuditLogEntry {
  id: string
  entityType: AuditEntityType
  entityId: string
  action: AuditAction
  user: AuditUserInfo
  timestamp: string
  changes: AuditChanges | null
  ipAddress: string | null
  correlationId: string | null
}

/**
 * Фильтры для запроса аудит-логов.
 *
 * Соответствует query params в GET /api/v1/audit.
 * action поддерживает multi-select (AC2).
 */
export interface AuditFilter {
  userId?: string
  action?: AuditAction[]
  entityType?: AuditEntityType
  dateFrom?: string
  dateTo?: string
  offset?: number
  limit?: number
}

/**
 * Пагинированный ответ со списком аудит-логов.
 *
 * Соответствует PagedResponse<AuditLogResponse> на backend.
 */
export interface AuditLogsResponse {
  items: AuditLogEntry[]
  total: number
  offset: number
  limit: number
}

// ========================================
// Типы для Route History (Story 7.6, AC1, AC2)
// Re-export из routes feature для обратной совместимости
// ========================================

export type {
  RouteHistoryEntry,
  RouteHistoryResponse,
  RouteHistoryAction,
  RouteHistoryUser,
  RouteHistoryChanges,
} from '@features/routes/types/route.types'

// ========================================
// Типы для Upstream Report (Story 7.6, AC3)
// ========================================

/**
 * Сводка по upstream сервису.
 *
 * Соответствует UpstreamSummary DTO на backend (Story 7.4).
 */
export interface UpstreamSummary {
  /** Хост upstream сервиса (без схемы, e.g., "order-service:8080") */
  host: string
  /** Количество маршрутов, использующих этот upstream */
  routeCount: number
}

/**
 * Ответ со списком upstream сервисов.
 *
 * Соответствует UpstreamsResponse DTO на backend (Story 7.4).
 */
export interface UpstreamsResponse {
  /** Список upstream сервисов */
  upstreams: UpstreamSummary[]
}
