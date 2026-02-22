package com.company.gateway.admin.service

import com.company.gateway.admin.client.PrometheusClient
import com.company.gateway.admin.client.PromQLBuilder
import com.company.gateway.admin.client.dto.PrometheusQueryResponse
import com.company.gateway.admin.dto.MetricsPeriod
import com.company.gateway.admin.dto.MetricsSortBy
import com.company.gateway.admin.dto.MetricsSummaryDto
import com.company.gateway.admin.dto.RouteMetricsDto
import com.company.gateway.admin.dto.TopRouteDto
import com.company.gateway.admin.exception.NotFoundException
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.common.model.RouteStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Сервис для агрегации и чтения метрик gateway.
 *
 * Story 7.0: Читает метрики из Prometheus HTTP API вместо локального MeterRegistry.
 * Это позволяет получать реальные метрики gateway-core, собранные MetricsFilter.
 *
 * Архитектура:
 * - gateway-core собирает метрики через MetricsFilter → /actuator/prometheus
 * - Prometheus scrape'ит gateway-core
 * - gateway-admin query Prometheus через HTTP API
 * - Admin UI отображает реальные данные
 */
@Service
class MetricsService(
    private val prometheusClient: PrometheusClient,
    private val routeRepository: RouteRepository
) {
    private val logger = LoggerFactory.getLogger(MetricsService::class.java)

    companion object {
        // Теги для фильтрации (используются при парсинге Prometheus ответов)
        const val TAG_ROUTE_ID = "route_id"
        const val TAG_STATUS = "status"
    }

    /**
     * Получает общую сводку метрик gateway из Prometheus.
     *
     * @param period период для расчёта rate-based метрик (5m, 15m, 1h, 6h, 24h)
     * @return Mono с агрегированными метриками
     */
    fun getSummary(period: MetricsPeriod): Mono<MetricsSummaryDto> {
        logger.debug("Получение сводки метрик за период: {}", period.value)

        // Формируем PromQL запросы
        val queries = mapOf(
            "totalRequests" to PromQLBuilder.totalRequests(period),
            "rps" to PromQLBuilder.requestsPerSecond(period),
            "avgLatency" to PromQLBuilder.avgLatencySeconds(period),
            "p95Latency" to PromQLBuilder.latencyPercentile(period, 0.95),
            "p99Latency" to PromQLBuilder.latencyPercentile(period, 0.99),
            "errorRate" to PromQLBuilder.errorRate(period),
            "totalErrors" to PromQLBuilder.totalErrors(period)
        )

        // Выполняем запросы к Prometheus параллельно
        return prometheusClient.queryMultiple(queries)
            .flatMap { results ->
                // Получаем количество активных маршрутов из DB
                routeRepository.countByStatus(RouteStatus.PUBLISHED)
                    .map { activeRoutes ->
                        buildSummaryDto(period, results, activeRoutes.toInt())
                    }
            }
            .doOnSuccess { logger.debug("Метрики сводки: {}", it) }
    }

    /**
     * Получает метрики для конкретного маршрута из Prometheus.
     *
     * @param routeId UUID маршрута
     * @param period период для расчёта rate-based метрик
     * @return Mono с метриками маршрута или NotFoundException если маршрут не найден
     */
    fun getRouteMetrics(routeId: UUID, period: MetricsPeriod): Mono<RouteMetricsDto> {
        logger.debug("Получение метрик маршрута: {} за период: {}", routeId, period.value)

        val routeIdStr = routeId.toString()

        // Сначала проверяем, что маршрут существует
        return routeRepository.findById(routeId)
            .switchIfEmpty(Mono.error(
                NotFoundException("Route not found: $routeId", "Маршрут не найден: $routeId")
            ))
            .flatMap { route ->
                // Формируем PromQL запросы для конкретного маршрута
                val queries = mapOf(
                    "rps" to PromQLBuilder.routeRequestsPerSecond(routeIdStr, period),
                    "avgLatency" to PromQLBuilder.routeAvgLatencySeconds(routeIdStr, period),
                    "p95Latency" to PromQLBuilder.routeLatencyPercentile(routeIdStr, period, 0.95),
                    "errorRate" to PromQLBuilder.routeErrorRate(routeIdStr, period),
                    "statusBreakdown" to PromQLBuilder.routeStatusBreakdown(routeIdStr, period)
                )

                prometheusClient.queryMultiple(queries)
                    .map { results ->
                        buildRouteMetricsDto(routeIdStr, route.path, period, results)
                    }
            }
            .doOnSuccess { logger.debug("Метрики маршрута {}: {}", routeId, it) }
    }

    /**
     * Получает топ маршрутов по указанному критерию из Prometheus.
     *
     * Архитектура (исправлено в review):
     * 1. Сначала получаем маршруты из БД (чтобы не показывать удалённые)
     * 2. Формируем PromQL запрос для этих конкретных route_id
     * 3. Сортируем по метрике и возвращаем top N
     *
     * Это гарантирует что мы показываем только существующие маршруты,
     * даже если в Prometheus есть метрики удалённых маршрутов.
     *
     * Story 10.10: Добавлен параметр period для фильтрации метрик по time range.
     *
     * @param sortBy критерий сортировки (requests, latency, errors)
     * @param limit максимальное количество маршрутов
     * @param ownerId если указан — фильтрует только маршруты созданные этим пользователем
     * @param period период для расчёта метрик (5m, 15m, 1h, 6h, 24h)
     * @return Mono со списком топ-маршрутов
     */
    fun getTopRoutes(
        sortBy: MetricsSortBy,
        limit: Int,
        ownerId: UUID? = null,
        period: MetricsPeriod = MetricsPeriod.FIVE_MINUTES
    ): Mono<List<TopRouteDto>> {
        logger.debug("Получение топ-{} маршрутов по: {}, ownerId: {}, period: {}", limit, sortBy.value, ownerId, period.value)

        // Шаг 1: Получаем маршруты из БД
        val routesMono = if (ownerId != null) {
            routeRepository.findByStatus(RouteStatus.PUBLISHED)
                .filter { it.createdBy == ownerId }
                .collectList()
        } else {
            routeRepository.findByStatus(RouteStatus.PUBLISHED)
                .collectList()
        }

        return routesMono.flatMap { routes ->
            if (routes.isEmpty()) {
                return@flatMap Mono.just(emptyList<TopRouteDto>())
            }

            // Создаём карту route_id -> path для быстрого lookup
            val routePathMap = routes.associate { it.id.toString() to it.path }
            val routeIds = routePathMap.keys.toList()

            // Шаг 2: Формируем PromQL запрос для маршрутов из БД с учётом period
            val query = when (sortBy) {
                MetricsSortBy.REQUESTS -> PromQLBuilder.totalRequestsByRouteIds(routeIds, period)
                MetricsSortBy.LATENCY -> PromQLBuilder.avgLatencyByRouteIds(routeIds, period)
                MetricsSortBy.ERRORS -> PromQLBuilder.totalErrorsByRouteIds(routeIds, period)
            }

            if (query.isEmpty()) {
                return@flatMap Mono.just(emptyList<TopRouteDto>())
            }

            // Шаг 3: Query Prometheus
            prometheusClient.query(query).map { response ->
                if (!response.isSuccess() || response.data?.result.isNullOrEmpty()) {
                    // Если нет метрик — возвращаем маршруты с value=0
                    routes.take(limit).map { route ->
                        TopRouteDto(
                            routeId = route.id.toString(),
                            path = route.path,
                            value = 0.0,
                            metric = sortBy.value
                        )
                    }
                } else {
                    // Парсим результаты из Prometheus
                    val metricsMap = response.data!!.result.mapNotNull { metric ->
                        val routeId = metric.getLabel(TAG_ROUTE_ID)
                        val value = metric.getValue()
                        if (routeId != null && value != null) {
                            routeId to value
                        } else {
                            null
                        }
                    }.toMap()

                    // Шаг 4: Сортируем по value (убывание) и берём top N
                    routes
                        .map { route ->
                            val routeId = route.id.toString()
                            val value = metricsMap[routeId] ?: 0.0
                            TopRouteDto(
                                routeId = routeId,
                                path = route.path,
                                value = roundTo2Decimals(sanitizeNaN(value)),
                                metric = sortBy.value
                            )
                        }
                        .sortedByDescending { it.value }
                        .take(limit)
                }
            }
        }.doOnSuccess { logger.debug("Топ маршруты: {}", it) }
    }

    /**
     * Собирает MetricsSummaryDto из результатов Prometheus запросов.
     */
    private fun buildSummaryDto(
        period: MetricsPeriod,
        results: Map<String, PrometheusQueryResponse>,
        activeRoutes: Int
    ): MetricsSummaryDto {
        val totalRequests = results["totalRequests"]?.getScalarValue()?.toLong() ?: 0L
        val rps = results["rps"]?.getScalarValue() ?: 0.0
        val avgLatencySeconds = results["avgLatency"]?.getScalarValue() ?: 0.0
        val p95LatencySeconds = results["p95Latency"]?.getScalarValue() ?: 0.0
        val p99LatencySeconds = results["p99Latency"]?.getScalarValue() ?: 0.0
        val errorRate = results["errorRate"]?.getScalarValue() ?: 0.0
        val totalErrors = results["totalErrors"]?.getScalarValue()?.toLong() ?: 0L

        return MetricsSummaryDto(
            period = period.value,
            totalRequests = totalRequests,
            requestsPerSecond = roundTo2Decimals(sanitizeNaN(rps)),
            avgLatencyMs = (sanitizeNaN(avgLatencySeconds) * 1000).toLong(),
            p95LatencyMs = (sanitizeNaN(p95LatencySeconds) * 1000).toLong(),
            p99LatencyMs = (sanitizeNaN(p99LatencySeconds) * 1000).toLong(),
            errorRate = roundTo4Decimals(sanitizeNaN(errorRate)),
            errorCount = totalErrors,
            activeRoutes = activeRoutes
        )
    }

    /**
     * Собирает RouteMetricsDto из результатов Prometheus запросов.
     */
    private fun buildRouteMetricsDto(
        routeId: String,
        path: String,
        period: MetricsPeriod,
        results: Map<String, PrometheusQueryResponse>
    ): RouteMetricsDto {
        val rps = results["rps"]?.getScalarValue() ?: 0.0
        val avgLatencySeconds = results["avgLatency"]?.getScalarValue() ?: 0.0
        val p95LatencySeconds = results["p95Latency"]?.getScalarValue() ?: 0.0
        val errorRate = results["errorRate"]?.getScalarValue() ?: 0.0

        // Парсим status breakdown из результата
        val statusBreakdown = parseStatusBreakdown(results["statusBreakdown"])

        return RouteMetricsDto(
            routeId = routeId,
            path = path,
            period = period.value,
            requestsPerSecond = roundTo2Decimals(sanitizeNaN(rps)),
            avgLatencyMs = (sanitizeNaN(avgLatencySeconds) * 1000).toLong(),
            p95LatencyMs = (sanitizeNaN(p95LatencySeconds) * 1000).toLong(),
            errorRate = roundTo4Decimals(sanitizeNaN(errorRate)),
            statusBreakdown = statusBreakdown
        )
    }

    /**
     * Парсит status breakdown из Prometheus ответа.
     *
     * Prometheus возвращает результат в формате:
     * [
     *   {"metric": {"status": "2xx"}, "value": [timestamp, "100"]},
     *   {"metric": {"status": "4xx"}, "value": [timestamp, "5"]}
     * ]
     */
    private fun parseStatusBreakdown(response: PrometheusQueryResponse?): Map<String, Long> {
        if (response == null || !response.isSuccess()) {
            return emptyMap()
        }

        return response.data?.result?.mapNotNull { metric ->
            val status = metric.getLabel(TAG_STATUS)
            val value = metric.getValue()?.toLong()
            if (status != null && value != null) {
                status to value
            } else {
                null
            }
        }?.toMap() ?: emptyMap()
    }

    /**
     * Заменяет NaN на 0.0.
     *
     * Prometheus может возвращать NaN при делении на ноль
     * (например, error rate когда нет запросов).
     */
    private fun sanitizeNaN(value: Double): Double =
        if (value.isNaN() || value.isInfinite()) 0.0 else value

    private fun roundTo2Decimals(value: Double): Double =
        kotlin.math.round(value * 100) / 100

    private fun roundTo4Decimals(value: Double): Double =
        kotlin.math.round(value * 10000) / 10000
}
