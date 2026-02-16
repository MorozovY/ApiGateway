package com.company.gateway.core.filter

import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.util.context.Context
import java.util.UUID

/**
 * Глобальный фильтр, управляющий заголовком X-Correlation-ID для трассировки запросов.
 *
 * - Генерирует UUID correlation ID для новых запросов без заголовка
 * - Сохраняет существующий correlation ID, если предоставлен клиентом
 * - Добавляет correlation ID в заголовки запроса (для propagation на upstream)
 * - Добавляет correlation ID в заголовки ответа (для клиента)
 * - Сохраняет correlation ID в Reactor Context для downstream операторов
 *
 * Выполняется с HIGHEST_PRECEDENCE для обеспечения доступности correlation ID для всех других фильтров.
 */
@Component
class CorrelationIdFilter : GlobalFilter, Ordered {

    companion object {
        const val CORRELATION_ID_HEADER = "X-Correlation-ID"
        const val CORRELATION_ID_CONTEXT_KEY = "correlationId"
        const val CORRELATION_ID_ATTRIBUTE = "gateway.correlationId"
    }

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        val correlationId = exchange.request.headers
            .getFirst(CORRELATION_ID_HEADER)
            ?: UUID.randomUUID().toString()

        // Добавляем в запрос для propagation на upstream
        val mutatedRequest = exchange.request.mutate()
            .header(CORRELATION_ID_HEADER, correlationId)
            .build()

        // Добавляем в ответ для клиента
        exchange.response.headers.add(CORRELATION_ID_HEADER, correlationId)

        // Сохраняем в атрибутах exchange для доступа в обработчиках ошибок
        val mutatedExchange = exchange.mutate()
            .request(mutatedRequest)
            .build()
        mutatedExchange.attributes[CORRELATION_ID_ATTRIBUTE] = correlationId

        return chain.filter(mutatedExchange)
            .contextWrite(Context.of(CORRELATION_ID_CONTEXT_KEY, correlationId))
    }
}
