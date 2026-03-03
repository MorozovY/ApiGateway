package com.company.gateway.core.filter

import io.micrometer.tracing.Tracer
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * Добавляет кастомные атрибуты к текущему span для поиска в Jaeger (Story 14.5).
 *
 * Атрибуты:
 * - gateway.route.id — ID маршрута из DynamicRouteLocator
 * - gateway.route.path — path pattern маршрута
 * - gateway.consumer.id — consumer ID из JWT/header
 * - gateway.ratelimit.decision — allowed/denied
 *
 * Выполняется после routing (HIGHEST_PRECEDENCE + 100) когда route уже определён,
 * но до upstream forwarding.
 *
 * Примечание: Использует Optional Tracer injection — если tracing не настроен,
 * фильтр gracefully degradates и не добавляет атрибуты.
 */
@Component
class TracingAttributesFilter(
    private val tracer: Tracer?
) : GlobalFilter, Ordered {

    private val logger = LoggerFactory.getLogger(TracingAttributesFilter::class.java)

    companion object {
        /**
         * Order фильтра: после routing и после RateLimitFilter.
         * Order 100 выполняется после:
         * - CorrelationIdFilter (HIGHEST_PRECEDENCE)
         * - JwtAuthenticationFilter (HIGHEST_PRECEDENCE + 3)
         * - ConsumerIdentityFilter (HIGHEST_PRECEDENCE + 8)
         * - MetricsFilter (HIGHEST_PRECEDENCE + 10)
         * - RateLimitFilter (10)
         * И до:
         * - LoggingFilter (LOWEST_PRECEDENCE - 1)
         *
         * Примечание: В Spring Ordered более высокое значение = более низкий приоритет.
         * HIGHEST_PRECEDENCE = Integer.MIN_VALUE = выполняется первым.
         * Наш order 100 > RateLimitFilter.FILTER_ORDER (10) = выполняется позже.
         */
        const val FILTER_ORDER = 100

        // Span attribute keys для Jaeger
        const val ROUTE_ID_ATTR = "gateway.route.id"
        const val ROUTE_PATH_ATTR = "gateway.route.path"
        const val CONSUMER_ID_ATTR = "gateway.consumer.id"
        const val RATELIMIT_DECISION_ATTR = "gateway.ratelimit.decision"

        // Exchange attribute key для rate limit decision
        // Устанавливается в RateLimitFilter после проверки лимита
        const val RATELIMIT_DECISION_ATTRIBUTE = "gateway.ratelimit.decision"
    }

    override fun getOrder(): Int = FILTER_ORDER

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        // Если tracer не настроен — пропускаем добавление атрибутов
        if (tracer == null) {
            return chain.filter(exchange)
        }

        val span = tracer.currentSpan()
        if (span == null) {
            logger.debug("No current span found, skipping tracing attributes")
            return chain.filter(exchange)
        }

        // Route info (устанавливается RoutePredicateHandlerMapping)
        val route = exchange.getAttribute<Route>(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR)
        if (route != null) {
            span.tag(ROUTE_ID_ATTR, route.id)
            // Path из predicate для поиска по pattern
            route.predicate.toString().let { predicate ->
                // Извлекаем path из predicate string (формат: "Paths: [/api/users/**], match trailing slash: true")
                val pathMatch = Regex("Paths: \\[([^\\]]+)\\]").find(predicate)
                val path = pathMatch?.groupValues?.get(1) ?: route.uri?.path ?: "unknown"
                span.tag(ROUTE_PATH_ATTR, path)
            }
        }

        // Consumer ID (устанавливается ConsumerIdentityFilter)
        val consumerId = exchange.getAttribute<String>(JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE)
        if (!consumerId.isNullOrBlank()) {
            span.tag(CONSUMER_ID_ATTR, consumerId)
        }

        // Rate limit decision (устанавливается RateLimitFilter)
        // Примечание: этот фильтр выполняется ПОСЛЕ RateLimitFilter (order 10 < 100)
        val rateLimitDecision = exchange.getAttribute<String>(RATELIMIT_DECISION_ATTRIBUTE)
        if (!rateLimitDecision.isNullOrBlank()) {
            span.tag(RATELIMIT_DECISION_ATTR, rateLimitDecision)
        }

        return chain.filter(exchange)
    }
}
