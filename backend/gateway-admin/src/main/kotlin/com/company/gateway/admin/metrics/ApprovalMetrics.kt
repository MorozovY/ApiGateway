package com.company.gateway.admin.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Метрики для workflow согласования маршрутов.
 *
 * Записывает:
 * - gateway_approval_actions_total — счётчик действий (submit, approve, reject)
 * - gateway_approval_duration_seconds — гистограмма времени от PENDING до PUBLISHED/REJECTED
 *
 * Story 14.3, AC2: Approval Workflow Metrics
 */
@Component
class ApprovalMetrics(
    private val meterRegistry: MeterRegistry
) {
    companion object {
        /**
         * Счётчик действий согласования.
         * Labels: action (submit|approve|reject), role (developer|security)
         */
        const val METRIC_APPROVAL_ACTIONS = "gateway_approval_actions_total"

        /**
         * Гистограмма времени согласования.
         * Измеряет время от submittedAt до approvedAt/rejectedAt.
         */
        const val METRIC_APPROVAL_DURATION = "gateway_approval_duration_seconds"

        // Константы ролей для метрик (соответствуют Role enum в lowercase)
        const val ROLE_DEVELOPER = "developer"
        const val ROLE_SECURITY = "security"

        // Константы действий
        const val ACTION_SUBMIT = "submit"
        const val ACTION_APPROVE = "approve"
        const val ACTION_REJECT = "reject"
    }

    /**
     * Кэш counter метрик для избежания повторной регистрации.
     */
    private val actionCounters = ConcurrentHashMap<String, Counter>()

    /**
     * Timer для записи длительности согласования.
     */
    private val approvalDurationTimer: Timer by lazy {
        Timer.builder(METRIC_APPROVAL_DURATION)
            .description("Время от PENDING до PUBLISHED/REJECTED")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry)
    }

    /**
     * Записывает действие согласования.
     *
     * @param action тип действия: submit, approve, reject
     * @param role роль пользователя: developer, security
     */
    fun recordAction(action: String, role: String) {
        val counterKey = "$action:$role"

        val counter = actionCounters.computeIfAbsent(counterKey) {
            Counter.builder(METRIC_APPROVAL_ACTIONS)
                .description("Количество действий согласования")
                .tag("action", action)
                .tag("role", role)
                .register(meterRegistry)
        }

        counter.increment()
    }

    /**
     * Записывает длительность согласования маршрута.
     *
     * @param pendingTimestamp время отправки на согласование (submittedAt)
     * @param completedTimestamp время завершения (approvedAt или rejectedAt)
     */
    fun recordApprovalDuration(pendingTimestamp: Instant, completedTimestamp: Instant) {
        val duration = Duration.between(pendingTimestamp, completedTimestamp)
        approvalDurationTimer.record(duration)
    }
}
