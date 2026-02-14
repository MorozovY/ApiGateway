package com.company.gateway.core.route

import com.company.gateway.core.cache.RouteCacheManager
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import org.springframework.stereotype.Service
import reactor.core.Disposable
import reactor.core.publisher.Mono
import java.time.Duration

@Service
@ConditionalOnBean(ReactiveRedisConnectionFactory::class)
class RouteRefreshService(
    private val redisConnectionFactory: ReactiveRedisConnectionFactory,
    private val cacheManager: RouteCacheManager
) {
    private val logger = LoggerFactory.getLogger(RouteRefreshService::class.java)

    @Value("\${gateway.cache.invalidation-channel:route-cache-invalidation}")
    private lateinit var invalidationChannel: String

    private var subscription: Disposable? = null
    private var redisAvailable = true

    @EventListener(ApplicationReadyEvent::class)
    fun subscribeToInvalidationEvents() {
        startRedisSubscription()
    }

    private fun startRedisSubscription() {
        try {
            val container = ReactiveRedisMessageListenerContainer(redisConnectionFactory)

            subscription = container.receive(ChannelTopic.of(invalidationChannel))
                .flatMap { message ->
                    logger.info("Cache invalidation event received on channel '{}': {}",
                        invalidationChannel, message.message)
                    cacheManager.refreshCache()
                }
                .doOnSubscribe {
                    logger.info("Subscribed to Redis channel '{}' for cache invalidation events", invalidationChannel)
                    redisAvailable = true
                }
                .doOnError { error ->
                    logger.warn("Redis subscription error, using Caffeine TTL fallback: {}", error.message)
                    redisAvailable = false
                    scheduleReconnect()
                }
                .onErrorResume { _ ->
                    logger.warn("Redis unavailable, using Caffeine cache with TTL fallback")
                    Mono.empty()
                }
                .subscribe()
        } catch (e: Exception) {
            logger.warn("Failed to connect to Redis: {}. Using Caffeine cache with TTL fallback", e.message)
            redisAvailable = false
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        Mono.delay(Duration.ofSeconds(30))
            .doOnNext {
                logger.info("Attempting to reconnect to Redis...")
                startRedisSubscription()
            }
            .subscribe()
    }

    fun isRedisAvailable(): Boolean = redisAvailable

    @PreDestroy
    fun cleanup() {
        subscription?.dispose()
        logger.info("Disposed Redis subscription")
    }
}
