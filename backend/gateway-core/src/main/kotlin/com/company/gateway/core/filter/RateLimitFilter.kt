package com.company.gateway.core.filter

import com.company.gateway.common.model.RateLimit
import com.company.gateway.core.ratelimit.RateLimitResult
import com.company.gateway.core.ratelimit.RateLimitService
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Глобальный фильтр для rate limiting запросов.
 *
 * Выполняется после CorrelationIdFilter (order: HIGHEST_PRECEDENCE) и
 * до LoggingFilter (order: LOWEST_PRECEDENCE - 1).
 *
 * Реализует:
 * - Distributed rate limiting через Redis (token bucket алгоритм)
 * - Graceful degradation с локальным fallback при недоступности Redis
 * - Информационные заголовки X-RateLimit-* в ответах
 * - HTTP 429 Too Many Requests в формате RFC 7807
 */
@Component
class RateLimitFilter(
    private val rateLimitService: RateLimitService
) : GlobalFilter, Ordered {

    private val logger = LoggerFactory.getLogger(RateLimitFilter::class.java)

    companion object {
        /**
         * Order фильтра: после CorrelationIdFilter (HIGHEST_PRECEDENCE), до LoggingFilter.
         */
        const val FILTER_ORDER = 10

        /**
         * Ключ атрибута exchange для ID маршрута.
         */
        const val ROUTE_ID_ATTRIBUTE = "gateway.routeId"

        /**
         * Ключ атрибута exchange для политики rate limit.
         */
        const val RATE_LIMIT_ATTRIBUTE = "gateway.rateLimit"

        // Заголовки rate limit
        private const val HEADER_RATE_LIMIT = "X-RateLimit-Limit"
        private const val HEADER_RATE_REMAINING = "X-RateLimit-Remaining"
        private const val HEADER_RATE_RESET = "X-RateLimit-Reset"
        private const val HEADER_RETRY_AFTER = "Retry-After"
    }

    override fun getOrder(): Int = FILTER_ORDER

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        // Получаем routeId и rateLimit из атрибутов exchange
        // Эти атрибуты устанавливаются в DynamicRouteLocator
        val routeId = exchange.getAttribute<UUID>(ROUTE_ID_ATTRIBUTE)
        val rateLimit = exchange.getAttribute<RateLimit>(RATE_LIMIT_ATTRIBUTE)

        // Если нет rate limit политики — пропускаем проверку (AC5)
        if (rateLimit == null || routeId == null) {
            return chain.filter(exchange)
        }

        // Получаем идентификатор клиента
        val clientKey = extractClientKey(exchange)

        // Выполняем проверку rate limit
        return rateLimitService.checkRateLimit(routeId, clientKey, rateLimit)
            .flatMap { result ->
                if (result.allowed) {
                    // Запрос разрешён — добавляем информационные заголовки и продолжаем
                    addRateLimitHeaders(exchange, rateLimit, result)
                    chain.filter(exchange)
                } else {
                    // Запрос отклонён — возвращаем 429 Too Many Requests
                    rejectRequest(exchange, rateLimit, result)
                }
            }
    }

    /**
     * Извлекает идентификатор клиента из запроса.
     *
     * Приоритет:
     * 1. X-Forwarded-For (первый IP в цепочке)
     * 2. Remote address
     * 3. "unknown" как fallback
     *
     * SECURITY NOTE: X-Forwarded-For может быть подделан клиентом для обхода rate limiting.
     * В production среде gateway должен быть развёрнут за доверенным reverse proxy/load balancer,
     * который устанавливает корректный X-Forwarded-For. При прямом доступе к gateway
     * рекомендуется использовать только remote address.
     *
     * TODO: Добавить настройку gateway.ratelimit.trusted-proxies для списка доверенных IP,
     * и использовать X-Forwarded-For только если запрос пришёл от доверенного прокси.
     * См. Spring Security XForwardedHeadersFilter для reference implementation.
     */
    private fun extractClientKey(exchange: ServerWebExchange): String {
        // Проверяем X-Forwarded-For для запросов через прокси/балансировщик
        val forwardedFor = exchange.request.headers.getFirst("X-Forwarded-For")
        if (!forwardedFor.isNullOrBlank()) {
            // Берём первый IP (оригинальный клиент)
            return forwardedFor.split(",").first().trim()
        }

        // Fallback на remote address
        return exchange.request.remoteAddress?.address?.hostAddress ?: "unknown"
    }

    /**
     * Добавляет заголовки rate limit в успешный ответ (AC7).
     */
    private fun addRateLimitHeaders(
        exchange: ServerWebExchange,
        rateLimit: RateLimit,
        result: RateLimitResult
    ) {
        val response = exchange.response
        response.headers.add(HEADER_RATE_LIMIT, rateLimit.requestsPerSecond.toString())
        response.headers.add(HEADER_RATE_REMAINING, result.remaining.toString())
        // Reset time в Unix seconds (не миллисекунды)
        response.headers.add(HEADER_RATE_RESET, (result.resetTime / 1000).toString())
    }

    /**
     * Отклоняет запрос с HTTP 429 Too Many Requests в формате RFC 7807 (AC2, Task 6).
     */
    private fun rejectRequest(
        exchange: ServerWebExchange,
        rateLimit: RateLimit,
        result: RateLimitResult
    ): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.TOO_MANY_REQUESTS

        // Rate limit заголовки
        response.headers.add(HEADER_RATE_LIMIT, rateLimit.requestsPerSecond.toString())
        response.headers.add(HEADER_RATE_REMAINING, "0")
        response.headers.add(HEADER_RATE_RESET, (result.resetTime / 1000).toString())

        // Retry-After в секундах
        val retryAfterSeconds = ((result.resetTime - System.currentTimeMillis()) / 1000).coerceAtLeast(1)
        response.headers.add(HEADER_RETRY_AFTER, retryAfterSeconds.toString())

        response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON

        // Получаем correlation ID из атрибутов
        val correlationId = exchange.getAttribute<String>(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE)
            ?: "unknown"

        // RFC 7807 Problem Details формат
        val errorBody = """
        {
            "type": "https://api.gateway/errors/rate-limit-exceeded",
            "title": "Too Many Requests",
            "status": 429,
            "detail": "Rate limit exceeded. Try again in $retryAfterSeconds seconds.",
            "correlationId": "$correlationId"
        }
        """.trimIndent()

        val buffer = response.bufferFactory().wrap(errorBody.toByteArray(Charsets.UTF_8))
        return response.writeWith(Mono.just(buffer))
    }
}
