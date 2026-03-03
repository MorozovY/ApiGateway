package com.company.gateway.admin.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Метрики для операций с маршрутами.
 *
 * Записывает:
 * - gateway_route_operations_total — счётчик операций (create, update, delete)
 * - gateway_routes_active_total — gauge количества активных маршрутов по статусу
 *
 * Story 14.3, AC1: Route Management Metrics
 */
@Component
class RouteMetrics(
    private val meterRegistry: MeterRegistry
) {
    companion object {
        /**
         * Счётчик операций с маршрутами.
         * Labels: operation (create|update|delete), status (success|failure)
         */
        const val METRIC_ROUTE_OPERATIONS = "gateway_route_operations_total"

        /**
         * Gauge количества активных маршрутов по статусу.
         * Labels: status (published|pending|draft|rejected)
         */
        const val METRIC_ROUTES_ACTIVE = "gateway_routes_active_total"
    }

    /**
     * Атомарные счётчики для gauge метрик по статусу.
     * Ключ: status (published, pending, draft, rejected)
     */
    private val activeRoutesCounters = ConcurrentHashMap<String, AtomicLong>()

    /**
     * Кэш counter метрик для избежания повторной регистрации.
     */
    private val operationCounters = ConcurrentHashMap<String, Counter>()

    /**
     * Записывает операцию с маршрутом.
     *
     * @param operation тип операции: create, update, delete
     * @param success true если операция успешна
     */
    fun recordOperation(operation: String, success: Boolean) {
        val status = if (success) "success" else "failure"
        val counterKey = "$operation:$status"

        val counter = operationCounters.computeIfAbsent(counterKey) {
            Counter.builder(METRIC_ROUTE_OPERATIONS)
                .description("Количество операций с маршрутами")
                .tag("operation", operation)
                .tag("status", status)
                .register(meterRegistry)
        }

        counter.increment()
    }

    /**
     * Обновляет gauge количества активных маршрутов для указанного статуса.
     *
     * @param status статус маршрута: published, pending, draft, rejected
     * @param count текущее количество маршрутов в этом статусе
     */
    fun updateActiveRoutesGauge(status: String, count: Long) {
        val atomicCount = activeRoutesCounters.computeIfAbsent(status) {
            val newCounter = AtomicLong(0)
            // Регистрируем gauge при первом обращении к статусу
            meterRegistry.gauge(
                METRIC_ROUTES_ACTIVE,
                listOf(io.micrometer.core.instrument.Tag.of("status", status)),
                newCounter
            ) { it.get().toDouble() }
            newCounter
        }
        atomicCount.set(count)
    }
}
