package com.company.gateway.core.filter

import com.company.gateway.core.util.PathNormalizer
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Глобальный фильтр для сбора метрик gateway через Micrometer.
 *
 * Собирает следующие метрики:
 * - gateway_requests_total — счётчик общего количества запросов
 * - gateway_request_duration_seconds — гистограмма latency запросов
 * - gateway_errors_total — счётчик ошибок по типам
 *
 * Выполняется с HIGHEST_PRECEDENCE + 10 для обеспечения измерения полного времени запроса.
 * Запускается после CorrelationIdFilter (HIGHEST_PRECEDENCE), но до остальных фильтров.
 */
@Component
class MetricsFilter(
    private val meterRegistry: MeterRegistry
) : GlobalFilter, Ordered {

    companion object {
        /**
         * Order фильтра: сразу после CorrelationIdFilter для измерения полного времени.
         */
        const val FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10

        // Названия метрик
        const val METRIC_REQUESTS_TOTAL = "gateway_requests_total"
        const val METRIC_REQUEST_DURATION = "gateway_request_duration_seconds"
        const val METRIC_ERRORS_TOTAL = "gateway_errors_total"
        const val METRIC_ACTIVE_CONNECTIONS = "gateway_active_connections"

        // Labels
        const val TAG_ROUTE_ID = "route_id"
        const val TAG_ROUTE_PATH = "route_path"
        const val TAG_UPSTREAM_HOST = "upstream_host"
        const val TAG_METHOD = "method"
        const val TAG_STATUS = "status"
        const val TAG_ERROR_TYPE = "error_type"
    }

    /**
     * Атомарный счётчик активных соединений для gauge метрики.
     */
    private val activeConnections = AtomicLong(0)

    init {
        // Регистрируем gauge для активных соединений при инициализации
        meterRegistry.gauge(METRIC_ACTIVE_CONNECTIONS, activeConnections) { it.get().toDouble() }
    }

    override fun getOrder(): Int = FILTER_ORDER

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val startTime = System.nanoTime()

        // Увеличиваем счётчик активных соединений
        activeConnections.incrementAndGet()

        return chain.filter(exchange)
            .doFinally {
                // Уменьшаем счётчик активных соединений и записываем метрики
                activeConnections.decrementAndGet()
                recordMetrics(exchange, startTime)
            }
    }

    /**
     * Записывает метрики после завершения запроса.
     *
     * Метрики включают полный набор labels для per-route анализа:
     * - route_id: UUID маршрута
     * - route_path: нормализованный путь запроса (для контроля cardinality)
     * - upstream_host: hostname:port upstream-сервиса
     * - method: HTTP метод
     * - status: категория статус-кода (2xx, 4xx, 5xx)
     */
    private fun recordMetrics(exchange: ServerWebExchange, startTime: Long) {
        val response = exchange.response
        val statusCode = response.statusCode?.value() ?: 0
        val durationNanos = System.nanoTime() - startTime

        // Извлекаем информацию о маршруте
        val route = exchange.getAttribute<Route>(GATEWAY_ROUTE_ATTR)
        val routeId = route?.id ?: "unknown"
        val routePath = extractRoutePath(route, exchange)
        val upstreamHost = extractUpstreamHost(route?.uri)
        val method = exchange.request.method.name()
        val statusCategory = statusCategory(statusCode)

        // Полный набор tags для всех метрик
        val baseTags = Tags.of(
            TAG_ROUTE_ID, routeId,
            TAG_ROUTE_PATH, routePath,
            TAG_UPSTREAM_HOST, upstreamHost,
            TAG_METHOD, method,
            TAG_STATUS, statusCategory
        )

        // Counter: общее количество запросов
        meterRegistry.counter(METRIC_REQUESTS_TOTAL, baseTags).increment()

        // Timer/Histogram: latency запросов
        // Используем meterRegistry.timer() вместо Timer.builder() для эффективности.
        // Percentiles вычисляются через histogram_quantile() в PromQL, не через publishPercentiles().
        meterRegistry.timer(METRIC_REQUEST_DURATION, baseTags)
            .record(durationNanos, TimeUnit.NANOSECONDS)

        // Counter: ошибки по типам
        if (statusCode >= 400) {
            val errorTags = baseTags.and(TAG_ERROR_TYPE, classifyError(statusCode))
            meterRegistry.counter(METRIC_ERRORS_TOTAL, errorTags).increment()
        }
    }

    /**
     * Извлекает и нормализует путь запроса для метрик.
     *
     * @param route маршрут (может быть null для unknown routes)
     * @param exchange HTTP exchange
     * @return нормализованный путь или "unknown"
     */
    private fun extractRoutePath(route: Route?, exchange: ServerWebExchange): String {
        if (route == null) {
            return "unknown"
        }

        // Используем путь из запроса
        val requestPath = exchange.request.path.value()

        // Нормализуем путь для контроля cardinality
        return PathNormalizer.normalize(requestPath)
    }

    /**
     * Извлекает upstream host из URI маршрута.
     *
     * @param uri URI upstream-сервиса
     * @return hostname:port или "unknown"
     */
    private fun extractUpstreamHost(uri: URI?): String {
        if (uri == null) {
            return "unknown"
        }

        return try {
            val host = uri.host ?: return "unknown"
            val port = when {
                uri.port > 0 -> uri.port
                uri.scheme == "https" -> 443
                else -> 80
            }
            "$host:$port"
        } catch (_: Exception) {
            "unknown"
        }
    }

    /**
     * Возвращает категорию HTTP статуса (2xx, 3xx, 4xx, 5xx).
     */
    private fun statusCategory(statusCode: Int): String = when {
        statusCode in 200..299 -> "2xx"
        statusCode in 300..399 -> "3xx"
        statusCode in 400..499 -> "4xx"
        statusCode in 500..599 -> "5xx"
        else -> "unknown"
    }

    /**
     * Классифицирует ошибку по типу на основе HTTP статуса.
     *
     * @param statusCode HTTP статус код
     * @return тип ошибки: upstream_error, rate_limited, not_found, auth_error, client_error, internal_error
     */
    fun classifyError(statusCode: Int): String = when (statusCode) {
        429 -> "rate_limited"
        404 -> "not_found"
        502, 504 -> "upstream_error"
        401, 403 -> "auth_error"
        in 400..499 -> "client_error"
        else -> "internal_error"  // 5xx и прочие
    }
}
