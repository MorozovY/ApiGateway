package com.company.gateway.admin.integration

import com.company.gateway.admin.dto.CreateRouteRequest
import com.company.gateway.admin.dto.UpdateRouteRequest
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
import org.springframework.http.MediaType
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
 * Integration тесты для RouteController (Story 3.1).
 *
 * Тестирование AC1-AC7:
 * - AC1: Создание маршрута
 * - AC2: Обновление своего draft маршрута
 * - AC3: Запрет обновления не-draft маршрута
 * - AC4: Удаление своего draft маршрута
 * - AC5: Запрет удаления не-draft маршрута
 * - AC6: Валидация входных данных
 * - AC7: Проверка уникальности path
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class RouteControllerIntegrationTest {

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
    private lateinit var adminToken: String
    private lateinit var securityToken: String
    private lateinit var developerUser: User
    private lateinit var otherDeveloperUser: User
    private lateinit var adminUser: User

    @BeforeEach
    fun setUp() {
        // Очищаем маршруты и audit_logs (FK: users → audit_logs с RESTRICT, V5_1 миграция)
        StepVerifier.create(routeRepository.deleteAll()).verifyComplete()
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
        adminUser = createTestUser("testadmin", "password", Role.ADMIN)
        val securityUser = createTestUser("security", "password", Role.SECURITY)

        developerToken = jwtService.generateToken(developerUser)
        otherDeveloperToken = jwtService.generateToken(otherDeveloperUser)
        adminToken = jwtService.generateToken(adminUser)
        securityToken = jwtService.generateToken(securityUser)
    }

    // ============================================
    // AC1: Создание маршрута
    // ============================================

    @Nested
    inner class AC1_CreateRoute {

        @Test
        fun `создаёт маршрут и возвращает 201`() {
            val request = CreateRouteRequest(
                path = "/api/orders",
                upstreamUrl = "http://order-service:8080",
                methods = listOf("GET", "POST"),
                description = "Order service endpoints"
            )

            webTestClient.post()
                .uri("/api/v1/routes")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").isNotEmpty
                .jsonPath("$.path").isEqualTo("/api/orders")
                .jsonPath("$.upstreamUrl").isEqualTo("http://order-service:8080")
                .jsonPath("$.methods[0]").isEqualTo("GET")
                .jsonPath("$.methods[1]").isEqualTo("POST")
                .jsonPath("$.description").isEqualTo("Order service endpoints")
                .jsonPath("$.status").isEqualTo("draft")
                .jsonPath("$.createdBy").isEqualTo(developerUser.id.toString())
                .jsonPath("$.createdAt").isNotEmpty
        }

        @Test
        fun `создаёт маршрут без description`() {
            val request = CreateRouteRequest(
                path = "/api/products",
                upstreamUrl = "http://product-service:8080",
                methods = listOf("GET")
            )

            webTestClient.post()
                .uri("/api/v1/routes")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.description").doesNotExist()
        }
    }

    // ============================================
    // AC2: Обновление своего draft маршрута
    // ============================================

    @Nested
    inner class AC2_UpdateOwnDraftRoute {

        @Test
        fun `обновляет свой draft маршрут`() {
            val route = createTestRoute("/api/orders", developerUser.id!!)

            val request = UpdateRouteRequest(
                description = "Updated description"
            )

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(route.id.toString())
                .jsonPath("$.description").isEqualTo("Updated description")
                .jsonPath("$.updatedAt").isNotEmpty
        }

        @Test
        fun `обновляет path маршрута`() {
            val route = createTestRoute("/api/orders", developerUser.id!!)

            val request = UpdateRouteRequest(
                path = "/api/orders/v2"
            )

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/orders/v2")
        }

        @Test
        fun `admin может обновлять чужой draft маршрут`() {
            val route = createTestRoute("/api/orders", developerUser.id!!)

            val request = UpdateRouteRequest(
                description = "Admin updated"
            )

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.description").isEqualTo("Admin updated")
        }
    }

    // ============================================
    // AC3: Запрет обновления не-draft маршрута
    // ============================================

    @Nested
    inner class AC3_CannotUpdateNonDraftRoute {

        @Test
        fun `возвращает 409 при обновлении published маршрута`() {
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PUBLISHED)

            val request = UpdateRouteRequest(
                description = "Try to update"
            )

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/conflict")
                .jsonPath("$.status").isEqualTo(409)
                .jsonPath("$.detail").isEqualTo("Cannot edit route in current status")
        }

        @Test
        fun `возвращает 409 при обновлении pending маршрута`() {
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

            val request = UpdateRouteRequest(
                description = "Try to update"
            )

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(409)
        }

        @Test
        fun `возвращает 409 при обновлении rejected маршрута`() {
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.REJECTED)

            val request = UpdateRouteRequest(
                description = "Try to update rejected"
            )

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Cannot edit route in current status")
        }

        @Test
        fun `admin получает 409 при обновлении published маршрута`() {
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PUBLISHED)

            val request = UpdateRouteRequest(
                description = "Admin try to update published"
            )

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Cannot edit route in current status")
        }
    }

    // ============================================
    // AC4: Удаление своего draft маршрута
    // ============================================

    @Nested
    inner class AC4_DeleteOwnDraftRoute {

        @Test
        fun `удаляет свой draft маршрут и возвращает 204`() {
            val route = createTestRoute("/api/orders", developerUser.id!!)

            webTestClient.delete()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isNoContent

            // Проверяем, что маршрут удалён
            StepVerifier.create(routeRepository.findById(route.id!!))
                .verifyComplete()
        }

        @Test
        fun `admin может удалить чужой draft маршрут`() {
            val route = createTestRoute("/api/orders", developerUser.id!!)

            webTestClient.delete()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isNoContent
        }
    }

    // ============================================
    // AC5: Запрет удаления не-draft маршрута
    // ============================================

    @Nested
    inner class AC5_CannotDeleteNonDraftRoute {

        @Test
        fun `возвращает 409 при удалении published маршрута`() {
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PUBLISHED)

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
        fun `возвращает 409 при удалении pending маршрута`() {
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

            webTestClient.delete()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isEqualTo(409)
        }

        @Test
        fun `возвращает 409 при удалении rejected маршрута`() {
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.REJECTED)

            webTestClient.delete()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Only draft routes can be deleted")
        }
    }

    // ============================================
    // AC6: Валидация входных данных
    // ============================================

    @Nested
    inner class AC6_InputValidation {

        @Test
        fun `возвращает 400 когда path не начинается со слеша`() {
            val request = mapOf(
                "path" to "api/orders",
                "upstreamUrl" to "http://order-service:8080",
                "methods" to listOf("GET")
            )

            webTestClient.post()
                .uri("/api/v1/routes")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/validation-failed")
                .jsonPath("$.status").isEqualTo(400)
        }

        @Test
        fun `возвращает 400 когда upstreamUrl невалиден`() {
            val request = mapOf(
                "path" to "/api/orders",
                "upstreamUrl" to "not-a-url",
                "methods" to listOf("GET")
            )

            webTestClient.post()
                .uri("/api/v1/routes")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `возвращает 400 когда methods пуст`() {
            val request = mapOf(
                "path" to "/api/orders",
                "upstreamUrl" to "http://order-service:8080",
                "methods" to emptyList<String>()
            )

            webTestClient.post()
                .uri("/api/v1/routes")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `возвращает 400 когда path отсутствует`() {
            val request = mapOf(
                "upstreamUrl" to "http://order-service:8080",
                "methods" to listOf("GET")
            )

            webTestClient.post()
                .uri("/api/v1/routes")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `возвращает 400 когда description превышает 1000 символов`() {
            val longDescription = "a".repeat(1001)
            val request = mapOf(
                "path" to "/api/orders",
                "upstreamUrl" to "http://order-service:8080",
                "methods" to listOf("GET"),
                "description" to longDescription
            )

            webTestClient.post()
                .uri("/api/v1/routes")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `возвращает 400 когда method невалиден`() {
            val request = mapOf(
                "path" to "/api/orders",
                "upstreamUrl" to "http://order-service:8080",
                "methods" to listOf("INVALID")
            )

            webTestClient.post()
                .uri("/api/v1/routes")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `возвращает 400 когда path содержит недопустимые символы`() {
            val request = mapOf(
                "path" to "/api/orders?query=1",
                "upstreamUrl" to "http://order-service:8080",
                "methods" to listOf("GET")
            )

            webTestClient.post()
                .uri("/api/v1/routes")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `возвращает 400 когда path содержит пробелы`() {
            val request = mapOf(
                "path" to "/api/orders with space",
                "upstreamUrl" to "http://order-service:8080",
                "methods" to listOf("GET")
            )

            webTestClient.post()
                .uri("/api/v1/routes")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    // ============================================
    // AC7: Проверка уникальности path
    // ============================================

    @Nested
    inner class AC7_PathUniqueness {

        @Test
        fun `возвращает 409 при создании маршрута с существующим path`() {
            // Создаём первый маршрут
            createTestRoute("/api/orders", developerUser.id!!)

            // Пытаемся создать второй с тем же path
            val request = CreateRouteRequest(
                path = "/api/orders",
                upstreamUrl = "http://other-service:8080",
                methods = listOf("GET")
            )

            webTestClient.post()
                .uri("/api/v1/routes")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/conflict")
                .jsonPath("$.detail").isEqualTo("Route with this path already exists")
        }

        @Test
        fun `возвращает 409 при обновлении path на существующий`() {
            // Создаём два маршрута
            createTestRoute("/api/orders", developerUser.id!!)
            val route2 = createTestRoute("/api/products", developerUser.id!!)

            // Пытаемся изменить path второго на path первого
            val request = UpdateRouteRequest(path = "/api/orders")

            webTestClient.put()
                .uri("/api/v1/routes/${route2.id}")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Route with this path already exists")
        }
    }

    // ============================================
    // Ownership проверки
    // ============================================

    @Nested
    inner class OwnershipChecks {

        @Test
        fun `developer не может обновить чужой маршрут`() {
            val route = createTestRoute("/api/orders", otherDeveloperUser.id!!)

            val request = UpdateRouteRequest(
                description = "Try to update"
            )

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/forbidden")
                .jsonPath("$.detail").isEqualTo("You can only modify your own routes")
        }

        @Test
        fun `developer не может удалить чужой маршрут`() {
            val route = createTestRoute("/api/orders", otherDeveloperUser.id!!)

            webTestClient.delete()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun `security может обновить чужой draft маршрут`() {
            val route = createTestRoute("/api/orders", developerUser.id!!)

            val request = UpdateRouteRequest(
                description = "Security updated"
            )

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", securityToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
        }
    }

    // ============================================
    // GET маршрута по ID
    // ============================================

    @Nested
    inner class GetRouteById {

        @Test
        fun `возвращает маршрут по ID`() {
            val route = createTestRoute("/api/orders", developerUser.id!!)

            webTestClient.get()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(route.id.toString())
                .jsonPath("$.path").isEqualTo("/api/orders")
        }

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
                .jsonPath("$.detail").isEqualTo("Route not found")
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
        status: RouteStatus = RouteStatus.DRAFT
    ): Route {
        val route = Route(
            path = path,
            upstreamUrl = "http://test-service:8080",
            methods = listOf("GET", "POST"),
            description = "Test route",
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
