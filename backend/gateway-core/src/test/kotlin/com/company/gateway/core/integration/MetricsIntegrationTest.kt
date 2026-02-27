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
import java.util.UUID

/**
 * Integration тесты для Per-Route Metrics (Story 6.2)
 *
 * Тесты:
 * - AC1: Метрики содержат route_path, upstream_host labels
 * - AC2: Prometheus queries по route_path работают
 * - AC3: Path normalization в метриках
 * - AC5: Unknown route fallback labels
 *
 * В CI режиме (TESTCONTAINERS_DISABLED=true) использует GitLab Services.
 * Локально запускает Testcontainers для PostgreSQL и Redis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MetricsIntegrationTest {

    companion object {
        // Проверяем запущены ли мы в CI
        private val isTestcontainersDisabled = System.getenv("TESTCONTAINERS_DISABLED") == "true"

        // Контейнеры — управляем lifecycle вручную (без @Container/@Testcontainers)
        private var postgres: PostgreSQLContainer<*>? = null
        private var redis: RedisContainer? = null
        private lateinit var wireMock: WireMockServer

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
            // WireMock нужен всегда
            wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
            wireMock.start()
        }

        @AfterAll
        @JvmStatic
        fun stopContainers() {
            wireMock.stop()
            postgres?.stop()
            redis?.stop()
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            if (isTestcontainersDisabled) {
                // В CI используем application-ci.yml — не переопределяем свойства
                return
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

    // ====== Story 12.6 Integration Tests: Multi-tenant Metrics ======

    @Nested
    inner class ConsumerIdLabelIntegration {

        @Test
        fun `AC2 - Prometheus endpoint содержит consumer_id label (anonymous)`() {
            // Arrange: создаём route
            insertRoute("/api/consumers-test", "http://localhost:${wireMock.port()}")

            wireMock.stubFor(
                WireMock.get(WireMock.urlMatching("/.*"))
                    .willReturn(WireMock.aResponse().withStatus(200).withBody("{}"))
            )

            // Act: запрос без JWT — consumer_id будет "anonymous"
            webTestClient.get()
                .uri("/api/consumers-test")
                .exchange()
                .expectStatus().isOk

            // Assert: проверяем что метрика содержит consumer_id label
            webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .consumeWith { response ->
                    val body = response.responseBody!!

                    // Находим метрику для нашего route
                    val metricLine = body.lines()
                        .find { it.startsWith("gateway_requests_total") && it.contains("/api/consumers-test") }

                    assert(metricLine != null) {
                        "Должна существовать метрика gateway_requests_total для /api/consumers-test"
                    }

                    // consumer_id должен быть "anonymous" для запросов без JWT
                    assert(metricLine!!.contains("consumer_id=\"anonymous\"")) {
                        "Метрика должна содержать consumer_id=anonymous, но получено:\n$metricLine"
                    }
                }
        }

        @Test
        fun `AC2 - consumer_id присутствует в gateway_request_duration_seconds`() {
            insertRoute("/api/duration-test", "http://localhost:${wireMock.port()}")

            wireMock.stubFor(
                WireMock.get(WireMock.urlMatching("/.*"))
                    .willReturn(WireMock.aResponse().withStatus(200).withBody("{}"))
            )

            webTestClient.get()
                .uri("/api/duration-test")
                .exchange()
                .expectStatus().isOk

            webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .consumeWith { response ->
                    val body = response.responseBody!!

                    // Timer создаёт _count метрику
                    val timerLine = body.lines()
                        .find { it.startsWith("gateway_request_duration_seconds_count") && it.contains("/api/duration-test") }

                    assert(timerLine != null) {
                        "Должна существовать метрика gateway_request_duration_seconds для /api/duration-test"
                    }

                    assert(timerLine!!.contains("consumer_id=")) {
                        "Timer должен содержать consumer_id label"
                    }
                }
        }

        @Test
        fun `AC2 - consumer_id из X-Consumer-ID header включён в метрики`() {
            // Arrange: создаём route
            insertRoute("/api/header-consumer-test", "http://localhost:${wireMock.port()}")

            wireMock.stubFor(
                WireMock.get(WireMock.urlMatching("/.*"))
                    .willReturn(WireMock.aResponse().withStatus(200).withBody("{}"))
            )

            // Act: запрос с X-Consumer-ID header
            webTestClient.get()
                .uri("/api/header-consumer-test")
                .header("X-Consumer-ID", "company-abc")
                .exchange()
                .expectStatus().isOk

            // Assert: проверяем что метрика содержит consumer_id из header
            webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .consumeWith { response ->
                    val body = response.responseBody!!

                    // Находим метрику для нашего route с consumer_id
                    val metricLine = body.lines()
                        .find { it.startsWith("gateway_requests_total") && it.contains("/api/header-consumer-test") }

                    assert(metricLine != null) {
                        "Должна существовать метрика gateway_requests_total для /api/header-consumer-test"
                    }

                    // consumer_id должен быть из X-Consumer-ID header
                    assert(metricLine!!.contains("consumer_id=\"company-abc\"")) {
                        "Метрика должна содержать consumer_id=company-abc из header, но получено:\n$metricLine"
                    }
                }
        }

        @Test
        fun `AC1 - consumer_id включён в gateway_errors_total`() {
            // Arrange: создаём route, upstream возвращает 500
            insertRoute("/api/error-consumer-test", "http://localhost:${wireMock.port()}")

            wireMock.stubFor(
                WireMock.get(WireMock.urlMatching("/.*"))
                    .willReturn(WireMock.aResponse().withStatus(500).withBody("Internal Server Error"))
            )

            // Act: запрос с X-Consumer-ID header, upstream возвращает 500
            webTestClient.get()
                .uri("/api/error-consumer-test")
                .header("X-Consumer-ID", "company-xyz")
                .exchange()
                .expectStatus().is5xxServerError

            // Assert: проверяем что gateway_errors_total содержит consumer_id
            webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk
                .expectBody(String::class.java)
                .consumeWith { response ->
                    val body = response.responseBody!!

                    // Находим метрику gateway_errors_total с consumer_id
                    val errorMetricLine = body.lines()
                        .find { it.startsWith("gateway_errors_total") && it.contains("/api/error-consumer-test") }

                    assert(errorMetricLine != null) {
                        "Должна существовать метрика gateway_errors_total для /api/error-consumer-test.\n" +
                            "Все error метрики:\n" + body.lines().filter { it.startsWith("gateway_errors_total") }.joinToString("\n")
                    }

                    // consumer_id должен присутствовать
                    assert(errorMetricLine!!.contains("consumer_id=\"company-xyz\"")) {
                        "gateway_errors_total должна содержать consumer_id=company-xyz, но получено:\n$errorMetricLine"
                    }

                    // error_type тоже должен быть
                    assert(errorMetricLine.contains("error_type=")) {
                        "gateway_errors_total должна содержать error_type label"
                    }
                }
        }
    }

    @Nested
    inner class AllLabelsPresent {

        @Test
        fun `все 6 labels присутствуют в gateway_requests_total включая consumer_id`() {
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

                    // Проверяем все 6 labels включая consumer_id
                    assert(metricLine!!.contains("route_id=")) { "Метрика должна содержать route_id" }
                    assert(metricLine.contains("route_path=\"/api/items/{id}\"")) { "Метрика должна содержать route_path" }
                    assert(metricLine.contains("upstream_host=")) { "Метрика должна содержать upstream_host" }
                    assert(metricLine.contains("method=\"GET\"")) { "Метрика должна содержать method" }
                    assert(metricLine.contains("status=\"2xx\"")) { "Метрика должна содержать status" }
                    assert(metricLine.contains("consumer_id=")) { "Метрика должна содержать consumer_id" }
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
