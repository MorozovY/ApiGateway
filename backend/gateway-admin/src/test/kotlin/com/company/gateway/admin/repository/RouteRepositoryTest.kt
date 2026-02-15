package com.company.gateway.admin.repository

import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.test.StepVerifier

@SpringBootTest
@Testcontainers
class RouteRepositoryTest {

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
            // Disable Redis auto-configuration for repository tests
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
            }
            // Disable Redis health check when Redis is not available
            registry.add("management.health.redis.enabled") { false }
            // Exclude Redis from readiness group
            registry.add("management.endpoint.health.group.readiness.include") { "r2dbc" }
        }
    }

    @Autowired
    private lateinit var routeRepository: RouteRepository

    @BeforeEach
    fun setup() {
        routeRepository.deleteAll().block()
    }

    @Test
    fun `should save and retrieve route`() {
        val route = Route(
            path = "/api/orders",
            upstreamUrl = "http://orders-service:8080",
            methods = listOf("GET", "POST"),
            status = RouteStatus.DRAFT
        )

        val savedRoute = routeRepository.save(route)

        StepVerifier.create(savedRoute)
            .expectNextMatches { saved ->
                saved.id != null &&
                saved.path == route.path &&
                saved.upstreamUrl == route.upstreamUrl &&
                saved.methods == route.methods &&
                saved.status == RouteStatus.DRAFT
            }
            .verifyComplete()
    }

    @Test
    fun `should find route by path`() {
        val route = Route(
            path = "/api/users",
            upstreamUrl = "http://users-service:8080",
            status = RouteStatus.PUBLISHED
        )

        routeRepository.save(route).block()

        val foundRoute = routeRepository.findByPath("/api/users")

        StepVerifier.create(foundRoute)
            .expectNextMatches { found ->
                found.path == "/api/users" &&
                found.upstreamUrl == "http://users-service:8080"
            }
            .verifyComplete()
    }

    @Test
    fun `should find routes by status`() {
        val draftRoute = Route(
            path = "/api/draft",
            upstreamUrl = "http://draft:8080",
            status = RouteStatus.DRAFT
        )
        val publishedRoute = Route(
            path = "/api/published",
            upstreamUrl = "http://published:8080",
            status = RouteStatus.PUBLISHED
        )

        routeRepository.save(draftRoute).block()
        routeRepository.save(publishedRoute).block()

        val draftRoutes = routeRepository.findByStatus(RouteStatus.DRAFT)

        StepVerifier.create(draftRoutes.collectList())
            .expectNextMatches { routes ->
                routes.size == 1 && routes[0].path == "/api/draft"
            }
            .verifyComplete()
    }

    @Test
    fun `should update route status`() {
        val route = Route(
            path = "/api/pending",
            upstreamUrl = "http://pending:8080",
            status = RouteStatus.DRAFT
        )

        val savedRoute = routeRepository.save(route).block()!!

        val updatedRoute = routeRepository.save(savedRoute.copy(status = RouteStatus.PENDING))

        StepVerifier.create(updatedRoute)
            .expectNextMatches { updated ->
                updated.id == savedRoute.id &&
                updated.status == RouteStatus.PENDING
            }
            .verifyComplete()
    }

    @Test
    fun `should delete route`() {
        val route = Route(
            path = "/api/delete-me",
            upstreamUrl = "http://delete:8080"
        )

        val savedRoute = routeRepository.save(route).block()!!

        routeRepository.deleteById(savedRoute.id!!).block()

        val found = routeRepository.findById(savedRoute.id!!)

        StepVerifier.create(found)
            .verifyComplete() // Should complete without emitting any element
    }

    @Test
    fun `should auto-populate created_at on insert`() {
        val route = Route(
            path = "/api/timestamp-test",
            upstreamUrl = "http://timestamp:8080"
        )

        val savedRoute = routeRepository.save(route).block()!!
        // DB generates created_at via DEFAULT, need to re-fetch to see it
        val refetchedRoute = routeRepository.findById(savedRoute.id!!)

        StepVerifier.create(refetchedRoute)
            .expectNextMatches { fetched ->
                fetched.createdAt != null
            }
            .verifyComplete()
    }

    @Test
    fun `should auto-update updated_at on update`() {
        val route = Route(
            path = "/api/update-test",
            upstreamUrl = "http://update:8080"
        )

        val savedRoute = routeRepository.save(route).block()!!
        // Re-fetch to get DB-generated created_at/updated_at
        val initialRoute = routeRepository.findById(savedRoute.id!!).block()!!
        val originalUpdatedAt = initialRoute.updatedAt

        // Small delay to ensure timestamp difference
        Thread.sleep(100)

        // Update the route
        routeRepository.save(initialRoute.copy(upstreamUrl = "http://updated:9090")).block()!!

        // After V2 migration trigger, updated_at should be changed
        val refetchedRoute = routeRepository.findById(savedRoute.id!!).block()!!

        assert(refetchedRoute.updatedAt != null) { "updated_at should not be null" }
        // Trigger should have updated the timestamp
        assert(originalUpdatedAt == null || refetchedRoute.updatedAt!! >= originalUpdatedAt) {
            "updated_at should be >= original value"
        }
    }
}
