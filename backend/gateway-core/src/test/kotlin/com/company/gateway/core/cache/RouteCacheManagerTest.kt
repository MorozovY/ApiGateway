package com.company.gateway.core.cache

import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import com.company.gateway.core.repository.RouteRepository
import com.github.benmanes.caffeine.cache.Cache
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat

@ExtendWith(MockitoExtension::class)
class RouteCacheManagerTest {

    @Mock
    private lateinit var routeRepository: RouteRepository

    @Mock
    private lateinit var caffeineCache: Cache<String, List<Route>>

    @Mock
    private lateinit var eventPublisher: ApplicationEventPublisher

    private lateinit var cacheManager: RouteCacheManager

    @BeforeEach
    fun setUp() {
        cacheManager = RouteCacheManager(routeRepository, caffeineCache, eventPublisher)
    }

    @Test
    fun `refreshCache загружает только PUBLISHED маршруты`() {
        val publishedRoute = createRoute(RouteStatus.PUBLISHED)
        whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
            .thenReturn(Flux.just(publishedRoute))

        StepVerifier.create(cacheManager.refreshCache())
            .verifyComplete()

        assertThat(cacheManager.getCachedRoutes()).containsExactly(publishedRoute)
        verify(caffeineCache).put(any(), any())
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

        // Вторая загрузка - атомарно заменяет
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
        whenever(caffeineCache.getIfPresent(any<String>())).thenReturn(caffeineRoutes)

        // Маршруты не загружены в atomic reference
        val routes = cacheManager.getCachedRoutes()
        assertThat(routes).isEqualTo(caffeineRoutes)
    }

    @Test
    fun `getCachedRoutes возвращает пустой список когда оба кэша пусты`() {
        whenever(caffeineCache.getIfPresent(any<String>())).thenReturn(null)

        val routes = cacheManager.getCachedRoutes()
        assertThat(routes).isEmpty()
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
        path: String = "/api/test"
    ) = Route(
        id = UUID.randomUUID(),
        path = path,
        upstreamUrl = "http://test-service:8080",
        methods = listOf("GET", "POST"),
        status = status,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
}
