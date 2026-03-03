package com.company.gateway.core.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Duration

/**
 * Unit тесты для CacheMetrics.
 *
 * Story 14.3, Task 4: Cache Performance Metrics
 */
class CacheMetricsTest {

    private lateinit var meterRegistry: MeterRegistry
    private lateinit var cacheMetrics: CacheMetrics

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        cacheMetrics = CacheMetrics(meterRegistry)
    }

    @Test
    fun `записывает cache hit для route кэша`() {
        // When
        cacheMetrics.recordOperation("route", "hit")

        // Then
        val counter = meterRegistry.find(CacheMetrics.METRIC_CACHE_OPERATIONS)
            .tag("cache", "route")
            .tag("result", "hit")
            .counter()

        assertNotNull(counter)
        assertEquals(1.0, counter!!.count())
    }

    @Test
    fun `записывает cache miss для ratelimit кэша`() {
        // When
        cacheMetrics.recordOperation("ratelimit", "miss")

        // Then
        val counter = meterRegistry.find(CacheMetrics.METRIC_CACHE_OPERATIONS)
            .tag("cache", "ratelimit")
            .tag("result", "miss")
            .counter()

        assertNotNull(counter)
        assertEquals(1.0, counter!!.count())
    }

    @Test
    fun `записывает cache refresh операцию`() {
        // When
        cacheMetrics.recordOperation("route", "refresh")

        // Then
        val counter = meterRegistry.find(CacheMetrics.METRIC_CACHE_OPERATIONS)
            .tag("cache", "route")
            .tag("result", "refresh")
            .counter()

        assertNotNull(counter)
        assertEquals(1.0, counter!!.count())
    }

    @Test
    fun `накапливает несколько операций`() {
        // When
        cacheMetrics.recordOperation("route", "hit")
        cacheMetrics.recordOperation("route", "hit")
        cacheMetrics.recordOperation("route", "miss")

        // Then
        val hitCounter = meterRegistry.find(CacheMetrics.METRIC_CACHE_OPERATIONS)
            .tag("cache", "route")
            .tag("result", "hit")
            .counter()

        val missCounter = meterRegistry.find(CacheMetrics.METRIC_CACHE_OPERATIONS)
            .tag("cache", "route")
            .tag("result", "miss")
            .counter()

        assertEquals(2.0, hitCounter!!.count())
        assertEquals(1.0, missCounter!!.count())
    }

    @Test
    fun `обновляет gauge размера кэша`() {
        // When
        cacheMetrics.updateCacheSize("route", 100)
        cacheMetrics.updateCacheSize("ratelimit", 50)

        // Then
        val routeGauge = meterRegistry.find(CacheMetrics.METRIC_CACHE_SIZE)
            .tag("cache", "route")
            .gauge()

        val ratelimitGauge = meterRegistry.find(CacheMetrics.METRIC_CACHE_SIZE)
            .tag("cache", "ratelimit")
            .gauge()

        assertNotNull(routeGauge)
        assertNotNull(ratelimitGauge)
        assertEquals(100.0, routeGauge!!.value())
        assertEquals(50.0, ratelimitGauge!!.value())
    }

    @Test
    fun `записывает длительность refresh операции`() {
        // When
        cacheMetrics.recordRefreshDuration(Duration.ofMillis(150))

        // Then
        val timer = meterRegistry.find(CacheMetrics.METRIC_CACHE_REFRESH_DURATION)
            .timer()

        assertNotNull(timer)
        assertEquals(1, timer!!.count())
        assertTrue(timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS) >= 149.0)
    }

    @Test
    fun `записывает несколько refresh для гистограммы`() {
        // When
        cacheMetrics.recordRefreshDuration(Duration.ofMillis(100))
        cacheMetrics.recordRefreshDuration(Duration.ofMillis(200))
        cacheMetrics.recordRefreshDuration(Duration.ofMillis(300))

        // Then
        val timer = meterRegistry.find(CacheMetrics.METRIC_CACHE_REFRESH_DURATION)
            .timer()

        assertNotNull(timer)
        assertEquals(3, timer!!.count())
    }
}
