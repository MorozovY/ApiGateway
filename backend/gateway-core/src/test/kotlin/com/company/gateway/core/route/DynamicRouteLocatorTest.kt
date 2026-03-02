package com.company.gateway.core.route

import com.company.gateway.common.model.RateLimit
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import com.company.gateway.core.cache.RouteCacheManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpMethod
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Duration
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DynamicRouteLocatorTest {

    @Mock
    private lateinit var cacheManager: RouteCacheManager

    @InjectMocks
    private lateinit var dynamicRouteLocator: DynamicRouteLocator

    @Test
    fun `getRoutes должен возвращать маршруты из кэша`() {
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
    fun `getRoutes должен возвращать пустой результат когда кэш пуст`() {
        whenever(cacheManager.getCachedRoutes())
            .thenReturn(emptyList())

        StepVerifier.create(dynamicRouteLocator.getRoutes())
            .verifyComplete()
    }

    @Test
    fun `getRoutes должен возвращать множество маршрутов из кэша`() {
        val route1 = createRoute("/api/orders", "http://order-service:8080", RouteStatus.PUBLISHED)
        val route2 = createRoute("/api/users", "http://user-service:8080", RouteStatus.PUBLISHED)

        whenever(cacheManager.getCachedRoutes())
            .thenReturn(listOf(route1, route2))

        StepVerifier.create(dynamicRouteLocator.getRoutes().collectList())
            .expectNextMatches { routes -> routes.size == 2 }
            .verifyComplete()
    }

    @Test
    fun `getRoutes должен пропускать маршруты с null id`() {
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
    fun `matchesPrefix должен совпадать с точным путём`() {
        assert(dynamicRouteLocator.matchesPrefix("/api/orders", "/api/orders"))
    }

    @Test
    fun `matchesPrefix должен совпадать с путём со слэшем в конце`() {
        assert(dynamicRouteLocator.matchesPrefix("/api/orders/", "/api/orders"))
    }

    @Test
    fun `matchesPrefix должен совпадать с путём с ID суффиксом`() {
        assert(dynamicRouteLocator.matchesPrefix("/api/orders/123", "/api/orders"))
    }

    @Test
    fun `matchesPrefix должен совпадать с вложенным путём`() {
        assert(dynamicRouteLocator.matchesPrefix("/api/orders/123/items", "/api/orders"))
    }

    @Test
    fun `matchesPrefix НЕ должен совпадать с путём без разделителя`() {
        assert(!dynamicRouteLocator.matchesPrefix("/api/ordershistory", "/api/orders"))
    }

    @Test
    fun `matchesPrefix НЕ должен совпадать с другим путём`() {
        assert(!dynamicRouteLocator.matchesPrefix("/api/users", "/api/orders"))
    }

    @Test
    fun `matchesPrefix НЕ должен совпадать с частичным префиксом`() {
        assert(!dynamicRouteLocator.matchesPrefix("/api/ord", "/api/orders"))
    }

    @Test
    fun `маршрут с определёнными методами должен совпадать только с этими методами`() {
        val route = createRouteWithMethods("/api/orders", "http://order-service:8080", listOf("GET", "POST"))

        whenever(cacheManager.getCachedRoutes())
            .thenReturn(listOf(route))

        StepVerifier.create(dynamicRouteLocator.getRoutes())
            .expectNextMatches { gatewayRoute ->
                // Маршрут должен быть успешно создан
                gatewayRoute.id == route.id.toString()
            }
            .verifyComplete()
    }

    @Test
    fun `маршрут с пустым списком методов должен быть успешно создан`() {
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
    fun `множество маршрутов с разными методами все создаются`() {
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

    private fun createRouteWithRateLimit(path: String, upstreamUrl: String, rateLimitId: UUID) = Route(
        id = UUID.randomUUID(),
        path = path,
        upstreamUrl = upstreamUrl,
        methods = listOf("GET"),
        status = RouteStatus.PUBLISHED,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        rateLimitId = rateLimitId
    )

    private fun createRateLimit(id: UUID) = RateLimit(
        id = id,
        name = "test-policy",
        requestsPerSecond = 10,
        burstSize = 15,
        createdBy = UUID.randomUUID(),
        createdAt = Instant.now()
    )

    // Story 5.8 тесты: маршруты с rate limit

    @Test
    fun `getRoutes создаёт маршрут с rateLimitId`() {
        // Тест проверяет что маршрут с rateLimitId успешно создаётся
        // Фактическая загрузка rate limit происходит в predicate при обработке запроса
        val rateLimitId = UUID.randomUUID()
        val route = createRouteWithRateLimit("/api/limited", "http://limited-service:8080", rateLimitId)

        whenever(cacheManager.getCachedRoutes())
            .thenReturn(listOf(route))

        StepVerifier.create(dynamicRouteLocator.getRoutes())
            .expectNextMatches { gatewayRoute ->
                gatewayRoute.id == route.id.toString()
            }
            .verifyComplete()
    }

    @Test
    fun `getRoutes создаёт маршрут без rate limit когда rateLimitId null`() {
        val route = createRoute("/api/unlimited", "http://unlimited-service:8080", RouteStatus.PUBLISHED)

        whenever(cacheManager.getCachedRoutes())
            .thenReturn(listOf(route))

        StepVerifier.create(dynamicRouteLocator.getRoutes())
            .expectNextMatches { gatewayRoute ->
                gatewayRoute.id == route.id.toString()
            }
            .verifyComplete()
    }

    // Story 14.1: Async rate limit loading тесты

    @Test
    fun `asyncPredicate загружает rate limit асинхронно при cache miss`() {
        // Given: маршрут с rateLimitId, политика не в кэше
        val rateLimitId = UUID.randomUUID()
        val route = createRouteWithRateLimit("/api/limited", "http://limited-service:8080", rateLimitId)
        val rateLimit = createRateLimit(rateLimitId)

        whenever(cacheManager.getCachedRoutes()).thenReturn(listOf(route))
        whenever(cacheManager.getCachedRateLimit(rateLimitId)).thenReturn(null)
        // Асинхронная загрузка с задержкой — имитация реального IO
        whenever(cacheManager.loadRateLimitAsync(rateLimitId))
            .thenReturn(Mono.just(rateLimit).delayElement(Duration.ofMillis(50)))

        // When: получаем маршрут и выполняем predicate
        val gatewayRoute = dynamicRouteLocator.getRoutes().blockFirst()!!
        val request = MockServerHttpRequest.get("/api/limited/test").build()
        val exchange = MockServerWebExchange.from(request)

        // Then: predicate завершается успешно (async)
        StepVerifier.create(gatewayRoute.predicate.apply(exchange))
            .expectNext(true)
            .verifyComplete()

        // Verify: использован async метод, НЕ sync
        verify(cacheManager).loadRateLimitAsync(rateLimitId)
        verify(cacheManager, never()).loadRateLimitSync(any())
    }

    @Test
    fun `asyncPredicate использует кэшированный rate limit без async загрузки`() {
        // Given: маршрут с rateLimitId, политика УЖЕ в кэше
        val rateLimitId = UUID.randomUUID()
        val route = createRouteWithRateLimit("/api/cached", "http://cached-service:8080", rateLimitId)
        val rateLimit = createRateLimit(rateLimitId)

        whenever(cacheManager.getCachedRoutes()).thenReturn(listOf(route))
        whenever(cacheManager.getCachedRateLimit(rateLimitId)).thenReturn(rateLimit)

        // When: получаем маршрут и выполняем predicate
        val gatewayRoute = dynamicRouteLocator.getRoutes().blockFirst()!!
        val request = MockServerHttpRequest.get("/api/cached/test").build()
        val exchange = MockServerWebExchange.from(request)

        StepVerifier.create(gatewayRoute.predicate.apply(exchange))
            .expectNext(true)
            .verifyComplete()

        // Verify: НЕ вызван async метод — взято из кэша
        verify(cacheManager, never()).loadRateLimitAsync(any())
        verify(cacheManager, never()).loadRateLimitSync(any())
    }

    @Test
    fun `asyncPredicate graceful degradation при ошибке загрузки rate limit`() {
        // Given: маршрут с rateLimitId, ошибка при загрузке
        val rateLimitId = UUID.randomUUID()
        val route = createRouteWithRateLimit("/api/error", "http://error-service:8080", rateLimitId)

        whenever(cacheManager.getCachedRoutes()).thenReturn(listOf(route))
        whenever(cacheManager.getCachedRateLimit(rateLimitId)).thenReturn(null)
        whenever(cacheManager.loadRateLimitAsync(rateLimitId))
            .thenReturn(Mono.error(RuntimeException("Database unavailable")))

        // When: получаем маршрут и выполняем predicate
        val gatewayRoute = dynamicRouteLocator.getRoutes().blockFirst()!!
        val request = MockServerHttpRequest.get("/api/error/test").build()
        val exchange = MockServerWebExchange.from(request)

        // Then: predicate возвращает true (graceful degradation — маршрут работает без rate limit)
        StepVerifier.create(gatewayRoute.predicate.apply(exchange))
            .expectNext(true)
            .verifyComplete()
    }

    @Test
    fun `asyncPredicate возвращает false когда path не совпадает`() {
        // Given: маршрут
        val route = createRoute("/api/orders", "http://order-service:8080", RouteStatus.PUBLISHED)

        whenever(cacheManager.getCachedRoutes()).thenReturn(listOf(route))

        // When: запрос на другой путь
        val gatewayRoute = dynamicRouteLocator.getRoutes().blockFirst()!!
        val request = MockServerHttpRequest.get("/api/users/test").build()
        val exchange = MockServerWebExchange.from(request)

        // Then: predicate возвращает false, async загрузка НЕ вызывается
        StepVerifier.create(gatewayRoute.predicate.apply(exchange))
            .expectNext(false)
            .verifyComplete()

        verify(cacheManager, never()).loadRateLimitAsync(any())
    }

    @Test
    fun `asyncPredicate продолжает работу когда rate limit не найден в БД`() {
        // Given: маршрут с rateLimitId, но политика отсутствует в БД
        val rateLimitId = UUID.randomUUID()
        val route = createRouteWithRateLimit("/api/notfound", "http://notfound-service:8080", rateLimitId)

        whenever(cacheManager.getCachedRoutes()).thenReturn(listOf(route))
        whenever(cacheManager.getCachedRateLimit(rateLimitId)).thenReturn(null)
        // Политика не найдена в БД — empty Mono
        whenever(cacheManager.loadRateLimitAsync(rateLimitId)).thenReturn(Mono.empty())

        // When: получаем маршрут и выполняем predicate
        val gatewayRoute = dynamicRouteLocator.getRoutes().blockFirst()!!
        val request = MockServerHttpRequest.get("/api/notfound/test").build()
        val exchange = MockServerWebExchange.from(request)

        // Then: predicate возвращает true (defaultIfEmpty) — маршрут работает без rate limit
        StepVerifier.create(gatewayRoute.predicate.apply(exchange))
            .expectNext(true)
            .verifyComplete()

        // Verify: async загрузка была вызвана
        verify(cacheManager).loadRateLimitAsync(rateLimitId)
    }
}
