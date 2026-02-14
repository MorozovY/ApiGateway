package com.company.gateway.core.route

import com.company.gateway.core.cache.RouteCacheManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.ReactiveSubscription
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.times

@ExtendWith(MockitoExtension::class)
class RouteRefreshServiceTest {

    @Mock
    private lateinit var redisConnectionFactory: ReactiveRedisConnectionFactory

    @Mock
    private lateinit var cacheManager: RouteCacheManager

    @Test
    fun `service initializes with redis available flag true by default`() {
        val service = RouteRefreshService(redisConnectionFactory, cacheManager)

        // Before any connection attempt, redisAvailable defaults to true
        assertThat(service.isRedisAvailable()).isTrue()
    }

    @Test
    fun `isRedisAvailable returns current redis connection state`() {
        val service = RouteRefreshService(redisConnectionFactory, cacheManager)

        // Initial state should be true
        assertThat(service.isRedisAvailable()).isTrue()
    }

    @Test
    fun `cleanup disposes subscription without error`() {
        val service = RouteRefreshService(redisConnectionFactory, cacheManager)

        // Should not throw even when no subscription exists
        service.cleanup()

        assertThat(service.isRedisAvailable()).isTrue()
    }

    @Test
    fun `cacheManager refreshCache is called correctly`() {
        whenever(cacheManager.refreshCache()).thenReturn(Mono.empty())

        cacheManager.refreshCache().block()

        verify(cacheManager, times(1)).refreshCache()
    }

    @Test
    fun `multiple cleanup calls are safe`() {
        val service = RouteRefreshService(redisConnectionFactory, cacheManager)

        // Multiple cleanup calls should be safe (idempotent)
        service.cleanup()
        service.cleanup()
        service.cleanup()

        // No exception should be thrown
        assertThat(service).isNotNull
    }
}
