package com.company.gateway.admin.integration

import com.company.gateway.admin.dto.CreateUserRequest
import com.company.gateway.admin.dto.UpdateRouteRequest
import com.company.gateway.admin.dto.UpdateUserRequest
import com.company.gateway.admin.repository.AuditLogRepository
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.admin.security.JwtService
import com.company.gateway.admin.service.PasswordService
import com.company.gateway.common.model.Role
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import com.company.gateway.common.model.User
import java.time.Instant
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import reactor.test.StepVerifier
import java.util.UUID

/**
 * Integration тесты для Role-Based Access Control (Story 2.4).
 *
 * Тестирование AC1-AC10:
 * - AC1: Developer не может получить доступ к ADMIN endpoints → 403
 * - AC2: Admin может получить доступ к ADMIN endpoints
 * - AC3: Role hierarchy — Admin > Security > Developer
 * - AC4: Developer может только read/update/delete свои маршруты
 * - AC5: Developer может удалять только draft маршруты
 * - AC6: Security может approve/reject маршруты
 * - AC7: Security может читать audit logs
 * - AC8: Admin может управлять пользователями
 * - AC9: Admin может управлять rate limit policies
 * - AC10: Correlation ID включён в 403 responses
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RbacIntegrationTest {

    companion object {
        // Проверяем запущены ли мы в CI
        private val isTestcontainersDisabled = System.getenv("TESTCONTAINERS_DISABLED") == "true"

        private var postgres: PostgreSQLContainer<*>? = null

        @BeforeAll
        @JvmStatic
        fun startContainers() {
            if (isTestcontainersDisabled) {
                return
            }
            postgres = PostgreSQLContainer("postgres:16")
                .withDatabaseName("gateway")
                .withUsername("gateway")
                .withPassword("gateway")
            postgres!!.start()
        }

        @AfterAll
        @JvmStatic
        fun stopContainers() {
            postgres?.stop()
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            if (isTestcontainersDisabled) {
                // В CI читаем из env переменных
                val pgHost = System.getenv("POSTGRES_HOST") ?: "localhost"
                val pgPort = System.getenv("POSTGRES_PORT") ?: "5432"
                val pgDb = System.getenv("POSTGRES_DB_ADMIN") ?: System.getenv("POSTGRES_DB") ?: "gateway_admin_test"
                val pgUser = System.getenv("POSTGRES_USER") ?: "gateway"
                val pgPass = System.getenv("POSTGRES_PASSWORD") ?: "gateway"

                registry.add("spring.r2dbc.url") { "r2dbc:postgresql://$pgHost:$pgPort/$pgDb" }
                registry.add("spring.r2dbc.username") { pgUser }
                registry.add("spring.r2dbc.password") { pgPass }
                registry.add("spring.flyway.url") { "jdbc:postgresql://$pgHost:$pgPort/$pgDb" }
                registry.add("spring.flyway.user") { pgUser }
                registry.add("spring.flyway.password") { pgPass }
            } else {
                registry.add("spring.r2dbc.url") {
                    "r2dbc:postgresql://${postgres!!.host}:${postgres!!.firstMappedPort}/${postgres!!.databaseName}"
                }
                registry.add("spring.r2dbc.username", postgres!!::getUsername)
                registry.add("spring.r2dbc.password", postgres!!::getPassword)
                registry.add("spring.flyway.url", postgres!!::getJdbcUrl)
                registry.add("spring.flyway.user", postgres!!::getUsername)
                registry.add("spring.flyway.password", postgres!!::getPassword)
            }
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
    private lateinit var routeRepository: RouteRepository

    @Autowired
    private lateinit var auditLogRepository: AuditLogRepository

    @Autowired
    private lateinit var passwordService: PasswordService

    @Autowired
    private lateinit var jwtService: JwtService

    private lateinit var developerToken: String
    private lateinit var securityToken: String
    private lateinit var adminToken: String

    // Сохранённые пользователи для тестов ownership
    private lateinit var developerUser: User
    private lateinit var otherDeveloperUser: User

    @BeforeEach
    fun setUp() {
        // Очищаем тестовые маршруты и audit_logs (FK: users → audit_logs с RESTRICT, V5_1 миграция)
        StepVerifier.create(routeRepository.deleteAll()).verifyComplete()
        StepVerifier.create(auditLogRepository.deleteAll()).verifyComplete()

        // Очищаем тестовых пользователей (кроме admin из миграции)
        StepVerifier.create(
            userRepository.findAll()
                .filter { it.username != "admin" }
                .flatMap { userRepository.delete(it) }
                .then()
        ).verifyComplete()

        // Создаём тестовых пользователей для каждой роли
        developerUser = createTestUser("developer", "password", Role.DEVELOPER)
        otherDeveloperUser = createTestUser("other_developer", "password", Role.DEVELOPER)
        val securityUser = createTestUser("security", "password", Role.SECURITY)
        val adminUser = createTestUser("adminuser", "password", Role.ADMIN)

        developerToken = jwtService.generateToken(developerUser)
        securityToken = jwtService.generateToken(securityUser)
        adminToken = jwtService.generateToken(adminUser)
    }

    // ============================================
    // AC1: Developer не может получить доступ к ADMIN endpoints
    // ============================================

    @Nested
    inner class AC1_DeveloperNoAdminAccess {

        @Test
        fun `developer получает 403 при доступе к users endpoint`() {
            webTestClient.get()
                .uri("/api/v1/users")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun `developer получает 403 при попытке создать пользователя`() {
            val request = CreateUserRequest(
                username = "newuser_forbidden",
                email = "newuser_forbidden@example.com",
                password = "password123",
                role = Role.DEVELOPER
            )

            webTestClient.post()
                .uri("/api/v1/users")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun `developer получает 403 response в RFC 7807 формате`() {
            webTestClient.get()
                .uri("/api/v1/users")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/forbidden")
                .jsonPath("$.title").isEqualTo("Forbidden")
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.detail").isEqualTo("Insufficient permissions")
        }
    }

    // ============================================
    // AC2: Admin может получить доступ к ADMIN endpoints
    // ============================================

    @Nested
    inner class AC2_AdminAccess {

        @Test
        fun `admin успешно получает доступ к users endpoint`() {
            webTestClient.get()
                .uri("/api/v1/users")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `admin успешно создаёт пользователя`() {
            val request = CreateUserRequest(
                username = "newuser_ac2",
                email = "newuser_ac2@example.com",
                password = "password123",
                role = Role.DEVELOPER
            )

            webTestClient.post()
                .uri("/api/v1/users")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
        }
    }

    // ============================================
    // AC3: Role hierarchy — Admin > Security > Developer
    // ============================================

    @Nested
    inner class AC3_RoleHierarchy {

        @Test
        fun `admin имеет доступ к SECURITY endpoint через иерархию`() {
            webTestClient.get()
                .uri("/api/v1/audit")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `admin имеет доступ к DEVELOPER endpoint через иерархию`() {
            webTestClient.get()
                .uri("/api/v1/routes")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `security имеет доступ к DEVELOPER endpoint через иерархию`() {
            webTestClient.get()
                .uri("/api/v1/routes")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `developer не имеет доступа к SECURITY endpoint`() {
            webTestClient.get()
                .uri("/api/v1/audit")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun `security не имеет доступа к ADMIN endpoint`() {
            webTestClient.get()
                .uri("/api/v1/users")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isForbidden
        }
    }

    // ============================================
    // AC4: Developer может только read/update/delete свои маршруты
    // ============================================

    @Nested
    inner class AC4_DeveloperOwnershipCheck {

        @Test
        fun `developer может обновить свой маршрут`() {
            // Создаём маршрут, принадлежащий developer
            val route = createTestRoute(developerUser.id!!, RouteStatus.DRAFT)

            val request = UpdateRouteRequest(description = "Updated description")

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `developer получает 403 при попытке обновить чужой маршрут`() {
            // Создаём маршрут, принадлежащий другому developer
            val route = createTestRoute(otherDeveloperUser.id!!, RouteStatus.DRAFT)

            val request = UpdateRouteRequest(description = "Try to update")

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden
                .expectBody()
                .jsonPath("$.detail").isEqualTo("You can only modify your own routes")
        }

        @Test
        fun `developer может удалить свой draft маршрут`() {
            // Создаём draft маршрут, принадлежащий developer
            val route = createTestRoute(developerUser.id!!, RouteStatus.DRAFT)

            webTestClient.delete()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isNoContent
        }

        @Test
        fun `developer получает 403 при попытке удалить чужой маршрут`() {
            // Создаём маршрут, принадлежащий другому developer
            val route = createTestRoute(otherDeveloperUser.id!!, RouteStatus.DRAFT)

            webTestClient.delete()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
                .expectBody()
                .jsonPath("$.detail").isEqualTo("You can only modify your own routes")
        }

        @Test
        fun `security может обновить чужой маршрут`() {
            // Создаём маршрут, принадлежащий developer
            val route = createTestRoute(developerUser.id!!, RouteStatus.DRAFT)

            val request = UpdateRouteRequest(description = "Security updated")

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", securityToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `admin может обновить чужой маршрут`() {
            // Создаём маршрут, принадлежащий developer
            val route = createTestRoute(developerUser.id!!, RouteStatus.DRAFT)

            val request = UpdateRouteRequest(description = "Admin updated")

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `admin может удалить чужой маршрут`() {
            // Создаём маршрут, принадлежащий developer
            val route = createTestRoute(developerUser.id!!, RouteStatus.DRAFT)

            webTestClient.delete()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isNoContent
        }
    }

    // ============================================
    // AC5: Developer может удалять только draft маршруты
    // ============================================

    @Nested
    inner class AC5_DeveloperDraftOnlyDelete {

        @Test
        fun `developer получает 409 при попытке удалить PENDING маршрут`() {
            // Создаём PENDING маршрут, принадлежащий developer
            val route = createTestRoute(developerUser.id!!, RouteStatus.PENDING)

            webTestClient.delete()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/conflict")
                .jsonPath("$.detail").isEqualTo("Only draft routes can be deleted")
        }

        @Test
        fun `developer получает 409 при попытке удалить PUBLISHED маршрут`() {
            // Создаём PUBLISHED маршрут, принадлежащий developer
            val route = createTestRoute(developerUser.id!!, RouteStatus.PUBLISHED)

            webTestClient.delete()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Only draft routes can be deleted")
        }

        @Test
        fun `admin НЕ может удалить PUBLISHED маршрут - Story 3-1 требует draft статус`() {
            // Story 3.1 AC5: Только draft маршруты могут быть удалены (даже admin)
            val route = createTestRoute(developerUser.id!!, RouteStatus.PUBLISHED)

            webTestClient.delete()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Only draft routes can be deleted")
        }

        @Test
        fun `409 response содержит correlationId`() {
            val route = createTestRoute(developerUser.id!!, RouteStatus.PENDING)

            webTestClient.delete()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().exists("X-Correlation-ID")
                .expectBody()
                .jsonPath("$.correlationId").isNotEmpty
        }
    }

    // ============================================
    // AC6: Security может approve/reject маршруты
    // ============================================

    @Nested
    inner class AC6_SecurityApproveReject {

        @Test
        fun `security может вызвать approve endpoint`() {
            webTestClient.post()
                .uri("/api/v1/routes/${UUID.randomUUID()}/approve")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isNotFound // 404 — маршрут не существует, но доступ разрешён
        }

        @Test
        fun `security может вызвать reject endpoint`() {
            // Передаём reason — без него API вернёт 400, не достигнув RBAC-проверки
            webTestClient.post()
                .uri("/api/v1/routes/${UUID.randomUUID()}/reject")
                .cookie("auth_token", securityToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""{"reason": "Security issue"}""")
                .exchange()
                .expectStatus().isNotFound // 404 — маршрут не существует, но доступ разрешён
        }

        @Test
        fun `admin может вызвать approve endpoint через иерархию`() {
            webTestClient.post()
                .uri("/api/v1/routes/${UUID.randomUUID()}/approve")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isNotFound // 404 — маршрут не существует, но доступ разрешён
        }

        @Test
        fun `developer получает 403 при попытке approve`() {
            webTestClient.post()
                .uri("/api/v1/routes/${UUID.randomUUID()}/approve")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun `developer получает 403 при попытке reject`() {
            // Передаём reason, чтобы RBAC-проверка выполнялась раньше body-валидации
            webTestClient.post()
                .uri("/api/v1/routes/${UUID.randomUUID()}/reject")
                .cookie("auth_token", developerToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""{"reason": "Security issue"}""")
                .exchange()
                .expectStatus().isForbidden
        }
    }

    // ============================================
    // AC7: Security может читать audit logs
    // ============================================

    @Nested
    inner class AC7_SecurityAuditAccess {

        @Test
        fun `security успешно получает доступ к audit endpoint`() {
            webTestClient.get()
                .uri("/api/v1/audit")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `admin успешно получает доступ к audit endpoint`() {
            webTestClient.get()
                .uri("/api/v1/audit")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `developer получает 403 при доступе к audit endpoint`() {
            webTestClient.get()
                .uri("/api/v1/audit")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
        }
    }

    // ============================================
    // AC8: Admin может управлять пользователями
    // ============================================

    @Nested
    inner class AC8_AdminUserManagement {

        @Test
        fun `admin может получить список пользователей`() {
            webTestClient.get()
                .uri("/api/v1/users")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `admin может создать пользователя`() {
            val request = CreateUserRequest(
                username = "newuser_rbac",
                email = "newuser_rbac@example.com",
                password = "password123",
                role = Role.DEVELOPER
            )

            webTestClient.post()
                .uri("/api/v1/users")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
        }

        @Test
        fun `admin может обновить пользователя`() {
            // Сначала создаём пользователя для обновления
            val hashedPassword = passwordService.hash("password123")
            val targetUser = User(
                username = "toupdate_rbac",
                email = "toupdate_rbac@example.com",
                passwordHash = hashedPassword,
                role = Role.DEVELOPER,
                isActive = true
            )
            var savedUser: User? = null
            StepVerifier.create(userRepository.save(targetUser))
                .consumeNextWith { savedUser = it }
                .verifyComplete()

            val request = UpdateUserRequest(role = Role.SECURITY)

            webTestClient.put()
                .uri("/api/v1/users/${savedUser?.id}")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `security не может получить список пользователей`() {
            webTestClient.get()
                .uri("/api/v1/users")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun `developer не может получить список пользователей`() {
            webTestClient.get()
                .uri("/api/v1/users")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
        }
    }

    // ============================================
    // AC9: Admin может управлять rate limit policies
    // ============================================

    @Nested
    inner class AC9_AdminRateLimitManagement {

        @Test
        fun `developer может читать rate limit policies`() {
            webTestClient.get()
                .uri("/api/v1/rate-limits")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `security может читать rate limit policies`() {
            webTestClient.get()
                .uri("/api/v1/rate-limits")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `admin может создать rate limit policy`() {
            // Запрос без тела — авторизация прошла (400 Bad Request, не 403)
            webTestClient.post()
                .uri("/api/v1/rate-limits")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `admin может обновить rate limit policy`() {
            // Несуществующий ID — авторизация прошла (400/404, не 403)
            webTestClient.put()
                .uri("/api/v1/rate-limits/${UUID.randomUUID()}")
                .cookie("auth_token", adminToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("name" to "test"))
                .exchange()
                .expectStatus().isNotFound
        }

        @Test
        fun `admin может удалить rate limit policy`() {
            // Несуществующий ID — авторизация прошла (404, не 403)
            webTestClient.delete()
                .uri("/api/v1/rate-limits/${UUID.randomUUID()}")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isNotFound
        }

        @Test
        fun `developer получает 403 при попытке создать rate limit policy`() {
            webTestClient.post()
                .uri("/api/v1/rate-limits")
                .cookie("auth_token", developerToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("name" to "test", "requestsPerSecond" to 100, "burstSize" to 150))
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun `security получает 403 при попытке создать rate limit policy`() {
            webTestClient.post()
                .uri("/api/v1/rate-limits")
                .cookie("auth_token", securityToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("name" to "test", "requestsPerSecond" to 100, "burstSize" to 150))
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun `developer получает 403 при попытке обновить rate limit policy`() {
            webTestClient.put()
                .uri("/api/v1/rate-limits/${UUID.randomUUID()}")
                .cookie("auth_token", developerToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("name" to "test"))
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun `developer получает 403 при попытке удалить rate limit policy`() {
            webTestClient.delete()
                .uri("/api/v1/rate-limits/${UUID.randomUUID()}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
        }
    }

    // ============================================
    // AC10: Correlation ID включён в 403 responses
    // ============================================

    @Nested
    inner class AC10_CorrelationId {

        @Test
        fun `403 response содержит X-Correlation-ID header`() {
            webTestClient.get()
                .uri("/api/v1/users")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
                .expectHeader().exists("X-Correlation-ID")
        }

        @Test
        fun `403 response body содержит correlationId`() {
            webTestClient.get()
                .uri("/api/v1/users")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
                .expectBody()
                .jsonPath("$.correlationId").isNotEmpty
        }

        @Test
        fun `переданный X-Correlation-ID сохраняется в 403 ответе`() {
            val correlationId = "test-rbac-correlation-12345"

            webTestClient.get()
                .uri("/api/v1/users")
                .header("X-Correlation-ID", correlationId)
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
                .expectHeader().valueEquals("X-Correlation-ID", correlationId)
                .expectBody()
                .jsonPath("$.correlationId").isEqualTo(correlationId)
        }

        @Test
        fun `403 при approve содержит X-Correlation-ID`() {
            webTestClient.post()
                .uri("/api/v1/routes/${UUID.randomUUID()}/approve")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
                .expectHeader().exists("X-Correlation-ID")
                .expectBody()
                .jsonPath("$.correlationId").isNotEmpty
        }
    }

    // ============================================
    // Дополнительные тесты
    // ============================================

    @Test
    fun `все роли могут получить список маршрутов`() {
        // Developer
        webTestClient.get()
            .uri("/api/v1/routes")
            .cookie("auth_token", developerToken)
            .exchange()
            .expectStatus().isOk

        // Security
        webTestClient.get()
            .uri("/api/v1/routes")
            .cookie("auth_token", securityToken)
            .exchange()
            .expectStatus().isOk

        // Admin
        webTestClient.get()
            .uri("/api/v1/routes")
            .cookie("auth_token", adminToken)
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `неаутентифицированный запрос к защищённому endpoint возвращает 401, а не 403`() {
        webTestClient.get()
            .uri("/api/v1/users")
            .exchange()
            .expectStatus().isUnauthorized
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

    private fun createTestRoute(
        createdBy: UUID,
        status: RouteStatus
    ): Route {
        val route = Route(
            path = "/api/test/${UUID.randomUUID()}",
            upstreamUrl = "http://localhost:8080",
            methods = listOf("GET"),
            status = status,
            createdBy = createdBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        var savedRoute: Route? = null
        StepVerifier.create(routeRepository.save(route))
            .consumeNextWith { savedRoute = it }
            .verifyComplete()
        return savedRoute!!
    }
}
