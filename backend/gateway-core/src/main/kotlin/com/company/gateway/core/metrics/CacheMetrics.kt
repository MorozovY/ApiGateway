package com.company.gateway.core.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Метрики для cache performance.
 *
 * Записывает:
 * - gateway_cache_operations_total — операции кэша (hit/miss/refresh)
 * - gateway_cache_size — размер кэша
 * - gateway_cache_refresh_duration_seconds — длительность refresh операций
 *
 * Story 14.3, AC4: Cache Performance Metrics
 */
@Component
class CacheMetrics(
    private val meterRegistry: MeterRegistry
) {
    companion object {
        /**
         * Счётчик операций кэша.
         * Labels: cache (route|ratelimit), result (hit|miss|refresh)
         */
        const val METRIC_CACHE_OPERATIONS = "gateway_cache_operations_total"

        /**
         * Gauge размера кэша.
         * Labels: cache (route|ratelimit)
         */
        const val METRIC_CACHE_SIZE = "gateway_cache_size"

        /**
         * Гистограмма длительности refresh операций.
         */
        const val METRIC_CACHE_REFRESH_DURATION = "gateway_cache_refresh_duration_seconds"
    }

    /**
     * Кэш counter метрик для операций.
     */
    private val operationCounters = ConcurrentHashMap<String, Counter>()

    /**
     * Атомарные счётчики для gauge размера кэша.
     */
    private val cacheSizeGauges = ConcurrentHashMap<String, AtomicLong>()

    /**
     * Timer для записи длительности refresh.
     */
    private val refreshDurationTimer: Timer by lazy {
        Timer.builder(METRIC_CACHE_REFRESH_DURATION)
            .description("Длительность refresh операций кэша")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry)
    }

    /**
     * Записывает операцию кэша.
     *
     * @param cache тип кэша: route, ratelimit
     * @param result результат: hit, miss, refresh
     */
    fun recordOperation(cache: String, result: String) {
        val counterKey = "$cache:$result"

        val counter = operationCounters.computeIfAbsent(counterKey) {
            Counter.builder(METRIC_CACHE_OPERATIONS)
                .description("Количество операций кэша")
                .tag("cache", cache)
                .tag("result", result)
                .register(meterRegistry)
        }

        counter.increment()
    }

    /**
     * Обновляет gauge размера кэша.
     *
     * @param cache тип кэша: route, ratelimit
     * @param size текущий размер
     */
    fun updateCacheSize(cache: String, size: Int) {
        val atomicSize = cacheSizeGauges.computeIfAbsent(cache) {
            val newValue = AtomicLong(0)
            meterRegistry.gauge(
                METRIC_CACHE_SIZE,
                listOf(Tag.of("cache", cache)),
                newValue
            ) { it.get().toDouble() }
            newValue
        }

        atomicSize.set(size.toLong())
    }

    /**
     * Записывает длительность refresh операции.
     *
     * @param duration длительность операции
     */
    fun recordRefreshDuration(duration: Duration) {
        refreshDurationTimer.record(duration)
    }
}
