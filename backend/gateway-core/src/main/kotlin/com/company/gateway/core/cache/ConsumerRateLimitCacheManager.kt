package com.company.gateway.core.cache

import com.company.gateway.common.model.ConsumerRateLimit
import com.company.gateway.core.repository.ConsumerRateLimitRepository
import com.github.benmanes.caffeine.cache.Cache
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.Optional

/**
 * Менеджер кэша per-consumer rate limits.
 *
 * Кэширует consumer rate limits для минимизации DB запросов в RateLimitFilter.
 * Использует Caffeine cache с TTL и поддерживает cache invalidation через Redis pub/sub.
 *
 * Особенность: кэшируем также "отсутствие" rate limit (Optional.empty()),
 * чтобы не делать повторные запросы к БД для consumers без лимитов.
 *
 * Story 12.8: Per-consumer Rate Limits (AC3-AC5)
 */
@Component
class ConsumerRateLimitCacheManager(
    private val consumerRateLimitRepository: ConsumerRateLimitRepository,
    private val caffeineConsumerRateLimitCache: Cache<String, Optional<ConsumerRateLimit>>
) {
    private val logger = LoggerFactory.getLogger(ConsumerRateLimitCacheManager::class.java)

    /**
     * Получает rate limit для consumer с кэшированием.
     *
     * Стратегия:
     * 1. Проверить Caffeine кэш
     * 2. Загрузить из БД и закэшировать результат (включая null)
     *
     * @param consumerId идентификатор consumer
     * @return Mono<ConsumerRateLimit?> — rate limit или null если не установлен
     */
    fun getConsumerRateLimit(consumerId: String): Mono<ConsumerRateLimit?> {
        // 1. Проверяем Caffeine кэш
        val cached = caffeineConsumerRateLimitCache.getIfPresent(consumerId)
        if (cached != null) {
            return Mono.justOrEmpty(cached.orElse(null))
        }

        // 2. Загружаем из БД
        return consumerRateLimitRepository.findByConsumerId(consumerId)
            .doOnNext { rateLimit ->
                // Кэшируем найденный rate limit
                caffeineConsumerRateLimitCache.put(consumerId, Optional.of(rateLimit))
                logger.debug("Consumer rate limit загружен и закэширован: consumerId={}", consumerId)
            }
            .switchIfEmpty(
                Mono.fromCallable {
                    // Кэшируем "отсутствие" rate limit (Optional.empty())
                    caffeineConsumerRateLimitCache.put(consumerId, Optional.empty())
                    logger.debug("Consumer rate limit не найден, закэшировано: consumerId={}", consumerId)
                    null
                }
            )
    }

    /**
     * Инвалидирует кэш для указанного consumer.
     * Вызывается при получении события из Redis pub/sub.
     *
     * @param consumerId идентификатор consumer
     */
    fun invalidateCache(consumerId: String): Mono<Void> {
        return Mono.fromRunnable {
            caffeineConsumerRateLimitCache.invalidate(consumerId)
            logger.info("Consumer rate limit cache invalidated: consumerId={}", consumerId)
        }
    }

    /**
     * Полная инвалидация кэша.
     * Используется при сбросе или для тестирования.
     */
    fun invalidateAll(): Mono<Void> {
        return Mono.fromRunnable {
            caffeineConsumerRateLimitCache.invalidateAll()
            logger.info("Consumer rate limit cache полностью очищен")
        }
    }

    /**
     * Возвращает размер Caffeine кэша.
     * Полезно для мониторинга.
     */
    fun getCacheSize(): Long = caffeineConsumerRateLimitCache.estimatedSize()
}
