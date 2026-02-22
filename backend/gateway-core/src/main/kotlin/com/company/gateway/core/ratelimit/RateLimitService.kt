package com.company.gateway.core.ratelimit

import com.company.gateway.common.model.RateLimit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Сервис rate limiting с поддержкой distributed Redis и локального fallback.
 *
 * Координирует проверку rate limit через Redis (основной режим) или
 * локальный Caffeine cache (fallback при недоступности Redis).
 */
@Service
class RateLimitService(
    private val tokenBucketScript: TokenBucketScript,
    private val localRateLimiter: LocalRateLimiter,
    @Value("\${gateway.ratelimit.fallback-enabled:true}")
    private val fallbackEnabled: Boolean = true,
    @Value("\${gateway.ratelimit.redis-key-prefix:ratelimit}")
    private val redisKeyPrefix: String = "ratelimit"
) {
    private val logger = LoggerFactory.getLogger(RateLimitService::class.java)

    /**
     * Флаг текущего состояния — используется ли fallback.
     * Volatile для видимости изменений между потоками.
     */
    private val usingFallback = AtomicBoolean(false)

    /**
     * Проверяет rate limit для указанного маршрута и клиента.
     *
     * @param routeId ID маршрута
     * @param clientKey идентификатор клиента (IP адрес или другой ключ)
     * @param rateLimit политика rate limiting
     * @return результат проверки
     */
    fun checkRateLimit(
        routeId: UUID,
        clientKey: String,
        rateLimit: RateLimit
    ): Mono<RateLimitResult> {
        val bucketKey = buildKey(routeId, clientKey)

        return tokenBucketScript.checkRateLimit(
            key = bucketKey,
            requestsPerSecond = rateLimit.requestsPerSecond,
            burstSize = rateLimit.burstSize
        )
            .doOnSuccess { result ->
                // Логируем результат проверки rate limit
                if (!result.allowed) {
                    logger.info("Rate limit exceeded: key={}, remaining={}, resetTime={}",
                        bucketKey, result.remaining, result.resetTime)
                }
                // Успешно использовали Redis — сбрасываем fallback флаг
                if (usingFallback.compareAndSet(true, false)) {
                    logger.info("Redis восстановлен, rate limiting вернулся к distributed режиму")
                }
            }
            .onErrorResume { ex ->
                handleRedisError(ex, bucketKey, rateLimit)
            }
    }

    /**
     * Обрабатывает ошибку Redis и переключается на fallback.
     */
    private fun handleRedisError(
        ex: Throwable,
        bucketKey: String,
        rateLimit: RateLimit
    ): Mono<RateLimitResult> {
        // Логируем warning только при первом переключении на fallback
        if (usingFallback.compareAndSet(false, true)) {
            logger.warn("Rate limiting disabled: Redis unavailable - {}", ex.message)
        }

        if (!fallbackEnabled) {
            // Fallback отключён — пропускаем запрос
            logger.debug("Fallback отключён, запрос пропущен без проверки rate limit")
            return Mono.just(
                RateLimitResult(
                    allowed = true,
                    remaining = rateLimit.burstSize,
                    resetTime = System.currentTimeMillis()
                )
            )
        }

        // Используем локальный rate limiter
        val result = localRateLimiter.checkRateLimit(
            key = bucketKey,
            requestsPerSecond = rateLimit.requestsPerSecond,
            burstSize = rateLimit.burstSize
        )

        return Mono.just(result)
    }

    /**
     * Формирует ключ Redis для bucket.
     *
     * @param routeId ID маршрута
     * @param clientKey идентификатор клиента
     * @return ключ в формате "ratelimit:{routeId}:{clientKey}"
     */
    private fun buildKey(routeId: UUID, clientKey: String): String {
        return "$redisKeyPrefix:$routeId:$clientKey"
    }

    /**
     * Возвращает true если сейчас используется fallback режим.
     */
    fun isUsingFallback(): Boolean = usingFallback.get()
}
