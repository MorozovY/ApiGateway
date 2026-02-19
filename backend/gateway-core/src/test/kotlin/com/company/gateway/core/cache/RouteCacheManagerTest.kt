package com.company.gateway.core.cache

import com.company.gateway.common.model.RateLimit
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import com.company.gateway.core.repository.RateLimitRepository
import com.company.gateway.core.repository.RouteRepository
import com.github.benmanes.caffeine.cache.Cache
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * Unit тесты для RouteCacheManager (Story 1.5, 5.3)
 *
 * Тесты:
 * - Загрузка только PUBLISHED маршрутов
 * - Атомарная замена кэша маршрутов
 * - Загрузка rate limit политик вместе с маршрутами
 * - Fallback на Caffeine кэш
 */
@ExtendWith(MockitoExtension::class)
class RouteCacheManagerTest {

    @Mock
    private lateinit var routeRepository: RouteRepository

    @Mock
    private lateinit var rateLimitRepository: RateLimitRepository

    @Mock
    private lateinit var caffeineRouteCache: Cache<String, List<Route>>

    @Mock
    private lateinit var caffeineRateLimitCache: Cache<UUID, RateLimit>

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    private lateinit var cacheManager: RouteCacheManager

    @BeforeEach
    fun setUp() {
        cacheManager = RouteCacheManager(
            routeRepository,
            rateLimitRepository,
            caffeineRouteCache,
            caffeineRateLimitCache,
            eventPublisher
        )
    }

    @Test
    fun `refreshCache загружает только PUBLISHED маршруты`() {
        // Маршрут без rate limit
        val publishedRoute = createRoute(RouteStatus.PUBLISHED)
        whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
            .thenReturn(Flux.just(publishedRoute))

        StepVerifier.create(cacheManager.refreshCache())
            .verifyComplete()

        assertThat(cacheManager.getCachedRoutes()).containsExactly(publishedRoute)
        verify(caffeineRouteCache).put(any(), any())
        // Не должен вызываться rateLimitRepository, так как нет rateLimitId
        verify(rateLimitRepository, never()).findAllByIdIn(any())
    }

    @Test
    fun `refreshCache загружает rate limit политики для маршрутов с rateLimitId`() {
        val rateLimitId = UUID.randomUUID()
        val rateLimit = createRateLimit(rateLimitId)
        val routeWithRateLimit = createRoute(RouteStatus.PUBLISHED, rateLimitId = rateLimitId)

        whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
            .thenReturn(Flux.just(routeWithRateLimit))
        whenever(rateLimitRepository.findAllByIdIn(listOf(rateLimitId)))
            .thenReturn(Flux.just(rateLimit))

        StepVerifier.create(cacheManager.refreshCache())
            .verifyComplete()

        assertThat(cacheManager.getCachedRoutes()).containsExactly(routeWithRateLimit)
        assertThat(cacheManager.getCachedRateLimit(rateLimitId)).isEqualTo(rateLimit)
        assertThat(cacheManager.getRateLimitCacheSize()).isEqualTo(1)
        verify(caffeineRateLimitCache).put(eq(rateLimitId), eq(rateLimit))
    }

    @Test
    fun `refreshCache не вызывает rateLimitRepository когда нет маршрутов с rateLimitId`() {
        val routeWithoutRateLimit = createRoute(RouteStatus.PUBLISHED)
        whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
            .thenReturn(Flux.just(routeWithoutRateLimit))

        StepVerifier.create(cacheManager.refreshCache())
            .verifyComplete()

        verify(rateLimitRepository, never()).findAllByIdIn(any())
        assertThat(cacheManager.getCachedRateLimits()).isEmpty()
    }

    @Test
    fun `refreshCache загружает уникальные rate limit политики для нескольких маршрутов`() {
        // Два маршрута с одинаковым rate limit — должен загрузить только одну политику
        val rateLimitId = UUID.randomUUID()
        val rateLimit = createRateLimit(rateLimitId)
        val route1 = createRoute(RouteStatus.PUBLISHED, path = "/api/v1", rateLimitId = rateLimitId)
        val route2 = createRoute(RouteStatus.PUBLISHED, path = "/api/v2", rateLimitId = rateLimitId)

        whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
            .thenReturn(Flux.just(route1, route2))
        whenever(rateLimitRepository.findAllByIdIn(listOf(rateLimitId)))
            .thenReturn(Flux.just(rateLimit))

        StepVerifier.create(cacheManager.refreshCache())
            .verifyComplete()

        assertThat(cacheManager.getCachedRoutes()).hasSize(2)
        assertThat(cacheManager.getRateLimitCacheSize()).isEqualTo(1)
    }

