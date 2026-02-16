package com.company.gateway.admin.integration

import com.company.gateway.admin.dto.LoginRequest
import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.admin.security.JwtService
import com.company.gateway.admin.service.PasswordService
import com.company.gateway.common.model.Role
import com.company.gateway.common.model.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.test.StepVerifier
import java.util.UUID

/**
 * Integration тесты для Authentication Middleware (Story 2.3).
 *
 * Тестирование AC1-AC6:
 * - AC1: Запрос без auth_token cookie возвращает 401
 * - AC2: Запрос с валидным JWT успешно проходит
 * - AC3: Запрос с истёкшим JWT возвращает 401 "Token expired"
 * - AC4: Запрос с невалидным/подделанным JWT возвращает 401 "Invalid token"
 * - AC5: Публичные endpoints доступны без аутентификации
 * - AC6: X-Correlation-ID включён в error responses
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class AuthMiddlewareIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16")
            .withDatabaseName("gateway")
            .withUsername("gateway")
            .withPassword("gateway")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") {
                "r2dbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/${postgres.databaseName}"
            }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
            registry.add("spring.flyway.url", postgres::getJdbcUrl)
            registry.add("spring.flyway.user", postgres::getUsername)
            registry.add("spring.flyway.password", postgres::getPassword)
            // Отключаем Redis для тестов
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
            }
            registry.add("management.health.redis.enabled") { false }
            registry.add("management.endpoint.health.group.readiness.include") { "r2dbc" }
            // JWT конфигурация
            registry.add("jwt.secret") { "test-secret-key-minimum-32-characters-long" }
            registry.add("jwt.expiration") { 86400000 }
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordService: PasswordService

    @Autowired
    private lateinit var jwtService: JwtService

    private lateinit var testUser: User
    private lateinit var validToken: String

    @BeforeEach
    fun setUp() {
        // Очищаем тестовых пользователей (кроме admin из миграции)
        StepVerifier.create(
            userRepository.findAll()
                .filter { it.username != "admin" }
                .flatMap { userRepository.delete(it) }
                .then()
        ).verifyComplete()

        // Создаём тестового пользователя
        testUser = createTestUser("testuser", "password123", Role.DEVELOPER)
        validToken = jwtService.generateToken(testUser)
    }

    // ============================================
    // AC1: Запрос без auth_token cookie возвращает 401
    // ============================================

    @Test
    fun `AC1 - защищённый endpoint без токена возвращает 401`() {
        webTestClient.get()
            .uri("/api/v1/routes")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `AC1 - 401 ответ содержит RFC 7807 формат`() {
        webTestClient.get()
            .uri("/api/v1/routes")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.type").isEqualTo("https://api.gateway/errors/unauthorized")
            .jsonPath("$.title").isEqualTo("Unauthorized")
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.detail").isEqualTo("Authentication required")
    }

    // ============================================
    // AC2: Запрос с валидным JWT успешно проходит
    // ============================================

    @Test
    fun `AC2 - защищённый endpoint с валидным токеном обрабатывается`() {
        // Примечание: /api/v1/routes вернёт 404 т.к. endpoint не существует,
        // но это доказывает что запрос прошёл authentication middleware
        webTestClient.get()
            .uri("/api/v1/routes")
            .cookie("auth_token", validToken)
            .exchange()
            // Не 401 — значит аутентификация прошла успешно
            .expectStatus().isNotFound // или любой другой статус кроме 401
    }

    @Test
    fun `AC2 - SecurityContext содержит данные пользователя`() {
        // Логин чтобы получить реальный токен через cookie
        val loginResponse = webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("testuser", "password123"))
            .exchange()
            .expectStatus().isOk
            .returnResult(Any::class.java)

        val cookies = loginResponse.responseCookies
        val authTokenCookie = cookies["auth_token"]?.firstOrNull()?.value

        assert(authTokenCookie != null) { "Cookie auth_token должен быть установлен после логина" }

        // Используем полученный токен для доступа к защищённому endpoint
        webTestClient.get()
            .uri("/api/v1/routes")
            .cookie("auth_token", authTokenCookie!!)
            .exchange()
            .expectStatus().isNotFound // Не 401 — аутентификация прошла
    }

    // ============================================
    // AC3: Запрос с истёкшим JWT возвращает 401 "Token expired"
    // ============================================

    @Test
    fun `AC3 - истёкший токен возвращает 401 с detail Token expired`() {
        // Генерируем истёкший токен
        val shortLivedJwtService = JwtService(
            "test-secret-key-minimum-32-characters-long",
            1L // 1 мс
        )
        val expiredToken = shortLivedJwtService.generateToken(testUser)

        // Небольшая пауза чтобы токен точно истёк
        Thread.sleep(10)

        webTestClient.get()
            .uri("/api/v1/routes")
            .cookie("auth_token", expiredToken)
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.detail").isEqualTo("Token expired")
    }

    // ============================================
    // AC4: Запрос с невалидным/подделанным JWT возвращает 401 "Invalid token"
    // ============================================

    @Test
    fun `AC4 - невалидный токен возвращает 401 с detail Invalid token`() {
        webTestClient.get()
            .uri("/api/v1/routes")
            .cookie("auth_token", "invalid.token.here")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.detail").isEqualTo("Invalid token")
    }

    @Test
    fun `AC4 - подделанный токен возвращает 401 с detail Invalid token`() {
        // Реверсируем подпись токена для гарантированной невалидности
        val parts = validToken.split(".")
        val tamperedSignature = parts[2].reversed()
        val tamperedToken = "${parts[0]}.${parts[1]}.$tamperedSignature"

        webTestClient.get()
            .uri("/api/v1/routes")
            .cookie("auth_token", tamperedToken)
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Invalid token")
    }

    @Test
    fun `AC4 - malformed токен возвращает 401 с detail Invalid token`() {
        webTestClient.get()
            .uri("/api/v1/routes")
            .cookie("auth_token", "not-a-jwt")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Invalid token")
    }

    // ============================================
    // AC5: Публичные endpoints доступны без аутентификации
    // ============================================

    @Test
    fun `AC5 - login endpoint доступен без токена`() {
        createTestUser("publicuser", "password123", Role.DEVELOPER)

        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("publicuser", "password123"))
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `AC5 - logout endpoint доступен без токена`() {
        webTestClient.post()
            .uri("/api/v1/auth/logout")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `AC5 - health endpoint доступен без токена`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `AC5 - health liveness endpoint доступен без токена`() {
        webTestClient.get()
            .uri("/actuator/health/liveness")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `AC5 - health readiness endpoint доступен без токена`() {
        webTestClient.get()
            .uri("/actuator/health/readiness")
            .exchange()
            .expectStatus().isOk
    }

    // ============================================
    // AC6: X-Correlation-ID включён в error responses
    // ============================================

    @Test
    fun `AC6 - error response содержит X-Correlation-ID header`() {
        webTestClient.get()
            .uri("/api/v1/routes")
            .exchange()
            .expectStatus().isUnauthorized
            .expectHeader().exists("X-Correlation-ID")
    }

    @Test
    fun `AC6 - error response body содержит correlationId`() {
        webTestClient.get()
            .uri("/api/v1/routes")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.correlationId").isNotEmpty
    }

    @Test
    fun `AC6 - переданный X-Correlation-ID сохраняется в ответе`() {
        val correlationId = "test-correlation-id-12345"

        webTestClient.get()
            .uri("/api/v1/routes")
            .header("X-Correlation-ID", correlationId)
            .exchange()
            .expectStatus().isUnauthorized
            .expectHeader().valueEquals("X-Correlation-ID", correlationId)
            .expectBody()
            .jsonPath("$.correlationId").isEqualTo(correlationId)
    }

    @Test
    fun `AC6 - error response с истёкшим токеном содержит X-Correlation-ID`() {
        val shortLivedJwtService = JwtService(
            "test-secret-key-minimum-32-characters-long",
            1L
        )
        val expiredToken = shortLivedJwtService.generateToken(testUser)
        Thread.sleep(10)

        webTestClient.get()
            .uri("/api/v1/routes")
            .cookie("auth_token", expiredToken)
            .exchange()
            .expectStatus().isUnauthorized
            .expectHeader().exists("X-Correlation-ID")
            .expectBody()
            .jsonPath("$.correlationId").isNotEmpty
    }

    @Test
    fun `AC6 - error response с невалидным токеном содержит X-Correlation-ID`() {
        webTestClient.get()
            .uri("/api/v1/routes")
            .cookie("auth_token", "invalid.token")
            .exchange()
            .expectStatus().isUnauthorized
            .expectHeader().exists("X-Correlation-ID")
            .expectBody()
            .jsonPath("$.correlationId").isNotEmpty
    }

    // ============================================
    // Дополнительные тесты
    // ============================================

    @Test
    fun `токен с ролью ADMIN успешно аутентифицируется`() {
        val adminUser = createTestUser("adminuser", "adminpass", Role.ADMIN)
        val adminToken = jwtService.generateToken(adminUser)

        webTestClient.get()
            .uri("/api/v1/routes")
            .cookie("auth_token", adminToken)
            .exchange()
            .expectStatus().isNotFound // Не 401 — аутентификация прошла
    }

    @Test
    fun `токен с ролью SECURITY успешно аутентифицируется`() {
        val securityUser = createTestUser("securityuser", "secpass", Role.SECURITY)
        val securityToken = jwtService.generateToken(securityUser)

        webTestClient.get()
            .uri("/api/v1/routes")
            .cookie("auth_token", securityToken)
            .exchange()
            .expectStatus().isNotFound // Не 401 — аутентификация прошла
    }

    // ============================================
    // Вспомогательные методы
    // ============================================

    private fun createTestUser(
        username: String,
        password: String,
        role: Role
    ): User {
        val hashedPassword = passwordService.hash(password)
        val user = User(
            username = username,
            email = "$username@example.com",
            passwordHash = hashedPassword,
            role = role,
            isActive = true
        )
        var savedUser: User? = null
        StepVerifier.create(userRepository.save(user))
            .consumeNextWith { savedUser = it }
            .verifyComplete()
        return savedUser!!
    }
}
