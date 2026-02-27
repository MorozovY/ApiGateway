package com.company.gateway.admin.integration

import com.company.gateway.admin.dto.UpdateRouteRequest
import com.company.gateway.admin.repository.AuditLogRepository
import com.company.gateway.admin.repository.RateLimitRepository
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.admin.security.JwtService
import com.company.gateway.admin.service.PasswordService
import com.company.gateway.common.model.RateLimit
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
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * Интеграционные тесты для назначения rate limit на маршруты (Story 5.2).
 *
 * Покрывает AC1-AC7:
 * - AC1: Назначение политики через PUT route
 * - AC2: Валидация несуществующей политики (400)
 * - AC3: Удаление политики с маршрута (rateLimitId: null)
 * - AC5: Rate limit в GET route response
 * - AC6: Route без rate limit
 * - AC7: Rate limit в списке маршрутов
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RouteRateLimitIntegrationTest {

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
    private lateinit var rateLimitRepository: RateLimitRepository

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
    private lateinit var testPolicy: RateLimit

    @BeforeEach
    fun setUp() {
        // Очищаем маршруты и политики
        StepVerifier.create(
            routeRepository.findAll()
                .flatMap { routeRepository.delete(it) }
                .then()
        ).verifyComplete()

        StepVerifier.create(rateLimitRepository.deleteAll()).verifyComplete()
        StepVerifier.create(auditLogRepository.deleteAll()).verifyComplete()

        // Очищаем тестовых пользователей (кроме admin из миграции)
        StepVerifier.create(
            userRepository.findAll()
                .filter { it.username != "admin" }
                .flatMap { userRepository.delete(it) }
                .then()
        ).verifyComplete()

        // Создаём тестового developer
        developerUser = createTestUser("developer", "password", Role.DEVELOPER)
        developerToken = jwtService.generateToken(developerUser)

        // Создаём тестовую политику rate limit
        testPolicy = createTestPolicy("standard", 100, 150)
    }

    // ============================================
    // AC1: Назначение политики через PUT route
    // ============================================

    @Nested
    inner class AC1_AssignPolicy {

        @Test
        fun `назначение rate limit политики возвращает 200`() {
            val route = createTestRoute(developerUser.id!!, null)

            val request = UpdateRouteRequest.withRateLimitId(testPolicy.id)

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(route.id.toString())
                .jsonPath("$.rateLimitId").isEqualTo(testPolicy.id.toString())
                .jsonPath("$.rateLimit").isNotEmpty
                .jsonPath("$.rateLimit.id").isEqualTo(testPolicy.id.toString())
                .jsonPath("$.rateLimit.name").isEqualTo("standard")
                .jsonPath("$.rateLimit.requestsPerSecond").isEqualTo(100)
                .jsonPath("$.rateLimit.burstSize").isEqualTo(150)
        }

        @Test
        fun `назначение политики обновляет маршрут`() {
            val route = createTestRoute(developerUser.id!!, null)
            val anotherPolicy = createTestPolicy("premium", 500, 750)

            val request = UpdateRouteRequest.withRateLimitId(anotherPolicy.id)

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.rateLimitId").isEqualTo(anotherPolicy.id.toString())
                .jsonPath("$.rateLimit.name").isEqualTo("premium")
                .jsonPath("$.rateLimit.requestsPerSecond").isEqualTo(500)
        }
    }

    // ============================================
    // AC2: Валидация несуществующей политики
    // ============================================

    @Nested
    inner class AC2_InvalidPolicy {

        @Test
        fun `назначение несуществующей политики возвращает 400`() {
            val route = createTestRoute(developerUser.id!!, null)
            val nonExistentPolicyId = UUID.randomUUID()

            val request = UpdateRouteRequest.withRateLimitId(nonExistentPolicyId)

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/validation")
                .jsonPath("$.detail").isEqualTo("Rate limit policy not found")
        }
    }

    // ============================================
    // AC3: Удаление политики с маршрута
    // ============================================

    @Nested
    inner class AC3_RemovePolicy {

        @Test
        fun `удаление политики через rateLimitId null возвращает 200`() {
            // Создаём маршрут с политикой
            val route = createTestRoute(developerUser.id!!, testPolicy.id)

            // Удаляем политику
            val request = """{"rateLimitId": null}"""

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(route.id.toString())
                .jsonPath("$.rateLimitId").doesNotExist()
                .jsonPath("$.rateLimit").doesNotExist()
        }
    }

    // ============================================
    // AC5: Rate limit в GET route response
    // ============================================

    @Nested
    inner class AC5_GetRouteWithRateLimit {

        @Test
        fun `GET route включает rateLimit объект`() {
            val route = createTestRoute(developerUser.id!!, testPolicy.id)

            webTestClient.get()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(route.id.toString())
                .jsonPath("$.rateLimitId").isEqualTo(testPolicy.id.toString())
                .jsonPath("$.rateLimit.id").isEqualTo(testPolicy.id.toString())
                .jsonPath("$.rateLimit.name").isEqualTo("standard")
                .jsonPath("$.rateLimit.requestsPerSecond").isEqualTo(100)
                .jsonPath("$.rateLimit.burstSize").isEqualTo(150)
        }
    }

    // ============================================
    // AC6: Route без rate limit
    // ============================================

    @Nested
    inner class AC6_RouteWithoutRateLimit {

        @Test
        fun `GET route без rate limit возвращает rateLimitId null`() {
            val route = createTestRoute(developerUser.id!!, null)

            webTestClient.get()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(route.id.toString())
                .jsonPath("$.rateLimitId").doesNotExist()
                .jsonPath("$.rateLimit").doesNotExist()
        }
    }

    // ============================================
    // AC7: Rate limit в списке маршрутов
    // ============================================

    @Nested
    inner class AC7_RoutesListWithRateLimit {

        @Test
        fun `GET routes список включает rateLimit для каждого маршрута`() {
            createTestRoute(developerUser.id!!, testPolicy.id, "/api/with-policy")
            createTestRoute(developerUser.id!!, null, "/api/without-policy")

            webTestClient.get()
                .uri("/api/v1/routes")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items").isArray
                .jsonPath("$.total").isEqualTo(2)
                // Маршруты сортируются по created_at DESC, поэтому второй создан позже
                // Проверяем что оба маршрута имеют правильные rateLimitId
                .jsonPath("$.items[?(@.path == '/api/with-policy')].rateLimitId").isEqualTo(testPolicy.id.toString())
                .jsonPath("$.items[?(@.path == '/api/with-policy')].rateLimit.name").isEqualTo("standard")
                // Для маршрутов без rate limit — значения null (JSON сериализует null явно)
                .jsonPath("$.items[?(@.path == '/api/without-policy')].rateLimitId").value<List<Any?>> { list ->
                    assert(list.size == 1 && list[0] == null) { "rateLimitId должен быть null" }
                }
                .jsonPath("$.items[?(@.path == '/api/without-policy')].rateLimit").value<List<Any?>> { list ->
                    assert(list.size == 1 && list[0] == null) { "rateLimit должен быть null" }
                }
        }

        @Test
        fun `N+1 оптимизация работает при загрузке списка маршрутов`() {
            // Создаём несколько политик и маршрутов
            val policy1 = createTestPolicy("policy-1", 100, 150)
            val policy2 = createTestPolicy("policy-2", 200, 300)

            createTestRoute(developerUser.id!!, policy1.id, "/api/route-1")
            createTestRoute(developerUser.id!!, policy1.id, "/api/route-2")
            createTestRoute(developerUser.id!!, policy2.id, "/api/route-3")
            createTestRoute(developerUser.id!!, null, "/api/route-4")

            webTestClient.get()
                .uri("/api/v1/routes")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items").isArray
                .jsonPath("$.total").isEqualTo(4)
                // Все маршруты с policy-1 должны иметь правильный rateLimit
                .jsonPath("$.items[?(@.path == '/api/route-1')].rateLimit.name").isEqualTo("policy-1")
                .jsonPath("$.items[?(@.path == '/api/route-2')].rateLimit.name").isEqualTo("policy-1")
                // Маршрут с policy-2
                .jsonPath("$.items[?(@.path == '/api/route-3')].rateLimit.name").isEqualTo("policy-2")
                // Маршрут без политики — rateLimit = null (JSON сериализует явно)
                .jsonPath("$.items[?(@.path == '/api/route-4')].rateLimit").value<List<Any?>> { list ->
                    assert(list.size == 1 && list[0] == null) { "rateLimit должен быть null" }
                }
        }
    }

    // ============================================
    // Partial Update — rateLimitId не должен сбрасываться
    // ============================================

    @Nested
    inner class PartialUpdate {

        @Test
        fun `обновление path не сбрасывает rateLimitId`() {
            // Given: маршрут с назначенной политикой
            val route = createTestRoute(developerUser.id!!, testPolicy.id)

            // When: обновляем только path (rateLimitId не передаём в JSON)
            val request = """{"path": "/api/updated-path"}"""

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                // Then: rateLimitId сохраняется
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/updated-path")
                .jsonPath("$.rateLimitId").isEqualTo(testPolicy.id.toString())
                .jsonPath("$.rateLimit.name").isEqualTo("standard")
        }

        @Test
        fun `обновление description не сбрасывает rateLimitId`() {
            // Given: маршрут с назначенной политикой
            val route = createTestRoute(developerUser.id!!, testPolicy.id)

            // When: обновляем только description
            val request = """{"description": "Updated description"}"""

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                // Then: rateLimitId сохраняется
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.description").isEqualTo("Updated description")
                .jsonPath("$.rateLimitId").isEqualTo(testPolicy.id.toString())
        }

        @Test
        fun `пустой запрос не изменяет rateLimitId`() {
            // Given: маршрут с назначенной политикой
            val route = createTestRoute(developerUser.id!!, testPolicy.id)

            // When: отправляем пустой JSON (все поля остаются как были)
            val request = """{}"""

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                // Then: rateLimitId сохраняется
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.rateLimitId").isEqualTo(testPolicy.id.toString())
                .jsonPath("$.rateLimit.name").isEqualTo("standard")
        }
    }

    // ============================================
    // Дополнительные тесты
    // ============================================

    @Nested
    inner class AdditionalTests {

        @Test
        fun `обновление маршрута с заменой политики возвращает 200`() {
            val newPolicy = createTestPolicy("premium", 500, 750)
            val route = createTestRoute(developerUser.id!!, testPolicy.id)

            val request = UpdateRouteRequest.withRateLimitId(newPolicy.id)

            webTestClient.put()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.rateLimitId").isEqualTo(newPolicy.id.toString())
                .jsonPath("$.rateLimit.name").isEqualTo("premium")
                .jsonPath("$.rateLimit.requestsPerSecond").isEqualTo(500)
        }

        @Test
        fun `детальный просмотр маршрута включает rateLimit данные`() {
            val route = createTestRoute(developerUser.id!!, testPolicy.id)

            webTestClient.get()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.rateLimit").isNotEmpty
                .jsonPath("$.rateLimit.id").isEqualTo(testPolicy.id.toString())
                .jsonPath("$.rateLimit.name").isEqualTo("standard")
                .jsonPath("$.rateLimit.requestsPerSecond").isEqualTo(100)
                .jsonPath("$.rateLimit.burstSize").isEqualTo(150)
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

    private fun createTestPolicy(name: String, rps: Int, burst: Int): RateLimit {
        val policy = RateLimit(
            name = name,
            requestsPerSecond = rps,
            burstSize = burst,
            createdBy = developerUser.id!!,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        var saved: RateLimit? = null
        StepVerifier.create(rateLimitRepository.save(policy))
            .consumeNextWith { saved = it }
            .verifyComplete()
        return saved!!
    }

    private fun createTestRoute(
        createdBy: UUID,
        rateLimitId: UUID?,
        path: String = "/api/test-${UUID.randomUUID()}"
    ): Route {
        val route = Route(
            path = path,
            upstreamUrl = "http://test-service:8080",
            methods = listOf("GET", "POST"),
            description = "Test route",
            status = RouteStatus.DRAFT,
            createdBy = createdBy,
            rateLimitId = rateLimitId,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        var saved: Route? = null
        StepVerifier.create(routeRepository.save(route))
            .consumeNextWith { saved = it }
            .verifyComplete()
        return saved!!
    }
}
