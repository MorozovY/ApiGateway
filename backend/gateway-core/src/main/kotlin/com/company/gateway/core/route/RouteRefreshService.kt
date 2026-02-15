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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Service
@ConditionalOnBean(ReactiveRedisConnectionFactory::class)
class RouteRefreshService(
    private val redisConnectionFactory: ReactiveRedisConnectionFactory,
    private val cacheManager: RouteCacheManager
) {
    private val logger = LoggerFactory.getLogger(RouteRefreshService::class.java)

    @Value("\${gateway.cache.invalidation-channel:route-cache-invalidation}")
    private lateinit var invalidationChannel: String

    @Value("\${gateway.cache.reconnect-delay-seconds:30}")
    private var reconnectDelaySeconds: Long = 30

    private val subscription = AtomicReference<Disposable?>(null)
    private val container = AtomicReference<ReactiveRedisMessageListenerContainer?>(null)
    private val redisAvailable = AtomicBoolean(true)
    private val reconnecting = AtomicBoolean(false)
    private var reconnectSubscription: Disposable? = null

    @EventListener(ApplicationReadyEvent::class)
    fun subscribeToInvalidationEvents() {
        startRedisSubscription()
    }

    private fun startRedisSubscription() {
        // Cleanup previous subscription and container if they exist
        cleanupCurrentSubscription()

        try {
            val newContainer = ReactiveRedisMessageListenerContainer(redisConnectionFactory)
            container.set(newContainer)

            val newSubscription = newContainer.receive(ChannelTopic.of(invalidationChannel))
                .flatMap { message ->
                    logger.info("Cache invalidation event received on channel '{}': {}",
                        invalidationChannel, message.message)
                    cacheManager.refreshCache()
                }
                .doOnSubscribe {
                    logger.info("Subscribed to Redis channel '{}' for cache invalidation events", invalidationChannel)
                    redisAvailable.set(true)
                    reconnecting.set(false)
                }
                .doOnError { error ->
                    logger.warn("Redis subscription error, using Caffeine TTL fallback: {}", error.message)
                    redisAvailable.set(false)
                    scheduleReconnect()
                }
                .onErrorResume { _ ->
                    logger.warn("Redis unavailable, using Caffeine cache with TTL fallback")
                    Mono.empty()
                }
                .subscribe()

            subscription.set(newSubscription)
        } catch (e: Exception) {
            logger.warn("Failed to connect to Redis: {}. Using Caffeine cache with TTL fallback", e.message)
            redisAvailable.set(false)
            scheduleReconnect()
        }
    }

    private fun cleanupCurrentSubscription() {
        subscription.getAndSet(null)?.dispose()
        container.getAndSet(null)?.destroy()
    }

    private fun scheduleReconnect() {
        // Prevent multiple concurrent reconnect attempts
        if (!reconnecting.compareAndSet(false, true)) {
            return
        }

        reconnectSubscription?.dispose()
        reconnectSubscription = Mono.delay(Duration.ofSeconds(reconnectDelaySeconds))
            .doOnNext {
                logger.info("Attempting to reconnect to Redis...")
            }
            .doFinally {
                // Always reset reconnecting flag after attempt completes
                // This allows future reconnect attempts if this one fails
                reconnecting.set(false)
            }
            .subscribe {
                startRedisSubscription()
            }
    }

    fun isRedisAvailable(): Boolean = redisAvailable.get()

    @PreDestroy
    fun cleanup() {
        reconnectSubscription?.dispose()
        cleanupCurrentSubscription()
        logger.info("Disposed Redis subscription and container")
    }
}
