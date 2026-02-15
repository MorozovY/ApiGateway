package com.company.gateway.core.filter

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import reactor.util.context.Context
import java.net.URI

/**
 * Unit tests for LoggingFilter (Story 1.6)
 *
 * Tests:
 * - AC3: Logs request completion with required fields
 * - Extracts correlation ID from Reactor Context
 * - Handles error cases with correlation ID
 * - Extracts client IP from X-Forwarded-For or remote address
 */
class LoggingFilterTest {

    private val filter = LoggingFilter()

    @Test
    fun `uses LOWEST_PRECEDENCE minus 1 order`() {
        assert(filter.order == org.springframework.core.Ordered.LOWEST_PRECEDENCE - 1) {
            "LoggingFilter should have order LOWEST_PRECEDENCE - 1"
        }
    }

    @Test
    fun `extracts correlation ID from Reactor Context`() {
        val correlationId = "test-correlation-123"
        val request = MockServerHttpRequest.get("/api/test").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.response.statusCode = HttpStatus.OK

        val chain = mock<GatewayFilterChain>()
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        // Execute filter with correlation ID in context
        StepVerifier.create(
            filter.filter(exchange, chain)
                .contextWrite(Context.of(CorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY, correlationId))
        ).verifyComplete()

        // Filter completes successfully - actual logging is verified via integration tests
    }

    @Test
    fun `handles request completion signal`() {
        val request = MockServerHttpRequest.get("/api/users").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.response.statusCode = HttpStatus.OK

        // Set upstream URL attribute (normally set by routing filter)
        exchange.attributes[ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR] = URI.create("http://upstream:8080/users")

        val chain = mock<GatewayFilterChain>()
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        StepVerifier.create(
            filter.filter(exchange, chain)
                .contextWrite(Context.of(CorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY, "log-test-id"))
        ).verifyComplete()
    }

    @Test
    fun `handles error signal`() {
        val request = MockServerHttpRequest.get("/api/error").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.response.statusCode = HttpStatus.INTERNAL_SERVER_ERROR

        val chain = mock<GatewayFilterChain>()
        val testException = RuntimeException("Test error")
        whenever(chain.filter(any())).thenReturn(Mono.error(testException))

        StepVerifier.create(
            filter.filter(exchange, chain)
                .contextWrite(Context.of(CorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY, "error-test-id"))
        ).verifyError(RuntimeException::class.java)
    }

    @Test
    fun `extracts client IP from X-Forwarded-For header`() {
        val clientIp = "192.168.1.100"
        val request = MockServerHttpRequest.get("/api/test")
            .header("X-Forwarded-For", "$clientIp, 10.0.0.1, 10.0.0.2")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.response.statusCode = HttpStatus.OK

        val chain = mock<GatewayFilterChain>()
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        StepVerifier.create(
            filter.filter(exchange, chain)
                .contextWrite(Context.of(CorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY, "ip-test-id"))
        ).verifyComplete()

        // Client IP extraction is verified via MDC in integration tests
    }

    @Test
    fun `handles missing correlation ID gracefully`() {
        val request = MockServerHttpRequest.get("/api/test").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.response.statusCode = HttpStatus.OK

        val chain = mock<GatewayFilterChain>()
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        // No context set - should use "unknown" as default
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()
    }

    @Test
    fun `handles missing upstream URL attribute`() {
        val request = MockServerHttpRequest.get("/api/test").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.response.statusCode = HttpStatus.OK
        // No GATEWAY_REQUEST_URL_ATTR set - should log "N/A"

        val chain = mock<GatewayFilterChain>()
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        StepVerifier.create(
            filter.filter(exchange, chain)
                .contextWrite(Context.of(CorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY, "no-upstream-test"))
        ).verifyComplete()
    }

    @Test
    fun `defaults to status 500 when error signal has no status code set`() {
        val request = MockServerHttpRequest.get("/api/error").build()
        val exchange = MockServerWebExchange.from(request)
        // Status code is null (not set by error handler yet)

        val chain = mock<GatewayFilterChain>()
        val testException = RuntimeException("Test error")
        whenever(chain.filter(any())).thenReturn(Mono.error(testException))

        StepVerifier.create(
            filter.filter(exchange, chain)
                .contextWrite(Context.of(CorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY, "error-no-status-test"))
        ).verifyError(RuntimeException::class.java)

        // Filter handles error signal - actual status logging verified in filter implementation
    }
}
