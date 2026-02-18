package com.company.gateway.admin.publisher

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.Optional
import java.util.UUID

/**
 * Издатель событий изменения маршрутов через Redis Pub/Sub.
 *
 * Используется для cache invalidation в gateway-core при:
 * - Одобрении маршрута (PENDING → PUBLISHED)
 * - Изменении или удалении опубликованного маршрута
 *
 * Зависимость от Redis опциональна — если Redis недоступен (например в тестах),
 * публикация пропускается с предупреждением в лог.
 *
 * Story 4.2, AC2: Автоматическая публикация после одобрения
 */
@Component
class RouteEventPublisher(
    private val redisTemplate: Optional<ReactiveStringRedisTemplate>
) {
    private val logger = LoggerFactory.getLogger(RouteEventPublisher::class.java)

    companion object {
        /**
         * Канал Redis для уведомлений об изменении маршрутов.
         * Должен совпадать с gateway.cache.invalidation-channel в gateway-core
         * (default: route-cache-invalidation).
         */
        const val ROUTE_CACHE_CHANNEL = "route-cache-invalidation"
    }

    /**
     * Публикует событие изменения маршрута в Redis.
     *
     * Gateway-core подписан на этот канал и инвалидирует свой кэш
     * при получении сообщения, что обеспечивает применение изменений
     * в течение 5 секунд (NFR3).
     *
     * Если Redis недоступен — возвращает Mono.just(0L) с предупреждением.
     *
     * @param routeId ID изменённого маршрута
     * @return Mono<Long> количество подписчиков, получивших сообщение
     */
    fun publishRouteChanged(routeId: UUID): Mono<Long> {
        logger.debug("Публикация cache invalidation для маршрута: routeId={}", routeId)

        // Если Redis недоступен — пропускаем публикацию
        if (!redisTemplate.isPresent) {
            logger.warn("Redis недоступен — cache invalidation не опубликован: routeId={}", routeId)
            return Mono.just(0L)
        }

        return redisTemplate.get().convertAndSend(ROUTE_CACHE_CHANNEL, routeId.toString())
            .doOnSuccess { subscribersCount ->
                logger.info(
                    "Cache invalidation опубликован: routeId={}, subscribers={}",
                    routeId, subscribersCount
                )
            }
            .doOnError { error ->
                logger.error(
                    "Ошибка публикации cache invalidation: routeId={}, error={}",
                    routeId, error.message
                )
            }
    }
}
