package com.company.gateway.core.filter

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * Глобальный фильтр для JWT аутентификации запросов от API consumers.
 *
 * Выполняется после CorrelationIdFilter (order: HIGHEST_PRECEDENCE) и
 * до RateLimitFilter (order: 10).
 *
 * Реализует:
 * - Валидация JWT токенов через Keycloak JWKS
 * - Проверка consumer whitelist (allowed_consumers)
 * - Извлечение consumer_id для последующей обработки (метрики, rate limits)
 * - HTTP 401/403 ответы в формате RFC 7807
 *
 * @see com.company.gateway.core.config.KeycloakJwtConfig
 */
@Component
@ConditionalOnProperty(name = ["keycloak.enabled"], havingValue = "true")
class JwtAuthenticationFilter(
    private val jwtDecoder: ReactiveJwtDecoder
) : GlobalFilter, Ordered {

    private val logger = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    companion object {
        /**
         * Order фильтра: после CorrelationIdFilter (HIGHEST_PRECEDENCE), до RateLimitFilter (10).
         */
        const val FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 5

        /**
         * Ключ атрибута exchange для consumer ID.
         * Используется в MetricsFilter и RateLimitFilter.
         */
        const val CONSUMER_ID_ATTRIBUTE = "gateway.consumerId"

        /**
         * Ключ атрибута exchange для флага auth_required.
         * Устанавливается в DynamicRouteLocator.
         */
        const val AUTH_REQUIRED_ATTRIBUTE = "gateway.authRequired"

        /**
         * Ключ атрибута exchange для списка разрешённых consumers.
         * Устанавливается в DynamicRouteLocator.
         */
        const val ALLOWED_CONSUMERS_ATTRIBUTE = "gateway.allowedConsumers"

        /**
         * Bearer token prefix.
         */
        private const val BEARER_PREFIX = "Bearer "

        /**
         * Consumer ID для неаутентифицированных запросов к публичным маршрутам.
         */
        private const val ANONYMOUS_CONSUMER = "anonymous"

        /**
         * Consumer ID по умолчанию если не удалось извлечь из JWT.
         */
        private const val UNKNOWN_CONSUMER = "unknown"
    }

    override fun getOrder(): Int = FILTER_ORDER

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val authRequired = exchange.getAttribute<Boolean>(AUTH_REQUIRED_ATTRIBUTE) ?: true
        val authHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)

        // Public route без токена — пропускаем (AC3)
        if (!authRequired && authHeader == null) {
            logger.debug("Public route, no token required")
            exchange.attributes[CONSUMER_ID_ATTRIBUTE] = ANONYMOUS_CONSUMER
            return chain.filter(exchange)
        }

        // Protected route без токена — 401 (AC1)
        if (authRequired && authHeader == null) {
            logger.debug("Protected route, missing Authorization header")
            return unauthorized(exchange, "Missing Authorization header")
        }

        // Извлекаем токен из заголовка
        val token = extractToken(authHeader)
        if (token == null) {
            logger.debug("Invalid Authorization header format")
            return unauthorized(exchange, "Invalid Authorization header format")
        }

        // Валидация JWT (AC2, AC7)
        return jwtDecoder.decode(token)
            .flatMap { jwt ->
                val consumerId = extractConsumerId(jwt)
                logger.debug("JWT validated, consumer: {}", consumerId)

                // Проверка whitelist (AC4, AC5)
                val allowedConsumers = exchange.getAttribute<List<String>>(ALLOWED_CONSUMERS_ATTRIBUTE)
                if (allowedConsumers != null && consumerId !in allowedConsumers) {
                    logger.debug("Consumer {} not in whitelist: {}", consumerId, allowedConsumers)
                    return@flatMap forbidden(exchange, "Consumer not allowed for this route")
                }

                // Сохраняем consumer ID для последующих фильтров
                exchange.attributes[CONSUMER_ID_ATTRIBUTE] = consumerId
                chain.filter(exchange)
            }
            .onErrorResume { e ->
                logger.debug("JWT validation failed: {}", e.message)
                unauthorized(exchange, "Invalid or expired JWT token")
            }
    }

    /**
     * Извлекает Bearer token из Authorization header.
     *
     * @return токен без префикса "Bearer " или null если формат неверный
     */
    private fun extractToken(authHeader: String?): String? {
        if (authHeader.isNullOrBlank()) return null
        if (!authHeader.startsWith(BEARER_PREFIX, ignoreCase = true)) return null
        val token = authHeader.substring(BEARER_PREFIX.length).trim()
        return token.ifBlank { null }
    }

    /**
     * Извлекает consumer ID из JWT claims.
     *
     * Приоритет:
     * 1. `azp` (Authorized Party) — для client_credentials flow
     * 2. `clientId` — fallback для некоторых конфигураций Keycloak
     * 3. "unknown" — если ничего не найдено
     */
    private fun extractConsumerId(jwt: Jwt): String {
        return jwt.claims["azp"]?.toString()
            ?: jwt.claims["clientId"]?.toString()
            ?: UNKNOWN_CONSUMER
    }

    /**
     * Возвращает HTTP 401 Unauthorized в формате RFC 7807 (AC1, AC7).
     */
    private fun unauthorized(exchange: ServerWebExchange, detail: String): Mono<Void> {
        return errorResponse(
            exchange = exchange,
            status = HttpStatus.UNAUTHORIZED,
            type = "https://api.gateway/errors/unauthorized",
            title = "Unauthorized",
            detail = detail,
            wwwAuthenticate = "Bearer"
        )
    }

    /**
     * Возвращает HTTP 403 Forbidden в формате RFC 7807 (AC4).
     */
    private fun forbidden(exchange: ServerWebExchange, detail: String): Mono<Void> {
        return errorResponse(
            exchange = exchange,
            status = HttpStatus.FORBIDDEN,
            type = "https://api.gateway/errors/forbidden",
            title = "Forbidden",
            detail = detail,
            wwwAuthenticate = null
        )
    }

    /**
     * Формирует RFC 7807 Problem Details ответ.
     */
    private fun errorResponse(
        exchange: ServerWebExchange,
        status: HttpStatus,
        type: String,
        title: String,
        detail: String,
        wwwAuthenticate: String?
    ): Mono<Void> {
        val response = exchange.response
        response.statusCode = status
        response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON

        // WWW-Authenticate header для 401 (AC1)
        if (wwwAuthenticate != null) {
            response.headers.add(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate)
        }

        // Получаем correlation ID из атрибутов
        val correlationId = exchange.getAttribute<String>(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE)
            ?: "unknown"

        // RFC 7807 Problem Details формат
        val errorBody = """
        {
            "type": "$type",
            "title": "$title",
            "status": ${status.value()},
            "detail": "$detail",
            "instance": "${exchange.request.path}",
            "correlationId": "$correlationId"
        }
        """.trimIndent()

        val buffer = response.bufferFactory().wrap(errorBody.toByteArray(Charsets.UTF_8))
        return response.writeWith(Mono.just(buffer))
    }
}
