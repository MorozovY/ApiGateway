package com.company.gateway.core.ratelimit

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Локальный in-memory rate limiter на основе Caffeine cache.
 *
 * Используется как fallback при недоступности Redis (graceful degradation).
 * Применяет консервативные лимиты (по умолчанию 50% от policy) для защиты upstream.
 *
 * Важно: это НЕ distributed rate limiter — каждый инстанс gateway имеет свой локальный кэш.
 */
@Component
class LocalRateLimiter(
    @Value("\${gateway.ratelimit.fallback-reduction:0.5}")
    private val fallbackReduction: Double = 0.5,

    @Value("\${gateway.ratelimit.local-cache-ttl-seconds:60}")
    private val cacheTtlSeconds: Long = 60
) {
    private val logger = LoggerFactory.getLogger(LocalRateLimiter::class.java)

    /**
     * Кэш локальных token bucket состояний.
     * Ключ: "ratelimit:{routeId}:{clientKey}"
     * Значение: AtomicReference на LocalTokenBucket
     */
    private val buckets: Cache<String, AtomicReference<LocalTokenBucket>> = Caffeine.newBuilder()
        .expireAfterAccess(cacheTtlSeconds, TimeUnit.SECONDS)
        .maximumSize(10000)
        .build()

    /**
     * Проверяет rate limit локально.
     *
     * @param key ключ в формате "ratelimit:{routeId}:{clientKey}"
     * @param requestsPerSecond оригинальный лимит из policy
     * @param burstSize оригинальный burst из policy
     * @return RateLimitResult с консервативными лимитами
     */
    fun checkRateLimit(
        key: String,
        requestsPerSecond: Int,
        burstSize: Int
    ): RateLimitResult {
        // Применяем консервативные лимиты при fallback
        val reducedRate = (requestsPerSecond * fallbackReduction).toInt().coerceAtLeast(1)
        val reducedBurst = (burstSize * fallbackReduction).toInt().coerceAtLeast(1)

        val bucketRef = buckets.get(key) {
            AtomicReference(LocalTokenBucket(reducedBurst.toDouble(), System.currentTimeMillis()))
        }!!

        val now = System.currentTimeMillis()

        // Атомарно обновляем bucket
        while (true) {
            val current = bucketRef.get()
            val updated = refillAndConsume(current, reducedRate, reducedBurst, now)

            if (bucketRef.compareAndSet(current, updated.first)) {
                return updated.second
            }
            // CAS не удался — повторяем с актуальным значением
        }
    }

    /**
     * Восполняет токены и пытается потребить один.
     *
     * @return Pair(обновлённый bucket, результат)
     */
    private fun refillAndConsume(
        bucket: LocalTokenBucket,
        rate: Int,
        capacity: Int,
        now: Long
    ): Pair<LocalTokenBucket, RateLimitResult> {
        val elapsedSeconds = (now - bucket.lastRefillTime) / 1000.0
        val refill = elapsedSeconds * rate
        var tokens = (bucket.tokens + refill).coerceAtMost(capacity.toDouble())

        val allowed: Boolean
        if (tokens >= 1.0) {
            tokens -= 1.0
            allowed = true
        } else {
            allowed = false
        }

        val remaining = tokens.toInt()
        val tokensNeeded = capacity - remaining
        val secondsToFull = if (tokensNeeded > 0 && rate > 0) {
            (tokensNeeded.toDouble() / rate).toLong() * 1000
        } else {
            0L
        }
        val resetTime = now + secondsToFull

        val newBucket = LocalTokenBucket(tokens, now)
        val result = RateLimitResult(allowed, remaining, resetTime)

        return Pair(newBucket, result)
    }

    /**
     * Возвращает текущий коэффициент снижения лимитов.
     */
    fun getFallbackReduction(): Double = fallbackReduction

    /**
     * Очищает локальный кэш. Полезно для тестов.
     */
    fun clearCache() {
        buckets.invalidateAll()
    }

    /**
     * Локальное состояние token bucket.
     *
     * @property tokens текущее количество токенов (дробное для точного восполнения)
     * @property lastRefillTime timestamp последнего восполнения
     */
    private data class LocalTokenBucket(
        val tokens: Double,
        val lastRefillTime: Long
    )
}
