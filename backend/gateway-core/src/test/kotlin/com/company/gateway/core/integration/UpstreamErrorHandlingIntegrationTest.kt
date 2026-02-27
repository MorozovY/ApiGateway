package com.company.gateway.core.integration

import com.company.gateway.common.exception.ErrorResponse
import com.company.gateway.common.model.RouteStatus
import com.company.gateway.core.cache.RouteCacheManager
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
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
 * Integration тесты для обработки ошибок upstream (Story 1.4)
 *
 * Тестирование AC1-AC5:
 * - AC1: Connection refused -> 502 Bad Gateway с RFC 7807
 * - AC2: Таймаут upstream -> 504 Gateway Timeout с RFC 7807
 * - AC3: Upstream 5xx -> Передаётся без изменений
 * - AC4: Все ошибки, генерируемые шлюзом, включают correlationId (placeholder)
 * - AC5: Внутренние детали не раскрываются в ошибках
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UpstreamErrorHandlingIntegrationTest {

    companion object {
        // Проверяем запущены ли мы в CI
        private val isTestcontainersDisabled = System.getenv("TESTCONTAINERS_DISABLED") == "true"

        // Контейнеры — управляем lifecycle вручную (без @Container/@Testcontainers)
        private var postgres: PostgreSQLContainer<*>? = null
        private var redis: RedisContainer? = null
        private lateinit var wireMock: WireMockServer

        // Port that is guaranteed to be closed (no service listening)
        const val DEAD_PORT = 59999

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
            // Cache configuration
            registry.add("gateway.cache.invalidation-channel") { "route-cache-invalidation" }
            registry.add("gateway.cache.ttl-seconds") { 60 }
            registry.add("gateway.cache.max-routes") { 1000 }
            // Set short timeout for faster tests
            registry.add("spring.cloud.gateway.httpclient.connect-timeout") { 2000 }
            registry.add("spring.cloud.gateway.httpclient.response-timeout") { "3s" }
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Autowired
    private lateinit var cacheManager: RouteCacheManager

    @BeforeEach
    fun clearRoutes() {
        databaseClient.sql("DELETE FROM routes").fetch().rowsUpdated().block()
        // Refresh cache to clear it
        cacheManager.refreshCache().block()
    }

    @AfterEach
    fun resetWireMock() {
        wireMock.resetAll()
    }

    // ============================================
    // AC1: Connection refused -> 502 Bad Gateway
    // ============================================

    @Test
    fun `AC1 - отказ в соединении возвращает 502 Bad Gateway в формате RFC 7807`() {
        // Подготовка: вставляем маршрут, указывающий на закрытый порт (отказ в соединении)
        insertRoute("/api/dead-service", "http://localhost:$DEAD_PORT", RouteStatus.PUBLISHED)

        // Выполнение и проверка
        webTestClient.get()
            .uri("/api/dead-service/test")
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectHeader().contentType("application/json")
            .expectBody(ErrorResponse::class.java)
            .consumeWith { result ->
                val body = result.responseBody!!
                assert(body.type == "https://api.gateway/errors/upstream-unavailable") {
                    "Ожидался type 'upstream-unavailable', получено '${body.type}'"
                }
                assert(body.title == "Bad Gateway") {
                    "Ожидался title 'Bad Gateway', получено '${body.title}'"
                }
                assert(body.status == 502) {
                    "Ожидался status 502, получено ${body.status}"
                }
                assert(body.detail == "Upstream service is unavailable") {
                    "Ожидался detail 'Upstream service is unavailable', получено '${body.detail}'"
                }
                assert(body.instance == "/api/dead-service/test") {
                    "Ожидался instance '/api/dead-service/test', получено '${body.instance}'"
                }
            }
    }

    @Test
    fun `AC5 - ошибка отказа в соединении не раскрывает внутренние детали`() {
        insertRoute("/api/dead-service", "http://localhost:$DEAD_PORT", RouteStatus.PUBLISHED)

        webTestClient.get()
            .uri("/api/dead-service/test")
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectBody()
            .consumeWith { result ->
                val bodyString = String(result.responseBody ?: ByteArray(0))
                // НЕ должен содержать внутренние детали
                assert(!bodyString.contains("localhost:$DEAD_PORT", ignoreCase = true)) {
                    "Ответ с ошибкой не должен раскрывать upstream host:port"
                }
                assert(!bodyString.contains("ConnectException", ignoreCase = true)) {
                    "Ответ с ошибкой не должен раскрывать имена классов исключений"
                }
                assert(!bodyString.contains("stackTrace", ignoreCase = true)) {
                    "Ответ с ошибкой не должен содержать stack traces"
                }
            }
    }

    // ============================================
    // AC2: Upstream timeout -> 504 Gateway Timeout
    // ============================================

    @Test
    fun `AC2 - медленный upstream возвращает 504 Gateway Timeout в формате RFC 7807`() {
        // Подготовка: настраиваем WireMock с задержкой больше таймаута (3s + буфер)
        wireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching("/api/slow.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withFixedDelay(5000) // 5 секунд > 3s таймаут
                        .withStatus(200)
                        .withBody("{}")
                )
        )

        insertRoute("/api/slow", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)

        // Выполнение и проверка
        webTestClient.get()
            .uri("/api/slow/test")
            .exchange()
            .expectStatus().isEqualTo(504)
            .expectHeader().contentType("application/json")
            .expectBody(ErrorResponse::class.java)
            .consumeWith { result ->
                val body = result.responseBody!!
                assert(body.type == "https://api.gateway/errors/upstream-timeout") {
                    "Ожидался type 'upstream-timeout', получено '${body.type}'"
                }
                assert(body.title == "Gateway Timeout") {
                    "Ожидался title 'Gateway Timeout', получено '${body.title}'"
                }
                assert(body.status == 504) {
                    "Ожидался status 504, получено ${body.status}"
                }
                assert(body.detail == "Upstream service did not respond in time") {
                    "Ожидался detail 'Upstream service did not respond in time', получено '${body.detail}'"
                }
            }
    }

    @Test
    fun `AC5 - ошибка таймаута не раскрывает внутренние детали`() {
        wireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching("/api/slow.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withFixedDelay(5000)
                        .withStatus(200)
                )
        )

        insertRoute("/api/slow", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)

        webTestClient.get()
            .uri("/api/slow/test")
            .exchange()
            .expectStatus().isEqualTo(504)
            .expectBody()
            .consumeWith { result ->
                val bodyString = String(result.responseBody ?: ByteArray(0))
                // НЕ должен содержать внутренние детали
                assert(!bodyString.contains("TimeoutException", ignoreCase = true)) {
                    "Ответ с ошибкой не должен раскрывать имена классов исключений"
                }
                assert(!bodyString.contains("ReadTimeoutException", ignoreCase = true)) {
                    "Ответ с ошибкой не должен раскрывать внутренние типы исключений"
                }
            }
    }

    // ============================================
    // AC3: Upstream 5xx -> Передаётся без изменений
    // ============================================

    @Test
    fun `AC3 - upstream 500 передаётся без изменений`() {
        wireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching("/api/failing.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"error": "Internal Server Error from upstream"}""")
                )
        )

        insertRoute("/api/failing", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)

        webTestClient.get()
            .uri("/api/failing/test")
            .exchange()
            .expectStatus().isEqualTo(500)
            .expectBody()
            .jsonPath("$.error").isEqualTo("Internal Server Error from upstream")
    }

    @Test
    fun `AC3 - upstream 502 передаётся без изменений`() {
        wireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching("/api/failing.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(502)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"message": "Bad Gateway from upstream service"}""")
                )
        )

        insertRoute("/api/failing", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)

        webTestClient.get()
            .uri("/api/failing/test")
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectBody()
            .jsonPath("$.message").isEqualTo("Bad Gateway from upstream service")
    }

    @Test
    fun `AC3 - upstream 503 передаётся без изменений`() {
        wireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching("/api/failing.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"status": "Service Unavailable", "retryAfter": 60}""")
                )
        )

        insertRoute("/api/failing", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)

        webTestClient.get()
            .uri("/api/failing/test")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.status").isEqualTo("Service Unavailable")
            .jsonPath("$.retryAfter").isEqualTo(60)
    }

    @Test
    fun `AC3 - upstream 504 передаётся без изменений`() {
        wireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching("/api/failing.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(504)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"error": "Gateway Timeout from upstream"}""")
                )
        )

        insertRoute("/api/failing", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)

        webTestClient.get()
            .uri("/api/failing/test")
            .exchange()
            .expectStatus().isEqualTo(504)
            .expectBody()
            .jsonPath("$.error").isEqualTo("Gateway Timeout from upstream")
    }

    // ============================================
    // AC4: CorrelationId placeholder
    // ============================================

    @Test
    fun `AC4 - структура error response включает поле correlationId`() {
        insertRoute("/api/dead-service", "http://localhost:$DEAD_PORT", RouteStatus.PUBLISHED)

        webTestClient.get()
            .uri("/api/dead-service/test")
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectBody()
            .consumeWith { result ->
                val bodyString = String(result.responseBody ?: ByteArray(0))

                // Проверяем наличие обязательных полей RFC 7807
                assert(bodyString.contains("\"type\"")) {
                    "RFC 7807 error response должен содержать поле 'type'"
                }
                assert(bodyString.contains("\"title\"")) {
                    "RFC 7807 error response должен содержать поле 'title'"
                }
                assert(bodyString.contains("\"status\"")) {
                    "RFC 7807 error response должен содержать поле 'status'"
                }
                assert(bodyString.contains("\"detail\"")) {
                    "RFC 7807 error response должен содержать поле 'detail'"
                }
                assert(bodyString.contains("\"instance\"")) {
                    "RFC 7807 error response должен содержать поле 'instance'"
                }

                // Примечание: correlationId добавляется в Story 1.6
                // Этот тест проверяет, что ответ соответствует структуре RFC 7807
            }
    }

    @Test
    fun `AC4 - класс ErrorResponse имеет определённое поле correlationId`() {
        // Проверяем, что data class ErrorResponse имеет поле correlationId (compile-time check)
        val errorResponse = ErrorResponse(
            type = "https://api.gateway/errors/test",
            title = "Test",
            status = 500,
            detail = "Test detail",
            instance = "/test",
            correlationId = "test-correlation-id" // Поле существует и может быть установлено
        )

        assert(errorResponse.correlationId == "test-correlation-id") {
            "ErrorResponse должен иметь поле correlationId"
        }
    }

    // ============================================
    // Helper methods
    // ============================================

    private fun insertRoute(path: String, upstreamUrl: String, status: RouteStatus) {
        databaseClient.sql("""
            INSERT INTO routes (id, path, upstream_url, methods, status)
            VALUES (:id, :path, :upstreamUrl, ARRAY['GET','POST'], :status)
        """.trimIndent())
            .bind("id", UUID.randomUUID())
            .bind("path", path)
            .bind("upstreamUrl", upstreamUrl)
            .bind("status", status.name.lowercase())
            .fetch()
            .rowsUpdated()
            .block()
        // Refresh cache to pick up newly inserted routes
        cacheManager.refreshCache().block()
    }
}
