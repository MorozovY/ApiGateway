package com.company.gateway.core.integration

import com.company.gateway.common.model.RouteStatus
import com.company.gateway.core.cache.RouteCacheManager
import com.company.gateway.core.filter.MetricsFilter
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * Integration тесты для Per-Route Metrics (Story 6.2)
 *
 * Тесты:
 * - AC1: Метрики содержат route_path, upstream_host labels
 * - AC2: Prometheus queries по route_path работают
 * - AC3: Path normalization в метриках
 * - AC5: Unknown route fallback labels
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class MetricsIntegrationTest {

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

        lateinit var wireMock: WireMockServer

        @BeforeAll
        @JvmStatic
        fun startWireMock() {
            wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
            wireMock.start()
        }

        @AfterAll
        @JvmStatic
        fun stopWireMock() {
            wireMock.stop()
        }

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
            registry.add("gateway.cache.invalidation-channel") { "route-cache-invalidation" }
            registry.add("gateway.cache.ttl-seconds") { 60 }
            registry.add("gateway.cache.max-routes") { 1000 }
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Autowired
    private lateinit var cacheManager: RouteCacheManager

    @BeforeEach
    fun setup() {
        databaseClient.sql("DELETE FROM routes").fetch().rowsUpdated().block()
        cacheManager.refreshCache().block()
    }

    @AfterEach
    fun resetWireMock() {
        wireMock.resetAll()
    }

    @Nested
    inner class RoutePathLabel {

        @Test
        fun `AC1 - метрики содержат route_path label после запроса через gateway`() {
            // Создаём route
            insertRoute("/api/orders", "http://localhost:${wireMock.port()}")

            // Настраиваем upstream ответ
            // Gateway переписывает путь: /api/orders/123 → /123 (обрезает route prefix)
            wireMock.stubFor(
                WireMock.get(WireMock.urlMatching("/.*"))
                    .willReturn(WireMock.aResponse().withStatus(200).withBody("{}"))
            )

            // Делаем запрос через gateway
            webTestClient.get()
                .uri("/api/orders/123")
                .exchange()
                .expectStatus().isOk

            // Проверяем prometheus endpoint содержит метрику с route_path
            webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .consumeWith { response ->
                    val body = response.responseBody!!

                    // Проверяем что gateway_requests_total содержит нормализованный route_path
                    assert(body.contains("gateway_requests_total")) {
                        "Prometheus output должен содержать gateway_requests_total"
                    }
                    assert(body.contains("route_path=\"/api/orders/{id}\"")) {
                        "Метрика должна содержать нормализованный route_path=/api/orders/{id}, но получено:\n" +
                            body.lines().filter { it.contains("gateway_requests_total") }.joinToString("\n")
                    }
                }
        }

        @Test
        fun `AC3 - path normalization работает для UUID в пути`() {
            insertRoute("/api/users", "http://localhost:${wireMock.port()}")

            // Gateway переписывает путь: /api/users/{uuid} → /{uuid}
            wireMock.stubFor(
                WireMock.get(WireMock.urlMatching("/.*"))
                    .willReturn(WireMock.aResponse().withStatus(200).withBody("{}"))
            )

            // Запрос с UUID в пути
            webTestClient.get()
                .uri("/api/users/550e8400-e29b-41d4-a716-446655440000")
                .exchange()
                .expectStatus().isOk

            // Проверяем что UUID нормализован в {uuid}
            webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .consumeWith { response ->
                    val body = response.responseBody!!

                    assert(body.contains("route_path=\"/api/users/{uuid}\"")) {
                        "UUID должен быть нормализован в {uuid}, но получено:\n" +
                            body.lines().filter { it.contains("route_path") }.joinToString("\n")
                    }
                }
        }
    }

    @Nested
    inner class UpstreamHostLabel {

        @Test
        fun `AC4 - метрики содержат upstream_host label`() {
            insertRoute("/api/products", "http://localhost:${wireMock.port()}")

            // Gateway переписывает путь: /api/products → / (пустой путь после обрезки)
            wireMock.stubFor(
                WireMock.get(WireMock.urlMatching("/.*"))
                    .willReturn(WireMock.aResponse().withStatus(200).withBody("[]"))
            )

            webTestClient.get()
                .uri("/api/products")
                .exchange()
                .expectStatus().isOk

            webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .consumeWith { response ->
                    val body = response.responseBody!!

                    // upstream_host должен содержать localhost:port
                    assert(body.contains("upstream_host=\"localhost:${wireMock.port()}\"")) {
                        "Метрика должна содержать upstream_host=localhost:${wireMock.port()}, но получено:\n" +
                            body.lines().filter { it.contains("upstream_host") }.take(5).joinToString("\n")
                    }
                }
        }
    }

    /**
     * AC5 Unknown Route Fallback:
     *
     * Spring Cloud Gateway не запускает GlobalFilter chain для запросов,
     * которые не соответствуют ни одному маршруту. Поэтому MetricsFilter
     * не вызывается для таких запросов, и метрики не записываются.
     *
     * Fallback labels (route_id="unknown", route_path="unknown", upstream_host="unknown")
     * работают только когда Route существует, но его атрибуты не установлены.
     * Это покрывается unit тестами в MetricsFilterTest.UnknownRouteFallback.
     *
     * В реальном production использовании:
     * - 404 для unknown routes возвращается GlobalErrorHandler
     * - Метрики для 404 можно отслеживать через Spring Boot Actuator metrics (http.server.requests)
     */

    @Nested
    inner class AllLabelsPresent {

        @Test
        fun `все 5 labels присутствуют в gateway_requests_total`() {
            insertRoute("/api/items", "http://localhost:${wireMock.port()}")

            // Gateway переписывает путь: /api/items/42 → /42
            wireMock.stubFor(
                WireMock.get(WireMock.urlMatching("/.*"))
                    .willReturn(WireMock.aResponse().withStatus(200).withBody("{}"))
            )

            webTestClient.get()
                .uri("/api/items/42")
                .exchange()
                .expectStatus().isOk

            webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .consumeWith { response ->
                    val body = response.responseBody!!

                    // Находим строку с нашей метрикой
                    val metricLine = body.lines()
                        .find { it.startsWith("gateway_requests_total") && it.contains("/api/items") }

                    assert(metricLine != null) {
                        "Должна существовать метрика gateway_requests_total для /api/items"
                    }

                    // Проверяем все labels
                    assert(metricLine!!.contains("route_id=")) { "Метрика должна содержать route_id" }
                    assert(metricLine.contains("route_path=\"/api/items/{id}\"")) { "Метрика должна содержать route_path" }
                    assert(metricLine.contains("upstream_host=")) { "Метрика должна содержать upstream_host" }
                    assert(metricLine.contains("method=\"GET\"")) { "Метрика должна содержать method" }
                    assert(metricLine.contains("status=\"2xx\"")) { "Метрика должна содержать status" }
                }
        }

        @Test
        fun `gateway_request_duration_seconds содержит все labels`() {
            insertRoute("/api/timing", "http://localhost:${wireMock.port()}")

            // Gateway переписывает путь: /api/timing/999 → /999
            wireMock.stubFor(
                WireMock.get(WireMock.urlMatching("/.*"))
                    .willReturn(WireMock.aResponse().withStatus(200).withBody("{}"))
            )

            webTestClient.get()
                .uri("/api/timing/999")
                .exchange()
                .expectStatus().isOk

            webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .consumeWith { response ->
                    val body = response.responseBody!!

                    // Timer создаёт _count и _sum метрики
                    val timerLine = body.lines()
                        .find { it.startsWith("gateway_request_duration_seconds_count") && it.contains("/api/timing") }

                    assert(timerLine != null) {
                        "Должна существовать метрика gateway_request_duration_seconds для /api/timing"
                    }

                    assert(timerLine!!.contains("route_path=\"/api/timing/{id}\"")) {
                        "Timer должен содержать route_path label"
                    }
                }
        }
    }

    private fun insertRoute(path: String, upstreamUrl: String) {
        databaseClient.sql("""
            INSERT INTO routes (id, path, upstream_url, methods, status)
            VALUES (:id, :path, :upstreamUrl, ARRAY['GET','POST'], :status)
        """.trimIndent())
            .bind("id", UUID.randomUUID())
            .bind("path", path)
            .bind("upstreamUrl", upstreamUrl)
            .bind("status", RouteStatus.PUBLISHED.name.lowercase())
            .fetch()
            .rowsUpdated()
            .block()
        cacheManager.refreshCache().block()
    }
}
