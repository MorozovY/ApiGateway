package com.company.gateway.core.filter

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.net.URI

/**
 * Глобальный фильтр для структурированного логирования запросов/ответов с correlation ID.
 *
 * Логирует завершение запроса со следующими полями (AC3):
 * - timestamp (формат ISO 8601 через конфигурацию logback)
 * - correlationId (строка UUID из Reactor Context)
 * - method (HTTP метод: GET, POST и т.д.)
 * - path (путь запроса)
 * - status (код статуса HTTP ответа)
 * - duration (длительность запроса в миллисекундах)
 * - upstreamUrl (целевой upstream URL)
 * - clientIp (IP адрес клиента)
 *
 * Выполняется с LOWEST_PRECEDENCE - 1 для логирования после завершения маршрутизации, но до отправки ответа.
 */
@Component
class LoggingFilter : GlobalFilter, Ordered {

    private val logger = LoggerFactory.getLogger(LoggingFilter::class.java)

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE - 1

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val startTime = System.currentTimeMillis()
        val request = exchange.request

        return chain.filter(exchange)
            .doOnEach { signal ->
                if (signal.isOnComplete || signal.isOnError) {
                    val correlationId = signal.contextView
                        .getOrDefault(CorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY, "unknown")
                    val consumerId = signal.contextView
                        .getOrDefault(ConsumerIdentityFilter.CONSUMER_ID_CONTEXT_KEY, "anonymous")

                    val duration = System.currentTimeMillis() - startTime
                    // Получаем код статуса; при error signal может быть null если обработчик ошибок ещё не установил его
                    val status = exchange.response.statusCode?.value()
                        ?: if (signal.isOnError) 500 else 0
                    val upstreamUrl = exchange.getAttribute<URI>(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR)
                    val clientIp = extractClientIp(exchange)

                    try {
                        MDC.put("correlationId", correlationId)
                        MDC.put("consumerId", consumerId)
                        MDC.put("method", request.method.name())
                        MDC.put("path", request.path.value())
                        MDC.put("status", status.toString())
                        MDC.put("duration", duration.toString())
                        MDC.put("upstreamUrl", upstreamUrl?.toString() ?: "N/A")
                        MDC.put("clientIp", clientIp)

                        if (signal.isOnError) {
                            logger.error(
                                "Request failed: {} {} -> {} in {}ms",
                                request.method.name(),
                                request.path.value(),
                                status,
                                duration
                            )
                        } else {
                            logger.info(
                                "Request completed: {} {} -> {} in {}ms",
                                request.method.name(),
                                request.path.value(),
                                status,
                                duration
                            )
                        }
                    } finally {
                        MDC.clear()
                    }
                }
            }
    }

    /**
     * Извлекает IP адрес клиента из запроса.
     * Сначала проверяет заголовок X-Forwarded-For, затем использует remote address.
     */
    private fun extractClientIp(exchange: ServerWebExchange): String {
        val forwardedFor = exchange.request.headers.getFirst("X-Forwarded-For")
        if (!forwardedFor.isNullOrBlank()) {
            // X-Forwarded-For может содержать несколько IP, берём первый (оригинальный клиент)
            return forwardedFor.split(",").first().trim()
        }

        return exchange.request.remoteAddress?.address?.hostAddress ?: "unknown"
    }
}
