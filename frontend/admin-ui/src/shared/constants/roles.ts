// Централизованные константы ролей (Story 11.6)

/**
 * Роль пользователя в системе.
 * Lowercase — стандарт проекта (см. CLAUDE.md).
 */
export type UserRole = 'developer' | 'security' | 'admin'

/**
 * Все доступные роли (для итерации).
 */
export const ROLES: readonly UserRole[] = ['developer', 'security', 'admin'] as const

/**
 * Опции ролей для форм и фильтров (на русском, Story 16.1).
 */
export const ROLE_OPTIONS: { value: UserRole; label: string }[] = [
  { value: 'developer', label: 'Разработчик' },
  { value: 'security', label: 'Безопасность' },
  { value: 'admin', label: 'Администратор' },
]

/**
 * Цвета для отображения ролей в UI.
 */
export const ROLE_COLORS: Record<UserRole, string> = {
  developer: 'blue',
  security: 'orange',
  admin: 'purple',
}

/**
 * Метки ролей для отображения (на русском, Story 16.1).
 */
export const ROLE_LABELS: Record<UserRole, string> = {
  developer: 'Разработчик',
  security: 'Безопасность',
  admin: 'Администратор',
}
