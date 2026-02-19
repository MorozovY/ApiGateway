package com.company.gateway.core.route

import com.company.gateway.core.cache.RouteCacheManager
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import org.springframework.stereotype.Service
import reactor.core.Disposable
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Сервис подписки на Redis Pub/Sub для обновления кэша маршрутов и rate limit политик.
 *
 * Подписывается на два канала:
 * - route-cache-invalidation: события изменения маршрутов
 * - ratelimit-cache-invalidation: события изменения rate limit политик (Story 5.8)
 *
 * При недоступности Redis использует Caffeine cache с TTL fallback
 * и автоматически переподключается каждые 30 секунд.
 */
@Service
class RouteRefreshService(
    private val redisConnectionFactory: ReactiveRedisConnectionFactory,
    private val cacheManager: RouteCacheManager
) {
    private val logger = LoggerFactory.getLogger(RouteRefreshService::class.java)

    @Value("\${gateway.cache.invalidation-channel:route-cache-invalidation}")
    private lateinit var routeInvalidationChannel: String

    @Value("\${gateway.cache.ratelimit-invalidation-channel:ratelimit-cache-invalidation}")
    private lateinit var rateLimitInvalidationChannel: String

    @Value("\${gateway.cache.reconnect-delay-seconds:30}")
    private var reconnectDelaySeconds: Long = 30

    private val routeSubscription = AtomicReference<Disposable?>(null)
    private val rateLimitSubscription = AtomicReference<Disposable?>(null)
    private val routeContainer = AtomicReference<ReactiveRedisMessageListenerContainer?>(null)
    private val rateLimitContainer = AtomicReference<ReactiveRedisMessageListenerContainer?>(null)
    private val redisAvailable = AtomicBoolean(true)
    private val reconnecting = AtomicBoolean(false)
    private var reconnectSubscription: Disposable? = null

    @EventListener(ApplicationReadyEvent::class)
    fun subscribeToInvalidationEvents() {
        startRouteSubscription()
        startRateLimitSubscription()
    }

    /**
     * Запускает подписку на канал route-cache-invalidation.
     * При получении события обновляется весь кэш маршрутов.
     */
    private fun startRouteSubscription() {
        // Cleanup previous subscription and container if they exist
        routeSubscription.getAndSet(null)?.dispose()
        routeContainer.getAndSet(null)?.destroy()

        try {
            val newContainer = ReactiveRedisMessageListenerContainer(redisConnectionFactory)
            routeContainer.set(newContainer)

            val newSubscription = newContainer.receive(ChannelTopic.of(routeInvalidationChannel))
                .flatMap { message ->
                    logger.info("Получено событие инвалидации маршрута на канале '{}': {}",
                        routeInvalidationChannel, message.message)
                    cacheManager.refreshCache()
                }
                .doOnSubscribe {
                    logger.info("Подписка на Redis канал '{}' активирована", routeInvalidationChannel)
                    redisAvailable.set(true)
                    reconnecting.set(false)
                }
                .doOnError { error ->
                    logger.warn("Ошибка подписки Redis (route), используем Caffeine TTL fallback: {}", error.message)
                    redisAvailable.set(false)
                    scheduleReconnect()
                }
                .onErrorResume { _ ->
                    logger.warn("Redis недоступен, используем Caffeine cache с TTL fallback")
                    Mono.empty()
                }
                .subscribe()

            routeSubscription.set(newSubscription)
        } catch (e: Exception) {
            logger.warn("Ошибка подключения к Redis (route): {}. Используем Caffeine cache с TTL fallback", e.message)
            redisAvailable.set(false)
            scheduleReconnect()
        }
    }

    /**
     * Запускает подписку на канал ratelimit-cache-invalidation.
     * При получении события обновляется одна политика rate limit по ID.
     *
     * Story 5.8, AC1: Rate limit политики синхронизируются немедленно
     */
    private fun startRateLimitSubscription() {
        // Cleanup previous subscription and container if they exist
        rateLimitSubscription.getAndSet(null)?.dispose()
        rateLimitContainer.getAndSet(null)?.destroy()

        try {
            val newContainer = ReactiveRedisMessageListenerContainer(redisConnectionFactory)
            rateLimitContainer.set(newContainer)

            val newSubscription = newContainer.receive(ChannelTopic.of(rateLimitInvalidationChannel))
                .flatMap { message ->
                    val rateLimitIdStr = message.message
                    logger.info("Получено событие инвалидации rate limit на канале '{}': {}",
                        rateLimitInvalidationChannel, rateLimitIdStr)

                    try {
                        val rateLimitId = UUID.fromString(rateLimitIdStr)
                        cacheManager.refreshRateLimitCache(rateLimitId)
                    } catch (e: IllegalArgumentException) {
                        logger.error("Некорректный UUID в событии rate limit: {}", rateLimitIdStr)
                        Mono.empty()
                    }
                }
                .doOnSubscribe {
                    logger.info("Подписка на Redis канал '{}' активирована", rateLimitInvalidationChannel)
                }
                .doOnError { error ->
                    logger.warn("Ошибка подписки Redis (ratelimit): {}", error.message)
                    scheduleReconnect()
                }
                .onErrorResume { _ ->
                    logger.warn("Redis недоступен для rate limit events")
                    Mono.empty()
                }
                .subscribe()

            rateLimitSubscription.set(newSubscription)
        } catch (e: Exception) {
            logger.warn("Ошибка подключения к Redis (ratelimit): {}", e.message)
            scheduleReconnect()
        }
    }

    /**
     * Очищает все текущие подписки и контейнеры.
     */
    private fun cleanupCurrentSubscription() {
        routeSubscription.getAndSet(null)?.dispose()
        routeContainer.getAndSet(null)?.destroy()
        rateLimitSubscription.getAndSet(null)?.dispose()
        rateLimitContainer.getAndSet(null)?.destroy()
    }

    private fun scheduleReconnect() {
        // Prevent multiple concurrent reconnect attempts
        if (!reconnecting.compareAndSet(false, true)) {
            return
        }

        reconnectSubscription?.dispose()
        reconnectSubscription = Mono.delay(Duration.ofSeconds(reconnectDelaySeconds))
            .doOnNext {
                logger.info("Попытка переподключения к Redis...")
            }
            .doFinally {
                // Always reset reconnecting flag after attempt completes
                // This allows future reconnect attempts if this one fails
                reconnecting.set(false)
            }
            .subscribe {
                startRouteSubscription()
                startRateLimitSubscription()
            }
    }

    fun isRedisAvailable(): Boolean = redisAvailable.get()

    @PreDestroy
    fun cleanup() {
        reconnectSubscription?.dispose()
        cleanupCurrentSubscription()
        logger.info("Подписки Redis очищены (route и ratelimit)")
    }
}
