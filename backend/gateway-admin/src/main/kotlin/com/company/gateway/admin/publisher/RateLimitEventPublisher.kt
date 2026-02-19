package com.company.gateway.admin.publisher

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Издатель событий изменения rate limit политик через Redis Pub/Sub.
 *
 * Используется для cache invalidation в gateway-core при:
 * - Создании новой политики rate limit
 * - Обновлении существующей политики
 * - Удалении политики
 *
 * Зависимость от Redis опциональна — если Redis недоступен (например в тестах),
 * публикация пропускается с предупреждением в лог.
 *
 * Story 5.8, AC1: Rate limit политики синхронизируются немедленно
 */
@Component
class RateLimitEventPublisher(
    @Autowired(required = false)
    private val redisTemplate: ReactiveStringRedisTemplate?
) {
    private val logger = LoggerFactory.getLogger(RateLimitEventPublisher::class.java)

    companion object {
        /**
         * Канал Redis для уведомлений об изменении rate limit политик.
         * Должен совпадать с gateway.cache.ratelimit-invalidation-channel в gateway-core
         * (default: ratelimit-cache-invalidation).
         */
        const val RATELIMIT_CACHE_CHANNEL = "ratelimit-cache-invalidation"
    }

    /**
     * Публикует событие изменения политики rate limit в Redis.
     *
     * Gateway-core подписан на этот канал и обновляет свой кэш
     * при получении сообщения, что обеспечивает применение изменений
     * в течение 5 секунд.
     *
     * Если Redis недоступен — возвращает Mono.just(0L) с предупреждением.
     *
     * @param rateLimitId ID изменённой политики
     * @return Mono<Long> количество подписчиков, получивших сообщение
     */
    fun publishRateLimitChanged(rateLimitId: UUID): Mono<Long> {
        logger.debug("Публикация cache invalidation для политики rate limit: rateLimitId={}", rateLimitId)

        // Если Redis недоступен — пропускаем публикацию
        if (redisTemplate == null) {
            logger.warn("Redis недоступен — cache invalidation не опубликован: rateLimitId={}", rateLimitId)
            return Mono.just(0L)
        }

        return redisTemplate.convertAndSend(RATELIMIT_CACHE_CHANNEL, rateLimitId.toString())
            .doOnSuccess { subscribersCount ->
                logger.info(
                    "Rate limit cache invalidation опубликован: rateLimitId={}, subscribers={}",
                    rateLimitId, subscribersCount
                )
            }
            .doOnError { error ->
                logger.error(
                    "Ошибка публикации rate limit cache invalidation: rateLimitId={}, error={}",
                    rateLimitId, error.message
                )
            }
    }
}
