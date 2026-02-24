package com.company.gateway.core.ratelimit

import com.company.gateway.common.model.ConsumerRateLimit
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
 *
 * Поддерживает two-level rate limiting (Story 12.8):
 * - Per-route rate limit — привязан к конкретному маршруту
 * - Per-consumer rate limit — глобальный для всех маршрутов consumer'а
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

    companion object {
        /** Prefix для consumer rate limit ключей в Redis */
        const val CONSUMER_KEY_PREFIX = "consumer"
    }

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
                // Логируем результат проверки rate limit (DEBUG — ожидаемое событие при нагрузке)
                if (!result.allowed) {
                    logger.debug("Rate limit exceeded: key={}, remaining={}, resetTime={}",
                        bucketKey, result.remaining, result.resetTime)
                }
                // Успешно использовали Redis — сбрасываем fallback флаг
                if (usingFallback.compareAndSet(true, false)) {
                    logger.info("Redis recovered, rate limiting returned to distributed mode")
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
     * Проверяет per-consumer rate limit.
     *
     * Story 12.8, AC3: Consumer rate limit check
     *
     * @param consumerId идентификатор consumer (Keycloak client_id)
     * @param consumerRateLimit политика rate limiting для consumer
     * @return результат проверки
     */
    fun checkConsumerRateLimit(
        consumerId: String,
        consumerRateLimit: ConsumerRateLimit
    ): Mono<RateLimitResult> {
        val bucketKey = buildConsumerKey(consumerId)

        return tokenBucketScript.checkRateLimit(
            key = bucketKey,
            requestsPerSecond = consumerRateLimit.requestsPerSecond,
            burstSize = consumerRateLimit.burstSize
        )
            .doOnSuccess { result ->
                if (!result.allowed) {
                    logger.debug("Consumer rate limit exceeded: consumerId={}, remaining={}, resetTime={}",
                        consumerId, result.remaining, result.resetTime)
                }
                // Успешно использовали Redis — сбрасываем fallback флаг
                if (usingFallback.compareAndSet(true, false)) {
                    logger.info("Redis recovered, rate limiting returned to distributed mode")
                }
            }
            .onErrorResume { ex ->
                handleConsumerRedisError(ex, bucketKey, consumerRateLimit)
            }
    }

    /**
     * Проверяет оба лимита (per-route и per-consumer) и применяет более строгий.
     *
     * Story 12.8, AC4: Two-level Rate Limiting (Stricter Wins)
     *
     * Алгоритм:
     * 1. Проверить per-route лимит
     * 2. Проверить per-consumer лимит
     * 3. Если хотя бы один не разрешён — отказать
     * 4. Если оба разрешены — вернуть данные от более строгого (меньше remaining)
     *
     * @param routeId ID маршрута (может быть null)
     * @param clientKey идентификатор клиента (IP)
     * @param consumerId идентификатор consumer
     * @param routeLimit per-route rate limit (может быть null)
     * @param consumerLimit per-consumer rate limit
     * @return результат проверки с типом сработавшего лимита
     */
    fun checkBothLimits(
        routeId: UUID?,
        clientKey: String,
        consumerId: String,
        routeLimit: RateLimit?,
        consumerLimit: ConsumerRateLimit
    ): Mono<RateLimitCheckResult> {
        // Проверяем per-route лимит (если есть)
        val routeCheck: Mono<RateLimitResult> = if (routeId != null && routeLimit != null) {
            checkRateLimit(routeId, clientKey, routeLimit)
        } else {
            Mono.just(RateLimitResult(allowed = true, remaining = Int.MAX_VALUE, resetTime = 0))
        }

        // Проверяем per-consumer лимит
        val consumerCheck: Mono<RateLimitResult> = checkConsumerRateLimit(consumerId, consumerLimit)

        return Mono.zip(routeCheck, consumerCheck)
            .map { tuple ->
                val routeResult = tuple.t1
                val consumerResult = tuple.t2

                when {
                    // Route limit не разрешён — возвращаем его
                    !routeResult.allowed -> RateLimitCheckResult(
                        result = routeResult,
                        limitType = RateLimitCheckResult.TYPE_ROUTE,
                        limit = routeLimit?.requestsPerSecond
                    )

                    // Consumer limit не разрешён — возвращаем его
                    !consumerResult.allowed -> RateLimitCheckResult(
                        result = consumerResult,
                        limitType = RateLimitCheckResult.TYPE_CONSUMER,
                        limit = consumerLimit.requestsPerSecond
                    )

                    // Оба разрешены — возвращаем более строгий (меньше remaining)
                    // Если per-route limit отсутствует (remaining = MAX_VALUE), выбираем consumer
                    routeResult.remaining <= consumerResult.remaining && routeLimit != null -> RateLimitCheckResult(
                        result = routeResult,
                        limitType = RateLimitCheckResult.TYPE_ROUTE,
                        limit = routeLimit.requestsPerSecond
                    )

                    else -> RateLimitCheckResult(
                        result = consumerResult,
                        limitType = RateLimitCheckResult.TYPE_CONSUMER,
                        limit = consumerLimit.requestsPerSecond
                    )
                }
            }
    }

    /**
     * Обрабатывает ошибку Redis для consumer rate limit.
     */
    private fun handleConsumerRedisError(
        ex: Throwable,
        bucketKey: String,
        consumerRateLimit: ConsumerRateLimit
    ): Mono<RateLimitResult> {
        // Логируем warning только при первом переключении на fallback
        if (usingFallback.compareAndSet(false, true)) {
            logger.warn("Rate limiting disabled: Redis unavailable - {}", ex.message)
        }

        if (!fallbackEnabled) {
            // Fallback отключён — пропускаем запрос
            logger.debug("Fallback отключён, запрос пропущен без проверки consumer rate limit")
            return Mono.just(
                RateLimitResult(
                    allowed = true,
                    remaining = consumerRateLimit.burstSize,
                    resetTime = System.currentTimeMillis()
                )
            )
        }

        // Используем локальный rate limiter
        val result = localRateLimiter.checkRateLimit(
            key = bucketKey,
            requestsPerSecond = consumerRateLimit.requestsPerSecond,
            burstSize = consumerRateLimit.burstSize
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
     * Формирует ключ Redis для consumer bucket.
     *
     * @param consumerId идентификатор consumer
     * @return ключ в формате "ratelimit:consumer:{consumerId}"
     */
    private fun buildConsumerKey(consumerId: String): String {
        return "$redisKeyPrefix:$CONSUMER_KEY_PREFIX:$consumerId"
    }

    /**
     * Возвращает true если сейчас используется fallback режим.
     */
    fun isUsingFallback(): Boolean = usingFallback.get()
}
