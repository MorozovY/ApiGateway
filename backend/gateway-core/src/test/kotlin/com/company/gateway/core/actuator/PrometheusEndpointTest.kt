package com.company.gateway.core.actuator

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Integration тесты для Prometheus endpoint (Story 6.1)
 *
 * Тесты:
 * - AC2: Prometheus endpoint доступен и возвращает метрики в правильном формате
 * - AC5: Actuator endpoints защищены корректно (prometheus и health доступны без auth)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
@ActiveProfiles("test")
class PrometheusEndpointTest {

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
            // Redis unavailable - use invalid port (не блокирует тест)
            registry.add("spring.data.redis.host") { "localhost" }
            registry.add("spring.data.redis.port") { 59999 }
            registry.add("gateway.cache.invalidation-channel") { "route-cache-invalidation" }
            registry.add("gateway.cache.ttl-seconds") { 60 }
            registry.add("gateway.cache.max-routes") { 1000 }
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun `prometheus endpoint возвращает 200 OK`() {
        webTestClient.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `prometheus endpoint возвращает Content-Type text plain`() {
        webTestClient.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType("text/plain;version=0.0.4;charset=utf-8")
    }

    @Test
    fun `prometheus endpoint содержит JVM метрики`() {
        webTestClient.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .consumeWith { response ->
                val body = response.responseBody!!

                // Проверяем наличие стандартных JVM метрик
                assert(body.contains("jvm_memory")) {
                    "Prometheus output должен содержать JVM memory метрики"
                }
            }
    }

    @Test
    fun `prometheus endpoint содержит application тег`() {
        webTestClient.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .consumeWith { response ->
                val body = response.responseBody!!

                // Проверяем наличие тега application="gateway-core"
                assert(body.contains("application=\"gateway-core\"")) {
                    "Prometheus output должен содержать тег application=\"gateway-core\""
                }
            }
    }

    @Test
    fun `prometheus endpoint доступен без аутентификации`() {
        // Тест уже неявно проверяет это - мы не передаём credentials
        webTestClient.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `health endpoint доступен без аутентификации`() {
        // Проверяем, что endpoint отвечает (не 401/403), статус может быть 503 если Redis недоступен
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().value { status ->
                assert(status != 401 && status != 403) {
                    "Health endpoint не должен требовать аутентификацию, получен status: $status"
                }
            }
    }

    @Test
    fun `liveness probe доступен без аутентификации`() {
        webTestClient.get()
            .uri("/actuator/health/liveness")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `info endpoint доступен без аутентификации`() {
        // info endpoint включён в exposure, но может вернуть пустой JSON
        webTestClient.get()
            .uri("/actuator/info")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `prometheus endpoint содержит gateway_active_connections метрику`() {
        // AC1: gateway_active_connections gauge должен быть зарегистрирован при инициализации MetricsFilter
        webTestClient.get()
            .uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .consumeWith { response ->
                val body = response.responseBody!!

                assert(body.contains("gateway_active_connections")) {
                    "Prometheus output должен содержать gateway_active_connections метрику (AC1)"
                }
            }
    }

    @Test
    fun `не-exposed actuator endpoints недоступны`() {
        // AC5: только health, info, prometheus exposed в application.yml
        // Другие endpoints (env, beans, etc.) должны возвращать 404
        webTestClient.get()
            .uri("/actuator/env")
            .exchange()
            .expectStatus().isNotFound
    }
}
