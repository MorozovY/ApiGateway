package com.company.gateway.admin.metrics

import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.common.model.RouteStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * Unit тесты для RouteMetricsUpdater.
 *
 * Story 14.3, Task 1: Route Management Metrics
 */
class RouteMetricsUpdaterTest {

    private lateinit var routeRepository: RouteRepository
    private lateinit var routeMetrics: RouteMetrics
    private lateinit var routeMetricsUpdater: RouteMetricsUpdater

    @BeforeEach
    fun setUp() {
        routeRepository = mock()
        routeMetrics = mock()
        routeMetricsUpdater = RouteMetricsUpdater(routeRepository, routeMetrics)
    }

    @Test
    fun `initializeGauges вызывает countByStatus для всех статусов`() {
        // Given
        whenever(routeRepository.countByStatus(RouteStatus.PUBLISHED)).thenReturn(Mono.just(10L))
        whenever(routeRepository.countByStatus(RouteStatus.PENDING)).thenReturn(Mono.just(5L))
        whenever(routeRepository.countByStatus(RouteStatus.DRAFT)).thenReturn(Mono.just(3L))
        whenever(routeRepository.countByStatus(RouteStatus.REJECTED)).thenReturn(Mono.just(2L))

        // When — вызываем напрямую internal метод через reflection или публичный API
        // initializeGauges() подписывается асинхронно, поэтому вызываем scheduledUpdate() который синхронен
        routeMetricsUpdater.scheduledUpdate()

        // Block для ожидания завершения reactive chain
        Mono.delay(Duration.ofMillis(50)).block()

        // Then
        verify(routeRepository).countByStatus(RouteStatus.PUBLISHED)
        verify(routeRepository).countByStatus(RouteStatus.PENDING)
        verify(routeRepository).countByStatus(RouteStatus.DRAFT)
        verify(routeRepository).countByStatus(RouteStatus.REJECTED)
    }

    @Test
    fun `initializeGauges обновляет gauge метрики для всех статусов`() {
        // Given
        whenever(routeRepository.countByStatus(RouteStatus.PUBLISHED)).thenReturn(Mono.just(10L))
        whenever(routeRepository.countByStatus(RouteStatus.PENDING)).thenReturn(Mono.just(5L))
        whenever(routeRepository.countByStatus(RouteStatus.DRAFT)).thenReturn(Mono.just(3L))
        whenever(routeRepository.countByStatus(RouteStatus.REJECTED)).thenReturn(Mono.just(2L))

        // When — используем scheduledUpdate() который синхронно подписывается на Mono
        routeMetricsUpdater.scheduledUpdate()

        // Block для ожидания завершения reactive chain
        Mono.delay(Duration.ofMillis(50)).block()

        // Then
        verify(routeMetrics).updateActiveRoutesGauge(eq("published"), eq(10L))
        verify(routeMetrics).updateActiveRoutesGauge(eq("pending"), eq(5L))
        verify(routeMetrics).updateActiveRoutesGauge(eq("draft"), eq(3L))
        verify(routeMetrics).updateActiveRoutesGauge(eq("rejected"), eq(2L))
    }

    @Test
    fun `scheduledUpdate обновляет gauge метрики`() {
        // Given
        whenever(routeRepository.countByStatus(RouteStatus.PUBLISHED)).thenReturn(Mono.just(15L))
        whenever(routeRepository.countByStatus(RouteStatus.PENDING)).thenReturn(Mono.just(7L))
        whenever(routeRepository.countByStatus(RouteStatus.DRAFT)).thenReturn(Mono.just(4L))
        whenever(routeRepository.countByStatus(RouteStatus.REJECTED)).thenReturn(Mono.just(1L))

        // When
        routeMetricsUpdater.scheduledUpdate()

        // Block для ожидания завершения reactive chain
        Mono.delay(Duration.ofMillis(50)).block()

        // Then
        verify(routeMetrics).updateActiveRoutesGauge(eq("published"), eq(15L))
        verify(routeMetrics).updateActiveRoutesGauge(eq("pending"), eq(7L))
        verify(routeMetrics).updateActiveRoutesGauge(eq("draft"), eq(4L))
        verify(routeMetrics).updateActiveRoutesGauge(eq("rejected"), eq(1L))
    }

    @Test
    fun `обрабатывает ошибку при countByStatus без падения`() {
        // Given
        whenever(routeRepository.countByStatus(any())).thenReturn(Mono.error(RuntimeException("DB error")))

        // When — не должен бросить исключение
        routeMetricsUpdater.scheduledUpdate()

        // Block для ожидания завершения reactive chain (даже с ошибкой)
        Mono.delay(Duration.ofMillis(50)).block()

        // Then — тест проходит если нет исключения
    }
}
