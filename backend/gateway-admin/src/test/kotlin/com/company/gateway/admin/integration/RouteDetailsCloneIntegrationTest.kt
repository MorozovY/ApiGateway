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
import org.assertj.core.api.Assertions.assertThat
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
 * Интеграционные тесты для получения деталей маршрута и клонирования (Story 3.3).
 *
 * Тестирование AC1-AC5:
 * - AC1: Получение деталей маршрута по ID с username создателя
 * - AC2: 404 Not Found для несуществующего маршрута
 * - AC3: Клонирование маршрута с -copy суффиксом
 * - AC4: Автоматическое разрешение конфликта path (-copy-2, -copy-3, etc.)
 * - AC5: 404 Not Found при клонировании несуществующего маршрута
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class RouteDetailsCloneIntegrationTest {

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
    private lateinit var otherDeveloperToken: String
    private lateinit var developerUser: User
    private lateinit var otherDeveloperUser: User

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
        developerUser = createTestUser("developer", "password", Role.DEVELOPER)
        otherDeveloperUser = createTestUser("otherdev", "password", Role.DEVELOPER)

        developerToken = jwtService.generateToken(developerUser)
        otherDeveloperToken = jwtService.generateToken(otherDeveloperUser)
    }

    // ============================================
    // AC1: Получение деталей маршрута по ID
    // ============================================

    @Nested
    inner class AC1_GetRouteDetails {

        @Test
        fun `возвращает детали маршрута с username создателя`() {
            // Создаём маршрут
            val route = createTestRoute("/api/orders", developerUser.id!!)

            webTestClient.get()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(route.id.toString())
                .jsonPath("$.path").isEqualTo("/api/orders")
                .jsonPath("$.upstreamUrl").isEqualTo("http://test-service:8080")
                .jsonPath("$.methods").isArray
                .jsonPath("$.methods[0]").isEqualTo("GET")
                .jsonPath("$.methods[1]").isEqualTo("POST")
                .jsonPath("$.description").isEqualTo("Test route")
                .jsonPath("$.status").isEqualTo("draft")
                .jsonPath("$.createdBy").isEqualTo(developerUser.id.toString())
                .jsonPath("$.creatorUsername").isEqualTo("developer")
                .jsonPath("$.createdAt").exists()
                .jsonPath("$.updatedAt").exists()
        }

        @Test
        fun `возвращает creatorUsername для маршрута другого пользователя`() {
            // Создаём маршрут от имени другого пользователя
            val route = createTestRoute("/api/products", otherDeveloperUser.id!!)

            // Текущий пользователь запрашивает детали
            webTestClient.get()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.createdBy").isEqualTo(otherDeveloperUser.id.toString())
                .jsonPath("$.creatorUsername").isEqualTo("otherdev")
        }

        @Test
        fun `возвращает null creatorUsername для маршрута с удалённым создателем`() {
            // Создаём маршрут с несуществующим created_by
            val orphanRoute = Route(
                path = "/api/orphan",
                upstreamUrl = "http://test-service:8080",
                methods = listOf("GET"),
                description = "Orphan route",
                status = RouteStatus.DRAFT,
                createdBy = UUID.randomUUID(), // Несуществующий пользователь
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            var savedRoute: Route? = null
            StepVerifier.create(routeRepository.save(orphanRoute))
                .consumeNextWith { savedRoute = it }
                .verifyComplete()

            webTestClient.get()
                .uri("/api/v1/routes/${savedRoute!!.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.creatorUsername").doesNotExist()
        }

        @Test
        fun `возвращает все поля маршрута со статусом published`() {
            val route = createTestRoute("/api/public", developerUser.id!!, RouteStatus.PUBLISHED)

            webTestClient.get()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.status").isEqualTo("published")
                .jsonPath("$.creatorUsername").isEqualTo("developer")
        }
    }

    // ============================================
    // AC2: Маршрут не найден (404)
    // ============================================

    @Nested
    inner class AC2_RouteNotFound {

        @Test
        fun `возвращает 404 для несуществующего маршрута`() {
            val nonExistentId = UUID.randomUUID()

            webTestClient.get()
                .uri("/api/v1/routes/$nonExistentId")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/not-found")
                .jsonPath("$.title").isEqualTo("Not Found")
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.detail").isEqualTo("Route not found")
                .jsonPath("$.instance").isEqualTo("/api/v1/routes/$nonExistentId")
                .jsonPath("$.correlationId").exists()
        }

        @Test
        fun `возвращает 404 с RFC 7807 форматом`() {
            val nonExistentId = UUID.randomUUID()

            webTestClient.get()
                .uri("/api/v1/routes/$nonExistentId")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isNotFound
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.type").exists()
                .jsonPath("$.title").exists()
                .jsonPath("$.status").exists()
                .jsonPath("$.detail").exists()
                .jsonPath("$.instance").exists()
                .jsonPath("$.correlationId").exists()
        }
    }

    // ============================================
    // AC3: Клонирование маршрута
    // ============================================

    @Nested
    inner class AC3_CloneRoute {

        @Test
        fun `клонирует маршрут с суффиксом -copy`() {
            val original = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PUBLISHED)

            webTestClient.post()
                .uri("/api/v1/routes/${original.id}/clone")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/orders-copy")
                .jsonPath("$.upstreamUrl").isEqualTo(original.upstreamUrl)
                .jsonPath("$.methods").isArray
                .jsonPath("$.methods[0]").isEqualTo("GET")
                .jsonPath("$.methods[1]").isEqualTo("POST")
                .jsonPath("$.description").isEqualTo("Test route")
                .jsonPath("$.status").isEqualTo("draft")
                .jsonPath("$.createdBy").isEqualTo(developerUser.id.toString())
                .jsonPath("$.creatorUsername").isEqualTo("developer")
        }

        @Test
        fun `клонированный маршрут имеет status=draft`() {
            val original = createTestRoute("/api/products", developerUser.id!!, RouteStatus.PUBLISHED)

            webTestClient.post()
                .uri("/api/v1/routes/${original.id}/clone")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.status").isEqualTo("draft")
        }

        @Test
        fun `createdBy клонированного маршрута = текущий пользователь`() {
            // Маршрут создан другим пользователем
            val original = createTestRoute("/api/users", otherDeveloperUser.id!!)

            // Текущий пользователь клонирует
            webTestClient.post()
                .uri("/api/v1/routes/${original.id}/clone")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.createdBy").isEqualTo(developerUser.id.toString())
                .jsonPath("$.creatorUsername").isEqualTo("developer")
        }

        @Test
        fun `клонированный маршрут получает новый UUID`() {
            val original = createTestRoute("/api/inventory", developerUser.id!!)

            webTestClient.post()
                .uri("/api/v1/routes/${original.id}/clone")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").value<String> { clonedId ->
                    assertThat(clonedId).isNotEqualTo(original.id.toString())
                }
        }

        @Test
        fun `клонированный маршрут имеет новые timestamps`() {
            val original = createTestRouteWithTimestamp(
                "/api/legacy",
                developerUser.id!!,
                createdAt = Instant.parse("2020-01-01T00:00:00Z")
            )

            webTestClient.post()
                .uri("/api/v1/routes/${original.id}/clone")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.createdAt").value<String> { createdAt ->
                    val clonedTime = Instant.parse(createdAt)
                    assertThat(clonedTime).isAfter(Instant.parse("2020-01-01T00:00:00Z"))
                }
        }

        @Test
        fun `клонирует маршрут с null description`() {
            val original = createTestRoute("/api/no-desc", developerUser.id!!, description = null)

            webTestClient.post()
                .uri("/api/v1/routes/${original.id}/clone")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/no-desc-copy")
                .jsonPath("$.description").doesNotExist()
        }
    }

    // ============================================
    // AC4: Автоматическое разрешение конфликта path
    // ============================================

    @Nested
    inner class AC4_PathConflictResolution {

        @Test
        fun `клонирует с -copy-2 когда -copy уже существует`() {
            // Создаём оригинал и первую копию
            val original = createTestRoute("/api/orders", developerUser.id!!)
            createTestRoute("/api/orders-copy", developerUser.id!!)

            webTestClient.post()
                .uri("/api/v1/routes/${original.id}/clone")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/orders-copy-2")
        }

        @Test
        fun `клонирует с -copy-3 когда -copy и -copy-2 существуют`() {
            // Создаём оригинал и две копии
            val original = createTestRoute("/api/orders", developerUser.id!!)
            createTestRoute("/api/orders-copy", developerUser.id!!)
            createTestRoute("/api/orders-copy-2", developerUser.id!!)

            webTestClient.post()
                .uri("/api/v1/routes/${original.id}/clone")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/orders-copy-3")
        }

        @Test
        fun `находит максимальный номер при пропусках в нумерации`() {
            // Создаём оригинал и копии с пропусками: -copy, -copy-5
            val original = createTestRoute("/api/orders", developerUser.id!!)
            createTestRoute("/api/orders-copy", developerUser.id!!)
            createTestRoute("/api/orders-copy-5", developerUser.id!!)

            webTestClient.post()
                .uri("/api/v1/routes/${original.id}/clone")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/orders-copy-6")
        }

        @Test
        fun `не путает пути с похожим префиксом`() {
            // /api/orders существует, /api/orders-v2-copy тоже
            // клонирование /api/orders должно дать /api/orders-copy
            val orders = createTestRoute("/api/orders", developerUser.id!!)
            createTestRoute("/api/orders-v2", developerUser.id!!)
            createTestRoute("/api/orders-v2-copy", developerUser.id!!)

            webTestClient.post()
                .uri("/api/v1/routes/${orders.id}/clone")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/orders-copy")
        }

        @Test
        fun `многократное клонирование работает корректно`() {
            val original = createTestRoute("/api/users", developerUser.id!!)

            // Первое клонирование
            webTestClient.post()
                .uri("/api/v1/routes/${original.id}/clone")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/users-copy")

            // Второе клонирование
            webTestClient.post()
                .uri("/api/v1/routes/${original.id}/clone")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/users-copy-2")

            // Третье клонирование
            webTestClient.post()
                .uri("/api/v1/routes/${original.id}/clone")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/users-copy-3")
        }
    }

    // ============================================
    // AC5: Клонирование несуществующего маршрута
    // ============================================

    @Nested
    inner class AC5_CloneNonExistentRoute {

        @Test
        fun `возвращает 404 при клонировании несуществующего маршрута`() {
            val nonExistentId = UUID.randomUUID()

            webTestClient.post()
                .uri("/api/v1/routes/$nonExistentId/clone")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/not-found")
                .jsonPath("$.title").isEqualTo("Not Found")
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.detail").isEqualTo("Route not found")
                .jsonPath("$.correlationId").exists()
        }

        @Test
        fun `ошибка клонирования возвращается в RFC 7807 формате`() {
            val nonExistentId = UUID.randomUUID()

            webTestClient.post()
                .uri("/api/v1/routes/$nonExistentId/clone")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isNotFound
                .expectHeader().contentType("application/json")
                .expectBody()
                .jsonPath("$.type").exists()
                .jsonPath("$.title").exists()
                .jsonPath("$.status").exists()
                .jsonPath("$.detail").exists()
                .jsonPath("$.instance").isEqualTo("/api/v1/routes/$nonExistentId/clone")
        }
    }

    // ============================================
    // Аутентификация
    // ============================================

    @Nested
    inner class Authentication {

        @Test
        fun `возвращает 401 без аутентификации для GET details`() {
            val route = createTestRoute("/api/auth-test", developerUser.id!!)

            webTestClient.get()
                .uri("/api/v1/routes/${route.id}")
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        fun `возвращает 401 без аутентификации для clone`() {
            val route = createTestRoute("/api/auth-test", developerUser.id!!)

            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/clone")
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
        role: Role,
        isActive: Boolean = true
    ): User {
        val hashedPassword = passwordService.hash(password)
        val user = User(
            username = username,
            email = "$username@example.com",
            passwordHash = hashedPassword,
            role = role,
            isActive = isActive
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
        status: RouteStatus = RouteStatus.DRAFT,
        description: String? = "Test route"
    ): Route {
        return createTestRouteWithTimestamp(path, createdBy, status, description, Instant.now())
    }

    private fun createTestRouteWithTimestamp(
        path: String,
        createdBy: UUID,
        status: RouteStatus = RouteStatus.DRAFT,
        description: String? = "Test route",
        createdAt: Instant
    ): Route {
        val route = Route(
            path = path,
            upstreamUrl = "http://test-service:8080",
            methods = listOf("GET", "POST"),
            description = description,
            status = status,
            createdBy = createdBy,
            createdAt = createdAt,
            updatedAt = createdAt
        )
        var savedRoute: Route? = null
        StepVerifier.create(routeRepository.save(route))
            .consumeNextWith { savedRoute = it }
            .verifyComplete()
        return savedRoute!!
    }
}
