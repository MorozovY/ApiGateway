package com.company.gateway.core.actuator

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Tests for health endpoint behavior when dependencies are unavailable (AC2: Story 1.7).
 *
 * AC2: Given gateway-core is starting but database/redis is not ready
 *      When a request is made to /actuator/health/readiness
 *      Then the response returns HTTP 503 with status "DOWN"
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
@ActiveProfiles("test")
class HealthEndpointTest {

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
            // Redis unavailable - use invalid port
            registry.add("spring.data.redis.host") { "localhost" }
            registry.add("spring.data.redis.port") { 59999 }
            registry.add("gateway.cache.invalidation-channel") { "route-cache-invalidation" }
            registry.add("gateway.cache.ttl-seconds") { 60 }
            registry.add("gateway.cache.max-routes") { 1000 }
            registry.add("management.endpoint.health.show-details") { "always" }
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun `health endpoint returns 200 even when Redis unavailable`() {
        // Liveness should be UP regardless of dependencies
        webTestClient.get()
            .uri("/actuator/health/liveness")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }

    @Test
    fun `readiness probe returns DOWN when Redis unavailable`() {
        // Readiness should reflect dependency status
        webTestClient.get()
            .uri("/actuator/health/readiness")
            .exchange()
            .expectStatus().is5xxServerError
            .expectBody()
            .jsonPath("$.status").isEqualTo("DOWN")
    }

    @Test
    fun `redis component shows DOWN status when unavailable`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectBody()
            .jsonPath("$.components.redis.status").isEqualTo("DOWN")
    }

    @Test
    fun `r2dbc component shows UP status when database available`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectBody()
            .jsonPath("$.components.r2dbc.status").isEqualTo("UP")
    }
}
