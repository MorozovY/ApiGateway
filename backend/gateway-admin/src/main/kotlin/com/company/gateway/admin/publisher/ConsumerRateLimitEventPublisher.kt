package com.company.gateway.admin.publisher

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * Издатель событий изменения per-consumer rate limits через Redis Pub/Sub.
 *
 * Используется для cache invalidation в gateway-core при:
 * - Создании нового consumer rate limit
 * - Обновлении существующего consumer rate limit
 * - Удалении consumer rate limit
 *
 * Зависимость от Redis опциональна — если Redis недоступен (например в тестах),
 * публикация пропускается с предупреждением в лог.
 *
 * Story 12.8, AC2: Consumer rate limits синхронизируются немедленно
 */
@Component
class ConsumerRateLimitEventPublisher(
    @Autowired(required = false)
    private val redisTemplate: ReactiveStringRedisTemplate?
) {
    private val logger = LoggerFactory.getLogger(ConsumerRateLimitEventPublisher::class.java)

    companion object {
        /**
         * Канал Redis для уведомлений об изменении consumer rate limits.
         * Gateway-core подписывается на этот канал для cache invalidation.
         */
        const val CONSUMER_RATELIMIT_CACHE_CHANNEL = "consumer-ratelimit-cache-invalidation"
    }

    /**
     * Публикует событие изменения consumer rate limit в Redis.
     *
     * Gateway-core подписан на этот канал и инвалидирует свой кэш
     * при получении сообщения.
     *
     * Если Redis недоступен — возвращает Mono.just(0L) с предупреждением.
     *
     * @param consumerId ID consumer, для которого изменился rate limit
     * @return Mono<Long> количество подписчиков, получивших сообщение
     */
    fun publishConsumerRateLimitChanged(consumerId: String): Mono<Long> {
        logger.debug("Публикация cache invalidation для consumer rate limit: consumerId={}", consumerId)

        // Если Redis недоступен — пропускаем публикацию
        if (redisTemplate == null) {
            logger.warn("Redis недоступен — cache invalidation не опубликован: consumerId={}", consumerId)
            return Mono.just(0L)
        }

        return redisTemplate.convertAndSend(CONSUMER_RATELIMIT_CACHE_CHANNEL, consumerId)
            .doOnSuccess { subscribersCount ->
                logger.info(
                    "Consumer rate limit cache invalidation опубликован: consumerId={}, subscribers={}",
                    consumerId, subscribersCount
                )
            }
            .doOnError { error ->
                logger.error(
                    "Ошибка публикации consumer rate limit cache invalidation: consumerId={}, error={}",
                    consumerId, error.message
                )
            }
    }
}
