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
 * Тесты для поведения health endpoint когда зависимости недоступны (AC2: Story 1.7).
 *
 * AC2: Given gateway-core запускается, но database/redis не готов
 *      When делается запрос к /actuator/health/readiness
 *      Then ответ возвращает HTTP 503 со статусом "DOWN"
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
@ActiveProfiles("test")
class HealthEndpointTest {

    companion object {
        // Проверяем запущены ли мы в CI
        private val isTestcontainersDisabled = System.getenv("TESTCONTAINERS_DISABLED") == "true"

        // PostgreSQL контейнер (null в CI)
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*>? = if (!isTestcontainersDisabled) {
            PostgreSQLContainer("postgres:16")
                .withDatabaseName("gateway")
                .withUsername("gateway")
                .withPassword("gateway")
        } else null

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

                registry.add("spring.r2dbc.url") { "r2dbc:postgresql://$pgHost:$pgPort/$pgDb" }
                registry.add("spring.r2dbc.username") { pgUser }
                registry.add("spring.r2dbc.password") { pgPass }
                registry.add("spring.flyway.url") { "jdbc:postgresql://$pgHost:$pgPort/$pgDb" }
                registry.add("spring.flyway.user") { pgUser }
                registry.add("spring.flyway.password") { pgPass }
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
            }
            // Redis unavailable - use invalid port (для тестирования поведения при недоступном Redis)
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
    fun `health endpoint возвращает 200 даже когда Redis недоступен`() {
        // Liveness должен быть UP независимо от зависимостей
        webTestClient.get()
            .uri("/actuator/health/liveness")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }

    @Test
    fun `readiness probe возвращает DOWN когда Redis недоступен`() {
        // Readiness должен отражать статус зависимостей
        webTestClient.get()
            .uri("/actuator/health/readiness")
            .exchange()
            .expectStatus().is5xxServerError
            .expectBody()
            .jsonPath("$.status").isEqualTo("DOWN")
    }

    @Test
    fun `redis компонент показывает DOWN статус когда недоступен`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectBody()
            .jsonPath("$.components.redis.status").isEqualTo("DOWN")
    }

    @Test
    fun `r2dbc компонент показывает UP статус когда база данных доступна`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectBody()
            .jsonPath("$.components.r2dbc.status").isEqualTo("UP")
    }
}
