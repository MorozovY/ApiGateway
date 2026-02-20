package com.company.gateway.admin.integration

import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.admin.security.JwtService
import com.company.gateway.admin.service.MetricsService
import com.company.gateway.admin.service.PasswordService
import com.company.gateway.common.model.Role
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import com.company.gateway.common.model.User
import io.micrometer.core.instrument.MeterRegistry
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
import java.util.UUID

/**
 * Интеграционные тесты для MetricsController (Story 6.3).
 *
 * Покрывает AC1-AC5:
 * - AC1: endpoint /metrics/summary возвращает 200
 * - AC2: endpoint /metrics/summary с period параметром
 * - AC3: endpoint /metrics/routes/{id} возвращает 200
 * - AC4: endpoint /metrics/top-routes возвращает 200
 * - AC5: 401 для неаутентифицированных запросов
 * - AC5: 404 для несуществующего routeId
 * - AC5: 400 для невалидного period
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class MetricsControllerIntegrationTest {

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
    private lateinit var passwordService: PasswordService

    @Autowired
    private lateinit var jwtService: JwtService

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    private lateinit var developerToken: String
    private lateinit var developerUser: User
    private lateinit var testRoute: Route

    @BeforeEach
    fun setUp() {
        // Очищаем маршруты
        StepVerifier.create(
            routeRepository.findAll()
                .flatMap { routeRepository.delete(it) }
                .then()
        ).verifyComplete()

        // Очищаем тестовых пользователей (кроме admin из миграции)
        StepVerifier.create(
            userRepository.findAll()
                .filter { it.username != "admin" }
                .flatMap { userRepository.delete(it) }
                .then()
        ).verifyComplete()

        // Очищаем метрики от предыдущих тестов
        meterRegistry.clear()

        // Создаём тестового пользователя
        developerUser = createTestUser("metrics_dev", "password", Role.DEVELOPER)

        // Создаём JWT токен
        developerToken = jwtService.generateToken(developerUser)

        // Создаём тестовый маршрут
        testRoute = createTestRoute("/api/test-metrics", developerUser.id!!)

        // Регистрируем тестовые метрики
        repeat(10) {
            meterRegistry.counter(
                MetricsService.METRIC_REQUESTS_TOTAL,
                MetricsService.TAG_ROUTE_ID, testRoute.id.toString(),
                MetricsService.TAG_STATUS, "2xx"
            ).increment()
        }
    }

    // ============================================
    // AC1: endpoint /metrics/summary возвращает 200
    // ============================================

    @Nested
    inner class AC1_MetricsSummary {

        @Test
        fun `GET метрики summary возвращает 200 и структуру данных`() {
            webTestClient.get()
                .uri("/api/v1/metrics/summary")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.period").isEqualTo("5m")
                .jsonPath("$.totalRequests").exists()
                .jsonPath("$.requestsPerSecond").exists()
                .jsonPath("$.avgLatencyMs").exists()
                .jsonPath("$.p95LatencyMs").exists()
                .jsonPath("$.p99LatencyMs").exists()
                .jsonPath("$.errorRate").exists()
                .jsonPath("$.errorCount").exists()
                .jsonPath("$.activeRoutes").exists()
        }

        @Test
        fun `GET метрики summary с period=1h возвращает 200`() {
            webTestClient.get()
                .uri("/api/v1/metrics/summary?period=1h")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.period").isEqualTo("1h")
        }

        @Test
        fun `GET метрики summary считает activeRoutes из published маршрутов`() {
            webTestClient.get()
                .uri("/api/v1/metrics/summary")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.activeRoutes").isEqualTo(1)
        }
    }

    // ============================================
    // AC2: поддержка параметра period
    // ============================================

    @Nested
    inner class AC2_PeriodParameter {

        @Test
        fun `GET метрики summary поддерживает period 5m`() {
            webTestClient.get()
                .uri("/api/v1/metrics/summary?period=5m")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.period").isEqualTo("5m")
        }

        @Test
        fun `GET метрики summary поддерживает period 15m`() {
            webTestClient.get()
                .uri("/api/v1/metrics/summary?period=15m")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.period").isEqualTo("15m")
        }

        @Test
        fun `GET метрики summary поддерживает period 6h`() {
            webTestClient.get()
                .uri("/api/v1/metrics/summary?period=6h")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.period").isEqualTo("6h")
        }

        @Test
        fun `GET метрики summary поддерживает period 24h`() {
            webTestClient.get()
                .uri("/api/v1/metrics/summary?period=24h")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.period").isEqualTo("24h")
        }
    }

    // ============================================
    // AC3: endpoint /metrics/routes/{id} возвращает 200
    // ============================================

    @Nested
    inner class AC3_RouteMetrics {

        @Test
        fun `GET метрики маршрута возвращает 200 и структуру данных`() {
            webTestClient.get()
                .uri("/api/v1/metrics/routes/${testRoute.id}")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.routeId").isEqualTo(testRoute.id.toString())
                .jsonPath("$.path").isEqualTo("/api/test-metrics")
                .jsonPath("$.period").isEqualTo("5m")
                .jsonPath("$.requestsPerSecond").exists()
                .jsonPath("$.avgLatencyMs").exists()
                .jsonPath("$.p95LatencyMs").exists()
                .jsonPath("$.errorRate").exists()
                .jsonPath("$.statusBreakdown").exists()
        }

        @Test
        fun `GET метрики маршрута с period параметром`() {
            webTestClient.get()
                .uri("/api/v1/metrics/routes/${testRoute.id}?period=1h")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.period").isEqualTo("1h")
        }
    }

    // ============================================
    // AC4: endpoint /metrics/top-routes возвращает 200
    // ============================================

    @Nested
    inner class AC4_TopRoutes {

        @Test
        fun `GET top-routes возвращает 200 и список маршрутов`() {
            webTestClient.get()
                .uri("/api/v1/metrics/top-routes")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$").isArray
        }

        @Test
        fun `GET top-routes поддерживает параметр by=requests`() {
            webTestClient.get()
                .uri("/api/v1/metrics/top-routes?by=requests")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `GET top-routes поддерживает параметр by=latency`() {
            webTestClient.get()
                .uri("/api/v1/metrics/top-routes?by=latency")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `GET top-routes поддерживает параметр by=errors`() {
            webTestClient.get()
                .uri("/api/v1/metrics/top-routes?by=errors")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `GET top-routes поддерживает параметр limit`() {
            webTestClient.get()
                .uri("/api/v1/metrics/top-routes?limit=5")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
        }
    }

    // ============================================
    // AC5: обработка ошибок
    // ============================================

    @Nested
    inner class AC5_ErrorHandling {

        @Test
        fun `GET метрики без аутентификации возвращает 401`() {
            webTestClient.get()
                .uri("/api/v1/metrics/summary")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        fun `GET метрики маршрута без аутентификации возвращает 401`() {
            webTestClient.get()
                .uri("/api/v1/metrics/routes/${testRoute.id}")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        fun `GET top-routes без аутентификации возвращает 401`() {
            webTestClient.get()
                .uri("/api/v1/metrics/top-routes")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        fun `GET метрики несуществующего маршрута возвращает 404`() {
            val nonExistentId = UUID.randomUUID()

            webTestClient.get()
                .uri("/api/v1/metrics/routes/$nonExistentId")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/not-found")
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.detail").exists()
        }

        @Test
        fun `GET метрики с невалидным period возвращает 400`() {
            webTestClient.get()
                .uri("/api/v1/metrics/summary?period=invalid")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/validation")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.detail").exists()
        }

        @Test
        fun `GET метрики с невалидным period 2h возвращает 400`() {
            webTestClient.get()
                .uri("/api/v1/metrics/summary?period=2h")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `GET top-routes с невалидным by возвращает 400`() {
            webTestClient.get()
                .uri("/api/v1/metrics/top-routes?by=invalid")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/validation")
                .jsonPath("$.status").isEqualTo(400)
        }
    }

    // ============================================
    // Дополнительные тесты для разных ролей
    // ============================================

    @Nested
    inner class Доступность_для_всех_ролей {

        @Test
        fun `developer может получить метрики`() {
            webTestClient.get()
                .uri("/api/v1/metrics/summary")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `admin может получить метрики`() {
            // Given: создаём admin пользователя
            val adminUser = createTestUser("metrics_admin", "password", Role.ADMIN)
            val adminToken = jwtService.generateToken(adminUser)

            // When & Then
            webTestClient.get()
                .uri("/api/v1/metrics/summary")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `security может получить метрики`() {
            // Given: создаём security пользователя
            val securityUser = createTestUser("metrics_security", "password", Role.SECURITY)
            val securityToken = jwtService.generateToken(securityUser)

            // When & Then
            webTestClient.get()
                .uri("/api/v1/metrics/summary")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `все роли могут получить метрики маршрута`() {
            // Given: создаём пользователей всех ролей
            val adminUser = createTestUser("route_metrics_admin", "password", Role.ADMIN)
            val securityUser = createTestUser("route_metrics_security", "password", Role.SECURITY)

            // When & Then: admin
            webTestClient.get()
                .uri("/api/v1/metrics/routes/${testRoute.id}")
                .cookie("auth_token", jwtService.generateToken(adminUser))
                .exchange()
                .expectStatus().isOk

            // When & Then: security
            webTestClient.get()
                .uri("/api/v1/metrics/routes/${testRoute.id}")
                .cookie("auth_token", jwtService.generateToken(securityUser))
                .exchange()
                .expectStatus().isOk

            // When & Then: developer
            webTestClient.get()
                .uri("/api/v1/metrics/routes/${testRoute.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `все роли могут получить top-routes`() {
            // Given: создаём admin пользователя
            val adminUser = createTestUser("top_routes_admin", "password", Role.ADMIN)

            // When & Then
            webTestClient.get()
                .uri("/api/v1/metrics/top-routes")
                .cookie("auth_token", jwtService.generateToken(adminUser))
                .exchange()
                .expectStatus().isOk
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

    private fun createTestRoute(path: String, createdBy: UUID): Route {
        val route = Route(
            path = path,
            upstreamUrl = "http://test-service:8080",
            methods = listOf("GET", "POST"),
            description = "Test route for metrics",
            status = RouteStatus.PUBLISHED,
            createdBy = createdBy
        )
        var savedRoute: Route? = null
        StepVerifier.create(routeRepository.save(route))
            .consumeNextWith { savedRoute = it }
            .verifyComplete()
        return savedRoute!!
    }
}
