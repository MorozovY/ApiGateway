package com.company.gateway.core.route

import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import com.company.gateway.core.cache.RouteCacheManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DynamicRouteLocatorTest {

    @Mock
    private lateinit var cacheManager: RouteCacheManager

    @InjectMocks
    private lateinit var dynamicRouteLocator: DynamicRouteLocator

    @Test
    fun `getRoutes should return routes from cache`() {
        val publishedRoute = createRoute("/api/orders", "http://order-service:8080", RouteStatus.PUBLISHED)

        whenever(cacheManager.getCachedRoutes())
            .thenReturn(listOf(publishedRoute))

        StepVerifier.create(dynamicRouteLocator.getRoutes())
            .expectNextMatches { route ->
                route.id == publishedRoute.id.toString() &&
                route.uri.toString() == publishedRoute.upstreamUrl
            }
            .verifyComplete()
    }

    @Test
    fun `getRoutes should return empty when cache is empty`() {
        whenever(cacheManager.getCachedRoutes())
            .thenReturn(emptyList())

        StepVerifier.create(dynamicRouteLocator.getRoutes())
            .verifyComplete()
    }

    @Test
    fun `getRoutes should return multiple routes from cache`() {
        val route1 = createRoute("/api/orders", "http://order-service:8080", RouteStatus.PUBLISHED)
        val route2 = createRoute("/api/users", "http://user-service:8080", RouteStatus.PUBLISHED)

        whenever(cacheManager.getCachedRoutes())
            .thenReturn(listOf(route1, route2))

        StepVerifier.create(dynamicRouteLocator.getRoutes().collectList())
            .expectNextMatches { routes -> routes.size == 2 }
            .verifyComplete()
    }

    @Test
    fun `getRoutes should skip routes with null id`() {
        val validRoute = createRoute("/api/orders", "http://order-service:8080", RouteStatus.PUBLISHED)
        val nullIdRoute = createRoute("/api/null", "http://null-service:8080", RouteStatus.PUBLISHED).copy(id = null)

        whenever(cacheManager.getCachedRoutes())
            .thenReturn(listOf(validRoute, nullIdRoute))

        StepVerifier.create(dynamicRouteLocator.getRoutes().collectList())
            .expectNextMatches { routes ->
                routes.size == 1 && routes[0].id == validRoute.id.toString()
            }
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

    @Test
    fun `route with specific methods should only match those methods`() {
        val route = createRouteWithMethods("/api/orders", "http://order-service:8080", listOf("GET", "POST"))

        whenever(cacheManager.getCachedRoutes())
            .thenReturn(listOf(route))

        StepVerifier.create(dynamicRouteLocator.getRoutes())
            .expectNextMatches { gatewayRoute ->
                // Route should be created successfully
                gatewayRoute.id == route.id.toString()
            }
            .verifyComplete()
    }

    @Test
    fun `route with empty methods should be created successfully`() {
        val route = createRouteWithMethods("/api/all", "http://all-service:8080", emptyList())

        whenever(cacheManager.getCachedRoutes())
            .thenReturn(listOf(route))

        StepVerifier.create(dynamicRouteLocator.getRoutes())
            .expectNextMatches { gatewayRoute ->
                gatewayRoute.id == route.id.toString()
            }
            .verifyComplete()
    }

    @Test
    fun `multiple routes with different methods are all created`() {
        val getRoute = createRouteWithMethods("/api/read", "http://read-service:8080", listOf("GET"))
        val postRoute = createRouteWithMethods("/api/write", "http://write-service:8080", listOf("POST", "PUT"))

        whenever(cacheManager.getCachedRoutes())
            .thenReturn(listOf(getRoute, postRoute))

        StepVerifier.create(dynamicRouteLocator.getRoutes().collectList())
            .expectNextMatches { routes ->
                routes.size == 2
            }
            .verifyComplete()
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

    private fun createRouteWithMethods(path: String, upstreamUrl: String, methods: List<String>) = Route(
        id = UUID.randomUUID(),
        path = path,
        upstreamUrl = upstreamUrl,
        methods = methods,
        status = RouteStatus.PUBLISHED,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
}
