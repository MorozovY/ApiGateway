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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayRoutingIntegrationTest {

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
            // Cache configuration
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
    fun clearRoutes() {
        databaseClient.sql("DELETE FROM routes").fetch().rowsUpdated().block()
        // Refresh cache to clear it
        cacheManager.refreshCache().block()
    }

    @AfterEach
    fun resetWireMock() {
        wireMock.resetAll()
    }

    @Test
    fun `AC1 - проксирует запрос на upstream и возвращает ответ с сохранённым путём`() {
        // Подготовка: вставляем опубликованный маршрут
        insertRoute("/api/orders", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)

        // Настраиваем WireMock для ответа
        wireMock.stubFor(
            WireMock.get(WireMock.urlEqualTo("/api/orders/123"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"id": 123, "status": "created"}""")
                )
        )

        // Выполнение и проверка
        webTestClient.get()
            .uri("/api/orders/123")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(123)

        // Проверяем, что путь сохранён при проксировании на upstream
        wireMock.verify(
            WireMock.getRequestedFor(WireMock.urlEqualTo("/api/orders/123"))
        )
    }

    @Test
    fun `AC1 - сохраняет оригинальные заголовки при проксировании`() {
        insertRoute("/api/orders", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)

        wireMock.stubFor(
            WireMock.get(WireMock.urlEqualTo("/api/orders/123"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withBody("{}")
                )
        )

        webTestClient.get()
            .uri("/api/orders/123")
            .header("X-Custom-Header", "test-value")
            .exchange()
            .expectStatus().isOk

        // Проверяем, что WireMock получил custom header
        wireMock.verify(
            WireMock.getRequestedFor(WireMock.urlEqualTo("/api/orders/123"))
                .withHeader("X-Custom-Header", WireMock.equalTo("test-value"))
        )
    }

    @Test
    fun `AC2 - черновик маршрута возвращает 404`() {
        // Вставляем черновик маршрута - НЕ должен маршрутизироваться
        insertRoute("/api/draft", "http://localhost:${wireMock.port()}", RouteStatus.DRAFT)

        webTestClient.get()
            .uri("/api/draft/resource")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `AC3 - неизвестный путь возвращает 404 в формате RFC 7807`() {
        webTestClient.get()
            .uri("/unknown/path")
            .exchange()
            .expectStatus().isNotFound
            .expectHeader().contentType("application/json")
            .expectBody(ErrorResponse::class.java)
            .consumeWith { result ->
                val body = result.responseBody!!
                assert(body.type == "https://api.gateway/errors/route-not-found")
                assert(body.title == "Not Found")
                assert(body.status == 404)
                assert(body.detail.contains("/unknown/path"))
            }
    }

    @Test
    fun `AC4 - шлюз загружает опубликованные маршруты из кэша`() {
        // Вставляем опубликованный маршрут
        insertRoute("/api/products", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)

        wireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/api/products.*"))
                .willReturn(WireMock.aResponse().withStatus(200).withBody("[]"))
        )

        // Шлюз должен маршрутизировать этот запрос (маршруты загружены из кэша)
        webTestClient.get()
            .uri("/api/products")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `AC5 - сопоставление префикса пути - точный путь`() {
        insertRoute("/api/orders", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)

        wireMock.stubFor(
            WireMock.get(WireMock.urlEqualTo("/api/orders"))
                .willReturn(WireMock.aResponse().withStatus(200).withBody("[]"))
        )

        webTestClient.get()
            .uri("/api/orders")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `AC5 - сопоставление префикса пути - путь с вложенным ресурсом`() {
        insertRoute("/api/orders", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)

        wireMock.stubFor(
            WireMock.get(WireMock.urlEqualTo("/api/orders/456/items"))
                .willReturn(WireMock.aResponse().withStatus(200).withBody("[]"))
        )

        webTestClient.get()
            .uri("/api/orders/456/items")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `AC5 - сопоставление префикса пути - НЕ должен совпадать с похожим путём без разделителя`() {
        insertRoute("/api/orders", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)

        webTestClient.get()
            .uri("/api/ordershistory")
            .exchange()
            .expectStatus().isNotFound
    }

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
