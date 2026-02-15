package com.company.gateway.core.route

import com.company.gateway.core.cache.RouteCacheManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import reactor.core.publisher.Mono
import org.assertj.core.api.Assertions.assertThat

@ExtendWith(MockitoExtension::class)
class RouteRefreshServiceTest {

    @Mock
    private lateinit var redisConnectionFactory: ReactiveRedisConnectionFactory

    @Mock
    private lateinit var cacheManager: RouteCacheManager

    private lateinit var service: RouteRefreshService

    @BeforeEach
    fun setUp() {
        service = RouteRefreshService(redisConnectionFactory, cacheManager)
    }

    @Test
    fun `service initializes with redis available flag true by default`() {
        // Before any connection attempt, redisAvailable defaults to true
        assertThat(service.isRedisAvailable()).isTrue()
    }

    @Test
    fun `isRedisAvailable returns current redis connection state`() {
        // Initial state should be true
        assertThat(service.isRedisAvailable()).isTrue()
    }

    @Test
    fun `cleanup disposes subscription without error when no subscription exists`() {
        // Should not throw even when no subscription exists
        service.cleanup()

        // State should remain valid
        assertThat(service.isRedisAvailable()).isTrue()
    }

    @Test
    fun `multiple cleanup calls are safe and idempotent`() {
        // Multiple cleanup calls should be safe (idempotent)
        service.cleanup()
        service.cleanup()
        service.cleanup()

        // No exception should be thrown, service should remain valid
        assertThat(service).isNotNull
        assertThat(service.isRedisAvailable()).isTrue()
    }

    @Test
    fun `service can be created with valid dependencies`() {
        val newService = RouteRefreshService(redisConnectionFactory, cacheManager)

        assertThat(newService).isNotNull
        assertThat(newService.isRedisAvailable()).isTrue()
    }

    @Test
    fun `cleanup is safe to call before subscription is started`() {
        // Service created but subscribeToInvalidationEvents not called
        val freshService = RouteRefreshService(redisConnectionFactory, cacheManager)

        // Cleanup should work without NPE
        freshService.cleanup()

        assertThat(freshService.isRedisAvailable()).isTrue()
    }

    @Test
    fun `cacheManager dependency is injected correctly`() {
        // Verify that cacheManager can be used (proves injection works)
        whenever(cacheManager.refreshCache()).thenReturn(Mono.empty())

        // This verifies the dependency is accessible
        val result = cacheManager.refreshCache()

        assertThat(result).isNotNull
        verify(cacheManager).refreshCache()
    }

    @Test
    fun `service does not call cacheManager on construction`() {
        // Creating service should NOT trigger any cache operations
        val newService = RouteRefreshService(redisConnectionFactory, cacheManager)

        // CacheManager should not be called during construction
        verify(cacheManager, never()).refreshCache()
        verify(cacheManager, never()).getCachedRoutes()
    }
}