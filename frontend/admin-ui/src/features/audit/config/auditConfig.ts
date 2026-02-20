// Конфигурация для аудит-логов (Story 7.5, AC5)
import type { AuditAction, AuditEntityType } from '../types/audit.types'

/**
 * Русские лейблы для действий аудит-лога (AC5).
 * Включает 'published' для полного соответствия backend schema (Story 7.1).
 */
export const AUDIT_ACTION_LABELS: Record<AuditAction, string> = {
  created: 'Создано',
  updated: 'Обновлено',
  deleted: 'Удалено',
  approved: 'Одобрено',
  rejected: 'Отклонено',
  submitted: 'Отправлено',
  published: 'Опубликовано',
}

/**
 * Цвета для action badges (AC5).
 *
 * created: green, updated: blue, deleted: red,
 * approved: green, rejected: orange, submitted: purple,
 * published: cyan (для полного соответствия backend schema)
 */
export const AUDIT_ACTION_COLORS: Record<AuditAction, string> = {
  created: 'green',
  updated: 'blue',
  deleted: 'red',
  approved: 'green',
  rejected: 'orange',
  submitted: 'purple',
  published: 'cyan',
}

/**
 * Русские лейблы для типов сущностей.
 */
export const ENTITY_TYPE_LABELS: Record<AuditEntityType, string> = {
  route: 'Маршрут',
  user: 'Пользователь',
  rate_limit: 'Rate Limit',
}

/**
 * Опции для фильтра по действию (multi-select) (AC2).
 */
export const AUDIT_ACTION_OPTIONS = Object.entries(AUDIT_ACTION_LABELS).map(
  ([value, label]) => ({
    value,
    label,
  })
)

/**
 * Опции для фильтра по типу сущности (AC2).
 */
export const ENTITY_TYPE_OPTIONS = Object.entries(ENTITY_TYPE_LABELS).map(
  ([value, label]) => ({
    value,
    label,
  })
)

/**
 * Размер страницы по умолчанию (AC1).
 */
export const DEFAULT_PAGE_SIZE = 20

/**
 * Задержка debounce для фильтров (AC2).
 */
export const FILTER_DEBOUNCE_MS = 300

/**
 * Максимальное количество записей для экспорта в CSV (AC4).
 */
export const MAX_CSV_EXPORT_ROWS = 10000
