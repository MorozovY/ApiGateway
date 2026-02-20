package com.company.gateway.admin.dto

/**
 * DTO для общей сводки метрик gateway.
 *
 * Возвращает агрегированные метрики с момента старта приложения.
 * Параметр period указывает запрошенный период, но метрики являются
 * cumulative (Micrometer ограничение).
 *
 * @see com.company.gateway.admin.service.MetricsService
 */
data class MetricsSummaryDto(
    /** Запрошенный период (5m, 15m, 1h, 6h, 24h) */
    val period: String,

    /** Общее количество запросов */
    val totalRequests: Long,

    /** Запросов в секунду (рассчитывается на основе period) */
    val requestsPerSecond: Double,

    /** Среднее время ответа в миллисекундах */
    val avgLatencyMs: Long,

    /** 95-й перцентиль latency в миллисекундах */
    val p95LatencyMs: Long,

    /** 99-й перцентиль latency в миллисекундах */
    val p99LatencyMs: Long,

    /** Доля ошибок (0.0 - 1.0) */
    val errorRate: Double,

    /** Общее количество ошибок */
    val errorCount: Long,

    /** Количество активных (published) маршрутов */
    val activeRoutes: Int
)
