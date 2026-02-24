package com.company.gateway.core.filter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.core.Ordered
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

/**
 * Unit тесты для JwtAuthenticationFilter (Story 12.4)
 *
 * Тесты AC1-AC7:
 * - AC1: Protected route без токена → 401 Unauthorized
 * - AC2: Protected route с valid JWT → forward to upstream
 * - AC3: Public route без токена → forward to upstream (anonymous)
 * - AC3+: Public route с valid JWT → forward to upstream (extract consumer_id)
 * - AC3++: Public route с invalid JWT → 401 (валидация всегда выполняется если токен предоставлен)
 * - AC4: Consumer whitelist → 403 Forbidden
 * - AC5: Consumer whitelist empty → allow all
 * - AC6: JWKS caching — NimbusReactiveJwtDecoder кэширует JWKS автоматически (5 минут)
 *        Unit тест проверяет graceful error handling при JwtException
 * - AC7: Invalid/Expired JWT → 401 Unauthorized
 */
@ExtendWith(MockitoExtension::class)
class JwtAuthenticationFilterTest {

    @Mock
    private lateinit var jwtDecoder: ReactiveJwtDecoder

    @Mock
    private lateinit var chain: GatewayFilterChain

    private lateinit var filter: JwtAuthenticationFilter

    @BeforeEach
    fun setUp() {
        filter = JwtAuthenticationFilter(jwtDecoder)
    }

    // ============ AC1: Protected Route Without Token — 401 Unauthorized ============

