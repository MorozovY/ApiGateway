package com.company.gateway.core.filter

import com.company.gateway.common.model.RateLimit
import com.company.gateway.core.cache.ConsumerRateLimitCacheManager
import com.company.gateway.core.ratelimit.RateLimitCheckResult
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
    private val rateLimitService: RateLimitService,
    private val consumerRateLimitCacheManager: ConsumerRateLimitCacheManager
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

        /**
         * Заголовок типа сработавшего лимита.
         * Story 12.8, AC3: Указывает какой лимит сработал (route/consumer).
         */
        private const val HEADER_RATE_LIMIT_TYPE = "X-RateLimit-Type"
    }

    override fun getOrder(): Int = FILTER_ORDER

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        // Получаем routeId и rateLimit из атрибутов exchange
        // Эти атрибуты устанавливаются в DynamicRouteLocator
        val routeId = exchange.getAttribute<UUID>(ROUTE_ID_ATTRIBUTE)
        val routeLimit = exchange.getAttribute<RateLimit>(RATE_LIMIT_ATTRIBUTE)

        // Получаем consumer ID из JwtAuthenticationFilter/ConsumerIdentityFilter
        val consumerId = exchange.getAttribute<String>(JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE)
            ?: ConsumerIdentityFilter.ANONYMOUS

        // Получаем идентификатор клиента (IP)
        val clientKey = extractClientKey(exchange)

        // Проверяем наличие per-consumer rate limit
        // Используем hasElement() + flatMap для корректной обработки Mono<Void>
        return consumerRateLimitCacheManager.getConsumerRateLimit(consumerId)
            .flatMap { consumerLimit ->
                if (consumerLimit != null) {
                    // AC4: Есть consumer limit — проверяем оба лимита
                    rateLimitService.checkBothLimits(routeId, clientKey, consumerId, routeLimit, consumerLimit)
                        .flatMap { checkResult -> handleCheckResult(exchange, chain, checkResult) }
                        .then(Mono.just(true))  // Маркер: rate limit обработан
                } else {
                    // Consumer limit = null, нужен fallback
                    Mono.just(false)
                }
            }
            .defaultIfEmpty(false)  // Если Mono.empty() от getConsumerRateLimit
            .flatMap { wasProcessed ->
                if (wasProcessed) {
                    // Rate limit уже обработан
                    Mono.empty()
                } else {
                    // AC5: Fallback на per-route rate limit
                    if (routeLimit != null && routeId != null) {
                        rateLimitService.checkRateLimit(routeId, clientKey, routeLimit)
                            .flatMap { result ->
                                val checkResult = RateLimitCheckResult(
                                    result = result,
                                    limitType = RateLimitCheckResult.TYPE_ROUTE,
                                    limit = routeLimit.requestsPerSecond
                                )
                                handleCheckResult(exchange, chain, checkResult)
                            }
                    } else {
                        // Нет никаких rate limits — пропускаем
                        chain.filter(exchange)
                    }
                }
            }
    }

    /**
     * Обрабатывает результат проверки rate limit.
     */
    private fun handleCheckResult(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
        checkResult: RateLimitCheckResult
    ): Mono<Void> {
        return if (checkResult.result.allowed) {
            // Запрос разрешён — добавляем информационные заголовки и продолжаем
            addRateLimitHeaders(exchange, checkResult)
            chain.filter(exchange)
        } else {
            // Запрос отклонён — возвращаем 429 Too Many Requests
            rejectRequest(exchange, checkResult)
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
     *
     * Story 12.8: Добавляет X-RateLimit-Type для указания типа лимита.
     */
    private fun addRateLimitHeaders(
        exchange: ServerWebExchange,
        checkResult: RateLimitCheckResult
    ) {
        val response = exchange.response
        checkResult.limit?.let { limit ->
            response.headers.add(HEADER_RATE_LIMIT, limit.toString())
        }
        response.headers.add(HEADER_RATE_REMAINING, checkResult.result.remaining.toString())
        // Reset time в Unix seconds (не миллисекунды)
        if (checkResult.result.resetTime > 0) {
            response.headers.add(HEADER_RATE_RESET, (checkResult.result.resetTime / 1000).toString())
        }
        // Тип лимита (route/consumer) — AC3, AC4, AC5
        checkResult.limitType?.let { type ->
            response.headers.add(HEADER_RATE_LIMIT_TYPE, type)
        }
    }

    /**
     * Отклоняет запрос с HTTP 429 Too Many Requests в формате RFC 7807 (AC2, Task 6).
     *
     * Story 12.8, AC3: Добавляет X-RateLimit-Type для указания какой лимит превышен.
     */
    private fun rejectRequest(
        exchange: ServerWebExchange,
        checkResult: RateLimitCheckResult
    ): Mono<Void> {
        val response = exchange.response
        response.statusCode = HttpStatus.TOO_MANY_REQUESTS

        val result = checkResult.result

        // Rate limit заголовки
        checkResult.limit?.let { limit ->
            response.headers.add(HEADER_RATE_LIMIT, limit.toString())
        }
        response.headers.add(HEADER_RATE_REMAINING, "0")
        response.headers.add(HEADER_RATE_RESET, (result.resetTime / 1000).toString())

        // Тип лимита (route/consumer) — AC3
        checkResult.limitType?.let { type ->
            response.headers.add(HEADER_RATE_LIMIT_TYPE, type)
        }

        // Retry-After в секундах
        val retryAfterSeconds = ((result.resetTime - System.currentTimeMillis()) / 1000).coerceAtLeast(1)
        response.headers.add(HEADER_RETRY_AFTER, retryAfterSeconds.toString())

        response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON

        // Получаем correlation ID из атрибутов
        val correlationId = exchange.getAttribute<String>(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE)
            ?: "unknown"

        // RFC 7807 Problem Details формат
        val limitTypeDesc = checkResult.limitType ?: "unknown"
        val errorBody = """
        {
            "type": "https://api.gateway/errors/rate-limit-exceeded",
            "title": "Too Many Requests",
            "status": 429,
            "detail": "Rate limit ($limitTypeDesc) exceeded. Try again in $retryAfterSeconds seconds.",
            "correlationId": "$correlationId"
        }
        """.trimIndent()

        val buffer = response.bufferFactory().wrap(errorBody.toByteArray(Charsets.UTF_8))
        return response.writeWith(Mono.just(buffer))
    }
}
