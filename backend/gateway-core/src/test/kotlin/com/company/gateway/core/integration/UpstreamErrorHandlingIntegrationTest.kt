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

/**
 * Integration tests for upstream error handling (Story 1.4)
 *
 * Tests AC1-AC5:
 * - AC1: Connection refused -> 502 Bad Gateway with RFC 7807
 * - AC2: Upstream timeout -> 504 Gateway Timeout with RFC 7807
 * - AC3: Upstream 5xx -> Passthrough unchanged
 * - AC4: All gateway-generated errors include correlationId (placeholder)
 * - AC5: No internal details exposed in errors
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class UpstreamErrorHandlingIntegrationTest {

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

        // Port that is guaranteed to be closed (no service listening)
        const val DEAD_PORT = 59999

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
    fun `AC1 - connection refused returns 502 Bad Gateway with RFC 7807 format`() {
        // Arrange: insert route pointing to closed port (connection refused)
        insertRoute("/api/dead-service", "http://localhost:$DEAD_PORT", RouteStatus.PUBLISHED)

        // Act & Assert
        webTestClient.get()
            .uri("/api/dead-service/test")
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectHeader().contentType("application/json")
            .expectBody(ErrorResponse::class.java)
            .consumeWith { result ->
                val body = result.responseBody!!
                assert(body.type == "https://api.gateway/errors/upstream-unavailable") {
                    "Expected type 'upstream-unavailable', got '${body.type}'"
                }
                assert(body.title == "Bad Gateway") {
                    "Expected title 'Bad Gateway', got '${body.title}'"
                }
                assert(body.status == 502) {
                    "Expected status 502, got ${body.status}"
                }
                assert(body.detail == "Upstream service is unavailable") {
                    "Expected detail 'Upstream service is unavailable', got '${body.detail}'"
                }
                assert(body.instance == "/api/dead-service/test") {
                    "Expected instance '/api/dead-service/test', got '${body.instance}'"
                }
            }
    }

    @Test
    fun `AC5 - connection refused error does not expose internal details`() {
        insertRoute("/api/dead-service", "http://localhost:$DEAD_PORT", RouteStatus.PUBLISHED)

        webTestClient.get()
            .uri("/api/dead-service/test")
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectBody()
            .consumeWith { result ->
                val bodyString = String(result.responseBody ?: ByteArray(0))
                // Should NOT contain internal details
                assert(!bodyString.contains("localhost:$DEAD_PORT", ignoreCase = true)) {
                    "Error response should not expose upstream host:port"
                }
                assert(!bodyString.contains("ConnectException", ignoreCase = true)) {
                    "Error response should not expose exception class names"
                }
                assert(!bodyString.contains("stackTrace", ignoreCase = true)) {
                    "Error response should not contain stack traces"
                }
            }
    }

    // ============================================
    // AC2: Upstream timeout -> 504 Gateway Timeout
    // ============================================

    @Test
    fun `AC2 - slow upstream returns 504 Gateway Timeout with RFC 7807 format`() {
        // Arrange: configure WireMock with delay longer than timeout (3s + buffer)
        wireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching("/api/slow.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withFixedDelay(5000) // 5 seconds > 3s timeout
                        .withStatus(200)
                        .withBody("{}")
                )
        )

        insertRoute("/api/slow", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)

        // Act & Assert
        webTestClient.get()
            .uri("/api/slow/test")
            .exchange()
            .expectStatus().isEqualTo(504)
            .expectHeader().contentType("application/json")
            .expectBody(ErrorResponse::class.java)
            .consumeWith { result ->
                val body = result.responseBody!!
                assert(body.type == "https://api.gateway/errors/upstream-timeout") {
                    "Expected type 'upstream-timeout', got '${body.type}'"
                }
                assert(body.title == "Gateway Timeout") {
                    "Expected title 'Gateway Timeout', got '${body.title}'"
                }
                assert(body.status == 504) {
                    "Expected status 504, got ${body.status}"
                }
                assert(body.detail == "Upstream service did not respond in time") {
                    "Expected detail 'Upstream service did not respond in time', got '${body.detail}'"
                }
            }
    }

    @Test
    fun `AC5 - timeout error does not expose internal details`() {
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
                // Should NOT contain internal details
                assert(!bodyString.contains("TimeoutException", ignoreCase = true)) {
                    "Error response should not expose exception class names"
                }
                assert(!bodyString.contains("ReadTimeoutException", ignoreCase = true)) {
                    "Error response should not expose internal exception types"
                }
            }
    }

    // ============================================
    // AC3: Upstream 5xx -> Passthrough unchanged
    // ============================================

    @Test
    fun `AC3 - upstream 500 is passed through unchanged`() {
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
    fun `AC3 - upstream 502 is passed through unchanged`() {
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
    fun `AC3 - upstream 503 is passed through unchanged`() {
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
    fun `AC3 - upstream 504 is passed through unchanged`() {
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
    fun `AC4 - error response structure includes correlationId field (null until Story 1_6)`() {
        insertRoute("/api/dead-service", "http://localhost:$DEAD_PORT", RouteStatus.PUBLISHED)

        webTestClient.get()
            .uri("/api/dead-service/test")
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectBody()
            .consumeWith { result ->
                val bodyString = String(result.responseBody ?: ByteArray(0))

                // Verify RFC 7807 required fields are present
                assert(bodyString.contains("\"type\"")) {
                    "RFC 7807 error response must contain 'type' field"
                }
                assert(bodyString.contains("\"title\"")) {
                    "RFC 7807 error response must contain 'title' field"
                }
                assert(bodyString.contains("\"status\"")) {
                    "RFC 7807 error response must contain 'status' field"
                }
                assert(bodyString.contains("\"detail\"")) {
                    "RFC 7807 error response must contain 'detail' field"
                }
                assert(bodyString.contains("\"instance\"")) {
                    "RFC 7807 error response must contain 'instance' field"
                }

                // Note: correlationId is null for now (JsonInclude.NON_NULL excludes it)
                // Story 1.6 will add actual correlationId generation
                // This test validates the response follows RFC 7807 structure
            }
    }

    @Test
    fun `AC4 - ErrorResponse class has correlationId field defined`() {
        // Verify ErrorResponse data class has correlationId field (compile-time check)
        val errorResponse = ErrorResponse(
            type = "https://api.gateway/errors/test",
            title = "Test",
            status = 500,
            detail = "Test detail",
            instance = "/test",
            correlationId = "test-correlation-id" // Field exists and can be set
        )

        assert(errorResponse.correlationId == "test-correlation-id") {
            "ErrorResponse should have correlationId field"
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
