package com.company.gateway.admin.metrics

import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.common.model.RouteStatus
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * Компонент для периодического обновления gauge метрик маршрутов.
 *
 * Обновляет gateway_routes_active_total gauge при:
 * - Старте приложения
 * - Каждые 30 секунд (для актуальности метрик)
 *
 * Story 14.3, AC1: Route Management Metrics
 */
@Component
class RouteMetricsUpdater(
    private val routeRepository: RouteRepository,
    private val routeMetrics: RouteMetrics
) {
    private val logger = LoggerFactory.getLogger(RouteMetricsUpdater::class.java)

    /**
     * Инициализирует gauge метрики при старте приложения.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun initializeGauges() {
        logger.info("Инициализация gauge метрик маршрутов")
        updateAllGauges()
            .doOnSuccess { logger.info("Gauge метрики маршрутов инициализированы") }
            .doOnError { e -> logger.error("Ошибка инициализации gauge метрик: {}", e.message) }
            .subscribe()
    }

    /**
     * Периодически обновляет gauge метрики (каждые 30 секунд).
     */
    @Scheduled(fixedRate = 30000, initialDelay = 30000)
    fun scheduledUpdate() {
        updateAllGauges()
            .doOnError { e -> logger.warn("Ошибка обновления gauge метрик: {}", e.message) }
            .subscribe()
    }

    /**
     * Обновляет gauge для всех статусов маршрутов.
     */
    private fun updateAllGauges(): Mono<Void> {
        return Mono.zip(
            routeRepository.countByStatus(RouteStatus.PUBLISHED),
            routeRepository.countByStatus(RouteStatus.PENDING),
            routeRepository.countByStatus(RouteStatus.DRAFT),
            routeRepository.countByStatus(RouteStatus.REJECTED)
        ).doOnNext { counts ->
            routeMetrics.updateActiveRoutesGauge("published", counts.t1)
            routeMetrics.updateActiveRoutesGauge("pending", counts.t2)
            routeMetrics.updateActiveRoutesGauge("draft", counts.t3)
            routeMetrics.updateActiveRoutesGauge("rejected", counts.t4)
        }.then()
    }
}
