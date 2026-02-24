package com.company.gateway.core.filter

import com.company.gateway.common.model.ConsumerRateLimit
import com.company.gateway.common.model.RateLimit
import com.company.gateway.core.cache.ConsumerRateLimitCacheManager
import com.company.gateway.core.ratelimit.RateLimitCheckResult
import com.company.gateway.core.ratelimit.RateLimitResult
import com.company.gateway.core.ratelimit.RateLimitService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat

/**
 * Unit тесты для RateLimitFilter (Story 5.3, Story 12.8)
 *
 * Тесты:
 * - AC5: Маршрут без rate limit проходит без проверки
 * - AC7: Rate limit заголовки добавляются в успешный ответ
 * - AC2: HTTP 429 при превышении лимита
 * - Correlation ID включается в ошибку
 * - Story 12.8: Two-level rate limiting (per-route + per-consumer)
 */
@ExtendWith(MockitoExtension::class)
class RateLimitFilterTest {

    @Mock
    private lateinit var rateLimitService: RateLimitService

    @Mock
    private lateinit var consumerRateLimitCacheManager: ConsumerRateLimitCacheManager

    @Mock
    private lateinit var chain: GatewayFilterChain

    private lateinit var filter: RateLimitFilter

    private val testRouteId = UUID.randomUUID()
    private val testRateLimit = RateLimit(
        id = UUID.randomUUID(),
        name = "test-policy",
        requestsPerSecond = 10,
        burstSize = 15,
        createdBy = UUID.randomUUID(),
        createdAt = Instant.now()
    )

    @BeforeEach
    fun setUp() {
        filter = RateLimitFilter(rateLimitService, consumerRateLimitCacheManager)
    }

    /**
     * Настраивает mock для отсутствия consumer rate limit.
     */
    private fun stubNoConsumerRateLimit() {
        whenever(consumerRateLimitCacheManager.getConsumerRateLimit(any()))
            .thenReturn(Mono.justOrEmpty(null as ConsumerRateLimit?))
    }

    @Test
    fun `маршрут без rate limit проходит без проверки (AC5)`() {
        // Arrange: маршрут без rate limit policy
        val request = MockServerHttpRequest.get("/api/test").build()
        val exchange = MockServerWebExchange.from(request)
        // НЕ устанавливаем RATE_LIMIT_ATTRIBUTE
        stubNoConsumerRateLimit()
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        // Act & Assert
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Не должен вызывать rateLimitService
        verify(rateLimitService, never()).checkRateLimit(any(), any(), any())
        verify(chain).filter(exchange)
    }

    @Test
    fun `маршрут без routeId проходит без проверки`() {
        // Arrange: есть rateLimit, но нет routeId
        val request = MockServerHttpRequest.get("/api/test").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[RateLimitFilter.RATE_LIMIT_ATTRIBUTE] = testRateLimit
        // НЕ устанавливаем ROUTE_ID_ATTRIBUTE
        stubNoConsumerRateLimit()
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        // Act & Assert
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        verify(rateLimitService, never()).checkRateLimit(any(), any(), any())
    }

    @Test
    fun `запрос в пределах лимита проходит и добавляет заголовки (AC7)`() {
        // Arrange
        val request = MockServerHttpRequest.get("/api/test")
            .remoteAddress(java.net.InetSocketAddress.createUnresolved("192.168.1.1", 8080))
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[RateLimitFilter.ROUTE_ID_ATTRIBUTE] = testRouteId
        exchange.attributes[RateLimitFilter.RATE_LIMIT_ATTRIBUTE] = testRateLimit
        stubNoConsumerRateLimit()

        val result = RateLimitResult(
            allowed = true,
            remaining = 14,
            resetTime = System.currentTimeMillis() + 1000
        )
        whenever(rateLimitService.checkRateLimit(eq(testRouteId), any(), eq(testRateLimit)))
            .thenReturn(Mono.just(result))
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: rate limit заголовки добавлены
        val response = exchange.response
        assertThat(response.headers.getFirst("X-RateLimit-Limit")).isEqualTo("10")
        assertThat(response.headers.getFirst("X-RateLimit-Remaining")).isEqualTo("14")
        assertThat(response.headers.getFirst("X-RateLimit-Reset")).isNotNull()

        verify(chain).filter(exchange)
    }

