/**
 * Типы для Dashboard API (Story 16.2)
 */

/**
 * Статистика маршрутов по статусам
 */
export interface RoutesByStatus {
  draft: number
  pending: number
  published: number
  rejected: number
}

/**
 * Сводка Dashboard
 *
 * Поля зависят от роли пользователя:
 * - DEVELOPER: routesByStatus, pendingApprovalsCount
 * - SECURITY: routesByStatus, pendingApprovalsCount
 * - ADMIN: все поля + totalUsers, totalConsumers, systemHealth
 */
export interface DashboardSummary {
  routesByStatus: RoutesByStatus
  pendingApprovalsCount: number
  totalUsers?: number
  totalConsumers?: number | null
  systemHealth?: 'healthy' | 'degraded' | 'down' | 'unknown'
}

/**
 * Элемент последней активности
 */
export interface ActivityItem {
  id: string
  action: string
  entityType: string
  entityId: string
  entityName: string | null
  performedBy: string
  performedAt: string // ISO 8601 timestamp
}

/**
 * Ответ API recent-activity
 */
export interface RecentActivityResponse {
  items: ActivityItem[]
}
