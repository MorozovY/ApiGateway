package com.company.gateway.core.route

import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import com.company.gateway.core.repository.RouteRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DynamicRouteLocatorTest {

    @Mock
    private lateinit var routeRepository: RouteRepository

    @InjectMocks
    private lateinit var dynamicRouteLocator: DynamicRouteLocator

    @Test
    fun `getRoutes should return routes for published status only`() {
        val publishedRoute = createRoute("/api/orders", "http://order-service:8080", RouteStatus.PUBLISHED)

        whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
            .thenReturn(Flux.just(publishedRoute))

        StepVerifier.create(dynamicRouteLocator.getRoutes())
            .expectNextMatches { route ->
                route.id == publishedRoute.id.toString() &&
                route.uri.toString() == publishedRoute.upstreamUrl
            }
            .verifyComplete()
    }

    @Test
    fun `getRoutes should return empty when no published routes`() {
        whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
            .thenReturn(Flux.empty())

        StepVerifier.create(dynamicRouteLocator.getRoutes())
            .verifyComplete()
    }

    @Test
    fun `getRoutes should return multiple routes`() {
        val route1 = createRoute("/api/orders", "http://order-service:8080", RouteStatus.PUBLISHED)
        val route2 = createRoute("/api/users", "http://user-service:8080", RouteStatus.PUBLISHED)

        whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
            .thenReturn(Flux.just(route1, route2))

        StepVerifier.create(dynamicRouteLocator.getRoutes().collectList())
            .expectNextMatches { routes -> routes.size == 2 }
            .verifyComplete()
    }

    @Test
    fun `matchesPrefix should match exact path`() {
        assert(dynamicRouteLocator.matchesPrefix("/api/orders", "/api/orders"))
    }

    @Test
    fun `matchesPrefix should match path with trailing slash`() {
        assert(dynamicRouteLocator.matchesPrefix("/api/orders/", "/api/orders"))
    }

    @Test
    fun `matchesPrefix should match path with ID suffix`() {
        assert(dynamicRouteLocator.matchesPrefix("/api/orders/123", "/api/orders"))
    }

    @Test
    fun `matchesPrefix should match nested path`() {
        assert(dynamicRouteLocator.matchesPrefix("/api/orders/123/items", "/api/orders"))
    }

    @Test
    fun `matchesPrefix should NOT match path without separator`() {
        assert(!dynamicRouteLocator.matchesPrefix("/api/ordershistory", "/api/orders"))
    }

    @Test
    fun `matchesPrefix should NOT match different path`() {
        assert(!dynamicRouteLocator.matchesPrefix("/api/users", "/api/orders"))
    }

    @Test
    fun `matchesPrefix should NOT match partial prefix`() {
        assert(!dynamicRouteLocator.matchesPrefix("/api/ord", "/api/orders"))
    }

    private fun createRoute(path: String, upstreamUrl: String, status: RouteStatus) = Route(
        id = UUID.randomUUID(),
        path = path,
        upstreamUrl = upstreamUrl,
        methods = listOf("GET", "POST"),
        status = status,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
}