    @Test
    fun `превышение лимита возвращает 429 с заголовками (AC2)`() {
        // Arrange
        val request = MockServerHttpRequest.get("/api/test")
            .remoteAddress(java.net.InetSocketAddress.createUnresolved("192.168.1.1", 8080))
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[RateLimitFilter.ROUTE_ID_ATTRIBUTE] = testRouteId
        exchange.attributes[RateLimitFilter.RATE_LIMIT_ATTRIBUTE] = testRateLimit
        exchange.attributes[CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE] = "test-correlation-id"
        stubNoConsumerRateLimit()

        val result = RateLimitResult(
            allowed = false,
            remaining = 0,
            resetTime = System.currentTimeMillis() + 5000
        )
        whenever(rateLimitService.checkRateLimit(eq(testRouteId), any(), eq(testRateLimit)))
            .thenReturn(Mono.just(result))

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: 429 статус
        val response = exchange.response
        assertThat(response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)

        // Assert: rate limit заголовки
        assertThat(response.headers.getFirst("X-RateLimit-Limit")).isEqualTo("10")
        assertThat(response.headers.getFirst("X-RateLimit-Remaining")).isEqualTo("0")
        assertThat(response.headers.getFirst("X-RateLimit-Reset")).isNotNull()
        assertThat(response.headers.getFirst("Retry-After")).isNotNull()

        // Не должен вызывать chain
        verify(chain, never()).filter(any())
    }

    @Test
    fun `использует X-Forwarded-For для определения клиента`() {
        // Arrange
        val request = MockServerHttpRequest.get("/api/test")
            .header("X-Forwarded-For", "10.0.0.1, 192.168.1.1")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[RateLimitFilter.ROUTE_ID_ATTRIBUTE] = testRouteId
        exchange.attributes[RateLimitFilter.RATE_LIMIT_ATTRIBUTE] = testRateLimit
        stubNoConsumerRateLimit()

        val result = RateLimitResult(allowed = true, remaining = 10, resetTime = System.currentTimeMillis())
        whenever(rateLimitService.checkRateLimit(eq(testRouteId), eq("10.0.0.1"), eq(testRateLimit)))
            .thenReturn(Mono.just(result))
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: использует первый IP из X-Forwarded-For
        verify(rateLimitService).checkRateLimit(eq(testRouteId), eq("10.0.0.1"), eq(testRateLimit))
    }

    @Test
    fun `order фильтра равен 10`() {
        assertThat(filter.order).isEqualTo(10)
    }

    // ============ Story 12.8: Two-level Rate Limiting ============

    @Test
    fun `Story 12-8 AC4 - two-level rate limiting проверяет оба лимита`() {
        // Arrange: consumer с rate limit и route с rate limit
        val consumerLimit = ConsumerRateLimit(
            id = UUID.randomUUID(),
            consumerId = "test-consumer",
            requestsPerSecond = 5,
            burstSize = 10,
            createdAt = Instant.now()
        )
        val request = MockServerHttpRequest.get("/api/test")
            .remoteAddress(java.net.InetSocketAddress.createUnresolved("192.168.1.1", 8080))
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[RateLimitFilter.ROUTE_ID_ATTRIBUTE] = testRouteId
        exchange.attributes[RateLimitFilter.RATE_LIMIT_ATTRIBUTE] = testRateLimit
        exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE] = "test-consumer"

        // Consumer rate limit найден
        whenever(consumerRateLimitCacheManager.getConsumerRateLimit("test-consumer"))
            .thenReturn(Mono.just(consumerLimit))

