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
 * Global filter that manages X-Correlation-ID header for request tracing.
 *
 * - Generates a UUID correlation ID for new requests without the header
 * - Preserves existing correlation ID if provided by client
 * - Adds correlation ID to request headers (for upstream propagation)
 * - Adds correlation ID to response headers (for client)
 * - Stores correlation ID in Reactor Context for downstream operators
 *
 * Runs at HIGHEST_PRECEDENCE to ensure correlation ID is available for all other filters.
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

        // Add to request for upstream propagation
        val mutatedRequest = exchange.request.mutate()
            .header(CORRELATION_ID_HEADER, correlationId)
            .build()

        // Add to response for client
        exchange.response.headers.add(CORRELATION_ID_HEADER, correlationId)

        // Store in exchange attributes for access in error handlers
        val mutatedExchange = exchange.mutate()
            .request(mutatedRequest)
            .build()
        mutatedExchange.attributes[CORRELATION_ID_ATTRIBUTE] = correlationId

        return chain.filter(mutatedExchange)
            .contextWrite(Context.of(CORRELATION_ID_CONTEXT_KEY, correlationId))
    }
}
