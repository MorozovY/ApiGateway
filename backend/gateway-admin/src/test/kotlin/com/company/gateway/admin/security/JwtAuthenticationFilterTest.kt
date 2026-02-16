package com.company.gateway.admin.security

import com.company.gateway.common.model.Role
import com.company.gateway.common.model.User
import io.jsonwebtoken.Claims
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpCookie
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.Date
import java.util.UUID

class JwtAuthenticationFilterTest {

    private lateinit var jwtService: JwtService
    private lateinit var filter: JwtAuthenticationFilter

    // Секрет минимум 32 символа для HS256
    private val testSecret = "test-secret-key-minimum-32-characters"
    private val testExpiration = 86400000L // 24 часа

    @BeforeEach
    fun setUp() {
        jwtService = JwtService(testSecret, testExpiration)
        filter = JwtAuthenticationFilter(jwtService)
    }

    @Test
    fun `пропускает запрос без токена для дальнейшей обработки SecurityConfig`() {
        // Given: запрос без cookie auth_token
        val request = MockServerHttpRequest.get("/api/v1/routes").build()
        val exchange = MockServerWebExchange.from(request)
        var filterChainCalled = false
        val filterChain = WebFilterChain { _ ->
            filterChainCalled = true
            Mono.empty()
        }

        // When: filter обрабатывает запрос
        val result = filter.filter(exchange, filterChain)

        // Then: цепочка продолжается без ошибки
        StepVerifier.create(result)
            .verifyComplete()
        assertThat(filterChainCalled).isTrue()
    }

    @Test
    fun `устанавливает authentication при валидном токене`() {
        // Given: валидный JWT в cookie
        val userId = UUID.randomUUID()
        val user = createTestUser(id = userId, username = "testuser", role = Role.DEVELOPER)
        val token = jwtService.generateToken(user)

        val request = MockServerHttpRequest.get("/api/v1/routes")
            .cookie(HttpCookie("auth_token", token))
            .build()
        val exchange = MockServerWebExchange.from(request)

        var capturedAuthentication: AuthenticatedUser? = null
        val filterChain = WebFilterChain { _ ->
            ReactiveSecurityContextHolder.getContext()
                .doOnNext { context ->
                    capturedAuthentication = context.authentication.principal as? AuthenticatedUser
                }
                .then()
        }

        // When: filter обрабатывает запрос
        val result = filter.filter(exchange, filterChain)

        // Then: SecurityContext содержит AuthenticatedUser
        StepVerifier.create(result)
            .verifyComplete()

        assertThat(capturedAuthentication).isNotNull
        assertThat(capturedAuthentication!!.userId).isEqualTo(userId)
        assertThat(capturedAuthentication!!.username).isEqualTo("testuser")
        assertThat(capturedAuthentication!!.role).isEqualTo(Role.DEVELOPER)
    }

    @Test
    fun `возвращает ошибку TokenInvalid при невалидном токене`() {
        // Given: невалидный JWT
        val request = MockServerHttpRequest.get("/api/v1/routes")
            .cookie(HttpCookie("auth_token", "invalid.token.here"))
            .build()
        val exchange = MockServerWebExchange.from(request)
        val filterChain = WebFilterChain { Mono.empty() }

        // When: filter обрабатывает запрос
        val result = filter.filter(exchange, filterChain)

        // Then: выбрасывается JwtAuthenticationException.TokenInvalid
        StepVerifier.create(result)
            .expectErrorMatches { error ->
                error is JwtAuthenticationException.TokenInvalid &&
                    error.detail == "Invalid token"
            }
            .verify()
    }

