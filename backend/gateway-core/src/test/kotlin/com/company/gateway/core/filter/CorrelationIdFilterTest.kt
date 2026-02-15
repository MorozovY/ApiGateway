package com.company.gateway.core.filter

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/**
 * Unit tests for CorrelationIdFilter (Story 1.6)
 *
 * Tests:
 * - AC1: Generates UUID correlation ID when header missing
 * - AC2: Preserves existing correlation ID
 * - Adds correlation ID to request headers (upstream propagation)
 * - Adds correlation ID to response headers (client response)
 * - Stores correlation ID in Reactor Context
 */
class CorrelationIdFilterTest {

    private val filter = CorrelationIdFilter()

    @Test
    fun `generates UUID correlation ID when X-Correlation-ID header missing`() {
        val request = MockServerHttpRequest.get("/api/test").build()
        val exchange = MockServerWebExchange.from(request)

        val chain = mock<GatewayFilterChain>()
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Verify correlation ID was added to response headers
        val correlationId = exchange.response.headers.getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER)
        assert(correlationId != null) { "Correlation ID should be present in response headers" }
        assert(correlationId!!.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))) {
            "Correlation ID should be a valid UUID format, got: $correlationId"
        }

        // Verify correlation ID was stored in exchange attributes
        val attributeCorrelationId = exchange.getAttribute<String>(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE)
        assert(attributeCorrelationId == correlationId) {
            "Correlation ID in attributes should match response header"
        }
    }

    @Test
    fun `preserves existing X-Correlation-ID header`() {
        val existingId = "test-correlation-id-123"
        val request = MockServerHttpRequest.get("/api/test")
            .header(CorrelationIdFilter.CORRELATION_ID_HEADER, existingId)
            .build()
        val exchange = MockServerWebExchange.from(request)

        val chain = mock<GatewayFilterChain>()
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Verify existing correlation ID is preserved in response
        val responseCorrelationId = exchange.response.headers.getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER)
        assert(responseCorrelationId == existingId) {
            "Expected correlation ID '$existingId', got '$responseCorrelationId'"
        }

        // Verify preserved in exchange attributes
        val attributeCorrelationId = exchange.getAttribute<String>(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE)
        assert(attributeCorrelationId == existingId) {
            "Expected attribute correlation ID '$existingId', got '$attributeCorrelationId'"
        }
    }

    @Test
    fun `adds correlation ID to mutated request for upstream propagation`() {
        val request = MockServerHttpRequest.get("/api/test").build()
        val exchange = MockServerWebExchange.from(request)

        val chain = mock<GatewayFilterChain>()
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Verify chain.filter was called with mutated exchange containing correlation ID header
        verify(chain).filter(argThat { mutatedExchange ->
            val headerValue = mutatedExchange.request.headers.getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER)
            headerValue != null && headerValue.isNotBlank()
        })
    }

    @Test
    fun `stores correlation ID in Reactor Context`() {
        val existingId = "context-test-id"
        val request = MockServerHttpRequest.get("/api/test")
            .header(CorrelationIdFilter.CORRELATION_ID_HEADER, existingId)
            .build()
        val exchange = MockServerWebExchange.from(request)

        val chain = mock<GatewayFilterChain>()
        // Return a Mono that checks the context
        whenever(chain.filter(any())).thenReturn(
            Mono.deferContextual { context ->
                val correlationId = context.getOrDefault(CorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY, "not-found")
                assert(correlationId == existingId) {
                    "Context should contain correlation ID '$existingId', got '$correlationId'"
                }
                Mono.empty()
            }
        )

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()
    }

    @Test
    fun `uses HIGHEST_PRECEDENCE order`() {
        assert(filter.order == org.springframework.core.Ordered.HIGHEST_PRECEDENCE) {
            "CorrelationIdFilter should have HIGHEST_PRECEDENCE order"
        }
    }

    @Test
    fun `correlation ID is different for each request when not provided`() {
        val chain = mock<GatewayFilterChain>()
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        val request1 = MockServerHttpRequest.get("/api/test1").build()
        val exchange1 = MockServerWebExchange.from(request1)

        val request2 = MockServerHttpRequest.get("/api/test2").build()
        val exchange2 = MockServerWebExchange.from(request2)

        StepVerifier.create(filter.filter(exchange1, chain)).verifyComplete()
        StepVerifier.create(filter.filter(exchange2, chain)).verifyComplete()

        val id1 = exchange1.response.headers.getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER)
        val id2 = exchange2.response.headers.getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER)

        assert(id1 != id2) {
            "Each request should get a unique correlation ID, but got same ID for both: $id1"
        }
    }
}
