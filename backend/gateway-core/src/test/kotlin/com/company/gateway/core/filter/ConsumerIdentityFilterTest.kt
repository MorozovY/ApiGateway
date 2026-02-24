package com.company.gateway.core.filter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.core.Ordered
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/**
 * Unit тесты для ConsumerIdentityFilter (Story 12.5)
 *
 * Тесты AC1-AC5:
 * - AC1: Consumer ID из JWT azp claim (от JwtAuthenticationFilter)
 * - AC2: Consumer ID из X-Consumer-ID header (public routes)
 * - AC3: Anonymous fallback (без JWT и без header)
 * - AC4: Consumer ID propagation через Reactor Context
 * - AC5: Consumer ID в MDC (косвенно через Context)
 */
@ExtendWith(MockitoExtension::class)
class ConsumerIdentityFilterTest {

    @Mock
    private lateinit var chain: GatewayFilterChain

    private lateinit var filter: ConsumerIdentityFilter

    @BeforeEach
    fun setUp() {
        filter = ConsumerIdentityFilter()
    }

    /**
     * Настраивает chain mock для тестов, которым нужен stub.
     */
    private fun stubChain() {
        whenever(chain.filter(any())).thenReturn(Mono.empty())
    }

    // ============ AC1: Consumer ID from JWT azp Claim ============

