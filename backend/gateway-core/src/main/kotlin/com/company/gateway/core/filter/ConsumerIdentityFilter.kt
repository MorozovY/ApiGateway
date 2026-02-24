package com.company.gateway.core.filter

import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.util.context.Context

/**
 * Глобальный фильтр для определения consumer identity запроса.
 *
 * Консолидирует идентификацию consumer из разных источников:
 * 1. JWT `azp` claim (от JwtAuthenticationFilter)
 * 2. X-Consumer-ID header (для public routes или legacy интеграций)
 * 3. "anonymous" (fallback для неидентифицированных запросов)
 *
 * Результат сохраняется в:
 * - exchange.attributes для синхронного доступа (MetricsFilter, RateLimitFilter)
 * - Reactor Context для реактивных операторов (LoggingFilter MDC)
 *
 * Выполняется после JwtAuthenticationFilter (HIGHEST_PRECEDENCE + 5)
 * и до MetricsFilter (HIGHEST_PRECEDENCE + 10).
 */
@Component
class ConsumerIdentityFilter : GlobalFilter, Ordered {

    private val logger = LoggerFactory.getLogger(ConsumerIdentityFilter::class.java)

    companion object {
        /**
         * Order фильтра: после JwtAuthenticationFilter (+5), до MetricsFilter (+10).
         */
        const val FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 8

        /**
         * Ключ Reactor Context для consumer ID.
         * Используется для MDC propagation в reactive chains.
         */
        const val CONSUMER_ID_CONTEXT_KEY = "consumerId"

        /**
         * Заголовок для передачи consumer ID на public routes.
         */
        const val CONSUMER_ID_HEADER = "X-Consumer-ID"

        /**
         * Consumer ID для неидентифицированных запросов.
         */
        const val ANONYMOUS = "anonymous"

        /**
         * Consumer ID "unknown" используется JwtAuthenticationFilter когда azp/clientId не найден.
         * Мы трактуем его как неопределённый и применяем fallback логику.
         */
        private const val UNKNOWN = "unknown"

        /**
         * Максимальная длина consumer ID из header.
         * Ограничивает cardinality метрик и предотвращает DoS через длинные значения.
         */
        private const val MAX_CONSUMER_ID_LENGTH = 64

        /**
         * Regex для валидации consumer ID из header.
         * Разрешены: буквы, цифры, дефис, underscore, точка.
         * Предотвращает log injection и невалидные metric labels.
         */
        private val CONSUMER_ID_PATTERN = Regex("^[a-zA-Z0-9._-]+$")
    }

    override fun getOrder(): Int = FILTER_ORDER

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        // Приоритет 1: consumer_id из JwtAuthenticationFilter (JWT azp claim)
        val jwtConsumerId = exchange.getAttribute<String>(JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE)
            ?.takeIf { it.isNotBlank() && it != UNKNOWN && it != ANONYMOUS }

        // Приоритет 2: X-Consumer-ID header (для public routes или legacy интеграций)
        // Валидация: длина <= 64, только допустимые символы (предотвращает log injection)
        val headerConsumerId = exchange.request.headers.getFirst(CONSUMER_ID_HEADER)
            ?.takeIf { it.isNotBlank() && it.length <= MAX_CONSUMER_ID_LENGTH && CONSUMER_ID_PATTERN.matches(it) }

        // Приоритет 3: anonymous (fallback)
        val consumerId = jwtConsumerId ?: headerConsumerId ?: ANONYMOUS

        logger.debug(
            "Consumer identity resolved: {} (source: {})",
            consumerId,
            when {
                jwtConsumerId != null -> "jwt"
                headerConsumerId != null -> "header"
                else -> "anonymous"
            }
        )

        // Сохраняем в exchange attributes для синхронного доступа
        // Перезаписываем значение, т.к. JwtAuthenticationFilter мог установить "anonymous" или "unknown"
        exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE] = consumerId

        return chain.filter(exchange)
            .contextWrite { context ->
                context.put(CONSUMER_ID_CONTEXT_KEY, consumerId)
            }
    }
}