    @Test
    fun `refreshCache атомарно заменяет маршруты`() {
        val route1 = createRoute(RouteStatus.PUBLISHED, "/api/v1")
        val route2 = createRoute(RouteStatus.PUBLISHED, "/api/v2")

        // Первая загрузка
        whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
            .thenReturn(Flux.just(route1))
        cacheManager.refreshCache().block()
        assertThat(cacheManager.getCachedRoutes()).containsExactly(route1)

        // Вторая загрузка — атомарно заменяет
        whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
            .thenReturn(Flux.just(route2))
        cacheManager.refreshCache().block()
        assertThat(cacheManager.getCachedRoutes()).containsExactly(route2)
    }

    @Test
    fun `getCachedRoutes возвращает atomic reference первым когда не пуст`() {
        val route = createRoute(RouteStatus.PUBLISHED)
        whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
            .thenReturn(Flux.just(route))

        cacheManager.refreshCache().block()

        val routes = cacheManager.getCachedRoutes()
        assertThat(routes).containsExactly(route)
    }

    @Test
    fun `getCachedRoutes возвращает Caffeine cache когда atomic reference пуст`() {
        val caffeineRoutes = listOf(createRoute(RouteStatus.PUBLISHED))
        whenever(caffeineRouteCache.getIfPresent(any<String>())).thenReturn(caffeineRoutes)

        // Маршруты не загружены в atomic reference
        val routes = cacheManager.getCachedRoutes()
        assertThat(routes).isEqualTo(caffeineRoutes)
    }

    @Test
    fun `getCachedRoutes возвращает пустой список когда оба кэша пусты`() {
        whenever(caffeineRouteCache.getIfPresent(any<String>())).thenReturn(null)

        val routes = cacheManager.getCachedRoutes()
        assertThat(routes).isEmpty()
    }

    @Test
    fun `getCachedRateLimit возвращает политику из atomic reference`() {
        val rateLimitId = UUID.randomUUID()
        val rateLimit = createRateLimit(rateLimitId)
        val route = createRoute(RouteStatus.PUBLISHED, rateLimitId = rateLimitId)

        whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
            .thenReturn(Flux.just(route))
        whenever(rateLimitRepository.findAllByIdIn(listOf(rateLimitId)))
            .thenReturn(Flux.just(rateLimit))

        cacheManager.refreshCache().block()

        assertThat(cacheManager.getCachedRateLimit(rateLimitId)).isEqualTo(rateLimit)
    }

    @Test
    fun `getCachedRateLimit возвращает Caffeine cache когда atomic reference пуст`() {
        val rateLimitId = UUID.randomUUID()
        val rateLimit = createRateLimit(rateLimitId)
        whenever(caffeineRateLimitCache.getIfPresent(rateLimitId)).thenReturn(rateLimit)

        // Политика не загружена в atomic reference
        val result = cacheManager.getCachedRateLimit(rateLimitId)
        assertThat(result).isEqualTo(rateLimit)
    }

    @Test
    fun `getCachedRateLimit возвращает null когда политика не найдена`() {
        val unknownId = UUID.randomUUID()
        whenever(caffeineRateLimitCache.getIfPresent(unknownId)).thenReturn(null)

        val result = cacheManager.getCachedRateLimit(unknownId)
        assertThat(result).isNull()
    }

    @Test
    fun `getCacheSize возвращает корректное количество`() {
        val routes = listOf(
            createRoute(RouteStatus.PUBLISHED, "/api/v1"),
            createRoute(RouteStatus.PUBLISHED, "/api/v2"),
            createRoute(RouteStatus.PUBLISHED, "/api/v3")
        )
        whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
            .thenReturn(Flux.fromIterable(routes))

        cacheManager.refreshCache().block()

        assertThat(cacheManager.getCacheSize()).isEqualTo(3)
    }

    @Test
    fun `refreshCache обрабатывает пустой результат из репозитория`() {
        whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
            .thenReturn(Flux.empty())

        StepVerifier.create(cacheManager.refreshCache())
            .verifyComplete()

        assertThat(cacheManager.getCachedRoutes()).isEmpty()
        assertThat(cacheManager.getCacheSize()).isEqualTo(0)
    }

    private fun createRoute(
        status: RouteStatus,
        path: String = "/api/test",
        rateLimitId: UUID? = null
    ) = Route(
        id = UUID.randomUUID(),
        path = path,
        upstreamUrl = "http://test-service:8080",
        methods = listOf("GET", "POST"),
        status = status,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        rateLimitId = rateLimitId
    )

    private fun createRateLimit(
        id: UUID,
        name: String = "test-policy",
        requestsPerSecond: Int = 10,
        burstSize: Int = 15
    ) = RateLimit(
        id = id,
        name = name,
        requestsPerSecond = requestsPerSecond,
        burstSize = burstSize,
        createdBy = UUID.randomUUID(),
        createdAt = Instant.now()
    )
}