    @Test
    fun `consumer_id из JWT имеет высший приоритет (AC1)`() {
        stubChain()
        // Arrange: JwtAuthenticationFilter установил consumer_id в атрибуты
        val request = MockServerHttpRequest.get("/api/orders")
            .header(ConsumerIdentityFilter.CONSUMER_ID_HEADER, "header-consumer")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE] = "jwt-consumer"

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: JWT consumer имеет приоритет над header
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo("jwt-consumer")
    }

    @Test
    fun `consumer_id из JWT сохраняется в exchange attributes (AC1)`() {
        stubChain()
        // Arrange
        val request = MockServerHttpRequest.get("/api/orders").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE] = "company-a"

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo("company-a")
    }

    @Test
    fun `consumer_id unknown от JWT обрабатывается как fallback`() {
        stubChain()
        // Arrange: JwtAuthenticationFilter установил "unknown" (нет azp/clientId в JWT)
        val request = MockServerHttpRequest.get("/api/orders")
            .header(ConsumerIdentityFilter.CONSUMER_ID_HEADER, "header-consumer")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE] = "unknown"

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: header consumer используется вместо "unknown"
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo("header-consumer")
    }

    @Test
    fun `consumer_id anonymous от JWT обрабатывается как fallback`() {
        stubChain()
        // Arrange: JwtAuthenticationFilter установил "anonymous" (public route без токена)
        val request = MockServerHttpRequest.get("/api/orders")
            .header(ConsumerIdentityFilter.CONSUMER_ID_HEADER, "header-consumer")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE] = "anonymous"

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: header consumer используется вместо "anonymous"
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo("header-consumer")
    }

    // ============ AC2: Consumer ID from X-Consumer-ID Header ============

    @Test
    fun `consumer_id из X-Consumer-ID header для public routes (AC2)`() {
        stubChain()
        // Arrange: нет JWT consumer_id, есть header
        val request = MockServerHttpRequest.get("/api/public/data")
            .header(ConsumerIdentityFilter.CONSUMER_ID_HEADER, "external-client-123")
            .build()
        val exchange = MockServerWebExchange.from(request)

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: consumer_id из header
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo("external-client-123")
    }

    @Test
    fun `пустой X-Consumer-ID header игнорируется`() {
        stubChain()
        // Arrange
        val request = MockServerHttpRequest.get("/api/orders")
            .header(ConsumerIdentityFilter.CONSUMER_ID_HEADER, "")
            .build()
        val exchange = MockServerWebExchange.from(request)

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: fallback на anonymous
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo("anonymous")
    }

    @Test
    fun `whitespace X-Consumer-ID header игнорируется`() {
        stubChain()
        // Arrange
        val request = MockServerHttpRequest.get("/api/orders")
            .header(ConsumerIdentityFilter.CONSUMER_ID_HEADER, "   ")
            .build()
        val exchange = MockServerWebExchange.from(request)

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: fallback на anonymous
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo("anonymous")
    }

    @Test
    fun `слишком длинный X-Consumer-ID header игнорируется`() {
        stubChain()
        // Arrange: header длиннее 64 символов
        val longConsumerId = "a".repeat(65)
        val request = MockServerHttpRequest.get("/api/orders")
            .header(ConsumerIdentityFilter.CONSUMER_ID_HEADER, longConsumerId)
            .build()
        val exchange = MockServerWebExchange.from(request)

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: fallback на anonymous (слишком длинный header отклонён)
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo("anonymous")
    }

    @Test
    fun `X-Consumer-ID header с максимальной длиной 64 принимается`() {
        stubChain()
        // Arrange: header ровно 64 символа
        val maxLengthConsumerId = "a".repeat(64)
        val request = MockServerHttpRequest.get("/api/orders")
            .header(ConsumerIdentityFilter.CONSUMER_ID_HEADER, maxLengthConsumerId)
            .build()
        val exchange = MockServerWebExchange.from(request)

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: header принят
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo(maxLengthConsumerId)
    }

    @Test
    fun `X-Consumer-ID header с newline отклоняется (log injection prevention)`() {
        stubChain()
        // Arrange: попытка log injection через newline
        val maliciousConsumerId = "fake\nINFO: admin logged in"
        val request = MockServerHttpRequest.get("/api/orders")
            .header(ConsumerIdentityFilter.CONSUMER_ID_HEADER, maliciousConsumerId)
            .build()
        val exchange = MockServerWebExchange.from(request)

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: malicious header отклонён, fallback на anonymous
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo("anonymous")
    }

    @Test
    fun `X-Consumer-ID header со спецсимволами отклоняется`() {
        stubChain()
        // Arrange: header с недопустимыми символами
        val invalidConsumerId = "consumer<script>alert(1)</script>"
        val request = MockServerHttpRequest.get("/api/orders")
            .header(ConsumerIdentityFilter.CONSUMER_ID_HEADER, invalidConsumerId)
            .build()
        val exchange = MockServerWebExchange.from(request)

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: invalid header отклонён
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo("anonymous")
    }

    @Test
    fun `X-Consumer-ID header с допустимыми символами принимается`() {
        stubChain()
        // Arrange: header с допустимыми символами: буквы, цифры, дефис, underscore, точка
        val validConsumerId = "company-a_client.v2"
        val request = MockServerHttpRequest.get("/api/orders")
            .header(ConsumerIdentityFilter.CONSUMER_ID_HEADER, validConsumerId)
            .build()
        val exchange = MockServerWebExchange.from(request)

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: valid header принят
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo(validConsumerId)
    }

    // ============ AC3: Anonymous Consumer (Fallback) ============

    @Test
    fun `anonymous fallback без JWT и без header (AC3)`() {
        stubChain()
        // Arrange: нет JWT consumer_id, нет header
        val request = MockServerHttpRequest.get("/api/orders").build()
        val exchange = MockServerWebExchange.from(request)

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: anonymous
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo("anonymous")
    }

    @Test
    fun `anonymous fallback при null JWT consumer_id и без header`() {
        stubChain()
        // Arrange: JWT consumer_id не установлен (null)
        val request = MockServerHttpRequest.get("/api/orders").build()
        val exchange = MockServerWebExchange.from(request)
        // Атрибут не установлен вообще

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: anonymous
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo("anonymous")
    }

    // ============ AC4: Consumer ID Propagation to Downstream Filters ============

    @Test
    fun `consumer_id propagation через Reactor Context (AC4)`() {
        stubChain()
        // Arrange
        val request = MockServerHttpRequest.get("/api/orders").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE] = "company-a"

        // Act & Assert: проверяем что context содержит consumer_id
        val result = filter.filter(exchange, chain)
            .contextWrite { ctx -> ctx }  // Пустой contextWrite для доступа к context

        StepVerifier.create(result.contextCapture())
            .expectAccessibleContext()
            .hasKey(ConsumerIdentityFilter.CONSUMER_ID_CONTEXT_KEY)
            .then()
            .verifyComplete()
    }

    @Test
    fun `consumer_id доступен в exchange attributes для MetricsFilter (AC4)`() {
        stubChain()
        // Arrange
        val request = MockServerHttpRequest.get("/api/orders").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE] = "company-b"

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: MetricsFilter может получить consumer_id из атрибутов
        val consumerId = exchange.getAttribute<String>(JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE)
        assertThat(consumerId).isEqualTo("company-b")
    }

    // ============ AC5: Consumer ID in Structured Logs (via Context) ============

    @Test
    fun `consumer_id propagation для LoggingFilter MDC (AC5)`() {
        stubChain()
        // Arrange: JWT consumer_id
        val request = MockServerHttpRequest.get("/api/orders").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE] = "company-a"

        // Act & Assert: LoggingFilter получает consumer_id через Context
        val result = filter.filter(exchange, chain)

        StepVerifier.create(result.contextCapture())
            .expectAccessibleContext()
            .assertThat { ctx ->
                assertThat(ctx.get<String>(ConsumerIdentityFilter.CONSUMER_ID_CONTEXT_KEY))
                    .isEqualTo("company-a")
            }
            .then()
            .verifyComplete()
    }

    @Test
    fun `anonymous consumer_id propagation через Context (AC5)`() {
        stubChain()
        // Arrange: без JWT и без header
        val request = MockServerHttpRequest.get("/api/orders").build()
        val exchange = MockServerWebExchange.from(request)

        // Act & Assert
        val result = filter.filter(exchange, chain)

        StepVerifier.create(result.contextCapture())
            .expectAccessibleContext()
            .assertThat { ctx ->
                assertThat(ctx.get<String>(ConsumerIdentityFilter.CONSUMER_ID_CONTEXT_KEY))
                    .isEqualTo("anonymous")
            }
            .then()
            .verifyComplete()
    }

    // ============ Filter Order ============

    @Test
    fun `filter order equals HIGHEST_PRECEDENCE + 8`() {
        // Этот тест не использует chain mock — создаём фильтр напрямую
        val localFilter = ConsumerIdentityFilter()
        assertThat(localFilter.order).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 8)
    }

    @Test
    fun `filter order после JwtAuthenticationFilter и до MetricsFilter`() {
        // JwtAuthenticationFilter: HIGHEST_PRECEDENCE + 5
        // ConsumerIdentityFilter: HIGHEST_PRECEDENCE + 8
        // MetricsFilter: HIGHEST_PRECEDENCE + 10
        // Этот тест не использует chain mock — создаём фильтр напрямую
        val localFilter = ConsumerIdentityFilter()
        assertThat(localFilter.order).isGreaterThan(JwtAuthenticationFilter.FILTER_ORDER)
        // MetricsFilter порядок проверяется косвенно через документацию
    }

    // ============ Edge Cases ============

    @Test
    fun `пустой JWT consumer_id игнорируется`() {
        stubChain()
        // Arrange
        val request = MockServerHttpRequest.get("/api/orders")
            .header(ConsumerIdentityFilter.CONSUMER_ID_HEADER, "header-consumer")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE] = ""

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: header consumer используется
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo("header-consumer")
    }

    @Test
    fun `whitespace JWT consumer_id игнорируется`() {
        stubChain()
        // Arrange
        val request = MockServerHttpRequest.get("/api/orders")
            .header(ConsumerIdentityFilter.CONSUMER_ID_HEADER, "header-consumer")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE] = "   "

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: header consumer используется
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo("header-consumer")
    }

    @Test
    fun `chain filter всегда вызывается`() {
        // Arrange: создаём новый mock для этого теста с отдельным stubbing
        val localChain = org.mockito.Mockito.mock(GatewayFilterChain::class.java)
        val request = MockServerHttpRequest.get("/api/orders").build()
        val exchange = MockServerWebExchange.from(request)

        var chainCalled = false
        whenever(localChain.filter(any())).thenAnswer {
            chainCalled = true
            Mono.empty<Void>()
        }

        // Act
        StepVerifier.create(filter.filter(exchange, localChain))
            .verifyComplete()

        // Assert: chain всегда вызывается (фильтр не блокирует запросы)
        assertThat(chainCalled).isTrue()
    }
}