        // Оба лимита разрешены, consumer лимит строже (меньше remaining)
        val checkResult = RateLimitCheckResult(
            result = RateLimitResult(allowed = true, remaining = 4, resetTime = System.currentTimeMillis() + 1000),
            limitType = RateLimitCheckResult.TYPE_CONSUMER,
            limit = 5
        )
        whenever(rateLimitService.checkBothLimits(anyOrNull(), any(), any(), anyOrNull(), any()))
            .thenReturn(Mono.just(checkResult))
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: заголовки содержат consumer лимит
        val response = exchange.response
        assertThat(response.headers.getFirst("X-RateLimit-Limit")).isEqualTo("5")
        assertThat(response.headers.getFirst("X-RateLimit-Type")).isEqualTo("consumer")
        verify(chain).filter(exchange)
    }

    @Test
    fun `Story 12-8 AC3 - 429 с X-RateLimit-Type consumer при превышении consumer лимита`() {
        // Arrange
        val consumerLimit = ConsumerRateLimit(
            id = UUID.randomUUID(),
            consumerId = "limited-consumer",
            requestsPerSecond = 2,
            burstSize = 3,
            createdAt = Instant.now()
        )
        val request = MockServerHttpRequest.get("/api/test")
            .remoteAddress(java.net.InetSocketAddress.createUnresolved("192.168.1.1", 8080))
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[RateLimitFilter.ROUTE_ID_ATTRIBUTE] = testRouteId
        exchange.attributes[RateLimitFilter.RATE_LIMIT_ATTRIBUTE] = testRateLimit
        exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE] = "limited-consumer"
        exchange.attributes[CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE] = "test-correlation-id"

        whenever(consumerRateLimitCacheManager.getConsumerRateLimit("limited-consumer"))
            .thenReturn(Mono.just(consumerLimit))

        // Consumer лимит превышен
        val checkResult = RateLimitCheckResult(
            result = RateLimitResult(allowed = false, remaining = 0, resetTime = System.currentTimeMillis() + 5000),
            limitType = RateLimitCheckResult.TYPE_CONSUMER,
            limit = 2
        )
        whenever(rateLimitService.checkBothLimits(anyOrNull(), any(), any(), anyOrNull(), any()))
            .thenReturn(Mono.just(checkResult))

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: 429 с consumer type
        val response = exchange.response
        assertThat(response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(response.headers.getFirst("X-RateLimit-Type")).isEqualTo("consumer")
        assertThat(response.headers.getFirst("X-RateLimit-Limit")).isEqualTo("2")
        verify(chain, never()).filter(any())
    }

    @Test
    fun `Story 12-8 AC5 - fallback на per-route лимит когда consumer лимит отсутствует`() {
        // Arrange: consumer без rate limit
        val request = MockServerHttpRequest.get("/api/test")
            .remoteAddress(java.net.InetSocketAddress.createUnresolved("192.168.1.1", 8080))
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[RateLimitFilter.ROUTE_ID_ATTRIBUTE] = testRouteId
        exchange.attributes[RateLimitFilter.RATE_LIMIT_ATTRIBUTE] = testRateLimit
        exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE] = "no-limit-consumer"

        // Consumer rate limit не найден
        whenever(consumerRateLimitCacheManager.getConsumerRateLimit("no-limit-consumer"))
            .thenReturn(Mono.justOrEmpty(null as ConsumerRateLimit?))

        // Используется только route лимит
        val result = RateLimitResult(allowed = true, remaining = 9, resetTime = System.currentTimeMillis() + 1000)
        whenever(rateLimitService.checkRateLimit(eq(testRouteId), any(), eq(testRateLimit)))
            .thenReturn(Mono.just(result))
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: X-RateLimit-Type = route
        val response = exchange.response
        assertThat(response.headers.getFirst("X-RateLimit-Type")).isEqualTo("route")
        assertThat(response.headers.getFirst("X-RateLimit-Limit")).isEqualTo("10")
        verify(chain).filter(exchange)
    }

    @Test
    fun `Story 12-8 - anonymous consumer без rate limit использует только route лимит`() {
        // Arrange: anonymous consumer (без CONSUMER_ID_ATTRIBUTE)
        val request = MockServerHttpRequest.get("/api/test")
            .remoteAddress(java.net.InetSocketAddress.createUnresolved("192.168.1.1", 8080))
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[RateLimitFilter.ROUTE_ID_ATTRIBUTE] = testRouteId
        exchange.attributes[RateLimitFilter.RATE_LIMIT_ATTRIBUTE] = testRateLimit
        // НЕ устанавливаем CONSUMER_ID_ATTRIBUTE — используется ConsumerIdentityFilter.ANONYMOUS

        // Consumer rate limit не найден для anonymous
        whenever(consumerRateLimitCacheManager.getConsumerRateLimit(ConsumerIdentityFilter.ANONYMOUS))
            .thenReturn(Mono.justOrEmpty(null as ConsumerRateLimit?))

        val result = RateLimitResult(allowed = true, remaining = 8, resetTime = System.currentTimeMillis() + 1000)
        whenever(rateLimitService.checkRateLimit(eq(testRouteId), any(), eq(testRateLimit)))
            .thenReturn(Mono.just(result))
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert
        assertThat(exchange.response.headers.getFirst("X-RateLimit-Type")).isEqualTo("route")
        verify(chain).filter(exchange)
    }

    @Test
    fun `429 ответ содержит correlation ID в RFC 7807 формате`() {
        // Arrange
        val correlationId = "test-correlation-123"
        val request = MockServerHttpRequest.get("/api/test")
            .remoteAddress(java.net.InetSocketAddress.createUnresolved("192.168.1.1", 8080))
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[RateLimitFilter.ROUTE_ID_ATTRIBUTE] = testRouteId
        exchange.attributes[RateLimitFilter.RATE_LIMIT_ATTRIBUTE] = testRateLimit
        exchange.attributes[CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE] = correlationId
        stubNoConsumerRateLimit()

        val result = RateLimitResult(
            allowed = false,
            remaining = 0,
            resetTime = System.currentTimeMillis() + 5000
        )
        whenever(rateLimitService.checkRateLimit(eq(testRouteId), any(), eq(testRateLimit)))
            .thenReturn(Mono.just(result))

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: Content-Type application/problem+json (RFC 7807)
        assertThat(exchange.response.headers.contentType)
            .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON)
    }
}
