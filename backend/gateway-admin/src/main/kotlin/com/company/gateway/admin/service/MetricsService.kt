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
        // Названия метрик (для обратной совместимости с тестами)
        const val METRIC_REQUESTS_TOTAL = "gateway_requests_total"
        const val METRIC_REQUEST_DURATION = "gateway_request_duration_seconds"
        const val METRIC_ERRORS_TOTAL = "gateway_errors_total"

        // Теги для фильтрации
        const val TAG_ROUTE_ID = "route_id"
        const val TAG_STATUS = "status"

        // Период по умолчанию для top routes
        private val DEFAULT_TOP_ROUTES_PERIOD = MetricsPeriod.FIVE_MINUTES
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
     * ВАЖНО: Фильтрация по ownerId применяется к глобальному топу маршрутов.
     * Например, если запрошен limit=10 и ownerId указан, метод вернёт только те
     * маршруты из глобального топ-10, которые принадлежат указанному владельцу.
     *
     * @param sortBy критерий сортировки (requests, latency, errors)
     * @param limit максимальное количество маршрутов
     * @param ownerId если указан — фильтрует только маршруты созданные этим пользователем
     * @return Mono со списком топ-маршрутов
     */
    fun getTopRoutes(sortBy: MetricsSortBy, limit: Int, ownerId: UUID? = null): Mono<List<TopRouteDto>> {
        logger.debug("Получение топ-{} маршрутов по: {}, ownerId: {}", limit, sortBy.value, ownerId)

        val period = DEFAULT_TOP_ROUTES_PERIOD

        // Формируем PromQL запрос в зависимости от критерия сортировки
        val query = when (sortBy) {
            MetricsSortBy.REQUESTS -> PromQLBuilder.topRoutesByRequests(period, limit)
            MetricsSortBy.LATENCY -> PromQLBuilder.topRoutesByLatency(period, limit)
            MetricsSortBy.ERRORS -> PromQLBuilder.topRoutesByErrors(period, limit)
        }

        return prometheusClient.query(query)
            .flatMap { response ->
                processTopRoutesResponse(response, sortBy, limit, ownerId)
            }
            .doOnSuccess { logger.debug("Топ маршруты: {}", it) }
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
     * Обрабатывает ответ Prometheus для top routes.
     */
    private fun processTopRoutesResponse(
        response: PrometheusQueryResponse,
        sortBy: MetricsSortBy,
        limit: Int,
        ownerId: UUID?
    ): Mono<List<TopRouteDto>> {
        if (!response.isSuccess() || response.data?.result.isNullOrEmpty()) {
            return Mono.just(emptyList())
        }

        // Извлекаем route_id и значения из результата
        val routeValues = response.data!!.result.mapNotNull { metric ->
            val routeId = metric.getLabel(TAG_ROUTE_ID)
            val value = metric.getValue()
            if (routeId != null && routeId != "unknown" && value != null) {
                routeId to value
            } else {
                null
            }
        }

        if (routeValues.isEmpty()) {
            return Mono.just(emptyList())
        }

        // Получаем UUID'ы маршрутов
        val uuids = routeValues.mapNotNull { (routeId, _) ->
            try {
                UUID.fromString(routeId)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        // Получаем пути маршрутов из базы данных с фильтрацией по владельцу
        val routesMono = if (ownerId != null) {
            routeRepository.findAllById(uuids)
                .filter { it.createdBy == ownerId }
                .collectMap({ it.id.toString() }, { it.path })
        } else {
            routeRepository.findAllById(uuids)
                .collectMap({ it.id.toString() }, { it.path })
        }

        return routesMono.map { pathMap ->
            routeValues
                .filter { (routeId, _) -> pathMap.containsKey(routeId) }
                .take(limit)
                .map { (routeId, value) ->
                    TopRouteDto(
                        routeId = routeId,
                        path = pathMap[routeId] ?: "unknown",
                        value = roundTo2Decimals(sanitizeNaN(value)),
                        metric = sortBy.value
                    )
                }
        }
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
