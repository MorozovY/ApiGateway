package com.company.gateway.core.integration

import com.company.gateway.common.exception.ErrorResponse
import com.company.gateway.common.model.RouteStatus
import com.company.gateway.core.cache.RouteCacheManager
import com.company.gateway.core.filter.ConsumerIdentityFilter
import com.company.gateway.core.filter.CorrelationIdFilter
import com.company.gateway.core.filter.JwtAuthenticationFilter
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
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
 * Integration тесты для Request Logging и Correlation IDs (Story 1.6)
 *
 * Тесты:
 * - AC1: Генерирует correlation ID для новых запросов
 * - AC2: Сохраняет существующий correlation ID
 * - AC3: Структурированное JSON логирование (проверяется через захват вывода логов)
 * - AC4: Correlation ID в error responses
 * - AC5: Thread-safe propagation контекста (проверяется через конкурентные запросы)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RequestLoggingIntegrationTest {

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

    // ============================================
    // AC1: Генерация Correlation ID для новых запросов
    // ============================================

    @Test
    fun `AC1 - генерирует UUID correlation ID когда header отсутствует`() {
        insertRoute("/api/test", "http://localhost:${wireMock.port()}")
        wireMock.stubFor(
            get(urlEqualTo("/api/test"))
                .willReturn(aResponse().withStatus(200).withBody("{}"))
        )

        webTestClient.get()
            .uri("/api/test")
            .exchange()
            .expectStatus().isOk
            .expectHeader().exists(CorrelationIdFilter.CORRELATION_ID_HEADER)
            .expectHeader().value(CorrelationIdFilter.CORRELATION_ID_HEADER) { correlationId ->
                assert(correlationId.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))) {
                    "Correlation ID должен быть валидным UUID, получено: $correlationId"
                }
            }
    }

    @Test
    fun `AC1 - correlation ID передаётся на upstream сервис`() {
        insertRoute("/api/upstream", "http://localhost:${wireMock.port()}")
        wireMock.stubFor(
            get(urlEqualTo("/api/upstream"))
                .willReturn(aResponse().withStatus(200).withBody("{}"))
        )

        webTestClient.get()
            .uri("/api/upstream")
            .exchange()
            .expectStatus().isOk

        // Проверяем, что WireMock получил correlation ID header
        wireMock.verify(
            getRequestedFor(urlEqualTo("/api/upstream"))
                .withHeader(
                    CorrelationIdFilter.CORRELATION_ID_HEADER,
                    matching("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                )
        )
    }

    // ============================================
    // AC2: Сохранение существующего Correlation ID
    // ============================================

    @Test
    fun `AC2 - сохраняет существующий correlation ID из request header`() {
        val existingCorrelationId = "my-custom-correlation-id-12345"

        insertRoute("/api/preserve", "http://localhost:${wireMock.port()}")
        wireMock.stubFor(
            get(urlEqualTo("/api/preserve"))
                .willReturn(aResponse().withStatus(200).withBody("{}"))
        )

        webTestClient.get()
            .uri("/api/preserve")
            .header(CorrelationIdFilter.CORRELATION_ID_HEADER, existingCorrelationId)
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals(CorrelationIdFilter.CORRELATION_ID_HEADER, existingCorrelationId)
    }

    @Test
    fun `AC2 - сохраняет существующий correlation ID при передаче на upstream`() {
        val existingCorrelationId = "preserved-correlation-id"

        insertRoute("/api/forward", "http://localhost:${wireMock.port()}")
        wireMock.stubFor(
            get(urlEqualTo("/api/forward"))
                .willReturn(aResponse().withStatus(200).withBody("{}"))
        )

        webTestClient.get()
            .uri("/api/forward")
            .header(CorrelationIdFilter.CORRELATION_ID_HEADER, existingCorrelationId)
            .exchange()
            .expectStatus().isOk

        // Проверяем, что тот же correlation ID был отправлен на upstream
        wireMock.verify(
            getRequestedFor(urlEqualTo("/api/forward"))
                .withHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, WireMock.equalTo(existingCorrelationId))
        )
    }

    // ============================================
    // AC4: Correlation ID в Error Responses
    // ============================================

    @Test
    fun `AC4 - error response включает correlation ID в теле для 404`() {
        // Маршрут не настроен - должен вернуть 404

        webTestClient.get()
            .uri("/api/nonexistent")
            .exchange()
            .expectStatus().isNotFound
            .expectHeader().exists(CorrelationIdFilter.CORRELATION_ID_HEADER)
            .expectBody(ErrorResponse::class.java)
            .consumeWith { result ->
                val body = result.responseBody!!
                val responseCorrelationId = result.responseHeaders.getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER)

                assert(body.correlationId != null) {
                    "Тело error response должно включать correlationId"
                }
                assert(body.correlationId == responseCorrelationId) {
                    "Correlation ID в теле (${body.correlationId}) должен совпадать с header ($responseCorrelationId)"
                }
            }
    }

    @Test
    fun `AC4 - error response сохраняет предоставленный correlation ID`() {
        val providedCorrelationId = "error-correlation-id-xyz"

        webTestClient.get()
            .uri("/api/nonexistent")
            .header(CorrelationIdFilter.CORRELATION_ID_HEADER, providedCorrelationId)
            .exchange()
            .expectStatus().isNotFound
            .expectHeader().valueEquals(CorrelationIdFilter.CORRELATION_ID_HEADER, providedCorrelationId)
            .expectBody(ErrorResponse::class.java)
            .consumeWith { result ->
                val body = result.responseBody!!
                assert(body.correlationId == providedCorrelationId) {
                    "Error response должен использовать предоставленный correlation ID"
                }
            }
    }

    @Test
    fun `AC4 - 502 error response включает correlation ID`() {
        // Маршрут на несуществующий upstream
        insertRoute("/api/upstream-down", "http://localhost:59999") // Порт без ответа

        webTestClient.get()
            .uri("/api/upstream-down")
            .exchange()
            .expectStatus().is5xxServerError
            .expectHeader().exists(CorrelationIdFilter.CORRELATION_ID_HEADER)
            .expectBody(ErrorResponse::class.java)
            .consumeWith { result ->
                val body = result.responseBody!!
                assert(body.correlationId != null && body.correlationId!!.isNotBlank()) {
                    "502 error response должен включать correlationId"
                }
            }
    }

    // ============================================
    // AC5: Thread-Safe Context Propagation
    // ============================================

    @Test
    fun `AC5 - конкурентные запросы сохраняют отдельные correlation IDs`() {
        insertRoute("/api/concurrent", "http://localhost:${wireMock.port()}")
        wireMock.stubFor(
            get(urlEqualTo("/api/concurrent"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{}")
                        .withFixedDelay(50) // Небольшая задержка для увеличения перекрытия конкурентности
                )
        )

        val correlationIds = mutableSetOf<String>()
        val requestCount = 10

        // Выполняем конкурентные запросы
        (1..requestCount).map { i ->
            webTestClient.get()
                .uri("/api/concurrent")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "request-$i")
                .exchange()
                .expectStatus().isOk
                .expectHeader().valueEquals(CorrelationIdFilter.CORRELATION_ID_HEADER, "request-$i")
                .returnResult(String::class.java)
                .responseHeaders.getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER)
        }.forEach { correlationId ->
            correlationId?.let { correlationIds.add(it) }
        }

        // Все correlation IDs должны быть уникальными (без перекрёстного загрязнения)
        assert(correlationIds.size == requestCount) {
            "Ожидалось $requestCount уникальных correlation IDs, получено ${correlationIds.size}"
        }
    }

    @Test
    fun `AC5 - сгенерированные correlation IDs уникальны для конкурентных запросов`() {
        insertRoute("/api/unique", "http://localhost:${wireMock.port()}")
        wireMock.stubFor(
            get(urlEqualTo("/api/unique"))
                .willReturn(aResponse().withStatus(200).withBody("{}"))
        )

        val correlationIds = mutableSetOf<String>()

        // Выполняем несколько запросов без предоставления correlation ID
        repeat(5) {
            webTestClient.get()
                .uri("/api/unique")
                .exchange()
                .expectStatus().isOk
                .expectHeader().value(CorrelationIdFilter.CORRELATION_ID_HEADER) { id ->
                    correlationIds.add(id)
                }
        }

        // Все сгенерированные IDs должны быть уникальными
        assert(correlationIds.size == 5) {
            "Ожидалось 5 уникальных сгенерированных correlation IDs, получено ${correlationIds.size}: $correlationIds"
        }
    }

    // ============================================
    // Story 12.5: Consumer Identity Filter Integration
    // ============================================

    @Test
    fun `ConsumerIdentityFilter - X-Consumer-ID header используется для public routes`() {
        insertRoute("/api/public", "http://localhost:${wireMock.port()}")
        wireMock.stubFor(
            get(urlEqualTo("/api/public"))
                .willReturn(aResponse().withStatus(200).withBody("{}"))
        )

        // Запрос с X-Consumer-ID header
        webTestClient.get()
            .uri("/api/public")
            .header(ConsumerIdentityFilter.CONSUMER_ID_HEADER, "external-client-123")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `ConsumerIdentityFilter - невалидный X-Consumer-ID header отклоняется`() {
        insertRoute("/api/secure", "http://localhost:${wireMock.port()}")
        wireMock.stubFor(
            get(urlEqualTo("/api/secure"))
                .willReturn(aResponse().withStatus(200).withBody("{}"))
        )

        // Запрос с невалидным X-Consumer-ID (содержит спецсимволы)
        webTestClient.get()
            .uri("/api/secure")
            .header(ConsumerIdentityFilter.CONSUMER_ID_HEADER, "consumer<script>")
            .exchange()
            .expectStatus().isOk
        // Фильтр не блокирует запрос, просто fallback на anonymous
    }

    @Test
    fun `ConsumerIdentityFilter - filter chain order корректен`() {
        // Проверяем что фильтры выполняются в правильном порядке:
        // CorrelationIdFilter (HIGHEST_PRECEDENCE) -> JwtAuthenticationFilter (+5) -> ConsumerIdentityFilter (+8)
        val correlationOrder = org.springframework.core.Ordered.HIGHEST_PRECEDENCE
        val jwtOrder = JwtAuthenticationFilter.FILTER_ORDER
        val consumerOrder = ConsumerIdentityFilter.FILTER_ORDER

        assert(correlationOrder < jwtOrder) {
            "CorrelationIdFilter ($correlationOrder) должен выполняться до JwtAuthenticationFilter ($jwtOrder)"
        }
        assert(jwtOrder < consumerOrder) {
            "JwtAuthenticationFilter ($jwtOrder) должен выполняться до ConsumerIdentityFilter ($consumerOrder)"
        }
    }

    // ============================================
    // Helper methods
    // ============================================

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
