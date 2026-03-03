package com.company.gateway.admin.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Duration
import java.time.Instant

/**
 * Unit тесты для ApprovalMetrics.
 *
 * Story 14.3, Task 2: Approval Workflow Metrics
 */
class ApprovalMetricsTest {

    private lateinit var meterRegistry: MeterRegistry
    private lateinit var approvalMetrics: ApprovalMetrics

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        approvalMetrics = ApprovalMetrics(meterRegistry)
    }

    @Test
    fun `записывает действие submit от developer`() {
        // When
        approvalMetrics.recordAction("submit", "developer")

        // Then
        val counter = meterRegistry.find(ApprovalMetrics.METRIC_APPROVAL_ACTIONS)
            .tag("action", "submit")
            .tag("role", "developer")
            .counter()

        assertNotNull(counter)
        assertEquals(1.0, counter!!.count())
    }

    @Test
    fun `записывает действие approve от security`() {
        // When
        approvalMetrics.recordAction("approve", "security")

        // Then
        val counter = meterRegistry.find(ApprovalMetrics.METRIC_APPROVAL_ACTIONS)
            .tag("action", "approve")
            .tag("role", "security")
            .counter()

        assertNotNull(counter)
        assertEquals(1.0, counter!!.count())
    }

    @Test
    fun `записывает действие reject от security`() {
        // When
        approvalMetrics.recordAction("reject", "security")

        // Then
        val counter = meterRegistry.find(ApprovalMetrics.METRIC_APPROVAL_ACTIONS)
            .tag("action", "reject")
            .tag("role", "security")
            .counter()

        assertNotNull(counter)
        assertEquals(1.0, counter!!.count())
    }

    @Test
    fun `накапливает несколько действий`() {
        // When
        approvalMetrics.recordAction("submit", "developer")
        approvalMetrics.recordAction("submit", "developer")
        approvalMetrics.recordAction("approve", "security")

        // Then
        val submitCounter = meterRegistry.find(ApprovalMetrics.METRIC_APPROVAL_ACTIONS)
            .tag("action", "submit")
            .tag("role", "developer")
            .counter()

        val approveCounter = meterRegistry.find(ApprovalMetrics.METRIC_APPROVAL_ACTIONS)
            .tag("action", "approve")
            .tag("role", "security")
            .counter()

        assertEquals(2.0, submitCounter!!.count())
        assertEquals(1.0, approveCounter!!.count())
    }

    @Test
    fun `записывает длительность approval`() {
        // Given
        val pendingTimestamp = Instant.now().minusSeconds(3600) // 1 час назад
        val completedTimestamp = Instant.now()

        // When
        approvalMetrics.recordApprovalDuration(pendingTimestamp, completedTimestamp)

        // Then
        val timer = meterRegistry.find(ApprovalMetrics.METRIC_APPROVAL_DURATION)
            .timer()

        assertNotNull(timer)
        assertEquals(1, timer!!.count())
        // Длительность должна быть примерно 1 час (3600 секунд)
        assertTrue(timer.mean(java.util.concurrent.TimeUnit.SECONDS) >= 3599.0)
    }

    @Test
    fun `записывает несколько длительностей для гистограммы`() {
        // Given
        val now = Instant.now()

        // When - записываем несколько длительностей
        approvalMetrics.recordApprovalDuration(now.minusSeconds(60), now)  // 1 минута
        approvalMetrics.recordApprovalDuration(now.minusSeconds(120), now) // 2 минуты
        approvalMetrics.recordApprovalDuration(now.minusSeconds(300), now) // 5 минут

        // Then
        val timer = meterRegistry.find(ApprovalMetrics.METRIC_APPROVAL_DURATION)
            .timer()

        assertNotNull(timer)
        assertEquals(3, timer!!.count())
    }
}