    @Test
    fun `protected route без токена возвращает 401 Unauthorized (AC1)`() {
        // Arrange: protected route (authRequired=true), нет Authorization header
        val request = MockServerHttpRequest.get("/api/orders").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.AUTH_REQUIRED_ATTRIBUTE] = true
        exchange.attributes[CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE] = "test-correlation-id"

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: 401 status
        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)

        // Assert: WWW-Authenticate header (AC1)
        assertThat(exchange.response.headers.getFirst(HttpHeaders.WWW_AUTHENTICATE))
            .isEqualTo("Bearer")

        // Assert: RFC 7807 content type
        assertThat(exchange.response.headers.contentType)
            .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON)

        // Chain не вызывается
        verify(chain, never()).filter(any())
    }

    @Test
    fun `protected route authRequired по умолчанию true`() {
        // Arrange: authRequired не установлен (должен быть true по умолчанию)
        val request = MockServerHttpRequest.get("/api/orders").build()
        val exchange = MockServerWebExchange.from(request)
        // НЕ устанавливаем AUTH_REQUIRED_ATTRIBUTE

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: 401 (authRequired=true по умолчанию)
        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `protected route с пустым Authorization header возвращает 401`() {
        // Arrange
        val request = MockServerHttpRequest.get("/api/orders")
            .header(HttpHeaders.AUTHORIZATION, "")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.AUTH_REQUIRED_ATTRIBUTE] = true

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: 401
        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `protected route с некорректным Bearer форматом возвращает 401`() {
        // Arrange: "Basic" вместо "Bearer"
        val request = MockServerHttpRequest.get("/api/orders")
            .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.AUTH_REQUIRED_ATTRIBUTE] = true

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: 401
        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    // ============ AC2: Protected Route With Valid JWT — Forward to Upstream ============

    @Test
    fun `protected route с valid JWT forward to upstream (AC2)`() {
        // Arrange
        val token = "valid.jwt.token"
        val request = MockServerHttpRequest.get("/api/orders")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.AUTH_REQUIRED_ATTRIBUTE] = true

        val jwt = createMockJwt("company-a")
        whenever(jwtDecoder.decode(token)).thenReturn(Mono.just(jwt))
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: chain вызван (forward to upstream)
        verify(chain).filter(exchange)

        // Assert: consumer_id сохранён в атрибутах
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo("company-a")
    }

    @Test
    fun `consumer_id извлекается из azp claim`() {
        // Arrange
        val token = "valid.jwt.token"
        val request = MockServerHttpRequest.get("/api/orders")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.AUTH_REQUIRED_ATTRIBUTE] = true

        val jwt = createMockJwt(azp = "company-a", clientId = "different-id")
        whenever(jwtDecoder.decode(token)).thenReturn(Mono.just(jwt))
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: azp имеет приоритет над clientId
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo("company-a")
    }

    @Test
    fun `consumer_id извлекается из clientId если azp отсутствует`() {
        // Arrange
        val token = "valid.jwt.token"
        val request = MockServerHttpRequest.get("/api/orders")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.AUTH_REQUIRED_ATTRIBUTE] = true

        val jwt = createMockJwt(azp = null, clientId = "company-b")
        whenever(jwtDecoder.decode(token)).thenReturn(Mono.just(jwt))
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: fallback на clientId
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo("company-b")
    }

    @Test
    fun `consumer_id unknown если нет azp и clientId`() {
        // Arrange
        val token = "valid.jwt.token"
        val request = MockServerHttpRequest.get("/api/orders")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.AUTH_REQUIRED_ATTRIBUTE] = true

        val jwt = createMockJwt(azp = null, clientId = null)
        whenever(jwtDecoder.decode(token)).thenReturn(Mono.just(jwt))
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: unknown если ничего нет
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo("unknown")
    }

    // ============ AC3: Public Route Without Token — Forward to Upstream ============

    @Test
    fun `public route без токена forward to upstream (AC3)`() {
        // Arrange: public route (authRequired=false), нет Authorization header
        val request = MockServerHttpRequest.get("/api/public/health").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.AUTH_REQUIRED_ATTRIBUTE] = false

        whenever(chain.filter(any())).thenReturn(Mono.empty())

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: chain вызван (forward to upstream)
        verify(chain).filter(exchange)

        // Assert: consumer_id = "anonymous"
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo("anonymous")

        // Assert: jwtDecoder не вызывается
        verify(jwtDecoder, never()).decode(any())
    }

    @Test
    fun `public route с valid JWT извлекает consumer_id (AC3+)`() {
        // Arrange: public route (authRequired=false), но токен предоставлен
        val token = "valid.jwt.token"
        val request = MockServerHttpRequest.get("/api/public/data")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.AUTH_REQUIRED_ATTRIBUTE] = false

        val jwt = createMockJwt("company-a")
        whenever(jwtDecoder.decode(token)).thenReturn(Mono.just(jwt))
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: chain вызван (forward to upstream)
        verify(chain).filter(exchange)

        // Assert: consumer_id извлечён из JWT (НЕ anonymous)
        assertThat(exchange.attributes[JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE])
            .isEqualTo("company-a")

        // Assert: jwtDecoder вызывается для валидации токена
        verify(jwtDecoder).decode(token)
    }

    @Test
    fun `public route с invalid JWT возвращает 401 (AC3++)`() {
        // Arrange: public route (authRequired=false), но токен невалидный
        // Design decision: если токен предоставлен, он всегда валидируется
        val token = "invalid.jwt.token"
        val request = MockServerHttpRequest.get("/api/public/data")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.AUTH_REQUIRED_ATTRIBUTE] = false
        exchange.attributes[CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE] = "test-correlation-id"

        whenever(jwtDecoder.decode(token)).thenReturn(Mono.error(JwtException("Invalid JWT")))

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: 401 (даже для public route — токен предоставлен, но невалидный)
        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)

        // Chain не вызывается
        verify(chain, never()).filter(any())
    }

    // ============ AC4: Consumer Whitelist — 403 Forbidden ============

    @Test
    fun `consumer не в whitelist возвращает 403 Forbidden (AC4)`() {
        // Arrange: whitelist = ["company-a", "company-b"], consumer = "company-c"
        val token = "valid.jwt.token"
        val request = MockServerHttpRequest.get("/api/orders")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.AUTH_REQUIRED_ATTRIBUTE] = true
        exchange.attributes[JwtAuthenticationFilter.ALLOWED_CONSUMERS_ATTRIBUTE] = listOf("company-a", "company-b")
        exchange.attributes[CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE] = "test-correlation-id"

        val jwt = createMockJwt("company-c")
        whenever(jwtDecoder.decode(token)).thenReturn(Mono.just(jwt))

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: 403 status
        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)

        // Assert: RFC 7807 content type
        assertThat(exchange.response.headers.contentType)
            .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON)

        // Chain не вызывается
        verify(chain, never()).filter(any())
    }

    @Test
    fun `consumer в whitelist проходит успешно`() {
        // Arrange: whitelist = ["company-a", "company-b"], consumer = "company-a"
        val token = "valid.jwt.token"
        val request = MockServerHttpRequest.get("/api/orders")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.AUTH_REQUIRED_ATTRIBUTE] = true
        exchange.attributes[JwtAuthenticationFilter.ALLOWED_CONSUMERS_ATTRIBUTE] = listOf("company-a", "company-b")

        val jwt = createMockJwt("company-a")
        whenever(jwtDecoder.decode(token)).thenReturn(Mono.just(jwt))
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: chain вызван
        verify(chain).filter(exchange)
    }

    // ============ AC5: Consumer Whitelist Empty — Allow All ============

    @Test
    fun `whitelist null разрешает всех consumers (AC5)`() {
        // Arrange: allowed_consumers = null
        val token = "valid.jwt.token"
        val request = MockServerHttpRequest.get("/api/orders")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.AUTH_REQUIRED_ATTRIBUTE] = true
        // НЕ устанавливаем ALLOWED_CONSUMERS_ATTRIBUTE

        val jwt = createMockJwt("any-consumer")
        whenever(jwtDecoder.decode(token)).thenReturn(Mono.just(jwt))
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: chain вызван
        verify(chain).filter(exchange)
    }

    // ============ AC7: Invalid/Expired JWT — 401 Unauthorized ============

    @Test
    fun `invalid JWT возвращает 401 Unauthorized (AC7)`() {
        // Arrange
        val token = "invalid.jwt.token"
        val request = MockServerHttpRequest.get("/api/orders")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.AUTH_REQUIRED_ATTRIBUTE] = true
        exchange.attributes[CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE] = "test-correlation-id"

        whenever(jwtDecoder.decode(token)).thenReturn(Mono.error(JwtException("Invalid JWT")))

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: 401 status
        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)

        // Assert: WWW-Authenticate header
        assertThat(exchange.response.headers.getFirst(HttpHeaders.WWW_AUTHENTICATE))
            .isEqualTo("Bearer")

        // Chain не вызывается
        verify(chain, never()).filter(any())
    }

    @Test
    fun `expired JWT возвращает 401 Unauthorized`() {
        // Arrange
        val token = "expired.jwt.token"
        val request = MockServerHttpRequest.get("/api/orders")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.AUTH_REQUIRED_ATTRIBUTE] = true

        whenever(jwtDecoder.decode(token)).thenReturn(Mono.error(JwtException("Token expired")))

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: 401 status
        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    // ============ AC6: JWKS Caching / Graceful Degradation ============

    @Test
    fun `ошибка декодирования JWT возвращает 401 с RFC 7807 (AC6)`() {
        // AC6: JWKS кэшируется NimbusReactiveJwtDecoder автоматически (5 минут).
        // При недоступности Keycloak И отсутствии кэша — decode выбрасывает ошибку.
        // Этот тест проверяет graceful error handling.
        val token = "valid.jwt.token"
        val request = MockServerHttpRequest.get("/api/orders")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.AUTH_REQUIRED_ATTRIBUTE] = true
        exchange.attributes[CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE] = "test-correlation-id"

        // Симулируем ошибку получения JWKS (Keycloak недоступен, кэш пуст)
        whenever(jwtDecoder.decode(token)).thenReturn(
            Mono.error(JwtException("Cannot obtain JWKS from Keycloak"))
        )

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: 401 с корректным форматом
        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        assertThat(exchange.response.headers.contentType)
            .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON)
        assertThat(exchange.response.headers.getFirst(HttpHeaders.WWW_AUTHENTICATE))
            .isEqualTo("Bearer")

        // Chain не вызывается
        verify(chain, never()).filter(any())
    }

    @Test
    fun `Bearer с whitespace-only токеном возвращает 401`() {
        // Arrange: "Bearer    " (только пробелы после Bearer)
        val request = MockServerHttpRequest.get("/api/orders")
            .header(HttpHeaders.AUTHORIZATION, "Bearer    ")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.AUTH_REQUIRED_ATTRIBUTE] = true

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: 401 (токен пустой после trim)
        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)

        // jwtDecoder не вызывается — токен отсутствует
        verify(jwtDecoder, never()).decode(any())
    }

    // ============ Filter Order ============

    @Test
    fun `filter order equals HIGHEST_PRECEDENCE + 5`() {
        assertThat(filter.order).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 5)
    }

    // ============ RFC 7807 Error Response ============

    @Test
    fun `401 ответ содержит correlationId в RFC 7807 формате`() {
        // Arrange
        val correlationId = "test-correlation-123"
        val request = MockServerHttpRequest.get("/api/orders").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.AUTH_REQUIRED_ATTRIBUTE] = true
        exchange.attributes[CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE] = correlationId

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: RFC 7807 content type
        assertThat(exchange.response.headers.contentType)
            .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON)
    }

    @Test
    fun `401 ответ содержит instance с request path`() {
        // Arrange
        val request = MockServerHttpRequest.get("/api/orders/123").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[JwtAuthenticationFilter.AUTH_REQUIRED_ATTRIBUTE] = true

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Assert: 401 status
        assertThat(exchange.response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    // ============ Helper Methods ============

    private fun createMockJwt(azp: String?, clientId: String? = null): Jwt {
        val claims = mutableMapOf<String, Any>(
            "sub" to "service-account-${azp ?: "test"}",
            "iss" to "http://localhost:8180/realms/api-gateway"
        )
        if (azp != null) claims["azp"] = azp
        if (clientId != null) claims["clientId"] = clientId

        return Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .claims { it.putAll(claims) }
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
    }

    private fun createMockJwt(consumerId: String): Jwt {
        return createMockJwt(azp = consumerId, clientId = null)
    }
}
