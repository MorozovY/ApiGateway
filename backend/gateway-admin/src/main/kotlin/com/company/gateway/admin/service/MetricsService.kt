package com.company.gateway.admin.service

import com.company.gateway.admin.dto.MetricsPeriod
import com.company.gateway.admin.dto.MetricsSortBy
import com.company.gateway.admin.dto.MetricsSummaryDto
import com.company.gateway.admin.dto.RouteMetricsDto
import com.company.gateway.admin.dto.TopRouteDto
import com.company.gateway.admin.exception.NotFoundException
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.common.model.RouteStatus
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Сервис для агрегации и чтения метрик gateway.
 *
 * Читает метрики из MeterRegistry (Micrometer), собранные MetricsFilter в gateway-core.
 *
 * ВАЖНО: Micrometer хранит cumulative метрики с момента старта приложения.
 * Параметр period используется для расчёта RPS (requests / period_seconds),
 * но сами метрики не фильтруются по времени.
 * Для полноценных time-series рекомендуется Prometheus + PromQL.
 */
@Service
class MetricsService(
    private val meterRegistry: MeterRegistry,
    private val routeRepository: RouteRepository
) {
    private val logger = LoggerFactory.getLogger(MetricsService::class.java)

    companion object {
        // Названия метрик (должны совпадать с MetricsFilter в gateway-core)
        const val METRIC_REQUESTS_TOTAL = "gateway_requests_total"
        const val METRIC_REQUEST_DURATION = "gateway_request_duration_seconds"
        const val METRIC_ERRORS_TOTAL = "gateway_errors_total"

        // Теги для фильтрации
        const val TAG_ROUTE_ID = "route_id"
        const val TAG_STATUS = "status"
    }

    /**
     * Получает общую сводку метрик gateway.
     *
     * @param period период для расчёта RPS (5m, 15m, 1h, 6h, 24h)
     * @return Mono с агрегированными метриками
     */
    fun getSummary(period: MetricsPeriod): Mono<MetricsSummaryDto> {
        logger.debug("Получение сводки метрик за период: {}", period.value)

        // Суммируем все counters запросов
        val requestsTotal = meterRegistry.find(METRIC_REQUESTS_TOTAL)
            .counters()
            .sumOf { it.count() }

        // Суммируем все counters ошибок
        val errorsTotal = meterRegistry.find(METRIC_ERRORS_TOTAL)
            .counters()
            .sumOf { it.count() }

        // Получаем timer для latency (агрегируем по всем тегам)
        val timers = meterRegistry.find(METRIC_REQUEST_DURATION).timers()
        val (avgLatencyMs, p95LatencyMs, p99LatencyMs) = calculateLatencyStats(timers)

        // RPS = requests / period_seconds
        val periodSeconds = period.duration.seconds.toDouble()
        val rps = if (periodSeconds > 0) requestsTotal / periodSeconds else 0.0

        // Error rate = errors / requests
        val errorRate = if (requestsTotal > 0) errorsTotal / requestsTotal else 0.0

        // Active routes — считаем published routes из DB
        return routeRepository.countByStatus(RouteStatus.PUBLISHED)
            .map { activeRoutes ->
                MetricsSummaryDto(
                    period = period.value,
                    totalRequests = requestsTotal.toLong(),
                    requestsPerSecond = roundTo2Decimals(rps),
                    avgLatencyMs = avgLatencyMs,
                    p95LatencyMs = p95LatencyMs,
                    p99LatencyMs = p99LatencyMs,
                    errorRate = roundTo4Decimals(errorRate),
                    errorCount = errorsTotal.toLong(),
                    activeRoutes = activeRoutes.toInt()
                )
            }
            .doOnSuccess { logger.debug("Метрики сводки: {}", it) }
    }

    /**
     * Получает метрики для конкретного маршрута.
     *
     * @param routeId UUID маршрута
     * @param period период для расчёта RPS
     * @return Mono с метриками маршрута или NotFoundException если маршрут не найден
     */
    fun getRouteMetrics(routeId: UUID, period: MetricsPeriod): Mono<RouteMetricsDto> {
        logger.debug("Получение метрик маршрута: {} за период: {}", routeId, period.value)

        // Сначала проверяем, что маршрут существует
        return routeRepository.findById(routeId)
            .switchIfEmpty(Mono.error(
                NotFoundException("Route not found: $routeId", "Маршрут не найден: $routeId")
            ))
            .map { route ->
                val routeIdStr = routeId.toString()

                // Фильтруем метрики по route_id
                val routeCounters = meterRegistry.find(METRIC_REQUESTS_TOTAL)
                    .tag(TAG_ROUTE_ID, routeIdStr)
                    .counters()

                val requestsTotal = routeCounters.sumOf { it.count() }

                // Ошибки по маршруту
                val errorsTotal = meterRegistry.find(METRIC_ERRORS_TOTAL)
                    .tag(TAG_ROUTE_ID, routeIdStr)
                    .counters()
                    .sumOf { it.count() }

                // Latency по маршруту
                val routeTimers = meterRegistry.find(METRIC_REQUEST_DURATION)
                    .tag(TAG_ROUTE_ID, routeIdStr)
                    .timers()

                val (avgLatencyMs, p95LatencyMs, _) = calculateLatencyStats(routeTimers)

                // RPS
                val periodSeconds = period.duration.seconds.toDouble()
                val rps = if (periodSeconds > 0) requestsTotal / periodSeconds else 0.0

                // Error rate
                val errorRate = if (requestsTotal > 0) errorsTotal / requestsTotal else 0.0

                // Status breakdown
                val statusBreakdown = calculateStatusBreakdown(routeIdStr)

                RouteMetricsDto(
                    routeId = routeIdStr,
                    path = route.path,
                    period = period.value,
                    requestsPerSecond = roundTo2Decimals(rps),
                    avgLatencyMs = avgLatencyMs,
                    p95LatencyMs = p95LatencyMs,
                    errorRate = roundTo4Decimals(errorRate),
                    statusBreakdown = statusBreakdown
                )
            }
            .doOnSuccess { logger.debug("Метрики маршрута {}: {}", routeId, it) }
    }

    /**
     * Получает топ маршрутов по указанному критерию.
     *
     * ВАЖНО: Фильтрация по ownerId применяется к глобальному топу маршрутов.
     * Например, если запрошен limit=10 и ownerId указан, метод вернёт только те
     * маршруты из глобального топ-10, которые принадлежат указанному владельцу.
     * Это может быть меньше чем limit, если не все маршруты из топа принадлежат владельцу.
     *
     * @param sortBy критерий сортировки (requests, latency, errors)
     * @param limit максимальное количество маршрутов в глобальном топе для анализа
     * @param ownerId если указан — фильтрует только маршруты созданные этим пользователем (AC1 Story 6.5.1)
     * @return Mono со списком топ-маршрутов (может быть меньше limit при фильтрации по ownerId)
     */
    fun getTopRoutes(sortBy: MetricsSortBy, limit: Int, ownerId: UUID? = null): Mono<List<TopRouteDto>> {
        logger.debug("Получение топ-{} маршрутов по: {}, ownerId: {}", limit, sortBy.value, ownerId)

        // Собираем метрики по каждому уникальному route_id
        val routeMetrics = mutableMapOf<String, RouteMetricData>()

        // Собираем requests
        meterRegistry.find(METRIC_REQUESTS_TOTAL)
            .counters()
            .forEach { counter ->
                val routeId = counter.id.getTag(TAG_ROUTE_ID) ?: return@forEach
                if (routeId == "unknown") return@forEach

                val data = routeMetrics.getOrPut(routeId) { RouteMetricData() }
                data.requests += counter.count()
            }

        // Собираем errors
        meterRegistry.find(METRIC_ERRORS_TOTAL)
            .counters()
            .forEach { counter ->
                val routeId = counter.id.getTag(TAG_ROUTE_ID) ?: return@forEach
                if (routeId == "unknown") return@forEach

                val data = routeMetrics.getOrPut(routeId) { RouteMetricData() }
                data.errors += counter.count()
            }

        // Собираем latency
        meterRegistry.find(METRIC_REQUEST_DURATION)
            .timers()
            .forEach { timer ->
                val routeId = timer.id.getTag(TAG_ROUTE_ID) ?: return@forEach
                if (routeId == "unknown") return@forEach

                val data = routeMetrics.getOrPut(routeId) { RouteMetricData() }
                // Среднее время для каждого timer
                val meanMs = timer.mean(TimeUnit.MILLISECONDS)
                val count = timer.count()
                if (count > 0) {
                    // Взвешенное среднее
                    data.totalLatencyMs += meanMs * count
                    data.latencyCount += count
                }
            }

        // Сортируем и берём топ
        val sorted = when (sortBy) {
            MetricsSortBy.REQUESTS -> routeMetrics.entries.sortedByDescending { it.value.requests }
            MetricsSortBy.LATENCY -> routeMetrics.entries.sortedByDescending { it.value.avgLatency }
            MetricsSortBy.ERRORS -> routeMetrics.entries.sortedByDescending { it.value.errors }
        }

        val topRouteIds = sorted.take(limit).map { it.key }

        // Загружаем path из DB для каждого route_id
        return if (topRouteIds.isEmpty()) {
            Mono.just(emptyList())
        } else {
            // Получаем пути маршрутов из базы данных
            val uuids = topRouteIds.mapNotNull {
                try {
                    UUID.fromString(it)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }

            // Фильтруем по владельцу если указан (AC1 Story 6.5.1)
            val routesMono = if (ownerId != null) {
                routeRepository.findAllById(uuids)
                    .filter { it.createdBy == ownerId }
                    .collectMap({ it.id.toString() }, { it.path })
            } else {
                routeRepository.findAllById(uuids)
                    .collectMap({ it.id.toString() }, { it.path })
            }

            routesMono.map { pathMap ->
                // Фильтруем sorted чтобы включать только маршруты которые прошли фильтр
                sorted.filter { (routeId, _) -> pathMap.containsKey(routeId) }
                    .take(limit)
                    .map { (routeId, data) ->
                        val value = when (sortBy) {
                            MetricsSortBy.REQUESTS -> data.requests
                            MetricsSortBy.LATENCY -> data.avgLatency
                            MetricsSortBy.ERRORS -> data.errors
                        }
                        TopRouteDto(
                            routeId = routeId,
                            path = pathMap[routeId] ?: "unknown",
                            value = roundTo2Decimals(value),
                            metric = sortBy.value
                        )
                    }
            }
        }.doOnSuccess { logger.debug("Топ маршруты: {}", it) }
    }

    /**
     * Рассчитывает статистику latency из коллекции timers.
     *
     * @return Triple(avgMs, p95Ms, p99Ms)
     */
    private fun calculateLatencyStats(timers: Collection<Timer>): Triple<Long, Long, Long> {
        if (timers.isEmpty()) {
            return Triple(0L, 0L, 0L)
        }

        // Взвешенное среднее по всем timers
        var totalTime = 0.0
        var totalCount = 0L
        timers.forEach { timer ->
            totalTime += timer.totalTime(TimeUnit.MILLISECONDS)
            totalCount += timer.count()
        }

        val avgMs = if (totalCount > 0) (totalTime / totalCount).toLong() else 0L

        // Для percentiles берём максимальные значения из всех timers
        // (Micrometer не агрегирует percentiles через несколько timers)
        val p95Ms = timers.maxOfOrNull { timer ->
            timer.takeSnapshot().percentileValues()
                .find { it.percentile() == 0.95 }?.value(TimeUnit.MILLISECONDS) ?: 0.0
        }?.toLong() ?: 0L

        val p99Ms = timers.maxOfOrNull { timer ->
            timer.takeSnapshot().percentileValues()
                .find { it.percentile() == 0.99 }?.value(TimeUnit.MILLISECONDS) ?: 0.0
        }?.toLong() ?: 0L

        return Triple(avgMs, p95Ms, p99Ms)
    }

    /**
     * Рассчитывает breakdown запросов по HTTP статусам для маршрута.
     */
    private fun calculateStatusBreakdown(routeId: String): Map<String, Long> {
        val breakdown = mutableMapOf<String, Long>()

        meterRegistry.find(METRIC_REQUESTS_TOTAL)
            .tag(TAG_ROUTE_ID, routeId)
            .counters()
            .forEach { counter ->
                val status = counter.id.getTag(TAG_STATUS) ?: "unknown"
                breakdown[status] = breakdown.getOrDefault(status, 0L) + counter.count().toLong()
            }

        return breakdown
    }

    private fun roundTo2Decimals(value: Double): Double =
        kotlin.math.round(value * 100) / 100

    private fun roundTo4Decimals(value: Double): Double =
        kotlin.math.round(value * 10000) / 10000

    /**
     * Внутренний класс для агрегации метрик маршрута.
     */
    private data class RouteMetricData(
        var requests: Double = 0.0,
        var errors: Double = 0.0,
        var totalLatencyMs: Double = 0.0,
        var latencyCount: Long = 0
    ) {
        val avgLatency: Double
            get() = if (latencyCount > 0) totalLatencyMs / latencyCount else 0.0
    }
}
