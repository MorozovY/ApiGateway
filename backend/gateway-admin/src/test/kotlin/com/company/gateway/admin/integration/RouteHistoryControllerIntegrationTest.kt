package com.company.gateway.admin.integration

import com.company.gateway.admin.repository.AuditLogRepository
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.admin.security.JwtService
import com.company.gateway.admin.service.PasswordService
import com.company.gateway.common.model.AuditLog
import com.company.gateway.common.model.Role
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import com.company.gateway.common.model.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * Integration тесты для RouteHistoryController.
 *
 * Story 7.3: Route Change History API.
 * Task 6: Integration тесты RouteHistoryController.
 *
 * - AC1: GET /api/v1/routes/{id}/history возвращает историю
 * - AC3: 404 для несуществующего маршрута
 * - AC4: фильтрация по from/to
 * - AC5: хронологический порядок
 * - Контроль доступа: только security/admin
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class RouteHistoryControllerIntegrationTest {

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
    private lateinit var auditLogRepository: AuditLogRepository

    @Autowired
    private lateinit var routeRepository: RouteRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordService: PasswordService

    @Autowired
    private lateinit var jwtService: JwtService

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    private lateinit var developerToken: String
    private lateinit var securityToken: String
    private lateinit var adminToken: String
    private lateinit var developerUser: User
    private lateinit var securityUser: User
    private lateinit var adminUser: User

    private lateinit var testRoute: Route

    @BeforeEach
    fun setUp() {
        // Очищаем audit_logs
        StepVerifier.create(auditLogRepository.deleteAll()).verifyComplete()

        // Очищаем routes
        StepVerifier.create(routeRepository.deleteAll()).verifyComplete()

        // Очищаем тестовых пользователей (кроме admin из миграции)
        StepVerifier.create(
            userRepository.findAll()
                .filter { it.username != "admin" }
                .flatMap { userRepository.delete(it) }
                .then()
        ).verifyComplete()

        // Создаём тестовых пользователей
        developerUser = createTestUser("developer", "password", Role.DEVELOPER)
        securityUser = createTestUser("security", "password", Role.SECURITY)
        adminUser = createTestUser("admin_test", "password", Role.ADMIN)

        developerToken = jwtService.generateToken(developerUser)
        securityToken = jwtService.generateToken(securityUser)
        adminToken = jwtService.generateToken(adminUser)

        // Создаём тестовый маршрут
        testRoute = createTestRoute()
    }

    // ============================================
    // AC1: GET /api/v1/routes/{id}/history возвращает историю
    // ============================================

    @Nested
    inner class AC1_БазовыйEndpointИсторииМаршрута {

        @Test
        fun `GET history возвращает историю маршрута`() {
            // Given — создаём audit log записи для маршрута
            createTestAuditLog(testRoute.id!!, "created", securityUser.id!!, "security")
            createTestAuditLog(testRoute.id!!, "route.submitted", securityUser.id!!, "security")
            createTestAuditLog(testRoute.id!!, "approved", securityUser.id!!, "security")

            // When & Then
            webTestClient.get()
                .uri("/api/v1/routes/${testRoute.id}/history")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.routeId").isEqualTo(testRoute.id.toString())
                .jsonPath("$.currentPath").isEqualTo(testRoute.path)
                .jsonPath("$.history").isArray
                .jsonPath("$.history.length()").isEqualTo(3)
        }

        @Test
        fun `GET history возвращает правильную структуру элементов`() {
            // Given
            val changesJson = """{"before":{"upstreamUrl":"http://v1:8080"},"after":{"upstreamUrl":"http://v2:8080"}}"""
            createTestAuditLogWithChanges(
                testRoute.id!!,
                "updated",
                securityUser.id!!,
                "security",
                changesJson
            )

            // When & Then
            webTestClient.get()
                .uri("/api/v1/routes/${testRoute.id}/history")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.history[0].timestamp").isNotEmpty
                .jsonPath("$.history[0].action").isEqualTo("updated")
                .jsonPath("$.history[0].user.id").isEqualTo(securityUser.id.toString())
                .jsonPath("$.history[0].user.username").isEqualTo("security")
                .jsonPath("$.history[0].changes.before.upstreamUrl").isEqualTo("http://v1:8080")
                .jsonPath("$.history[0].changes.after.upstreamUrl").isEqualTo("http://v2:8080")
        }

        @Test
        fun `GET history возвращает пустой список когда нет событий`() {
            // Given — маршрут есть, но audit logs нет

            // When & Then
            webTestClient.get()
                .uri("/api/v1/routes/${testRoute.id}/history")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.routeId").isEqualTo(testRoute.id.toString())
                .jsonPath("$.history").isArray
                .jsonPath("$.history.length()").isEqualTo(0)
        }
    }

    // ============================================
    // AC3: 404 для несуществующего маршрута
    // ============================================

    @Nested
    inner class AC3_НесуществующийМаршрут {

        @Test
        fun `GET history для несуществующего маршрута возвращает 404`() {
            // Given
            val nonExistentRouteId = UUID.randomUUID()

            // When & Then
            webTestClient.get()
                .uri("/api/v1/routes/$nonExistentRouteId/history")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.type").exists()
                .jsonPath("$.title").exists()
                .jsonPath("$.status").isEqualTo(404)
        }
    }

    // ============================================
    // AC4: фильтрация по from/to
    // ============================================

    @Nested
    inner class AC4_ФильтрацияПоДиапазонуДат {

        @Test
        fun `GET history с from и to возвращает записи в диапазоне`() {
            // Given — записи с разными датами
            val jan15 = Instant.parse("2026-01-15T12:00:00Z")
            val feb05 = Instant.parse("2026-02-05T12:00:00Z")
            val feb10 = Instant.parse("2026-02-10T12:00:00Z")
            val mar01 = Instant.parse("2026-03-01T12:00:00Z")

            createTestAuditLogWithTimestamp(testRoute.id!!, "created", securityUser.id!!, "security", jan15)
            createTestAuditLogWithTimestamp(testRoute.id!!, "updated", securityUser.id!!, "security", feb05)
            createTestAuditLogWithTimestamp(testRoute.id!!, "approved", securityUser.id!!, "security", feb10)
            createTestAuditLogWithTimestamp(testRoute.id!!, "published", securityUser.id!!, "security", mar01)

            // When & Then — фильтруем по диапазону 2026-02-01 to 2026-02-11
            webTestClient.get()
                .uri("/api/v1/routes/${testRoute.id}/history?from=2026-02-01&to=2026-02-11")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.history.length()").isEqualTo(2)
        }

        @Test
        fun `GET history только с from возвращает записи начиная с этой даты`() {
            // Given
            val feb01 = Instant.parse("2026-02-01T12:00:00Z")
            val feb15 = Instant.parse("2026-02-15T12:00:00Z")

            createTestAuditLogWithTimestamp(testRoute.id!!, "created", securityUser.id!!, "security", feb01)
            createTestAuditLogWithTimestamp(testRoute.id!!, "approved", securityUser.id!!, "security", feb15)

            // When & Then — from=2026-02-10
            webTestClient.get()
                .uri("/api/v1/routes/${testRoute.id}/history?from=2026-02-10")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.history.length()").isEqualTo(1)
        }

        @Test
        fun `GET history только с to возвращает записи до этой даты`() {
            // Given
            val feb01 = Instant.parse("2026-02-01T12:00:00Z")
            val feb15 = Instant.parse("2026-02-15T12:00:00Z")

            createTestAuditLogWithTimestamp(testRoute.id!!, "created", securityUser.id!!, "security", feb01)
            createTestAuditLogWithTimestamp(testRoute.id!!, "approved", securityUser.id!!, "security", feb15)

            // When & Then — to=2026-02-10
            webTestClient.get()
                .uri("/api/v1/routes/${testRoute.id}/history?to=2026-02-10")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.history.length()").isEqualTo(1)
        }

        @Test
        fun `GET history возвращает 400 когда from позже to`() {
            // When & Then — невалидный диапазон: from > to
            webTestClient.get()
                .uri("/api/v1/routes/${testRoute.id}/history?from=2026-02-20&to=2026-02-10")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.detail").exists()
        }
    }

    // ============================================
    // AC5: хронологический порядок
    // ============================================

    @Nested
    inner class AC5_ХронологическийПорядок {

        @Test
        fun `GET history сортирует по timestamp ASC (oldest first)`() {
            // Given — создаём записи в обратном порядке
            val olderTimestamp = Instant.parse("2026-01-01T10:00:00Z")
            val newerTimestamp = Instant.parse("2026-02-20T10:00:00Z")

            // Сначала создаём новую запись
            createTestAuditLogWithTimestamp(testRoute.id!!, "approved", securityUser.id!!, "security", newerTimestamp)
            // Затем создаём старую запись
            createTestAuditLogWithTimestamp(testRoute.id!!, "created", securityUser.id!!, "security", olderTimestamp)

            // When & Then — старая запись должна быть первой (ASC order)
            webTestClient.get()
                .uri("/api/v1/routes/${testRoute.id}/history")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.history[0].action").isEqualTo("created")
                .jsonPath("$.history[1].action").isEqualTo("approved")
        }
    }

    // ============================================
    // Контроль доступа: только security/admin
    // ============================================

    @Nested
    inner class КонтрольДоступа {

        @Test
        fun `developer получает 403 Forbidden при доступе к history`() {
            // When & Then
            webTestClient.get()
                .uri("/api/v1/routes/${testRoute.id}/history")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun `security получает доступ к history`() {
            // When & Then
            webTestClient.get()
                .uri("/api/v1/routes/${testRoute.id}/history")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `admin получает доступ к history`() {
            // When & Then
            webTestClient.get()
                .uri("/api/v1/routes/${testRoute.id}/history")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `неавторизованный пользователь получает 401`() {
            // When & Then
            webTestClient.get()
                .uri("/api/v1/routes/${testRoute.id}/history")
                .exchange()
                .expectStatus().isUnauthorized
        }
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

    private fun createTestRoute(): Route {
        val route = Route(
            path = "/api/orders",
            upstreamUrl = "http://orders-service:8080",
            methods = listOf("GET", "POST"),
            createdBy = securityUser.id!!,
            status = RouteStatus.PUBLISHED
        )
        var savedRoute: Route? = null
        StepVerifier.create(routeRepository.save(route))
            .consumeNextWith { savedRoute = it }
            .verifyComplete()
        return savedRoute!!
    }

    private fun createTestAuditLog(
        routeId: UUID,
        action: String,
        userId: UUID,
        username: String
    ) {
        val auditLog = AuditLog(
            entityType = "route",
            entityId = routeId.toString(),
            action = action,
            userId = userId,
            username = username,
            changes = null,
            ipAddress = "192.168.1.100",
            correlationId = "test-corr-id",
            createdAt = Instant.now()
        )
        StepVerifier.create(auditLogRepository.save(auditLog))
            .expectNextCount(1)
            .verifyComplete()
    }

    private fun createTestAuditLogWithChanges(
        routeId: UUID,
        action: String,
        userId: UUID,
        username: String,
        changes: String
    ) {
        val auditLog = AuditLog(
            entityType = "route",
            entityId = routeId.toString(),
            action = action,
            userId = userId,
            username = username,
            changes = changes,
            ipAddress = "192.168.1.100",
            correlationId = "test-corr-id",
            createdAt = Instant.now()
        )
        StepVerifier.create(auditLogRepository.save(auditLog))
            .expectNextCount(1)
            .verifyComplete()
    }

    /**
     * Создаёт audit log с явно указанным timestamp через прямой SQL.
     *
     * Используется вместо auditLogRepository.save(), потому что @CreatedDate
     * в AuditLog entity автоматически переопределяет createdAt при вставке.
     */
    private fun createTestAuditLogWithTimestamp(
        routeId: UUID,
        action: String,
        userId: UUID,
        username: String,
        timestamp: Instant
    ) {
        val id = UUID.randomUUID()

        StepVerifier.create(
            databaseClient.sql(
                """
                INSERT INTO audit_logs (id, entity_type, entity_id, action, user_id, username, created_at)
                VALUES (:id, :entityType, :entityId, :action, :userId, :username, :createdAt)
                """.trimIndent()
            )
                .bind("id", id)
                .bind("entityType", "route")
                .bind("entityId", routeId.toString())
                .bind("action", action)
                .bind("userId", userId)
                .bind("username", username)
                .bind("createdAt", timestamp)
                .fetch()
                .rowsUpdated()
        )
            .expectNext(1L)
            .verifyComplete()
    }
}
