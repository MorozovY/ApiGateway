package com.company.gateway.admin.integration

import com.company.gateway.admin.repository.AuditLogRepository
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.admin.security.JwtService
import com.company.gateway.admin.service.PasswordService
import com.company.gateway.common.model.Role
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import com.company.gateway.common.model.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
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
 * Integration тесты для GET /api/v1/routes/pending (Story 4.3).
 *
 * Покрывает все Acceptance Criteria:
 * - AC1: Успешное получение списка pending маршрутов
 * - AC2: Сортировка по submittedAt descending
 * - AC3: Пустой список pending маршрутов
 * - AC4: Недостаточно прав (Developer)
 * - AC5: Пагинация
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class PendingRoutesIntegrationTest {

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
    private lateinit var routeRepository: RouteRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var auditLogRepository: AuditLogRepository

    @Autowired
    private lateinit var passwordService: PasswordService

    @Autowired
    private lateinit var jwtService: JwtService

    private lateinit var developerToken: String
    private lateinit var securityToken: String
    private lateinit var adminToken: String
    private lateinit var developerUser: User
    private lateinit var securityUser: User
    private lateinit var adminUser: User

    @BeforeEach
    fun setUp() {
        // Очищаем маршруты
        StepVerifier.create(routeRepository.deleteAll()).verifyComplete()
        // Очищаем audit_logs перед пользователями (FK RESTRICT, V5_1 миграция)
        StepVerifier.create(auditLogRepository.deleteAll()).verifyComplete()

        // Очищаем тестовых пользователей (кроме admin из миграции)
        StepVerifier.create(
            userRepository.findAll()
                .filter { it.username != "admin" }
                .flatMap { userRepository.delete(it) }
                .then()
        ).verifyComplete()

        // Создаём тестовых пользователей
        developerUser = createTestUser("developer", Role.DEVELOPER)
        securityUser = createTestUser("security", Role.SECURITY)
        adminUser = createTestUser("admintest", Role.ADMIN)

        developerToken = jwtService.generateToken(developerUser)
        securityToken = jwtService.generateToken(securityUser)
        adminToken = jwtService.generateToken(adminUser)
    }

    // ============================================
    // AC1: Успешное получение списка pending маршрутов
    // ============================================

    @Nested
    inner class AC1_УспешноеПолучениеПендинг {

        @Test
        fun `GET pending возвращает 200 со списком pending маршрутов`() {
            // Given
            createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)
            createTestRoute("/api/payments", developerUser.id!!, RouteStatus.PENDING)
            // Не-pending маршруты не должны попасть в результат
            createTestRoute("/api/draft", developerUser.id!!, RouteStatus.DRAFT)

            // When & Then
            webTestClient.get()
                .uri("/api/v1/routes/pending")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items").isArray
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.total").isEqualTo(2)
                .jsonPath("$.offset").isEqualTo(0)
                .jsonPath("$.limit").isEqualTo(20)
        }

        @Test
        fun `GET pending сортирует по submittedAt asc по умолчанию`() {
            // Given — создаём маршруты с разным временем submittedAt
            val olderRoute = createTestRouteWithSubmittedAt(
                "/api/older",
                developerUser.id!!,
                Instant.parse("2026-02-17T10:00:00Z")
            )
            val newerRoute = createTestRouteWithSubmittedAt(
                "/api/newer",
                developerUser.id!!,
                Instant.parse("2026-02-17T11:00:00Z")
            )

            // When & Then — oldest first (FIFO)
            webTestClient.get()
                .uri("/api/v1/routes/pending")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items[0].id").isEqualTo(olderRoute.id.toString())
                .jsonPath("$.items[1].id").isEqualTo(newerRoute.id.toString())
        }

        @Test
        fun `GET pending включает createdBy с username`() {
            // Given
            createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

            // When & Then
            webTestClient.get()
                .uri("/api/v1/routes/pending")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items[0].createdBy").isEqualTo(developerUser.id.toString())
                .jsonPath("$.items[0].creatorUsername").isEqualTo("developer")
        }

        @Test
        fun `GET pending содержит все необходимые поля маршрута`() {
            // Given
            createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

            // When & Then
            webTestClient.get()
                .uri("/api/v1/routes/pending")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items[0].id").isNotEmpty
                .jsonPath("$.items[0].path").isEqualTo("/api/orders")
                .jsonPath("$.items[0].upstreamUrl").isEqualTo("http://test-service:8080")
                .jsonPath("$.items[0].methods").isArray
                .jsonPath("$.items[0].status").isEqualTo("pending")
                .jsonPath("$.items[0].submittedAt").isNotEmpty
        }

        @Test
        fun `GET pending работает для admin роли`() {
            // Given
            createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

            // When & Then
            webTestClient.get()
                .uri("/api/v1/routes/pending")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.total").isEqualTo(1)
        }
    }

    // ============================================
    // AC2: Сортировка по submittedAt descending
    // ============================================

    @Nested
    inner class AC2_СортировкаDesc {

        @Test
        fun `GET pending с sort submittedAt desc сортирует по newest first`() {
            // Given — создаём маршруты с разным временем submittedAt
            val olderRoute = createTestRouteWithSubmittedAt(
                "/api/older",
                developerUser.id!!,
                Instant.parse("2026-02-17T10:00:00Z")
            )
            val newerRoute = createTestRouteWithSubmittedAt(
                "/api/newer",
                developerUser.id!!,
                Instant.parse("2026-02-17T11:00:00Z")
            )

            // When & Then — newest first
            webTestClient.get()
                .uri("/api/v1/routes/pending?sort=submittedAt:desc")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items[0].id").isEqualTo(newerRoute.id.toString())
                .jsonPath("$.items[1].id").isEqualTo(olderRoute.id.toString())
        }
    }

    // ============================================
    // AC3: Пустой список pending маршрутов
    // ============================================

    @Nested
    inner class AC3_ПустойСписок {

        @Test
        fun `GET pending возвращает пустой список с total 0 когда нет pending`() {
            // Given — только draft маршруты
            createTestRoute("/api/draft", developerUser.id!!, RouteStatus.DRAFT)
            createTestRoute("/api/published", developerUser.id!!, RouteStatus.PUBLISHED)

            // When & Then
            webTestClient.get()
                .uri("/api/v1/routes/pending")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items").isArray
                .jsonPath("$.items.length()").isEqualTo(0)
                .jsonPath("$.total").isEqualTo(0)
        }
    }

    // ============================================
    // AC4: Недостаточно прав (Developer)
    // ============================================

    @Nested
    inner class AC4_НедостаточноПрав {

        @Test
        fun `GET pending возвращает 403 для developer роли`() {
            // When & Then
            webTestClient.get()
                .uri("/api/v1/routes/pending")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/forbidden")
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.detail").isEqualTo("Insufficient permissions")
        }

        @Test
        fun `GET pending возвращает 401 без авторизации`() {
            // When & Then
            webTestClient.get()
                .uri("/api/v1/routes/pending")
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        fun `GET pending содержит correlationId в ответе 403`() {
            // When & Then
            webTestClient.get()
                .uri("/api/v1/routes/pending")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
                .expectBody()
                .jsonPath("$.correlationId").isNotEmpty
        }
    }

    // ============================================
    // AC5: Пагинация
    // ============================================

    @Nested
    inner class AC5_Пагинация {

        @Test
        fun `GET pending применяет пагинацию`() {
            // Given — создаём 15 pending маршрутов
            repeat(15) { i ->
                createTestRoute("/api/route-$i", developerUser.id!!, RouteStatus.PENDING)
            }

            // When & Then — запрашиваем первые 10
            webTestClient.get()
                .uri("/api/v1/routes/pending?offset=0&limit=10")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(10)
                .jsonPath("$.total").isEqualTo(15)
                .jsonPath("$.offset").isEqualTo(0)
                .jsonPath("$.limit").isEqualTo(10)
        }

        @Test
        fun `GET pending с offset возвращает вторую страницу`() {
            // Given — создаём 5 pending маршрутов с известным временем
            repeat(5) { i ->
                createTestRouteWithSubmittedAt(
                    "/api/route-$i",
                    developerUser.id!!,
                    Instant.parse("2026-02-17T10:0$i:00Z")
                )
            }

            // When & Then — запрашиваем вторую страницу (offset=3)
            webTestClient.get()
                .uri("/api/v1/routes/pending?offset=3&limit=10")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.total").isEqualTo(5)
                .jsonPath("$.offset").isEqualTo(3)
                .jsonPath("$.limit").isEqualTo(10)
        }
    }

    // ============================================
    // Вспомогательные методы
    // ============================================

    private fun createTestUser(username: String, role: Role): User {
        val hashedPassword = passwordService.hash("password")
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
        path: String,
        createdBy: UUID,
        status: RouteStatus = RouteStatus.DRAFT
    ): Route {
        val submittedAt = if (status == RouteStatus.PENDING) Instant.now() else null
        val route = Route(
            path = path,
            upstreamUrl = "http://test-service:8080",
            methods = listOf("GET", "POST"),
            description = "Test route",
            status = status,
            createdBy = createdBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            submittedAt = submittedAt
        )
        var savedRoute: Route? = null
        StepVerifier.create(routeRepository.save(route))
            .consumeNextWith { savedRoute = it }
            .verifyComplete()
        return savedRoute!!
    }

    private fun createTestRouteWithSubmittedAt(
        path: String,
        createdBy: UUID,
        submittedAt: Instant
    ): Route {
        val route = Route(
            path = path,
            upstreamUrl = "http://test-service:8080",
            methods = listOf("GET", "POST"),
            description = "Test route",
            status = RouteStatus.PENDING,
            createdBy = createdBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            submittedAt = submittedAt
        )
        var savedRoute: Route? = null
        StepVerifier.create(routeRepository.save(route))
            .consumeNextWith { savedRoute = it }
            .verifyComplete()
        return savedRoute!!
    }
}
