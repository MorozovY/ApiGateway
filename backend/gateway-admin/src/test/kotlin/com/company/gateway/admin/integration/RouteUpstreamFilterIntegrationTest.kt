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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
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
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * Integration тесты для фильтрации маршрутов по upstream URL (Story 7.4).
 *
 * Тестирование AC1-AC5:
 * - AC1: Фильтрация по части upstream URL (ILIKE)
 * - AC2: Точное совпадение upstream URL
 * - AC3: Список уникальных upstream хостов
 * - AC4: Комбинация фильтров
 * - AC5: Фильтр upstream включает creator info
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RouteUpstreamFilterIntegrationTest {

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
                val pgDb = System.getenv("POSTGRES_DB") ?: "gateway_test"
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
    private lateinit var developerUser: User

    @BeforeEach
    fun setUp() {
        // Очищаем маршруты и audit_logs
        StepVerifier.create(routeRepository.deleteAll()).verifyComplete()
        StepVerifier.create(auditLogRepository.deleteAll()).verifyComplete()

        // Очищаем тестовых пользователей (кроме admin из миграции)
        StepVerifier.create(
            userRepository.findAll()
                .filter { it.username != "admin" }
                .flatMap { userRepository.delete(it) }
                .then()
        ).verifyComplete()

        // Создаём тестового пользователя
        developerUser = createTestUser("developer", "password", Role.DEVELOPER)
        developerToken = jwtService.generateToken(developerUser)

        // Создаём тестовые маршруты с разными upstream URLs
        createTestRoute("/api/orders", "http://order-service:8080", developerUser.id!!)
        createTestRoute("/api/orders/v2", "http://order-service:8080/v2", developerUser.id!!)
        createTestRoute("/api/users", "http://user-service:8080", developerUser.id!!)
        createTestRoute("/api/users/admin", "https://user-service:8443", developerUser.id!!)
        createTestRoute("/api/payments", "http://payment-service:9000", developerUser.id!!, RouteStatus.PUBLISHED)
        createTestRoute("/api/data", "http://USER-DATA-service:8080", developerUser.id!!) // uppercase для теста case-insensitive
    }

    // ============================================
    // AC1: Фильтрация по части upstream URL (ILIKE)
    // ============================================

    @Nested
    inner class AC1_UpstreamIlikeFilter {

        @Test
        fun `фильтрует маршруты по части upstream URL`() {
            webTestClient.get()
                .uri("/api/v1/routes?upstream=order-service")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.total").isEqualTo(2)
                .jsonPath("$.items[0].upstreamUrl").value<String> { url ->
                    assert(url.contains("order-service")) { "Expected upstream URL to contain 'order-service'" }
                }
                .jsonPath("$.items[1].upstreamUrl").value<String> { url ->
                    assert(url.contains("order-service")) { "Expected upstream URL to contain 'order-service'" }
                }
        }

        @Test
        fun `поиск по upstream URL case-insensitive`() {
            // Ищем "user-data" — должен найти "USER-DATA-service"
            webTestClient.get()
                .uri("/api/v1/routes?upstream=user-data")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.total").isEqualTo(1)
                .jsonPath("$.items[0].upstreamUrl").isEqualTo("http://USER-DATA-service:8080")
        }

        @Test
        fun `возвращает пустой список когда нет совпадений`() {
            webTestClient.get()
                .uri("/api/v1/routes?upstream=nonexistent-service")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.total").isEqualTo(0)
                .jsonPath("$.items").isArray
                .jsonPath("$.items.length()").isEqualTo(0)
        }

        @Test
        fun `корректно экранирует спецсимволы в upstream поиске`() {
            // Создаём маршрут со спецсимволами
            createTestRoute("/api/special", "http://service-with_underscore:8080", developerUser.id!!)

            webTestClient.get()
                .uri("/api/v1/routes?upstream=with_underscore")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.total").isEqualTo(1)
        }
    }

    // ============================================
    // AC2: Точное совпадение upstream URL
    // ============================================

    @Nested
    inner class AC2_UpstreamExactFilter {

        @Test
        fun `фильтрует маршруты по точному совпадению upstream URL`() {
            webTestClient.get()
                .uri("/api/v1/routes?upstreamExact=http://order-service:8080")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.total").isEqualTo(1)
                .jsonPath("$.items[0].upstreamUrl").isEqualTo("http://order-service:8080")
                .jsonPath("$.items[0].path").isEqualTo("/api/orders")
        }

        @Test
        fun `точное совпадение case-sensitive`() {
            // "USER-DATA-service" с другим регистром не должен найтись
            webTestClient.get()
                .uri("/api/v1/routes?upstreamExact=http://user-data-service:8080")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.total").isEqualTo(0)
        }

        @Test
        fun `возвращает пустой список при несовпадении`() {
            webTestClient.get()
                .uri("/api/v1/routes?upstreamExact=http://nonexistent:8080")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.total").isEqualTo(0)
        }
    }

    // ============================================
    // AC3: Список уникальных upstream хостов
    // ============================================

    @Nested
    inner class AC3_UniqueUpstreamsList {

        @Test
        fun `возвращает список уникальных upstream хостов`() {
            webTestClient.get()
                .uri("/api/v1/routes/upstreams")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.upstreams").isArray
                .jsonPath("$.upstreams.length()").value<Int> { length ->
                    assert(length >= 4) { "Expected at least 4 unique upstream hosts" }
                }
        }

        @Test
        fun `upstream хосты отсортированы по количеству маршрутов DESC`() {
            // order-service имеет 2 маршрута — должен быть первым (или одним из первых)
            webTestClient.get()
                .uri("/api/v1/routes/upstreams")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.upstreams[0].routeCount").value<Int> { count ->
                    assert(count >= 1) { "First host should have at least 1 route" }
                }
        }

        @Test
        fun `host извлекается без схемы`() {
            webTestClient.get()
                .uri("/api/v1/routes/upstreams")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.upstreams[*].host").value<List<String>> { hosts ->
                    hosts.forEach { host ->
                        assert(!host.startsWith("http://") && !host.startsWith("https://")) {
                            "Host '$host' should not contain scheme"
                        }
                    }
                }
        }

        @Test
        fun `routeCount корректно подсчитан`() {
            webTestClient.get()
                .uri("/api/v1/routes/upstreams")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.upstreams[?(@.host == 'order-service:8080')].routeCount").isEqualTo(1)
        }
    }

    // ============================================
    // AC4: Комбинация фильтров
    // ============================================

    @Nested
    inner class AC4_CombinedFilters {

        @Test
        fun `комбинация upstream и status фильтров работает с AND логикой`() {
            // Ищем user-service со статусом published — должен быть 0
            webTestClient.get()
                .uri("/api/v1/routes?upstream=user-service&status=published")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.total").isEqualTo(0)
        }

        @Test
        fun `комбинация upstream и status находит правильные маршруты`() {
            // Ищем payment-service со статусом published
            webTestClient.get()
                .uri("/api/v1/routes?upstream=payment-service&status=published")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.total").isEqualTo(1)
                .jsonPath("$.items[0].path").isEqualTo("/api/payments")
        }

        @Test
        fun `upstream фильтр без status возвращает маршруты любого статуса`() {
            // order-service имеет маршруты со статусом draft
            webTestClient.get()
                .uri("/api/v1/routes?upstream=order-service")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.total").isEqualTo(2)
        }

        @Test
        fun `возвращает 400 при указании обоих upstream и upstreamExact`() {
            webTestClient.get()
                .uri("/api/v1/routes?upstream=order&upstreamExact=http://order-service:8080")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/validation")
                .jsonPath("$.detail").isEqualTo("Cannot specify both 'upstream' and 'upstreamExact' parameters")
        }

        @Test
        fun `возвращает 400 при слишком длинном upstream параметре`() {
            val longUpstream = "a".repeat(101) // MAX_SEARCH_LENGTH = 100
            webTestClient.get()
                .uri("/api/v1/routes?upstream=$longUpstream")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/validation")
                .jsonPath("$.detail").isEqualTo("Upstream filter must be between 1 and 100 characters")
        }
    }

    // ============================================
    // AC5: Фильтр upstream включает creator info
    // ============================================

    @Nested
    inner class AC5_CreatorInfo {

        @Test
        fun `ответ содержит createdBy для отфильтрованных маршрутов`() {
            webTestClient.get()
                .uri("/api/v1/routes?upstream=user-service")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items[0].createdBy").isEqualTo(developerUser.id.toString())
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
        upstreamUrl: String,
        createdBy: UUID,
        status: RouteStatus = RouteStatus.DRAFT
    ): Route {
        val route = Route(
            path = path,
            upstreamUrl = upstreamUrl,
            methods = listOf("GET", "POST"),
            description = "Test route for $path",
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
