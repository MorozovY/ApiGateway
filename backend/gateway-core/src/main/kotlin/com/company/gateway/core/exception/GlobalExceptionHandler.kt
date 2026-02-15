package com.company.gateway.core.exception

import com.company.gateway.common.exception.ErrorResponse
import com.company.gateway.core.filter.CorrelationIdFilter
import com.fasterxml.jackson.databind.ObjectMapper
import io.netty.channel.ConnectTimeoutException
import io.netty.handler.timeout.ReadTimeoutException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler
import org.springframework.cloud.gateway.support.NotFoundException
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.net.ConnectException
import java.util.concurrent.TimeoutException

/**
 * Global exception handler for the gateway.
 *
 * Handles upstream errors (connection refused, timeout) and returns RFC 7807 formatted responses.
 * Security: Does NOT expose internal details (hostnames, stack traces, exception class names).
 */
@Component
@Order(-1) // Intercept before default Spring error handlers (which have Order(0))
class GlobalExceptionHandler(
    private val objectMapper: ObjectMapper
) : ErrorWebExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        return Mono.deferContextual { context ->
            val response = exchange.response
            val requestPath = exchange.request.path.value()

            // Extract correlation ID from Reactor Context, exchange attributes, or request header
            // Priority: Context > Exchange Attribute > Request Header > Generate new
            val correlationId: String = (context.getOrDefault<String>(
                CorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY,
                exchange.getAttribute<String>(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE)
                    ?: exchange.request.headers.getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER)
            ) ?: java.util.UUID.randomUUID().toString())

            // Set MDC for structured logging of error
            MDC.put("correlationId", correlationId)
            try {
                // Log the error for debugging (internal details ok for logs, not for response)
                logUpstreamError(requestPath, ex)

                val (httpStatus, errorResponse) = resolveException(ex, requestPath, correlationId)

                response.statusCode = httpStatus
                response.headers.contentType = MediaType.APPLICATION_JSON

                // Ensure correlation ID is in response header (may already be set by filter)
                if (!response.headers.containsKey(CorrelationIdFilter.CORRELATION_ID_HEADER)) {
                    response.headers.add(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId)
                }

                val bytes = objectMapper.writeValueAsBytes(errorResponse)
                val buffer = response.bufferFactory().wrap(bytes)
                response.writeWith(Mono.just(buffer))
            } finally {
                MDC.clear()
            }
        }
    }

    /**
     * Resolves exception to HTTP status and RFC 7807 error response.
     * Checks root cause for wrapped exceptions (WebClientRequestException).
     */
    private fun resolveException(ex: Throwable, requestPath: String, correlationId: String): Pair<HttpStatus, ErrorResponse> {
        // First, check if this is a wrapped exception and get the root cause
        val rootCause = getRootCause(ex)

        // Check root cause for upstream errors
        return when {
            // Connection errors -> 502 Bad Gateway
            isConnectionError(rootCause) -> createUpstreamUnavailableResponse(requestPath, correlationId)

            // Timeout errors -> 504 Gateway Timeout
            isTimeoutError(rootCause) -> createUpstreamTimeoutResponse(requestPath, correlationId)

            // Check the original exception for non-upstream errors
            else -> resolveNonUpstreamException(ex, requestPath, correlationId)
        }
    }

    /**
     * Resolves non-upstream exceptions (route not found, response status, etc.)
     */
    private fun resolveNonUpstreamException(ex: Throwable, requestPath: String, correlationId: String): Pair<HttpStatus, ErrorResponse> {
        return when (ex) {
            is RouteNotFoundException -> Pair(
                HttpStatus.NOT_FOUND,
                ErrorResponse(
                    type = "https://api.gateway/errors/route-not-found",
                    title = "Not Found",
                    status = HttpStatus.NOT_FOUND.value(),
                    detail = "No route found for path: ${ex.path}",
                    instance = requestPath,
                    correlationId = correlationId
                )
            )
            is NotFoundException -> Pair(
                HttpStatus.NOT_FOUND,
                ErrorResponse(
                    type = "https://api.gateway/errors/route-not-found",
                    title = "Not Found",
                    status = HttpStatus.NOT_FOUND.value(),
                    detail = "No route found for path: $requestPath",
                    instance = requestPath,
                    correlationId = correlationId
                )
            )
            is ResponseStatusException -> {
                // Spring 6: statusCode returns HttpStatusCode interface, not HttpStatus enum
                val status = HttpStatus.resolve(ex.statusCode.value()) ?: HttpStatus.INTERNAL_SERVER_ERROR

                when (status) {
                    // Handle generic 404 from WebFlux as route not found
                    HttpStatus.NOT_FOUND -> Pair(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse(
                            type = "https://api.gateway/errors/route-not-found",
                            title = "Not Found",
                            status = HttpStatus.NOT_FOUND.value(),
                            detail = "No route found for path: $requestPath",
                            instance = requestPath,
                            correlationId = correlationId
                        )
                    )
                    // Spring Cloud Gateway wraps timeouts in ResponseStatusException with 504
                    HttpStatus.GATEWAY_TIMEOUT -> createUpstreamTimeoutResponse(requestPath, correlationId)
                    // Spring Cloud Gateway may wrap connection errors in ResponseStatusException with 502
                    HttpStatus.BAD_GATEWAY -> createUpstreamUnavailableResponse(requestPath, correlationId)
                    else -> Pair(
                        status,
                        ErrorResponse(
                            type = "https://api.gateway/errors/request-error",
                            title = ex.reason ?: status.reasonPhrase,
                            status = status.value(),
                            detail = ex.message ?: "Request error",
                            instance = requestPath,
                            correlationId = correlationId
                        )
                    )
                }
            }
            else -> Pair(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorResponse(
                    type = "https://api.gateway/errors/internal-error",
                    title = "Internal Server Error",
                    status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    detail = "An unexpected error occurred",
                    instance = requestPath,
                    correlationId = correlationId
                )
            )
        }
    }

    /**
     * Checks if the exception represents a connection error (connection refused, connect timeout).
     */
    private fun isConnectionError(ex: Throwable): Boolean {
        return ex is ConnectException || ex is ConnectTimeoutException
    }

    /**
     * Checks if the exception represents a timeout error (read timeout, general timeout).
     */
    private fun isTimeoutError(ex: Throwable): Boolean {
        return ex is ReadTimeoutException || ex is TimeoutException
    }

    /**
     * Gets the root cause of an exception chain.
     * WebClientRequestException and other wrappers may have deeply nested causes.
     * Unwraps the full chain to find the actual network error.
     */
    private fun getRootCause(ex: Throwable): Throwable {
        var current: Throwable = ex
        val seen = mutableSetOf<Throwable>() // Prevent infinite loops from circular references

        while (current.cause != null && current.cause !in seen) {
            seen.add(current)
            current = current.cause!!
        }

        return current
    }

    /**
     * Creates 502 Bad Gateway response for upstream unavailable.
     * Does NOT include internal details (hostname, port, stack trace).
     */
    private fun createUpstreamUnavailableResponse(requestPath: String, correlationId: String): Pair<HttpStatus, ErrorResponse> {
        return Pair(
            HttpStatus.BAD_GATEWAY,
            ErrorResponse(
                type = "https://api.gateway/errors/upstream-unavailable",
                title = "Bad Gateway",
                status = HttpStatus.BAD_GATEWAY.value(),
                detail = "Upstream service is unavailable",
                instance = requestPath,
                correlationId = correlationId
            )
        )
    }

    /**
     * Creates 504 Gateway Timeout response for upstream timeout.
     * Does NOT include internal details.
     */
    private fun createUpstreamTimeoutResponse(requestPath: String, correlationId: String): Pair<HttpStatus, ErrorResponse> {
        return Pair(
            HttpStatus.GATEWAY_TIMEOUT,
            ErrorResponse(
                type = "https://api.gateway/errors/upstream-timeout",
                title = "Gateway Timeout",
                status = HttpStatus.GATEWAY_TIMEOUT.value(),
                detail = "Upstream service did not respond in time",
                instance = requestPath,
                correlationId = correlationId
            )
        )
    }

    /**
     * Logs upstream errors for debugging purposes.
     * Internal details are OK for logs (not for client response).
     * Logs both wrapper exception and root cause for better debugging.
     */
    private fun logUpstreamError(requestPath: String, ex: Throwable) {
        val rootCause = getRootCause(ex)
        val rootCauseInfo = if (rootCause !== ex) {
            " (root cause: ${rootCause.javaClass.simpleName})"
        } else {
            ""
        }

        when {
            isConnectionError(rootCause) -> {
                logger.warn("Upstream connection failed for path: $requestPath - ${ex.javaClass.simpleName}$rootCauseInfo")
            }
            isTimeoutError(rootCause) -> {
                logger.warn("Upstream timeout for path: $requestPath - ${ex.javaClass.simpleName}$rootCauseInfo")
            }
            ex !is RouteNotFoundException && ex !is NotFoundException && ex !is ResponseStatusException -> {
                logger.error("Unexpected error for path: $requestPath", ex)
            }
        }
    }
}
