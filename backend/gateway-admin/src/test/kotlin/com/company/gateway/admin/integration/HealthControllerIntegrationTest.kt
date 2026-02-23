package com.company.gateway.admin.integration

import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.admin.security.JwtService
import com.company.gateway.admin.service.PasswordService
import com.company.gateway.common.model.Role
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

/**
 * Интеграционные тесты для HealthController (Story 8.1, 10.5, 12.1).
 *
 * Проверяет AC1, AC2:
 * - AC1: endpoint /health/services возвращает статусы сервисов
 * - AC2: Отображение DOWN для недоступных сервисов
 *
 * Использует Testcontainers для PostgreSQL.
 * Redis отключён, поэтому в ответе Redis будет DOWN.
 * nginx, gateway-core, keycloak, prometheus, grafana не запущены, поэтому будут DOWN.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class HealthControllerIntegrationTest {

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
            // gateway-core URL (не запущен, будет DOWN)
            registry.add("gateway.core.url") { "http://localhost:58080" }  // порт недоступен
            // nginx URL (не запущен, будет DOWN) — Story 10.5
            registry.add("nginx.url") { "http://localhost:58081" }  // порт недоступен
            // prometheus URL (не запущен, будет DOWN) — фиксация для изоляции тестов
            registry.add("prometheus.url") { "http://localhost:58082" }  // порт недоступен
            // grafana URL (не запущена, будет DOWN) — фиксация для изоляции тестов
            registry.add("grafana.url") { "http://localhost:58083" }  // порт недоступен
            // keycloak URL (не запущен, будет DOWN) — Story 12.1
            registry.add("keycloak.url") { "http://localhost:58084" }  // порт недоступен
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordService: PasswordService

    @Autowired
    private lateinit var jwtService: JwtService

    private lateinit var developerToken: String

    @BeforeEach
    fun setUp() {
        // Очищаем тестовых пользователей (кроме admin из миграции)
        StepVerifier.create(
            userRepository.findAll()
                .filter { it.username != "admin" }
                .flatMap { userRepository.delete(it) }
                .then()
        ).verifyComplete()

        // Создаём тестового пользователя
        val developerUser = createTestUser("health_dev", "password", Role.DEVELOPER)
        developerToken = jwtService.generateToken(developerUser)
    }

    // ============================================
    // AC1: endpoint /health/services возвращает статусы
    // ============================================

    @Nested
    inner class AC1_HealthServicesEndpoint {

        @Test
        fun `GET health services возвращает 200 и все сервисы`() {
            // Backend возвращает 8 сервисов: nginx + 4 из AC (gateway-core, gateway-admin, postgresql, redis)
            // + 3 дополнительных (keycloak, prometheus, grafana)
            webTestClient.get()
                .uri("/api/v1/health/services")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.services").isArray
                .jsonPath("$.services.length()").isEqualTo(8)
                .jsonPath("$.timestamp").exists()
        }

        @Test
        fun `GET health services содержит nginx`() {
            webTestClient.get()
                .uri("/api/v1/health/services")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.services[?(@.name=='nginx')]").exists()
                .jsonPath("$.services[?(@.name=='nginx')].status").exists()
                .jsonPath("$.services[?(@.name=='nginx')].lastCheck").exists()
        }

        @Test
        fun `GET health services содержит gateway-core`() {
            webTestClient.get()
                .uri("/api/v1/health/services")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.services[?(@.name=='gateway-core')]").exists()
                .jsonPath("$.services[?(@.name=='gateway-core')].status").exists()
                .jsonPath("$.services[?(@.name=='gateway-core')].lastCheck").exists()
        }

        @Test
        fun `GET health services содержит gateway-admin UP`() {
            webTestClient.get()
                .uri("/api/v1/health/services")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.services[?(@.name=='gateway-admin')].status").isEqualTo("UP")
        }

        @Test
        fun `GET health services содержит postgresql UP`() {
            // PostgreSQL запущен через Testcontainers
            webTestClient.get()
                .uri("/api/v1/health/services")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.services[?(@.name=='postgresql')].status").isEqualTo("UP")
        }

        @Test
        fun `GET health services содержит redis`() {
            webTestClient.get()
                .uri("/api/v1/health/services")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.services[?(@.name=='redis')]").exists()
        }

        @Test
        fun `GET health services содержит prometheus`() {
            webTestClient.get()
                .uri("/api/v1/health/services")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.services[?(@.name=='prometheus')]").exists()
                .jsonPath("$.services[?(@.name=='prometheus')].status").exists()
        }

        @Test
        fun `GET health services содержит grafana`() {
            webTestClient.get()
                .uri("/api/v1/health/services")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.services[?(@.name=='grafana')]").exists()
                .jsonPath("$.services[?(@.name=='grafana')].status").exists()
        }

        @Test
        fun `GET health services содержит keycloak`() {
            webTestClient.get()
                .uri("/api/v1/health/services")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.services[?(@.name=='keycloak')]").exists()
                .jsonPath("$.services[?(@.name=='keycloak')].status").exists()
        }
    }

    // ============================================
    // AC2: DOWN для недоступных сервисов
    // ============================================

    @Nested
    inner class AC2_ServiceDown {

        @Test
        fun `nginx показывает DOWN когда сервис недоступен`() {
            // nginx не запущен в тестах, должен быть DOWN
            webTestClient.get()
                .uri("/api/v1/health/services")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.services[?(@.name=='nginx')].status").isEqualTo("DOWN")
                .jsonPath("$.services[?(@.name=='nginx')].details").exists()
        }

        @Test
        fun `gateway-core показывает DOWN когда сервис недоступен`() {
            // gateway-core не запущен в тестах, должен быть DOWN
            webTestClient.get()
                .uri("/api/v1/health/services")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.services[?(@.name=='gateway-core')].status").isEqualTo("DOWN")
                .jsonPath("$.services[?(@.name=='gateway-core')].details").exists()
        }

        @Test
        fun `redis показывает DOWN когда не настроен`() {
            // Redis отключён в тестах
            webTestClient.get()
                .uri("/api/v1/health/services")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.services[?(@.name=='redis')].status").isEqualTo("DOWN")
        }

        @Test
        fun `prometheus показывает DOWN когда не запущен`() {
            // Prometheus не запущен в тестах
            webTestClient.get()
                .uri("/api/v1/health/services")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.services[?(@.name=='prometheus')].status").isEqualTo("DOWN")
                .jsonPath("$.services[?(@.name=='prometheus')].details").exists()
        }

        @Test
        fun `grafana показывает DOWN когда не запущен`() {
            // Grafana не запущена в тестах
            webTestClient.get()
                .uri("/api/v1/health/services")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.services[?(@.name=='grafana')].status").isEqualTo("DOWN")
                .jsonPath("$.services[?(@.name=='grafana')].details").exists()
        }

        @Test
        fun `keycloak показывает DOWN когда не запущен`() {
            // Keycloak не запущен в тестах
            webTestClient.get()
                .uri("/api/v1/health/services")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.services[?(@.name=='keycloak')].status").isEqualTo("DOWN")
                .jsonPath("$.services[?(@.name=='keycloak')].details").exists()
        }
    }

    // ============================================
    // Безопасность: требуется аутентификация
    // ============================================

    @Nested
    inner class SecurityRequirements {

        @Test
        fun `GET health services без аутентификации возвращает 401`() {
            webTestClient.get()
                .uri("/api/v1/health/services")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        fun `developer может получить health services`() {
            webTestClient.get()
                .uri("/api/v1/health/services")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `admin может получить health services`() {
            val adminUser = createTestUser("health_admin", "password", Role.ADMIN)
            val adminToken = jwtService.generateToken(adminUser)

            webTestClient.get()
                .uri("/api/v1/health/services")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `security может получить health services`() {
            val securityUser = createTestUser("health_security", "password", Role.SECURITY)
            val securityToken = jwtService.generateToken(securityUser)

            webTestClient.get()
                .uri("/api/v1/health/services")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
        }
    }

    // ============================================
    // Структура ответа API
    // ============================================

    @Nested
    inner class ApiResponseStructure {

        @Test
        fun `ответ соответствует формату из Dev Notes`() {
            webTestClient.get()
                .uri("/api/v1/health/services")
                .cookie("auth_token", developerToken)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                // Проверяем структуру согласно Dev Notes Story 8.1
                .jsonPath("$.services").isArray
                .jsonPath("$.services[*].name").exists()
                .jsonPath("$.services[*].status").exists()
                .jsonPath("$.services[*].lastCheck").exists()
                .jsonPath("$.timestamp").exists()
        }

        @Test
        fun `сервисы имеют корректные имена`() {
            webTestClient.get()
                .uri("/api/v1/health/services")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                // nginx — entry point (Story 10.5)
                .jsonPath("$.services[?(@.name=='nginx')]").exists()
                // 4 сервиса из AC
                .jsonPath("$.services[?(@.name=='gateway-core')]").exists()
                .jsonPath("$.services[?(@.name=='gateway-admin')]").exists()
                .jsonPath("$.services[?(@.name=='postgresql')]").exists()
                .jsonPath("$.services[?(@.name=='redis')]").exists()
                // keycloak — Identity Provider (Story 12.1)
                .jsonPath("$.services[?(@.name=='keycloak')]").exists()
                // + 2 дополнительных сервиса мониторинга
                .jsonPath("$.services[?(@.name=='prometheus')]").exists()
                .jsonPath("$.services[?(@.name=='grafana')]").exists()
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
}
