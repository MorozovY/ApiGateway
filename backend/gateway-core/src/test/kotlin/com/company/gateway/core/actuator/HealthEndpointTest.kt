package com.company.gateway.core.actuator

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Тесты для поведения health endpoint когда зависимости недоступны (AC2: Story 1.7).
 *
 * AC2: Given gateway-core запускается, но database/redis не готов
 *      When делается запрос к /actuator/health/readiness
 *      Then ответ возвращает HTTP 503 со статусом "DOWN"
 *
 * ВАЖНО: @DirtiesContext нужен потому что этот тест использует невалидный Redis порт (59999)
 * для тестирования DOWN сценария. Без него контекст кэшируется и другие тесты
 * (например HealthEndpointIntegrationTest) получают контекст с неправильным Redis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HealthEndpointTest {

    companion object {
        // Проверяем запущены ли мы в CI
        private val isTestcontainersDisabled = System.getenv("TESTCONTAINERS_DISABLED") == "true"

        // Контейнер — управляем lifecycle вручную (без @Container/@Testcontainers)
        private var postgres: PostgreSQLContainer<*>? = null

        @BeforeAll
        @JvmStatic
        fun startContainers() {
            // Запускаем контейнер только локально
            if (!isTestcontainersDisabled) {
                postgres = PostgreSQLContainer("postgres:16")
                    .withDatabaseName("gateway")
                    .withUsername("gateway")
                    .withPassword("gateway")
                postgres?.start()
            }
        }

        @AfterAll
        @JvmStatic
        fun stopContainers() {
            postgres?.stop()
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            if (!isTestcontainersDisabled) {
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
            // В CI для PostgreSQL используется application-ci.yml

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
