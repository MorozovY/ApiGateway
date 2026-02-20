package com.company.gateway.admin.dto

/**
 * DTO для метрик конкретного маршрута.
 *
 * Содержит детальную информацию о производительности маршрута,
 * включая breakdown по HTTP статус-кодам.
 *
 * @see com.company.gateway.admin.service.MetricsService
 */
data class RouteMetricsDto(
    /** UUID маршрута */
    val routeId: String,

    /** URL path маршрута */
    val path: String,

    /** Запрошенный период (5m, 15m, 1h, 6h, 24h) */
    val period: String,

    /** Запросов в секунду */
    val requestsPerSecond: Double,

    /** Среднее время ответа в миллисекундах */
    val avgLatencyMs: Long,

    /** 95-й перцентиль latency в миллисекундах */
    val p95LatencyMs: Long,

    /** Доля ошибок (0.0 - 1.0) */
    val errorRate: Double,

    /**
     * Breakdown запросов по категориям статус-кодов.
     * Ключи: "2xx", "3xx", "4xx", "5xx"
     */
    val statusBreakdown: Map<String, Long>
)
