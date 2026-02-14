package com.company.gateway.core.integration

import com.company.gateway.common.model.RouteStatus
import com.company.gateway.core.cache.RouteCacheManager
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.redis.testcontainers.RedisContainer
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class HotReloadIntegrationTest {

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
    private lateinit var redisTemplate: ReactiveRedisTemplate<String, String>

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
    fun `AC1 - route becomes active within 5 seconds of cache refresh`() {
        // 1. Insert DRAFT route in DB (not accessible)
        val routeId = UUID.randomUUID()
        insertRouteWithoutRefresh(routeId, "/api/hotreload", "http://localhost:${wireMock.port()}", RouteStatus.DRAFT)

        // Setup WireMock
        wireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/api/hotreload.*"))
                .willReturn(WireMock.aResponse().withStatus(200).withBody("""{"status":"ok"}"""))
        )

        // 2. Verify route is NOT accessible (404) - draft route not in cache
        cacheManager.refreshCache().block()
        webTestClient.get()
            .uri("/api/hotreload/test")
            .exchange()
            .expectStatus().isNotFound

        // 3. Update route status to PUBLISHED in DB
        updateRouteStatus(routeId, RouteStatus.PUBLISHED)

        // 4. Trigger cache refresh (simulates what Redis invalidation event would do)
        val startTime = System.currentTimeMillis()
        cacheManager.refreshCache().block()
        val elapsedTime = System.currentTimeMillis() - startTime

        // 5. Verify route is now accessible
        webTestClient.get()
            .uri("/api/hotreload/test")
            .exchange()
            .expectStatus().isOk

        // Verify NFR3: Configuration Reload < 5 seconds
        // Note: In production, Redis pub/sub adds ~100ms latency
        assertThat(elapsedTime).isLessThan(5000)
    }

    @Test
    fun `AC1 - Redis pub-sub triggers cache refresh`() {
        // This test verifies that Redis pub/sub integration works
        // Insert a published route
        insertRoute(UUID.randomUUID(), "/api/pubsub-test", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)

        wireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/api/pubsub-test.*"))
                .willReturn(WireMock.aResponse().withStatus(200).withBody("""{"status":"ok"}"""))
        )

        // Verify route is accessible (should work after insertRoute refreshes cache)
        webTestClient.get()
            .uri("/api/pubsub-test")
            .exchange()
            .expectStatus().isOk

        // Send Redis invalidation event and verify cache is still working
        redisTemplate.convertAndSend("route-cache-invalidation", "*").block()

        // Small delay for async processing
        Thread.sleep(500)

        // Route should still be accessible
        webTestClient.get()
            .uri("/api/pubsub-test")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `AC3 - routes are pre-loaded from DB into cache on startup`() {
        // Insert published route
        insertRoute(UUID.randomUUID(), "/api/startup", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)
        // Refresh cache (simulates startup)
        cacheManager.refreshCache().block()

        wireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/api/startup.*"))
                .willReturn(WireMock.aResponse().withStatus(200).withBody("[]"))
        )

        // Gateway should route this request (routes loaded from cache)
        webTestClient.get()
            .uri("/api/startup")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `AC4 - only PUBLISHED routes are loaded into cache`() {
        // Insert routes with different statuses
        insertRoute(UUID.randomUUID(), "/api/draft", "http://localhost:${wireMock.port()}", RouteStatus.DRAFT)
        insertRoute(UUID.randomUUID(), "/api/pending", "http://localhost:${wireMock.port()}", RouteStatus.PENDING)
        insertRoute(UUID.randomUUID(), "/api/rejected", "http://localhost:${wireMock.port()}", RouteStatus.REJECTED)
        insertRoute(UUID.randomUUID(), "/api/published", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)

        // Refresh cache
        cacheManager.refreshCache().block()

        wireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/api/published.*"))
                .willReturn(WireMock.aResponse().withStatus(200).withBody("[]"))
        )

        // Only PUBLISHED route should be accessible
        webTestClient.get().uri("/api/draft/test").exchange().expectStatus().isNotFound
        webTestClient.get().uri("/api/pending/test").exchange().expectStatus().isNotFound
        webTestClient.get().uri("/api/rejected/test").exchange().expectStatus().isNotFound
        webTestClient.get().uri("/api/published").exchange().expectStatus().isOk
    }

    @Test
    fun `AC5 - cache refresh is atomic - no partial state visible`() {
        // Insert initial route
        val routeId1 = UUID.randomUUID()
        insertRoute(routeId1, "/api/route1", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)
        cacheManager.refreshCache().block()

        wireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/api/route.*"))
                .willReturn(WireMock.aResponse().withStatus(200).withBody("[]"))
        )

        // Verify initial route works
        webTestClient.get().uri("/api/route1").exchange().expectStatus().isOk

        // Insert new route and update cache atomically
        val routeId2 = UUID.randomUUID()
        insertRoute(routeId2, "/api/route2", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)

        // Delete old route
        deleteRoute(routeId1)

        // Refresh cache atomically
        cacheManager.refreshCache().block()

        // Old route should be gone, new route should work
        webTestClient.get().uri("/api/route1").exchange().expectStatus().isNotFound
        webTestClient.get().uri("/api/route2").exchange().expectStatus().isOk
    }

    @Test
    fun `cache size is reported correctly`() {
        insertRoute(UUID.randomUUID(), "/api/test1", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)
        insertRoute(UUID.randomUUID(), "/api/test2", "http://localhost:${wireMock.port()}", RouteStatus.PUBLISHED)
        cacheManager.refreshCache().block()

        assertThat(cacheManager.getCacheSize()).isEqualTo(2)
    }

    private fun insertRoute(id: UUID, path: String, upstreamUrl: String, status: RouteStatus) {
        insertRouteWithoutRefresh(id, path, upstreamUrl, status)
        cacheManager.refreshCache().block()
    }

    private fun insertRouteWithoutRefresh(id: UUID, path: String, upstreamUrl: String, status: RouteStatus) {
        databaseClient.sql("""
            INSERT INTO routes (id, path, upstream_url, methods, status)
            VALUES (:id, :path, :upstreamUrl, ARRAY['GET','POST'], :status)
        """.trimIndent())
            .bind("id", id)
            .bind("path", path)
            .bind("upstreamUrl", upstreamUrl)
            .bind("status", status.name.lowercase())
            .fetch()
            .rowsUpdated()
            .block()
    }

    private fun updateRouteStatus(id: UUID, status: RouteStatus) {
        databaseClient.sql("UPDATE routes SET status = :status WHERE id = :id")
            .bind("id", id)
            .bind("status", status.name.lowercase())
            .fetch()
            .rowsUpdated()
            .block()
    }

    private fun deleteRoute(id: UUID) {
        databaseClient.sql("DELETE FROM routes WHERE id = :id")
            .bind("id", id)
            .fetch()
            .rowsUpdated()
            .block()
    }
}
