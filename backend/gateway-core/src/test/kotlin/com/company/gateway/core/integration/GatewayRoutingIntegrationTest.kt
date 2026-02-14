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
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class GatewayRoutingIntegrationTest {

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
            // Flyway JDBC URL for schema creation
            registry.add("spring.flyway.url", postgres::getJdbcUrl)
            registry.add("spring.flyway.user", postgres::getUsername)
            registry.add("spring.flyway.password", postgres::getPassword)
            // Redis configuration
            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.firstMappedPort }
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
    fun `AC1 - should proxy request to upstream and return response with path preserved`() {
        // Arrange: insert published route
        insertRoute("/api/orders", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)

        // Setup WireMock to respond
        wireMock.stubFor(
            WireMock.get(WireMock.urlEqualTo("/api/orders/123"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"id": 123, "status": "created"}""")
                )
        )

        // Act & Assert
        webTestClient.get()
            .uri("/api/orders/123")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(123)

        // Verify path was preserved when proxying to upstream
        wireMock.verify(
            WireMock.getRequestedFor(WireMock.urlEqualTo("/api/orders/123"))
        )
    }

    @Test
    fun `AC1 - should preserve original headers when proxying`() {
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

        // Verify WireMock received the custom header
        wireMock.verify(
            WireMock.getRequestedFor(WireMock.urlEqualTo("/api/orders/123"))
                .withHeader("X-Custom-Header", WireMock.equalTo("test-value"))
        )
    }

    @Test
    fun `AC2 - draft route should return 404`() {
        // Insert draft route - should NOT be routed
        insertRoute("/api/draft", "http://localhost:${wireMock.port()}", RouteStatus.DRAFT)

        webTestClient.get()
            .uri("/api/draft/resource")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `AC3 - unknown path should return 404 with RFC 7807 format`() {
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
    fun `AC4 - gateway loads published routes from cache`() {
        // Insert published route
        insertRoute("/api/products", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)

        wireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/api/products.*"))
                .willReturn(WireMock.aResponse().withStatus(200).withBody("[]"))
        )

        // Gateway should route this request (routes loaded from cache)
        webTestClient.get()
            .uri("/api/products")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `AC5 - path prefix matching - exact path`() {
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
    fun `AC5 - path prefix matching - path with nested resource`() {
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
    fun `AC5 - path prefix matching - should NOT match similar path without separator`() {
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
