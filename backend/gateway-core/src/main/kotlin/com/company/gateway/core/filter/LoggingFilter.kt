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
 * Global filter for structured request/response logging with correlation ID.
 *
 * Logs request completion with the following fields (AC3):
 * - timestamp (ISO 8601 format via logback configuration)
 * - correlationId (UUID string from Reactor Context)
 * - method (HTTP method: GET, POST, etc.)
 * - path (request path)
 * - status (HTTP response status code)
 * - duration (request duration in milliseconds)
 * - upstreamUrl (target upstream URL)
 * - clientIp (client IP address)
 *
 * Runs at LOWEST_PRECEDENCE - 1 to log after routing completes but before response sent.
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

                    val duration = System.currentTimeMillis() - startTime
                    // Get status code; on error signal, may still be null if error handler hasn't set it
                    val status = exchange.response.statusCode?.value()
                        ?: if (signal.isOnError) 500 else 0
                    val upstreamUrl = exchange.getAttribute<URI>(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR)
                    val clientIp = extractClientIp(exchange)

                    try {
                        MDC.put("correlationId", correlationId)
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
     * Extracts client IP address from request.
     * Checks X-Forwarded-For header first, then falls back to remote address.
     */
    private fun extractClientIp(exchange: ServerWebExchange): String {
        val forwardedFor = exchange.request.headers.getFirst("X-Forwarded-For")
        if (!forwardedFor.isNullOrBlank()) {
            // X-Forwarded-For can contain multiple IPs, take the first (original client)
            return forwardedFor.split(",").first().trim()
        }

        return exchange.request.remoteAddress?.address?.hostAddress ?: "unknown"
    }
}
