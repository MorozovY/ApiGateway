package com.company.gateway.admin.integration

import com.redis.testcontainers.RedisContainer
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

/**
 * Integration tests for gateway-admin health check endpoints (Story 1.7).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class HealthEndpointIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16")
            .withDatabaseName("gateway")
            .withUsername("gateway")
            .withPassword("gateway")

        @Container
        @JvmStatic
        val redis = RedisContainer("redis:7")

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
            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.firstMappedPort }
            registry.add("management.endpoint.health.show-details") { "always" }
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun `health endpoint returns 200 with status UP when all components healthy`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }

    @Test
    fun `health endpoint includes r2dbc component status with details`() {
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
    fun `health endpoint includes redis component status with details`() {
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
    fun `health endpoint includes diskSpace component status with details`() {
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
    fun `liveness probe returns 200 with status UP`() {
        webTestClient.get()
            .uri("/actuator/health/liveness")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }

    @Test
    fun `readiness probe returns 200 when dependencies ready`() {
        webTestClient.get()
            .uri("/actuator/health/readiness")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }

    @Test
    fun `health endpoint accessible without authentication`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `info endpoint accessible without authentication`() {
        webTestClient.get()
            .uri("/actuator/info")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `prometheus endpoint accessible without authentication`() {
        webTestClient.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isOk
    }
}
