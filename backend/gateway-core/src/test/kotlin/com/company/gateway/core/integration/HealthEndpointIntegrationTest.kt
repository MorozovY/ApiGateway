package com.company.gateway.core.integration

import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Integration тесты для health check endpoints (Story 1.7).
 *
 * Тестирование AC1, AC2, AC3, AC5, AC6 - Health endpoints с работающими зависимостями.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HealthEndpointIntegrationTest {

    companion object {
        // Проверяем запущены ли мы в CI
        private val isTestcontainersDisabled = System.getenv("TESTCONTAINERS_DISABLED") == "true"

        // Контейнеры — управляем lifecycle вручную (без @Container/@Testcontainers)
        private var postgres: PostgreSQLContainer<*>? = null
        private var redis: RedisContainer? = null

        @BeforeAll
        @JvmStatic
        fun startContainers() {
            // Запускаем контейнеры только локально
            if (!isTestcontainersDisabled) {
                postgres = PostgreSQLContainer("postgres:16")
                    .withDatabaseName("gateway")
                    .withUsername("gateway")
                    .withPassword("gateway")
                postgres?.start()

                redis = RedisContainer("redis:7")
                redis?.start()
            }
        }

        @AfterAll
        @JvmStatic
        fun stopContainers() {
            postgres?.stop()
            redis?.stop()
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            if (isTestcontainersDisabled) {
                // В CI читаем из env переменных (GitLab Services)
                val pgHost = System.getenv("POSTGRES_HOST") ?: "localhost"
                val pgPort = System.getenv("POSTGRES_PORT") ?: "5432"
                val pgDb = System.getenv("POSTGRES_DB") ?: "gateway_test"
                val pgUser = System.getenv("POSTGRES_USER") ?: "gateway"
                val pgPass = System.getenv("POSTGRES_PASSWORD") ?: "gateway"
                val redisHost = System.getenv("REDIS_HOST") ?: "localhost"
                val redisPort = System.getenv("REDIS_PORT") ?: "6379"

                registry.add("spring.r2dbc.url") { "r2dbc:postgresql://$pgHost:$pgPort/$pgDb" }
                registry.add("spring.r2dbc.username") { pgUser }
                registry.add("spring.r2dbc.password") { pgPass }
                registry.add("spring.flyway.url") { "jdbc:postgresql://$pgHost:$pgPort/$pgDb" }
                registry.add("spring.flyway.user") { pgUser }
                registry.add("spring.flyway.password") { pgPass }
                registry.add("spring.data.redis.host") { redisHost }
                registry.add("spring.data.redis.port") { redisPort.toInt() }
            } else {
                // Локально настраиваем Testcontainers
                postgres?.let { pg ->
                    registry.add("spring.r2dbc.url") {
                        "r2dbc:postgresql://${pg.host}:${pg.firstMappedPort}/${pg.databaseName}"
                    }
                    registry.add("spring.r2dbc.username", pg::getUsername)
                    registry.add("spring.r2dbc.password", pg::getPassword)
                    registry.add("spring.flyway.url", pg::getJdbcUrl)
                    registry.add("spring.flyway.user", pg::getUsername)
                    registry.add("spring.flyway.password", pg::getPassword)
                }
                redis?.let { rd ->
                    registry.add("spring.data.redis.host", rd::getHost)
                    registry.add("spring.data.redis.port") { rd.firstMappedPort }
                }
            }
            registry.add("gateway.cache.invalidation-channel") { "route-cache-invalidation" }
            registry.add("gateway.cache.ttl-seconds") { 60 }
            registry.add("gateway.cache.max-routes") { 1000 }
            // Enable health details for testing
            registry.add("management.endpoint.health.show-details") { "always" }
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun `AC1 - health endpoint возвращает 200 со статусом UP когда все компоненты здоровы`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }

    @Test
    fun `AC5 - health endpoint включает статус database компонента с деталями`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.components.r2dbc.status").isEqualTo("UP")
            .jsonPath("$.components.r2dbc.details").exists()
            .jsonPath("$.components.r2dbc.details.database").isEqualTo("PostgreSQL")
    }

    @Test
    fun `AC5 - health endpoint включает статус redis компонента с деталями`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.components.redis.status").isEqualTo("UP")
            .jsonPath("$.components.redis.details").exists()
            .jsonPath("$.components.redis.details.version").exists()
    }

    @Test
    fun `AC5 - health endpoint включает статус diskSpace компонента с деталями`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.components.diskSpace.status").isEqualTo("UP")
            .jsonPath("$.components.diskSpace.details").exists()
            .jsonPath("$.components.diskSpace.details.total").exists()
            .jsonPath("$.components.diskSpace.details.free").exists()
            .jsonPath("$.components.diskSpace.details.threshold").exists()
    }

    @Test
    fun `AC3 - liveness probe возвращает 200 со статусом UP`() {
        webTestClient.get()
            .uri("/actuator/health/liveness")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }

    @Test
    fun `AC2 - readiness probe возвращает 200 когда зависимости готовы`() {
        webTestClient.get()
            .uri("/actuator/health/readiness")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }

    @Test
    fun `AC6 - health endpoint доступен без аутентификации`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `AC6 - liveness probe доступен без аутентификации`() {
        webTestClient.get()
            .uri("/actuator/health/liveness")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `AC6 - readiness probe доступен без аутентификации`() {
        webTestClient.get()
            .uri("/actuator/health/readiness")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `info endpoint доступен без аутентификации`() {
        webTestClient.get()
            .uri("/actuator/info")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `prometheus endpoint доступен без аутентификации`() {
        webTestClient.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isOk
    }
}
