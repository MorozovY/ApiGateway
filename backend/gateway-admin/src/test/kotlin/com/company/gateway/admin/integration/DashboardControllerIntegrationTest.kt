package com.company.gateway.admin.integration

import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.admin.repository.AuditLogRepository
import com.company.gateway.admin.security.JwtService
import com.company.gateway.admin.service.PasswordService
import com.company.gateway.common.model.Role
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import com.company.gateway.common.model.User
import com.company.gateway.common.model.AuditLog
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
 * Интеграционные тесты для DashboardController (Story 16.2).
 *
 * Покрывает AC1-AC5:
 * - AC1: Developer Dashboard — своя статистика, recent activity, quick actions
 * - AC2: Security Dashboard — pending approvals, recent approvals
 * - AC3: Admin Dashboard — system overview, health status
 * - AC4: Loading states — проверяется через error handling
 * - AC5: Responsive layout — проверяется на frontend
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DashboardControllerIntegrationTest {

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
            // Отключаем Redis и Keycloak для тестов
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
            }
            registry.add("management.health.redis.enabled") { false }
            registry.add("management.endpoint.health.group.readiness.include") { "r2dbc" }
            registry.add("keycloak.enabled") { false }
            // JWT конфигурация
            registry.add("jwt.secret") { "test-secret-key-minimum-32-characters-long" }
            registry.add("jwt.expiration") { 86400000 }
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var routeRepository: RouteRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var auditLogRepository: AuditLogRepository

    @Autowired
    private lateinit var passwordService: PasswordService

    @Autowired
    private lateinit var jwtService: JwtService

    private lateinit var developerUser: User
    private lateinit var developerToken: String
    private lateinit var securityUser: User
    private lateinit var securityToken: String
    private lateinit var adminUser: User
    private lateinit var adminToken: String

    @BeforeEach
    fun setUp() {
        // Очищаем audit logs
        StepVerifier.create(
            auditLogRepository.deleteAll()
        ).verifyComplete()

        // Очищаем маршруты
        StepVerifier.create(
            routeRepository.deleteAll()
        ).verifyComplete()

        // Очищаем тестовых пользователей (кроме admin из миграции)
        StepVerifier.create(
            userRepository.findAll()
                .filter { it.username != "admin" }
                .flatMap { userRepository.delete(it) }
                .then()
        ).verifyComplete()

        // Создаём тестовых пользователей
        developerUser = createTestUser("dashboard_dev", "password", Role.DEVELOPER)
        developerToken = jwtService.generateToken(developerUser)

        securityUser = createTestUser("dashboard_security", "password", Role.SECURITY)
        securityToken = jwtService.generateToken(securityUser)

        adminUser = createTestUser("dashboard_admin", "password", Role.ADMIN)
        adminToken = jwtService.generateToken(adminUser)
    }

    // ============================================
    // AC1: Developer Dashboard
    // ============================================

    @Nested
    inner class AC1_DeveloperDashboard {

        @Test
        fun `GET dashboard summary возвращает статистику маршрутов для developer`() {
            // Given: создаём маршруты для developer
            createTestRoute("/api/draft-route", developerUser.id!!, RouteStatus.DRAFT)
            createTestRoute("/api/pending-route", developerUser.id!!, RouteStatus.PENDING)
            createTestRoute("/api/published-route", developerUser.id!!, RouteStatus.PUBLISHED)

            // When & Then
            webTestClient.get()
                .uri("/api/v1/dashboard/summary")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.routesByStatus.draft").isEqualTo(1)
                .jsonPath("$.routesByStatus.pending").isEqualTo(1)
                .jsonPath("$.routesByStatus.published").isEqualTo(1)
                .jsonPath("$.routesByStatus.rejected").isEqualTo(0)
        }

        @Test
        fun `developer видит только свои маршруты в статистике`() {
            // Given: создаём маршруты для developer
            createTestRoute("/api/dev-route", developerUser.id!!, RouteStatus.PUBLISHED)

            // И маршрут другого пользователя
            val otherDev = createTestUser("other_dev", "password", Role.DEVELOPER)
            createTestRoute("/api/other-route", otherDev.id!!, RouteStatus.PUBLISHED)

            // When & Then
            webTestClient.get()
                .uri("/api/v1/dashboard/summary")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                // developer видит только свой 1 маршрут
                .jsonPath("$.routesByStatus.published").isEqualTo(1)
        }

        @Test
        fun `developer не видит totalUsers и totalConsumers`() {
            webTestClient.get()
                .uri("/api/v1/dashboard/summary")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.totalUsers").doesNotExist()
                .jsonPath("$.totalConsumers").doesNotExist()
                .jsonPath("$.systemHealth").doesNotExist()
        }
    }

    // ============================================
    // AC2: Security Dashboard
    // ============================================

    @Nested
    inner class AC2_SecurityDashboard {

        @Test
        fun `security видит pendingApprovalsCount`() {
            // Given: создаём pending маршруты
            createTestRoute("/api/pending-1", developerUser.id!!, RouteStatus.PENDING)
            createTestRoute("/api/pending-2", developerUser.id!!, RouteStatus.PENDING)

            // When & Then
            webTestClient.get()
                .uri("/api/v1/dashboard/summary")
                .cookie("auth_token", securityToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.pendingApprovalsCount").isEqualTo(2)
        }

        @Test
        fun `security видит все маршруты в статистике`() {
            // Given: создаём маршруты от разных пользователей
            createTestRoute("/api/dev-route", developerUser.id!!, RouteStatus.PUBLISHED)
            createTestRoute("/api/security-route", securityUser.id!!, RouteStatus.DRAFT)

            // When & Then
            webTestClient.get()
                .uri("/api/v1/dashboard/summary")
                .cookie("auth_token", securityToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                // security видит ВСЕ маршруты
                .jsonPath("$.routesByStatus.published").isEqualTo(1)
                .jsonPath("$.routesByStatus.draft").isEqualTo(1)
        }

        @Test
        fun `security не видит totalUsers и totalConsumers`() {
            webTestClient.get()
                .uri("/api/v1/dashboard/summary")
                .cookie("auth_token", securityToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.totalUsers").doesNotExist()
                .jsonPath("$.totalConsumers").doesNotExist()
        }
    }

    // ============================================
    // AC3: Admin Dashboard
    // ============================================

    @Nested
    inner class AC3_AdminDashboard {

        @Test
        fun `admin видит totalUsers`() {
            webTestClient.get()
                .uri("/api/v1/dashboard/summary")
                .cookie("auth_token", adminToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                // admin из миграции + 3 тестовых (developer, security, admin) = 4
                .jsonPath("$.totalUsers").isEqualTo(4)
        }

        @Test
        fun `admin видит все поля summary`() {
            // Given
            createTestRoute("/api/test-route", developerUser.id!!, RouteStatus.PENDING)

            // When & Then
            webTestClient.get()
                .uri("/api/v1/dashboard/summary")
                .cookie("auth_token", adminToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.routesByStatus").exists()
                .jsonPath("$.pendingApprovalsCount").isEqualTo(1)
                .jsonPath("$.totalUsers").isEqualTo(4)
                // totalConsumers null если Keycloak отключен
                .jsonPath("$.totalConsumers").doesNotExist()
                // systemHealth может быть degraded если часть сервисов недоступна в тестовом окружении
                .jsonPath("$.systemHealth").exists()
        }

        @Test
        fun `admin видит systemHealth`() {
            // В тестовом окружении часть сервисов может быть недоступна (Redis)
            // поэтому проверяем только что поле существует
            webTestClient.get()
                .uri("/api/v1/dashboard/summary")
                .cookie("auth_token", adminToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.systemHealth").exists()
        }

        @Test
        fun `admin видит все маршруты в статистике`() {
            // Given: создаём маршруты от разных пользователей
            createTestRoute("/api/admin-draft-route", developerUser.id!!, RouteStatus.DRAFT)
            createTestRoute("/api/admin-published-route", securityUser.id!!, RouteStatus.PUBLISHED)

            // When & Then
            webTestClient.get()
                .uri("/api/v1/dashboard/summary")
                .cookie("auth_token", adminToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.routesByStatus.draft").isEqualTo(1)
                .jsonPath("$.routesByStatus.published").isEqualTo(1)
                // Проверяем что totalUsers отображается для admin
                .jsonPath("$.totalUsers").isEqualTo(4)
        }
    }

    // ============================================
    // Recent Activity tests
    // ============================================

    @Nested
    inner class RecentActivity {

        @Test
        fun `GET recent-activity возвращает последние действия`() {
            // Given: создаём audit log entries
            createAuditLog("route", "route-1", "created", developerUser.id!!, developerUser.username)
            createAuditLog("route", "route-2", "updated", developerUser.id!!, developerUser.username)

            // When & Then
            webTestClient.get()
                .uri("/api/v1/dashboard/recent-activity")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items").isArray
                .jsonPath("$.items.length()").isEqualTo(2)
        }

        @Test
        fun `developer видит только свои действия в recent-activity`() {
            // Given: создаём audit logs для разных пользователей
            createAuditLog("route", "route-1", "created", developerUser.id!!, developerUser.username)
            createAuditLog("route", "route-2", "approved", securityUser.id!!, securityUser.username)

            // When & Then
            webTestClient.get()
                .uri("/api/v1/dashboard/recent-activity")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                // developer видит только свои действия
                .jsonPath("$.items.length()").isEqualTo(1)
        }

        @Test
        fun `security видит только approve и reject действия`() {
            // Given: создаём audit logs
            createAuditLog("route", "route-1", "created", developerUser.id!!, developerUser.username)
            createAuditLog("route", "route-2", "approved", securityUser.id!!, securityUser.username)
            createAuditLog("route", "route-3", "rejected", securityUser.id!!, securityUser.username)

            // When & Then
            webTestClient.get()
                .uri("/api/v1/dashboard/recent-activity")
                .cookie("auth_token", securityToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                // security видит только approve/reject
                .jsonPath("$.items.length()").isEqualTo(2)
        }

        @Test
        fun `recent-activity поддерживает параметр limit`() {
            // Given: создаём много audit logs
            repeat(10) { i ->
                createAuditLog("route", "route-$i", "created", developerUser.id!!, developerUser.username)
            }

            // When & Then
            webTestClient.get()
                .uri("/api/v1/dashboard/recent-activity?limit=5")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(5)
        }

        @Test
        fun `recent-activity возвращает пустой список если нет данных`() {
            webTestClient.get()
                .uri("/api/v1/dashboard/recent-activity")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items").isArray
                .jsonPath("$.items.length()").isEqualTo(0)
        }
    }

    // ============================================
    // Error handling tests
    // ============================================

    @Nested
    inner class ErrorHandling {

        @Test
        fun `GET dashboard summary без аутентификации возвращает 401`() {
            webTestClient.get()
                .uri("/api/v1/dashboard/summary")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        fun `GET recent-activity без аутентификации возвращает 401`() {
            webTestClient.get()
                .uri("/api/v1/dashboard/recent-activity")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized
        }
    }

    // ============================================
    // Вспомогательные методы
    // ============================================

    private fun createTestUser(username: String, password: String, role: Role): User {
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

    private fun createTestRoute(path: String, createdBy: UUID, status: RouteStatus): Route {
        val route = Route(
            path = path,
            upstreamUrl = "http://test-service:8080",
            methods = listOf("GET", "POST"),
            description = "Test route for dashboard",
            status = status,
            createdBy = createdBy
        )
        var savedRoute: Route? = null
        StepVerifier.create(routeRepository.save(route))
            .consumeNextWith { savedRoute = it }
            .verifyComplete()
        return savedRoute!!
    }

    private fun createAuditLog(entityType: String, entityId: String, action: String, userId: UUID, username: String) {
        val auditLog = AuditLog(
            entityType = entityType,
            entityId = entityId,
            action = action,
            userId = userId,
            username = username
        )
        StepVerifier.create(auditLogRepository.save(auditLog))
            .expectNextCount(1)
            .verifyComplete()
    }
}
