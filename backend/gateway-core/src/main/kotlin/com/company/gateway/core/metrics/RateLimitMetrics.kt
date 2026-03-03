package com.company.gateway.core.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Метрики для rate limiting.
 *
 * Записывает:
 * - gateway_ratelimit_decisions_total — решения (allowed/denied)
 * - gateway_ratelimit_cache_hits_total — cache hits (redis/caffeine)
 * - gateway_ratelimit_cache_misses_total — cache misses
 * - gateway_ratelimit_remaining_tokens — оставшиеся токены (sampled)
 *
 * Story 14.3, AC3: Rate Limit Metrics
 */
@Component
class RateLimitMetrics(
    private val meterRegistry: MeterRegistry
) {
    companion object {
        /**
         * Счётчик решений rate limit.
         * Labels: decision (allowed|denied), limit_type (route|consumer)
         */
        const val METRIC_RATELIMIT_DECISIONS = "gateway_ratelimit_decisions_total"

        /**
         * Счётчик cache hits.
         * Labels: cache (redis|caffeine)
         */
        const val METRIC_RATELIMIT_CACHE_HITS = "gateway_ratelimit_cache_hits_total"

        /**
         * Счётчик cache misses.
         */
        const val METRIC_RATELIMIT_CACHE_MISSES = "gateway_ratelimit_cache_misses_total"

        /**
         * Gauge оставшихся токенов (sampled).
         * Labels: route_id, consumer_id
         *
         * ВАЖНО: Используется sampling (1 из 100 запросов) для контроля cardinality.
         */
        const val METRIC_RATELIMIT_REMAINING_TOKENS = "gateway_ratelimit_remaining_tokens"

        /**
         * Sampling rate для remaining tokens gauge (1 из 100).
         *
         * Обоснование: При ~1000 уникальных consumers, rate 100 даёт
         * максимум ~10 gauge updates/sec при 1000 RPS, что безопасно
         * для cardinality Prometheus (max ~1000 unique label combinations).
         */
        private const val SAMPLING_RATE = 100
    }

    /**
     * Кэш counter метрик для decisions.
     */
    private val decisionCounters = ConcurrentHashMap<String, Counter>()

    /**
     * Кэш counter метрик для cache hits.
     */
    private val cacheHitCounters = ConcurrentHashMap<String, Counter>()

    /**
     * Counter для cache misses.
     */
    private val cacheMissCounter: Counter by lazy {
        Counter.builder(METRIC_RATELIMIT_CACHE_MISSES)
            .description("Количество cache misses")
            .register(meterRegistry)
    }

    /**
     * Атомарные счётчики для remaining tokens gauge.
     * Ключ: "routeId:consumerId"
     */
    private val remainingTokensGauges = ConcurrentHashMap<String, AtomicLong>()

    /**
     * Записывает решение rate limit.
     *
     * @param decision тип решения: allowed, denied
     * @param limitType тип лимита: route, consumer
     */
    fun recordDecision(decision: String, limitType: String) {
        val counterKey = "$decision:$limitType"

        val counter = decisionCounters.computeIfAbsent(counterKey) {
            Counter.builder(METRIC_RATELIMIT_DECISIONS)
                .description("Количество решений rate limit")
                .tag("decision", decision)
                .tag("limit_type", limitType)
                .register(meterRegistry)
        }

        counter.increment()
    }

    /**
     * Записывает cache hit.
     *
     * @param cache тип кэша: redis, caffeine
     */
    fun recordCacheHit(cache: String) {
        val counter = cacheHitCounters.computeIfAbsent(cache) {
            Counter.builder(METRIC_RATELIMIT_CACHE_HITS)
                .description("Количество cache hits")
                .tag("cache", cache)
                .register(meterRegistry)
        }

        counter.increment()
    }

    /**
     * Записывает cache miss.
     */
    fun recordCacheMiss() {
        cacheMissCounter.increment()
    }

    /**
     * Записывает оставшиеся токены (sampled).
     *
     * Использует sampling (1 из 100) для контроля cardinality.
     *
     * @param routeId ID маршрута
     * @param consumerId ID consumer
     * @param remaining количество оставшихся токенов
     */
    fun recordRemainingTokens(routeId: String, consumerId: String, remaining: Int) {
        // Sampling: записываем только 1 из SAMPLING_RATE запросов
        if (Random.nextInt(SAMPLING_RATE) != 0) return

        recordRemainingTokensInternal(routeId, consumerId, remaining)
    }

    /**
     * Записывает оставшиеся токены без sampling.
     * Используется в тестах для детерминированного поведения.
     */
    internal fun recordRemainingTokensForced(routeId: String, consumerId: String, remaining: Int) {
        recordRemainingTokensInternal(routeId, consumerId, remaining)
    }

    /**
     * Внутренний метод записи remaining tokens gauge.
     */
    private fun recordRemainingTokensInternal(routeId: String, consumerId: String, remaining: Int) {
        val gaugeKey = "$routeId:$consumerId"

        val atomicValue = remainingTokensGauges.computeIfAbsent(gaugeKey) {
            val newValue = AtomicLong(0)
            meterRegistry.gauge(
                METRIC_RATELIMIT_REMAINING_TOKENS,
                listOf(
                    Tag.of("route_id", routeId),
                    Tag.of("consumer_id", consumerId)
                ),
                newValue
            ) { it.get().toDouble() }
            newValue
        }

        atomicValue.set(remaining.toLong())
    }
}
