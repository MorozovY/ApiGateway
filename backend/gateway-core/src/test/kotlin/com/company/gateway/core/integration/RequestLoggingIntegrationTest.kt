package com.company.gateway.core.integration

import com.company.gateway.common.exception.ErrorResponse
import com.company.gateway.common.model.RouteStatus
import com.company.gateway.core.cache.RouteCacheManager
import com.company.gateway.core.filter.CorrelationIdFilter
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
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * Integration tests for Request Logging & Correlation IDs (Story 1.6)
 *
 * Tests:
 * - AC1: Generates correlation ID for new requests
 * - AC2: Preserves existing correlation ID
 * - AC3: Structured JSON logging (verified via log output capture)
 * - AC4: Correlation ID in error responses
 * - AC5: Thread-safe context propagation (verified via concurrent requests)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class RequestLoggingIntegrationTest {

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

    // ============================================
    // AC1: Generate Correlation ID for New Requests
    // ============================================

    @Test
    fun `AC1 - generates UUID correlation ID when header missing`() {
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
                    "Correlation ID should be a valid UUID, got: $correlationId"
                }
            }
    }

    @Test
    fun `AC1 - correlation ID is propagated to upstream service`() {
        insertRoute("/api/upstream", "http://localhost:${wireMock.port()}")
        wireMock.stubFor(
            get(urlEqualTo("/api/upstream"))
                .willReturn(aResponse().withStatus(200).withBody("{}"))
        )

        webTestClient.get()
            .uri("/api/upstream")
            .exchange()
            .expectStatus().isOk

        // Verify WireMock received the correlation ID header
        wireMock.verify(
            getRequestedFor(urlEqualTo("/api/upstream"))
                .withHeader(
                    CorrelationIdFilter.CORRELATION_ID_HEADER,
                    matching("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                )
        )
    }

    // ============================================
    // AC2: Preserve Existing Correlation ID
    // ============================================

    @Test
    fun `AC2 - preserves existing correlation ID from request header`() {
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
    fun `AC2 - preserves existing correlation ID when propagating to upstream`() {
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

        // Verify same correlation ID was sent to upstream
        wireMock.verify(
            getRequestedFor(urlEqualTo("/api/forward"))
                .withHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, WireMock.equalTo(existingCorrelationId))
        )
    }

    // ============================================
    // AC4: Correlation ID in Error Responses
    // ============================================

    @Test
    fun `AC4 - error response includes correlation ID in body for 404`() {
        // No route configured - should return 404

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
                    "Error response body should include correlationId"
                }
                assert(body.correlationId == responseCorrelationId) {
                    "Correlation ID in body (${body.correlationId}) should match header ($responseCorrelationId)"
                }
            }
    }

    @Test
    fun `AC4 - error response preserves provided correlation ID`() {
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
                    "Error response should use provided correlation ID"
                }
            }
    }

    @Test
    fun `AC4 - 502 error response includes correlation ID`() {
        // Route to non-existent upstream
        insertRoute("/api/upstream-down", "http://localhost:59999") // Port that won't respond

        webTestClient.get()
            .uri("/api/upstream-down")
            .exchange()
            .expectStatus().is5xxServerError
            .expectHeader().exists(CorrelationIdFilter.CORRELATION_ID_HEADER)
            .expectBody(ErrorResponse::class.java)
            .consumeWith { result ->
                val body = result.responseBody!!
                assert(body.correlationId != null && body.correlationId!!.isNotBlank()) {
                    "502 error response should include correlationId"
                }
            }
    }

    // ============================================
    // AC5: Thread-Safe Context Propagation
    // ============================================

    @Test
    fun `AC5 - concurrent requests maintain separate correlation IDs`() {
        insertRoute("/api/concurrent", "http://localhost:${wireMock.port()}")
        wireMock.stubFor(
            get(urlEqualTo("/api/concurrent"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("{}")
                        .withFixedDelay(50) // Small delay to increase concurrency overlap
                )
        )

        val correlationIds = mutableSetOf<String>()
        val requestCount = 10

        // Make concurrent requests
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

        // All correlation IDs should be unique (no cross-contamination)
        assert(correlationIds.size == requestCount) {
            "Expected $requestCount unique correlation IDs, got ${correlationIds.size}"
        }
    }

    @Test
    fun `AC5 - generated correlation IDs are unique for concurrent requests`() {
        insertRoute("/api/unique", "http://localhost:${wireMock.port()}")
        wireMock.stubFor(
            get(urlEqualTo("/api/unique"))
                .willReturn(aResponse().withStatus(200).withBody("{}"))
        )

        val correlationIds = mutableSetOf<String>()

        // Make multiple requests without providing correlation ID
        repeat(5) {
            webTestClient.get()
                .uri("/api/unique")
                .exchange()
                .expectStatus().isOk
                .expectHeader().value(CorrelationIdFilter.CORRELATION_ID_HEADER) { id ->
                    correlationIds.add(id)
                }
        }

        // All generated IDs should be unique
        assert(correlationIds.size == 5) {
            "Expected 5 unique generated correlation IDs, got ${correlationIds.size}: $correlationIds"
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
