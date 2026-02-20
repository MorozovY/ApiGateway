// Утилиты для работы с error rate (Story 6.5)

/**
 * Пороговые значения для error rate.
 */
export const ERROR_RATE_THRESHOLDS = {
  HEALTHY: 0.01, // < 1%
  WARNING: 0.05, // < 5%
} as const

/**
 * Цвета для разных уровней error rate.
 */
export const ERROR_RATE_COLORS = {
  healthy: '#52c41a', // зелёный
  warning: '#faad14', // жёлтый
  critical: '#f5222d', // красный
} as const

/**
 * Цвета для Ant Design Tag компонента.
 */
export const ERROR_RATE_TAG_COLORS = {
  healthy: 'green',
  warning: 'orange',
  critical: 'red',
} as const

export type ErrorRateStatus = 'healthy' | 'warning' | 'critical'

/**
 * Определяет статус error rate по порогам.
 *
 * - healthy: < 1%
 * - warning: 1-5%
 * - critical: > 5%
 */
export function getErrorRateStatus(rate: number): ErrorRateStatus {
  if (rate < ERROR_RATE_THRESHOLDS.HEALTHY) return 'healthy'
  if (rate < ERROR_RATE_THRESHOLDS.WARNING) return 'warning'
  return 'critical'
}

/**
 * Возвращает HEX цвет для error rate (для inline styles).
 */
export function getErrorRateColor(rate: number): string {
  const status = getErrorRateStatus(rate)
  return ERROR_RATE_COLORS[status]
}

/**
 * Возвращает цвет для Ant Design Tag компонента.
 */
export function getErrorRateTagColor(rate: number): string {
  const status = getErrorRateStatus(rate)
  return ERROR_RATE_TAG_COLORS[status]
}
