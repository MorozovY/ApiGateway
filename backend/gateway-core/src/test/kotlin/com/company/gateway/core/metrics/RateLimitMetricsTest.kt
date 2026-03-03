package com.company.gateway.core.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Unit тесты для RateLimitMetrics.
 *
 * Story 14.3, Task 3: Rate Limit Metrics
 */
class RateLimitMetricsTest {

    private lateinit var meterRegistry: MeterRegistry
    private lateinit var rateLimitMetrics: RateLimitMetrics

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        rateLimitMetrics = RateLimitMetrics(meterRegistry)
    }

    @Test
    fun `записывает решение allowed для route лимита`() {
        // When
        rateLimitMetrics.recordDecision("allowed", "route")

        // Then
        val counter = meterRegistry.find(RateLimitMetrics.METRIC_RATELIMIT_DECISIONS)
            .tag("decision", "allowed")
            .tag("limit_type", "route")
            .counter()

        assertNotNull(counter)
        assertEquals(1.0, counter!!.count())
    }

    @Test
    fun `записывает решение denied для consumer лимита`() {
        // When
        rateLimitMetrics.recordDecision("denied", "consumer")

        // Then
        val counter = meterRegistry.find(RateLimitMetrics.METRIC_RATELIMIT_DECISIONS)
            .tag("decision", "denied")
            .tag("limit_type", "consumer")
            .counter()

        assertNotNull(counter)
        assertEquals(1.0, counter!!.count())
    }

    @Test
    fun `накапливает несколько решений`() {
        // When
        rateLimitMetrics.recordDecision("allowed", "route")
        rateLimitMetrics.recordDecision("allowed", "route")
        rateLimitMetrics.recordDecision("denied", "route")

        // Then
        val allowedCounter = meterRegistry.find(RateLimitMetrics.METRIC_RATELIMIT_DECISIONS)
            .tag("decision", "allowed")
            .tag("limit_type", "route")
            .counter()

        val deniedCounter = meterRegistry.find(RateLimitMetrics.METRIC_RATELIMIT_DECISIONS)
            .tag("decision", "denied")
            .tag("limit_type", "route")
            .counter()

        assertEquals(2.0, allowedCounter!!.count())
        assertEquals(1.0, deniedCounter!!.count())
    }

    @Test
    fun `записывает cache hit для redis`() {
        // When
        rateLimitMetrics.recordCacheHit("redis")

        // Then
        val counter = meterRegistry.find(RateLimitMetrics.METRIC_RATELIMIT_CACHE_HITS)
            .tag("cache", "redis")
            .counter()

        assertNotNull(counter)
        assertEquals(1.0, counter!!.count())
    }

    @Test
    fun `записывает cache hit для caffeine`() {
        // When
        rateLimitMetrics.recordCacheHit("caffeine")

        // Then
        val counter = meterRegistry.find(RateLimitMetrics.METRIC_RATELIMIT_CACHE_HITS)
            .tag("cache", "caffeine")
            .counter()

        assertNotNull(counter)
        assertEquals(1.0, counter!!.count())
    }

    @Test
    fun `записывает cache miss`() {
        // When
        rateLimitMetrics.recordCacheMiss()

        // Then
        val counter = meterRegistry.find(RateLimitMetrics.METRIC_RATELIMIT_CACHE_MISSES)
            .counter()

        assertNotNull(counter)
        assertEquals(1.0, counter!!.count())
    }

    @Test
    fun `записывает оставшиеся токены через forced метод`() {
        // When — используем recordRemainingTokensForced для детерминированного теста
        rateLimitMetrics.recordRemainingTokensForced("route-123", "consumer-456", 50)

        // Then
        val gauge = meterRegistry.find(RateLimitMetrics.METRIC_RATELIMIT_REMAINING_TOKENS)
            .tag("route_id", "route-123")
            .tag("consumer_id", "consumer-456")
            .gauge()

        assertNotNull(gauge)
        assertEquals(50.0, gauge!!.value())
    }

    @Test
    fun `обновляет gauge при повторном вызове`() {
        // Given
        rateLimitMetrics.recordRemainingTokensForced("route-123", "consumer-456", 50)

        // When
        rateLimitMetrics.recordRemainingTokensForced("route-123", "consumer-456", 25)

        // Then
        val gauge = meterRegistry.find(RateLimitMetrics.METRIC_RATELIMIT_REMAINING_TOKENS)
            .tag("route_id", "route-123")
            .tag("consumer_id", "consumer-456")
            .gauge()

        assertNotNull(gauge)
        assertEquals(25.0, gauge!!.value())
    }
}
