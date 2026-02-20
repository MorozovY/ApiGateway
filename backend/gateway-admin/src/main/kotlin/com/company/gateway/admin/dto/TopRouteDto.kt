package com.company.gateway.admin.dto

/**
 * DTO для маршрута в топ-листе метрик.
 *
 * Используется в endpoint /metrics/top-routes для отображения
 * маршрутов с наибольшим количеством запросов, latency или ошибок.
 *
 * @see com.company.gateway.admin.service.MetricsService
 */
data class TopRouteDto(
    /** UUID маршрута */
    val routeId: String,

    /** URL path маршрута */
    val path: String,

    /**
     * Значение метрики (зависит от параметра by):
     * - requests: количество запросов
     * - latency: среднее время ответа в мс
     * - errors: количество ошибок
     */
    val value: Double,

    /** Тип метрики для сортировки: "requests", "latency", "errors" */
    val metric: String
)
