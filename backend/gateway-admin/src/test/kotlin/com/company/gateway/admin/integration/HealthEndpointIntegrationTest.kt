package com.company.gateway.admin.integration

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
 * Integration тесты для health check endpoints gateway-admin (Story 1.7).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HealthEndpointIntegrationTest {

    companion object {
        // Проверяем запущены ли мы в CI
        private val isTestcontainersDisabled = System.getenv("TESTCONTAINERS_DISABLED") == "true"

        private var postgres: PostgreSQLContainer<*>? = null
        private var redis: RedisContainer? = null

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

            redis = RedisContainer("redis:7")
            redis!!.start()
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
                // В CI читаем из env переменных
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
                registry.add("spring.r2dbc.url") {
                    "r2dbc:postgresql://${postgres!!.host}:${postgres!!.firstMappedPort}/${postgres!!.databaseName}"
                }
                registry.add("spring.r2dbc.username", postgres!!::getUsername)
                registry.add("spring.r2dbc.password", postgres!!::getPassword)
                registry.add("spring.flyway.url", postgres!!::getJdbcUrl)
                registry.add("spring.flyway.user", postgres!!::getUsername)
                registry.add("spring.flyway.password", postgres!!::getPassword)
                registry.add("spring.data.redis.host", redis!!::getHost)
                registry.add("spring.data.redis.port") { redis!!.firstMappedPort }
            }
            registry.add("management.endpoint.health.show-details") { "always" }
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun `health endpoint возвращает 200 со статусом UP когда все компоненты здоровы`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }

    @Test
    fun `health endpoint включает статус r2dbc компонента с деталями`() {
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
    fun `health endpoint включает статус redis компонента с деталями`() {
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
    fun `health endpoint включает статус diskSpace компонента с деталями`() {
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
    fun `liveness probe возвращает 200 со статусом UP`() {
        webTestClient.get()
            .uri("/actuator/health/liveness")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }

    @Test
    fun `readiness probe возвращает 200 когда зависимости готовы`() {
        webTestClient.get()
            .uri("/actuator/health/readiness")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }

    @Test
    fun `health endpoint доступен без аутентификации`() {
        webTestClient.get()
            .uri("/actuator/health")
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
