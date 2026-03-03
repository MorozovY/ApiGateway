package com.company.gateway.admin.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Unit тесты для RouteMetrics.
 *
 * Story 14.3, Task 1: Route Management Metrics
 */
class RouteMetricsTest {

    private lateinit var meterRegistry: MeterRegistry
    private lateinit var routeMetrics: RouteMetrics

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        routeMetrics = RouteMetrics(meterRegistry)
    }

    @Test
    fun `записывает успешную операцию создания маршрута`() {
        // When
        routeMetrics.recordOperation("create", true)

        // Then
        val counter = meterRegistry.find(RouteMetrics.METRIC_ROUTE_OPERATIONS)
            .tag("operation", "create")
            .tag("status", "success")
            .counter()

        assertNotNull(counter)
        assertEquals(1.0, counter!!.count())
    }

    @Test
    fun `записывает неуспешную операцию обновления маршрута`() {
        // When
        routeMetrics.recordOperation("update", false)

        // Then
        val counter = meterRegistry.find(RouteMetrics.METRIC_ROUTE_OPERATIONS)
            .tag("operation", "update")
            .tag("status", "failure")
            .counter()

        assertNotNull(counter)
        assertEquals(1.0, counter!!.count())
    }

    @Test
    fun `записывает операцию удаления маршрута`() {
        // When
        routeMetrics.recordOperation("delete", true)

        // Then
        val counter = meterRegistry.find(RouteMetrics.METRIC_ROUTE_OPERATIONS)
            .tag("operation", "delete")
            .tag("status", "success")
            .counter()

        assertNotNull(counter)
        assertEquals(1.0, counter!!.count())
    }

    @Test
    fun `накапливает несколько операций одного типа`() {
        // When
        routeMetrics.recordOperation("create", true)
        routeMetrics.recordOperation("create", true)
        routeMetrics.recordOperation("create", false)

        // Then
        val successCounter = meterRegistry.find(RouteMetrics.METRIC_ROUTE_OPERATIONS)
            .tag("operation", "create")
            .tag("status", "success")
            .counter()

        val failureCounter = meterRegistry.find(RouteMetrics.METRIC_ROUTE_OPERATIONS)
            .tag("operation", "create")
            .tag("status", "failure")
            .counter()

        assertEquals(2.0, successCounter!!.count())
        assertEquals(1.0, failureCounter!!.count())
    }

    @Test
    fun `обновляет gauge количества активных маршрутов по статусу`() {
        // When
        routeMetrics.updateActiveRoutesGauge("published", 10)
        routeMetrics.updateActiveRoutesGauge("pending", 5)
        routeMetrics.updateActiveRoutesGauge("draft", 3)
        routeMetrics.updateActiveRoutesGauge("rejected", 2)

        // Then
        val publishedGauge = meterRegistry.find(RouteMetrics.METRIC_ROUTES_ACTIVE)
            .tag("status", "published")
            .gauge()

        val pendingGauge = meterRegistry.find(RouteMetrics.METRIC_ROUTES_ACTIVE)
            .tag("status", "pending")
            .gauge()

        assertNotNull(publishedGauge)
        assertNotNull(pendingGauge)
        assertEquals(10.0, publishedGauge!!.value())
        assertEquals(5.0, pendingGauge!!.value())
    }

    @Test
    fun `gauge обновляется при изменении значения`() {
        // Given
        routeMetrics.updateActiveRoutesGauge("published", 10)

        // When
        routeMetrics.updateActiveRoutesGauge("published", 15)

        // Then
        val gauge = meterRegistry.find(RouteMetrics.METRIC_ROUTES_ACTIVE)
            .tag("status", "published")
            .gauge()

        assertEquals(15.0, gauge!!.value())
    }
}
