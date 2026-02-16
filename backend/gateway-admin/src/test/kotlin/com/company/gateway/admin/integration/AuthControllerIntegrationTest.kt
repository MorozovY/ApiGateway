package com.company.gateway.admin.integration

import com.company.gateway.admin.dto.LoginRequest
import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.admin.service.PasswordService
import com.company.gateway.common.model.Role
import com.company.gateway.common.model.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.test.StepVerifier

/**
 * Integration тесты для AuthController (Story 2.2).
 *
 * Тестирование AC1-AC4:
 * - AC1: Успешный логин возвращает JWT в HTTP-only cookie
 * - AC2: Неверные учётные данные возвращают 401
 * - AC3: Logout очищает cookie
 * - AC4: Неактивный пользователь не может залогиниться
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthControllerIntegrationTest {

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
            // Отключаем Redis для тестов аутентификации
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

    @BeforeEach
    fun setUp() {
        // Очищаем тестовых пользователей (кроме admin из миграции)
        StepVerifier.create(
            userRepository.findAll()
                .filter { it.username != "admin" }
                .flatMap { userRepository.delete(it) }
                .then()
        ).verifyComplete()
    }

    // ============================================
    // AC1: Успешный логин возвращает JWT в HTTP-only cookie
    // ============================================

    @Test
    fun `AC1 - успешный логин возвращает 200 и Set-Cookie header`() {
        createTestUser("maria", "password123", Role.DEVELOPER)

        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("maria", "password123"))
            .exchange()
            .expectStatus().isOk
            .expectHeader().exists("Set-Cookie")
    }

    @Test
    fun `AC1 - успешный логин возвращает userId, username и role в теле ответа`() {
        createTestUser("maria", "password123", Role.DEVELOPER)

        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("maria", "password123"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.username").isEqualTo("maria")
            .jsonPath("$.role").isEqualTo("developer")
            .jsonPath("$.userId").isNotEmpty
    }

    @Test
    fun `AC1 - cookie содержит auth_token с HttpOnly атрибутом`() {
        createTestUser("maria", "password123", Role.DEVELOPER)

        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("maria", "password123"))
            .exchange()
            .expectStatus().isOk
            .expectHeader().value("Set-Cookie") { cookie ->
                assert(cookie.contains("auth_token=")) { "Cookie должен содержать auth_token" }
                assert(cookie.contains("HttpOnly")) { "Cookie должен иметь HttpOnly атрибут" }
            }
    }

    @Test
    fun `AC1 - cookie содержит SameSite=Strict и Path корня`() {
        createTestUser("maria", "password123", Role.DEVELOPER)

        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("maria", "password123"))
            .exchange()
            .expectStatus().isOk
            .expectHeader().value("Set-Cookie") { cookie ->
                assert(cookie.contains("SameSite=Strict")) { "Cookie должен иметь SameSite=Strict" }
                assert(cookie.contains("Path=/")) { "Cookie должен иметь Path=/" }
            }
    }

    @Test
    fun `AC1 - логин с ролью ADMIN возвращает role=admin`() {
        createTestUser("adminuser", "adminpass", Role.ADMIN)

        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("adminuser", "adminpass"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.role").isEqualTo("admin")
    }

    @Test
    fun `AC1 - логин с ролью SECURITY возвращает role=security`() {
        createTestUser("securityuser", "secpass", Role.SECURITY)

        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("securityuser", "secpass"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.role").isEqualTo("security")
    }

    // ============================================
    // AC2: Неверные учётные данные возвращают 401
    // ============================================

    @Test
    fun `AC2 - неверный пароль возвращает 401 Unauthorized`() {
        createTestUser("maria", "password123", Role.DEVELOPER)

        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("maria", "wrongpassword"))
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `AC2 - неверный пароль возвращает RFC 7807 формат с detail`() {
        createTestUser("maria", "password123", Role.DEVELOPER)

        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("maria", "wrongpassword"))
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.type").isEqualTo("https://api.gateway/errors/authentication-failed")
            .jsonPath("$.title").isEqualTo("Unauthorized")
            .jsonPath("$.status").isEqualTo(401)
            .jsonPath("$.detail").isEqualTo("Invalid credentials")
    }

    @Test
    fun `AC2 - несуществующий пользователь возвращает 401`() {
        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("nonexistent", "anypassword"))
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Invalid credentials")
    }

    @Test
    fun `AC2 - неверные учётные данные не устанавливают cookie`() {
        createTestUser("maria", "password123", Role.DEVELOPER)

        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("maria", "wrongpassword"))
            .exchange()
            .expectStatus().isUnauthorized
            .expectHeader().doesNotExist("Set-Cookie")
    }

    // ============================================
    // AC3: Logout очищает cookie
    // ============================================

    @Test
    fun `AC3 - logout возвращает 200`() {
        webTestClient.post()
            .uri("/api/v1/auth/logout")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `AC3 - logout устанавливает cookie с Max-Age=0`() {
        webTestClient.post()
            .uri("/api/v1/auth/logout")
            .exchange()
            .expectStatus().isOk
            .expectHeader().value("Set-Cookie") { cookie ->
                assert(cookie.contains("auth_token=")) { "Cookie должен содержать auth_token" }
                assert(cookie.contains("Max-Age=0")) { "Cookie должен иметь Max-Age=0 для удаления" }
            }
    }

    @Test
    fun `AC3 - logout сохраняет атрибуты безопасности cookie`() {
        webTestClient.post()
            .uri("/api/v1/auth/logout")
            .exchange()
            .expectStatus().isOk
            .expectHeader().value("Set-Cookie") { cookie ->
                assert(cookie.contains("HttpOnly")) { "Logout cookie должен иметь HttpOnly" }
                assert(cookie.contains("SameSite=Strict")) { "Logout cookie должен иметь SameSite=Strict" }
                assert(cookie.contains("Path=/")) { "Logout cookie должен иметь Path=/" }
            }
    }

    // ============================================
    // AC4: Неактивный пользователь не может залогиниться
    // ============================================

    @Test
    fun `AC4 - неактивный пользователь получает 401`() {
        createTestUser("inactive", "password123", Role.DEVELOPER, isActive = false)

        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("inactive", "password123"))
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `AC4 - неактивный пользователь получает detail Account is disabled`() {
        createTestUser("inactive", "password123", Role.DEVELOPER, isActive = false)

        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("inactive", "password123"))
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Account is disabled")
    }

    @Test
    fun `AC4 - неактивный пользователь не получает cookie`() {
        createTestUser("inactive", "password123", Role.DEVELOPER, isActive = false)

        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("inactive", "password123"))
            .exchange()
            .expectStatus().isUnauthorized
            .expectHeader().doesNotExist("Set-Cookie")
    }

    // ============================================
    // Валидация входных данных
    // ============================================

    @Test
    fun `пустой username возвращает 400 Bad Request`() {
        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("username" to "", "password" to "password123"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.type").isEqualTo("https://api.gateway/errors/validation-failed")
            .jsonPath("$.status").isEqualTo(400)
    }

    @Test
    fun `пустой password возвращает 400 Bad Request`() {
        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("username" to "maria", "password" to ""))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.type").isEqualTo("https://api.gateway/errors/validation-failed")
    }

    @Test
    fun `отсутствующие поля возвращают 400 Bad Request`() {
        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf<String, String>())
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `слишком длинный username возвращает 400 Bad Request`() {
        val longUsername = "a".repeat(101)

        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("username" to longUsername, "password" to "password123"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.detail").value<String> { detail ->
                assert(detail.contains("100")) { "Сообщение должно упоминать лимит в 100 символов" }
            }
    }

    // ============================================
    // Вспомогательные методы
    // ============================================

    private fun createTestUser(
        username: String,
        password: String,
        role: Role,
        isActive: Boolean = true
    ) {
        val hashedPassword = passwordService.hash(password)
        val user = User(
            username = username,
            email = "$username@example.com",
            passwordHash = hashedPassword,
            role = role,
            isActive = isActive
        )
        StepVerifier.create(userRepository.save(user))
            .expectNextCount(1)
            .verifyComplete()
    }
}