    @Test
    fun `возвращает ошибку TokenExpired при истёкшем токене`() {
        // Given: истёкший JWT (создаём с очень коротким временем жизни)
        val shortLivedJwtService = JwtService(testSecret, 1L)
        val filter = JwtAuthenticationFilter(shortLivedJwtService)

        val user = createTestUser()
        val expiredToken = shortLivedJwtService.generateToken(user)

        // Небольшая пауза чтобы токен точно истёк
        Thread.sleep(10)

        val request = MockServerHttpRequest.get("/api/v1/routes")
            .cookie(HttpCookie("auth_token", expiredToken))
            .build()
        val exchange = MockServerWebExchange.from(request)
        val filterChain = WebFilterChain { Mono.empty() }

        // When: filter обрабатывает запрос
        val result = filter.filter(exchange, filterChain)

        // Then: выбрасывается JwtAuthenticationException.TokenExpired
        StepVerifier.create(result)
            .expectErrorMatches { error ->
                error is JwtAuthenticationException.TokenExpired &&
                    error.detail == "Token expired"
            }
            .verify()
    }

    @Test
    fun `возвращает ошибку TokenInvalid при подделанном токене`() {
        // Given: подделанный JWT (реверсированная подпись для гарантированной невалидности)
        val user = createTestUser()
        val validToken = jwtService.generateToken(user)
        val parts = validToken.split(".")
        val tamperedSignature = parts[2].reversed()
        val tamperedToken = "${parts[0]}.${parts[1]}.$tamperedSignature"

        val request = MockServerHttpRequest.get("/api/v1/routes")
            .cookie(HttpCookie("auth_token", tamperedToken))
            .build()
        val exchange = MockServerWebExchange.from(request)
        val filterChain = WebFilterChain { Mono.empty() }

        // When: filter обрабатывает запрос
        val result = filter.filter(exchange, filterChain)

        // Then: выбрасывается JwtAuthenticationException.TokenInvalid
        StepVerifier.create(result)
            .expectErrorMatches { error ->
                error is JwtAuthenticationException.TokenInvalid &&
                    error.detail == "Invalid token"
            }
            .verify()
    }

    @Test
    fun `authorities содержат роль пользователя`() {
        // Given: валидный JWT с ролью ADMIN
        val user = createTestUser(role = Role.ADMIN)
        val token = jwtService.generateToken(user)

        val request = MockServerHttpRequest.get("/api/v1/routes")
            .cookie(HttpCookie("auth_token", token))
            .build()
        val exchange = MockServerWebExchange.from(request)

        var capturedAuthentication: AuthenticatedUser? = null
        val filterChain = WebFilterChain { _ ->
            ReactiveSecurityContextHolder.getContext()
                .doOnNext { context ->
                    capturedAuthentication = context.authentication.principal as? AuthenticatedUser
                }
                .then()
        }

        // When: filter обрабатывает запрос
        val result = filter.filter(exchange, filterChain)

        // Then: authorities содержат ROLE_ADMIN
        StepVerifier.create(result)
            .verifyComplete()

        assertThat(capturedAuthentication).isNotNull
        assertThat(capturedAuthentication!!.authorities)
            .extracting("authority")
            .contains("ROLE_ADMIN")
    }

    @Test
    fun `игнорирует другие cookies кроме auth_token`() {
        // Given: запрос с другой cookie, но без auth_token
        val request = MockServerHttpRequest.get("/api/v1/routes")
            .cookie(HttpCookie("session_id", "some-session"))
            .cookie(HttpCookie("tracking", "some-tracking"))
            .build()
        val exchange = MockServerWebExchange.from(request)
        var filterChainCalled = false
        val filterChain = WebFilterChain { _ ->
            filterChainCalled = true
            Mono.empty()
        }

        // When: filter обрабатывает запрос
        val result = filter.filter(exchange, filterChain)

        // Then: цепочка продолжается (токен не найден)
        StepVerifier.create(result)
            .verifyComplete()
        assertThat(filterChainCalled).isTrue()
    }

    private fun createTestUser(
        id: UUID = UUID.randomUUID(),
        username: String = "testuser",
        role: Role = Role.DEVELOPER
    ) = User(
        id = id,
        username = username,
        email = "test@example.com",
        passwordHash = "\$2a\$10\$hashedpassword",
        role = role,
        isActive = true
    )
}
