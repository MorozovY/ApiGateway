package com.company.gateway.core.filter

import io.micrometer.tracing.Span
import io.micrometer.tracing.Tracer
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.net.URI
import java.util.function.Predicate

/**
 * Unit тесты для TracingAttributesFilter (Story 14.5).
 *
 * Проверяет:
 * - Добавление route attributes (AC4: gateway.route.id, gateway.route.path)
 * - Добавление consumer ID attribute (AC4: gateway.consumer.id)
 * - Добавление rate limit decision attribute (AC4: gateway.ratelimit.decision)
 * - Graceful degradation когда tracer = null
 * - Graceful degradation когда span отсутствует
 */
class TracingAttributesFilterTest {

    private val chain: GatewayFilterChain = mock {
        on { filter(any()) } doReturn Mono.empty()
    }

    @Test
    fun `добавляет route ID и path к span когда route присутствует`() {
        // Given
        val span: Span = mock()
        val tracer: Tracer = mock {
            on { currentSpan() } doReturn span
        }
        val filter = TracingAttributesFilter(tracer)

        val request = MockServerHttpRequest.get("/api/users").build()
        val exchange = MockServerWebExchange.from(request)

        // Создаём mock route
        val route = Route.async()
            .id("test-route-123")
            .uri(URI.create("http://backend:8080"))
            .predicate(Predicate { true })
            .build()
        exchange.attributes[ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR] = route

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Then
        verify(span).tag(eq(TracingAttributesFilter.ROUTE_ID_ATTR), eq("test-route-123"))
        verify(span).tag(eq(TracingAttributesFilter.ROUTE_PATH_ATTR), any<String>())
        verify(chain).filter(exchange)
    }

    @Test
    fun `добавляет consumer ID к span когда consumerId присутствует`() {
        // Given
        val span: Span = mock()
        val tracer: Tracer = mock {
            on { currentSpan() } doReturn span
        }
        val filter = TracingAttributesFilter(tracer)

        val request = MockServerHttpRequest.get("/api/users").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE] = "test-consumer"

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Then
        verify(span).tag(TracingAttributesFilter.CONSUMER_ID_ATTR, "test-consumer")
    }

    @Test
    fun `добавляет rate limit decision к span когда decision присутствует`() {
        // Given
        val span: Span = mock()
        val tracer: Tracer = mock {
            on { currentSpan() } doReturn span
        }
        val filter = TracingAttributesFilter(tracer)

        val request = MockServerHttpRequest.get("/api/users").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[TracingAttributesFilter.RATELIMIT_DECISION_ATTRIBUTE] = "allowed"

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Then
        verify(span).tag(TracingAttributesFilter.RATELIMIT_DECISION_ATTR, "allowed")
    }

    @Test
    fun `добавляет denied rate limit decision к span`() {
        // Given
        val span: Span = mock()
        val tracer: Tracer = mock {
            on { currentSpan() } doReturn span
        }
        val filter = TracingAttributesFilter(tracer)

        val request = MockServerHttpRequest.get("/api/users").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[TracingAttributesFilter.RATELIMIT_DECISION_ATTRIBUTE] = "denied"

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Then
        verify(span).tag(TracingAttributesFilter.RATELIMIT_DECISION_ATTR, "denied")
    }

    @Test
    fun `gracefully degradates когда tracer null`() {
        // Given
        val filter = TracingAttributesFilter(null)

        val request = MockServerHttpRequest.get("/api/users").build()
        val exchange = MockServerWebExchange.from(request)

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Then
        verify(chain).filter(exchange)
    }

    @Test
    fun `gracefully degradates когда span отсутствует`() {
        // Given
        val tracer: Tracer = mock {
            on { currentSpan() } doReturn null
        }
        val filter = TracingAttributesFilter(tracer)

        val request = MockServerHttpRequest.get("/api/users").build()
        val exchange = MockServerWebExchange.from(request)

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Then
        verify(chain).filter(exchange)
    }

    @Test
    fun `не добавляет consumer ID когда он пустой`() {
        // Given
        val span: Span = mock()
        val tracer: Tracer = mock {
            on { currentSpan() } doReturn span
        }
        val filter = TracingAttributesFilter(tracer)

        val request = MockServerHttpRequest.get("/api/users").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE] = ""

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Then
        verify(span, never()).tag(eq(TracingAttributesFilter.CONSUMER_ID_ATTR), any<String>())
    }

    @Test
    fun `не добавляет rate limit decision когда он отсутствует`() {
        // Given
        val span: Span = mock()
        val tracer: Tracer = mock {
            on { currentSpan() } doReturn span
        }
        val filter = TracingAttributesFilter(tracer)

        val request = MockServerHttpRequest.get("/api/users").build()
        val exchange = MockServerWebExchange.from(request)
        // Не устанавливаем RATELIMIT_DECISION_ATTRIBUTE

        // When
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Then
        verify(span, never()).tag(eq(TracingAttributesFilter.RATELIMIT_DECISION_ATTR), any<String>())
    }

    @Test
    fun `имеет правильный order после routing`() {
        // Given
        val filter = TracingAttributesFilter(null)

        // Then
        assert(filter.order == TracingAttributesFilter.FILTER_ORDER)
        assert(filter.order > RateLimitFilter.FILTER_ORDER) // После RateLimitFilter
    }
}
