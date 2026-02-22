package com.company.gateway.admin.integration

import com.company.gateway.admin.dto.LoginRequest
import com.company.gateway.admin.repository.AuditLogRepository
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
    private lateinit var auditLogRepository: AuditLogRepository

    @Autowired
    private lateinit var passwordService: PasswordService

    @BeforeEach
    fun setUp() {
        // Очищаем audit_logs перед пользователями (FK RESTRICT, V5_1 миграция)
        StepVerifier.create(auditLogRepository.deleteAll()).verifyComplete()

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
    // Story 9.1 AC1: GET /api/v1/auth/me — восстановление сессии
    // ============================================

    @Test
    fun `Story 9-1 AC1 - GET me с валидным токеном возвращает 200 и данные пользователя`() {
        createTestUser("maria", "password123", Role.DEVELOPER)

        // Сначала логинимся, чтобы получить cookie
        val loginResponse = webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("maria", "password123"))
            .exchange()
            .expectStatus().isOk
            .returnResult(Any::class.java)

        val authCookie = loginResponse.responseHeaders.getFirst("Set-Cookie")!!

        // Используем cookie для запроса /me
        webTestClient.get()
            .uri("/api/v1/auth/me")
            .header("Cookie", authCookie.split(";")[0]) // Берём только "auth_token=..."
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.username").isEqualTo("maria")
            .jsonPath("$.role").isEqualTo("developer")
            .jsonPath("$.userId").isNotEmpty
    }

    @Test
    fun `Story 9-1 AC1 - GET me без cookie возвращает 401`() {
        webTestClient.get()
            .uri("/api/v1/auth/me")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `Story 9-1 AC1 - GET me с невалидным токеном возвращает 401`() {
        webTestClient.get()
            .uri("/api/v1/auth/me")
            .header("Cookie", "auth_token=invalid.jwt.token")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `Story 9-1 AC1 - GET me возвращает корректную роль admin`() {
        createTestUser("adminuser", "adminpass", Role.ADMIN)

        val loginResponse = webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("adminuser", "adminpass"))
            .exchange()
            .expectStatus().isOk
            .returnResult(Any::class.java)

        val authCookie = loginResponse.responseHeaders.getFirst("Set-Cookie")!!

        webTestClient.get()
            .uri("/api/v1/auth/me")
            .header("Cookie", authCookie.split(";")[0])
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.role").isEqualTo("admin")
    }

    @Test
    fun `Story 9-1 AC1 - GET me возвращает корректную роль security`() {
        createTestUser("secuser", "secpass", Role.SECURITY)

        val loginResponse = webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("secuser", "secpass"))
            .exchange()
            .expectStatus().isOk
            .returnResult(Any::class.java)

        val authCookie = loginResponse.responseHeaders.getFirst("Set-Cookie")!!

        webTestClient.get()
            .uri("/api/v1/auth/me")
            .header("Cookie", authCookie.split(";")[0])
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.role").isEqualTo("security")
    }

    // ============================================
    // Story 9.4: Change Password — успешная смена пароля (AC3)
    // ============================================

    @Test
    fun `Story 9-4 AC3 - успешная смена пароля возвращает 200`() {
        createTestUser("maria", "password123", Role.DEVELOPER)

        // Логинимся, чтобы получить cookie
        val loginResponse = webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("maria", "password123"))
            .exchange()
            .expectStatus().isOk
            .returnResult(Any::class.java)

        val authCookie = loginResponse.responseHeaders.getFirst("Set-Cookie")!!

        // Меняем пароль
        webTestClient.post()
            .uri("/api/v1/auth/change-password")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Cookie", authCookie.split(";")[0])
            .bodyValue(mapOf("currentPassword" to "password123", "newPassword" to "newSecurePassword123"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.message").isEqualTo("Password changed successfully")
    }

    @Test
    fun `Story 9-4 AC3 - после смены пароля старый пароль не работает`() {
        createTestUser("maria", "password123", Role.DEVELOPER)

        // Логинимся и меняем пароль
        val loginResponse = webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("maria", "password123"))
            .exchange()
            .expectStatus().isOk
            .returnResult(Any::class.java)

        val authCookie = loginResponse.responseHeaders.getFirst("Set-Cookie")!!

        webTestClient.post()
            .uri("/api/v1/auth/change-password")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Cookie", authCookie.split(";")[0])
            .bodyValue(mapOf("currentPassword" to "password123", "newPassword" to "newSecurePassword123"))
            .exchange()
            .expectStatus().isOk

        // Пробуем залогиниться со старым паролем — должен быть 401
        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("maria", "password123"))
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `Story 9-4 AC3 - после смены пароля новый пароль работает`() {
        createTestUser("maria", "password123", Role.DEVELOPER)

        // Логинимся и меняем пароль
        val loginResponse = webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("maria", "password123"))
            .exchange()
            .expectStatus().isOk
            .returnResult(Any::class.java)

        val authCookie = loginResponse.responseHeaders.getFirst("Set-Cookie")!!

        webTestClient.post()
            .uri("/api/v1/auth/change-password")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Cookie", authCookie.split(";")[0])
            .bodyValue(mapOf("currentPassword" to "password123", "newPassword" to "newSecurePassword123"))
            .exchange()
            .expectStatus().isOk

        // Пробуем залогиниться с новым паролем — должен быть 200
        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("maria", "newSecurePassword123"))
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `Story 9-4 AC3 - смена пароля записывает audit log`() {
        createTestUser("maria", "password123", Role.DEVELOPER)

        val loginResponse = webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("maria", "password123"))
            .exchange()
            .expectStatus().isOk
            .returnResult(Any::class.java)

        val authCookie = loginResponse.responseHeaders.getFirst("Set-Cookie")!!

        // Очищаем audit logs перед тестом
        StepVerifier.create(auditLogRepository.deleteAll()).verifyComplete()

        webTestClient.post()
            .uri("/api/v1/auth/change-password")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Cookie", authCookie.split(";")[0])
            .bodyValue(mapOf("currentPassword" to "password123", "newPassword" to "newSecurePassword123"))
            .exchange()
            .expectStatus().isOk

        // Проверяем, что audit log записан
        StepVerifier.create(auditLogRepository.findAll().collectList())
            .expectNextMatches { logs ->
                logs.any { it.action == "password_changed" && it.entityType == "user" && it.username == "maria" }
            }
            .verifyComplete()
    }

    // ============================================
    // Story 9.4: Change Password — неверный текущий пароль (AC4)
    // ============================================

    @Test
    fun `Story 9-4 AC4 - неверный текущий пароль возвращает 401`() {
        createTestUser("maria", "password123", Role.DEVELOPER)

        val loginResponse = webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("maria", "password123"))
            .exchange()
            .expectStatus().isOk
            .returnResult(Any::class.java)

        val authCookie = loginResponse.responseHeaders.getFirst("Set-Cookie")!!

        webTestClient.post()
            .uri("/api/v1/auth/change-password")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Cookie", authCookie.split(";")[0])
            .bodyValue(mapOf("currentPassword" to "wrongPassword", "newPassword" to "newSecurePassword123"))
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Current password is incorrect")
    }

    @Test
    fun `Story 9-4 AC4 - при неверном текущем пароле пароль не изменяется`() {
        createTestUser("maria", "password123", Role.DEVELOPER)

        val loginResponse = webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("maria", "password123"))
            .exchange()
            .expectStatus().isOk
            .returnResult(Any::class.java)

        val authCookie = loginResponse.responseHeaders.getFirst("Set-Cookie")!!

        // Пытаемся сменить пароль с неверным текущим
        webTestClient.post()
            .uri("/api/v1/auth/change-password")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Cookie", authCookie.split(";")[0])
            .bodyValue(mapOf("currentPassword" to "wrongPassword", "newPassword" to "newSecurePassword123"))
            .exchange()
            .expectStatus().isUnauthorized

        // Проверяем, что старый пароль всё ещё работает
        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("maria", "password123"))
            .exchange()
            .expectStatus().isOk
    }

    // ============================================
    // Story 9.4: Change Password — валидация (AC5)
    // ============================================

    @Test
    fun `Story 9-4 валидация - слабый новый пароль менее 8 символов возвращает 400`() {
        createTestUser("maria", "password123", Role.DEVELOPER)

        val loginResponse = webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("maria", "password123"))
            .exchange()
            .expectStatus().isOk
            .returnResult(Any::class.java)

        val authCookie = loginResponse.responseHeaders.getFirst("Set-Cookie")!!

        webTestClient.post()
            .uri("/api/v1/auth/change-password")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Cookie", authCookie.split(";")[0])
            .bodyValue(mapOf("currentPassword" to "password123", "newPassword" to "short"))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.detail").value<String> { detail ->
                assert(detail.contains("8")) { "Сообщение должно упоминать минимум 8 символов" }
            }
    }

    @Test
    fun `Story 9-4 валидация - пустой currentPassword возвращает 400`() {
        createTestUser("maria", "password123", Role.DEVELOPER)

        val loginResponse = webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("maria", "password123"))
            .exchange()
            .expectStatus().isOk
            .returnResult(Any::class.java)

        val authCookie = loginResponse.responseHeaders.getFirst("Set-Cookie")!!

        webTestClient.post()
            .uri("/api/v1/auth/change-password")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Cookie", authCookie.split(";")[0])
            .bodyValue(mapOf("currentPassword" to "", "newPassword" to "newSecurePassword123"))
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `Story 9-4 валидация - пустой newPassword возвращает 400`() {
        createTestUser("maria", "password123", Role.DEVELOPER)

        val loginResponse = webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("maria", "password123"))
            .exchange()
            .expectStatus().isOk
            .returnResult(Any::class.java)

        val authCookie = loginResponse.responseHeaders.getFirst("Set-Cookie")!!

        webTestClient.post()
            .uri("/api/v1/auth/change-password")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Cookie", authCookie.split(";")[0])
            .bodyValue(mapOf("currentPassword" to "password123", "newPassword" to ""))
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `Story 9-4 - смена пароля без авторизации возвращает 401`() {
        webTestClient.post()
            .uri("/api/v1/auth/change-password")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("currentPassword" to "password123", "newPassword" to "newSecurePassword123"))
            .exchange()
            .expectStatus().isUnauthorized
    }

    // ============================================
    // Story 9.5: Сброс паролей демо-пользователей
    // ============================================

    @Test
    fun `Story 9-5 - сброс паролей демо-пользователей успешно`() {
        // Создаём демо-пользователей с изменёнными паролями (удаляем если существуют)
        createOrUpdateTestUser("developer", "changedPassword1", Role.DEVELOPER)
        createOrUpdateTestUser("security", "changedPassword2", Role.SECURITY)
        createOrUpdateTestUser("admin", "changedPassword3", Role.ADMIN)

        // Вызываем endpoint сброса паролей (без авторизации)
        webTestClient.post()
            .uri("/api/v1/auth/reset-demo-passwords")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.message").isEqualTo("Demo passwords reset successfully")
            .jsonPath("$.users").isArray
            .jsonPath("$.users.length()").isEqualTo(3)

        // Проверяем, что теперь можно войти с дефолтными паролями
        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("developer", "developer123"))
            .exchange()
            .expectStatus().isOk

        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("security", "security123"))
            .exchange()
            .expectStatus().isOk

        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("admin", "admin123"))
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `Story 9-5 - сброс паролей доступен без авторизации`() {
        // Создаём хотя бы одного демо-пользователя (удаляем если существует)
        createOrUpdateTestUser("developer", "somePassword", Role.DEVELOPER)

        // Вызываем без cookie/токена — должно работать
        webTestClient.post()
            .uri("/api/v1/auth/reset-demo-passwords")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `Story 9-5 - создаёт отсутствующих демо-пользователей при сбросе`() {
        // Удаляем всех демо-пользователей
        deleteDemoUsers()

        // Вызываем сброс — должен создать всех троих
        webTestClient.post()
            .uri("/api/v1/auth/reset-demo-passwords")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.message").isEqualTo("Demo passwords reset successfully")
            .jsonPath("$.users").isArray
            .jsonPath("$.users.length()").isEqualTo(3)

        // Проверяем, что созданные пользователи могут войти
        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("developer", "developer123"))
            .exchange()
            .expectStatus().isOk

        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("security", "security123"))
            .exchange()
            .expectStatus().isOk

        webTestClient.post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(LoginRequest("admin", "admin123"))
            .exchange()
            .expectStatus().isOk
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

    /**
     * Создаёт или обновляет пользователя (для тестов сброса паролей).
     */
    private fun createOrUpdateTestUser(
        username: String,
        password: String,
        role: Role
    ) {
        val hashedPassword = passwordService.hash(password)

        StepVerifier.create(
            userRepository.findByUsername(username)
                .flatMap { existingUser ->
                    // Обновляем пароль существующего пользователя
                    userRepository.save(existingUser.copy(passwordHash = hashedPassword))
                }
                .switchIfEmpty(
                    // Создаём нового пользователя
                    userRepository.save(User(
                        username = username,
                        email = "$username@example.com",
                        passwordHash = hashedPassword,
                        role = role,
                        isActive = true
                    ))
                )
        )
            .expectNextCount(1)
            .verifyComplete()
    }

    /**
     * Удаляет демо-пользователей (developer, security, admin).
     */
    private fun deleteDemoUsers() {
        val demoUsernames = listOf("developer", "security", "admin")
        demoUsernames.forEach { username ->
            StepVerifier.create(
                userRepository.findByUsername(username)
                    .flatMap { user -> userRepository.delete(user).thenReturn(user) }
                    .then()
            )
                .verifyComplete()
        }
    }
}
