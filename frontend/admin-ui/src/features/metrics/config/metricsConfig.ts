// Конфигурация для Metrics feature (Story 6.5)

/**
 * URL Grafana сервера.
 * В production должен быть переопределён через environment variable.
 */
export const GRAFANA_URL = import.meta.env.VITE_GRAFANA_URL || 'http://localhost:3001'

/**
 * ID Grafana dashboard для API Gateway.
 */
export const GRAFANA_DASHBOARD_ID = 'gateway-dashboard'

/**
 * Полный URL Grafana dashboard.
 */
export const GRAFANA_DASHBOARD_URL = `${GRAFANA_URL}/d/${GRAFANA_DASHBOARD_ID}/api-gateway`

/**
 * Интервал автообновления метрик (в миллисекундах).
 */
export const METRICS_REFRESH_INTERVAL = 10000 // 10 секунд

/**
 * Время устаревания кэша метрик (в миллисекундах).
 */
export const METRICS_STALE_TIME = 5000 // 5 секунд

/**
 * Количество точек истории для sparkline графиков.
 * 180 точек × 10 секунд = 30 минут истории (AC2).
 */
export const TREND_HISTORY_SIZE = 180

/**
 * Минимальное количество точек для отображения sparkline.
 * При меньшем количестве отображаем placeholder.
 */
export const MIN_SPARKLINE_POINTS = 3
