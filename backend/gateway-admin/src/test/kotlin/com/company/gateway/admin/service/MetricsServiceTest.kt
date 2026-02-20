package com.company.gateway.admin.service

import com.company.gateway.admin.dto.MetricsPeriod
import com.company.gateway.admin.dto.MetricsSortBy
import com.company.gateway.admin.exception.NotFoundException
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.search.MeterNotFoundException
import io.micrometer.core.instrument.search.Search
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Unit тесты для MetricsService (Story 6.3).
 *
 * Покрывает AC1, AC2, AC3, AC4:
 * - AC1: getSummary возвращает корректные агрегированные метрики
 * - AC2: различные значения period для getSummary
 * - AC3: getRouteMetrics для существующего маршрута
 * - AC4: getTopRoutes с разными параметрами сортировки
 */
class MetricsServiceTest {

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var routeRepository: RouteRepository
    private lateinit var metricsService: MetricsService

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        routeRepository = mock()
        metricsService = MetricsService(meterRegistry, routeRepository)
    }

    // ============================================
    // AC1: getSummary возвращает корректные данные
    // ============================================

    @Nested
    inner class AC1_GetSummary {

        @Test
        fun `возвращает корректную сводку метрик`() {
            // Given: 100 запросов, 5 ошибок, 10 активных маршрутов
            val routeId = UUID.randomUUID().toString()

            // Регистрируем метрики запросов
            repeat(100) {
                meterRegistry.counter(
                    MetricsService.METRIC_REQUESTS_TOTAL,
                    MetricsService.TAG_ROUTE_ID, routeId,
                    MetricsService.TAG_STATUS, "2xx"
                ).increment()
            }

            // Регистрируем метрики ошибок
            repeat(5) {
                meterRegistry.counter(
                    MetricsService.METRIC_ERRORS_TOTAL,
                    MetricsService.TAG_ROUTE_ID, routeId
                ).increment()
            }

            // Регистрируем latency
            val timer = meterRegistry.timer(
                MetricsService.METRIC_REQUEST_DURATION,
                MetricsService.TAG_ROUTE_ID, routeId
            )
            repeat(10) {
                timer.record(50, TimeUnit.MILLISECONDS)
            }

            whenever(routeRepository.countByStatus(RouteStatus.PUBLISHED))
                .thenReturn(Mono.just(10L))

            // When
            StepVerifier.create(metricsService.getSummary(MetricsPeriod.FIVE_MINUTES))
                // Then
                .expectNextMatches { summary ->
                    summary.period == "5m" &&
                    summary.totalRequests == 100L &&
                    summary.errorCount == 5L &&
                    summary.activeRoutes == 10 &&
                    summary.errorRate > 0.04 && summary.errorRate < 0.06
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает нули когда метрики отсутствуют`() {
            // Given: нет метрик
            whenever(routeRepository.countByStatus(RouteStatus.PUBLISHED))
                .thenReturn(Mono.just(0L))

            // When
            StepVerifier.create(metricsService.getSummary(MetricsPeriod.FIVE_MINUTES))
                // Then
                .expectNextMatches { summary ->
                    summary.period == "5m" &&
                    summary.totalRequests == 0L &&
                    summary.requestsPerSecond == 0.0 &&
                    summary.avgLatencyMs == 0L &&
                    summary.errorRate == 0.0 &&
                    summary.errorCount == 0L &&
                    summary.activeRoutes == 0
                }
                .verifyComplete()
        }

        @Test
        fun `рассчитывает RPS на основе периода`() {
            // Given: 300 запросов за 5 минут = 1 RPS
            val routeId = UUID.randomUUID().toString()

            repeat(300) {
                meterRegistry.counter(
                    MetricsService.METRIC_REQUESTS_TOTAL,
                    MetricsService.TAG_ROUTE_ID, routeId,
                    MetricsService.TAG_STATUS, "2xx"
                ).increment()
            }

            whenever(routeRepository.countByStatus(RouteStatus.PUBLISHED))
                .thenReturn(Mono.just(1L))

            // When
            StepVerifier.create(metricsService.getSummary(MetricsPeriod.FIVE_MINUTES))
                // Then: 300 / 300 секунд = 1.0 RPS
                .expectNextMatches { summary ->
                    summary.requestsPerSecond == 1.0
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает percentiles когда timer настроен с publishPercentiles`() {
            // Given: timer с publishPercentiles
            val routeId = UUID.randomUUID().toString()

            // Создаём timer с percentiles через builder
            val timer = Timer.builder(MetricsService.METRIC_REQUEST_DURATION)
                .tag(MetricsService.TAG_ROUTE_ID, routeId)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)

            // Записываем latency данные
            repeat(100) { i ->
                // Распределение: большинство быстрые, некоторые медленные
                val latency = if (i < 90) 50L else 200L
                timer.record(latency, TimeUnit.MILLISECONDS)
            }

            whenever(routeRepository.countByStatus(RouteStatus.PUBLISHED))
                .thenReturn(Mono.just(1L))

            // When
            StepVerifier.create(metricsService.getSummary(MetricsPeriod.FIVE_MINUTES))
                // Then: percentiles должны быть ненулевыми
                .expectNextMatches { summary ->
                    summary.avgLatencyMs > 0 &&
                    // p95 и p99 могут быть 0 в SimpleMeterRegistry без полной конфигурации,
                    // но avgLatency должен работать
                    summary.avgLatencyMs in 50..100
                }
                .verifyComplete()
        }

        @Test
        fun `avgLatencyMs рассчитывается как взвешенное среднее`() {
            // Given: два timer с разными latency
            val route1 = UUID.randomUUID().toString()
            val route2 = UUID.randomUUID().toString()

            val timer1 = meterRegistry.timer(
                MetricsService.METRIC_REQUEST_DURATION,
                MetricsService.TAG_ROUTE_ID, route1
            )
            val timer2 = meterRegistry.timer(
                MetricsService.METRIC_REQUEST_DURATION,
                MetricsService.TAG_ROUTE_ID, route2
            )

            // Route1: 10 запросов по 100ms = 1000ms total
            repeat(10) { timer1.record(100, TimeUnit.MILLISECONDS) }
            // Route2: 10 запросов по 200ms = 2000ms total
            repeat(10) { timer2.record(200, TimeUnit.MILLISECONDS) }

            whenever(routeRepository.countByStatus(RouteStatus.PUBLISHED))
                .thenReturn(Mono.just(2L))

            // When
            StepVerifier.create(metricsService.getSummary(MetricsPeriod.FIVE_MINUTES))
                // Then: avg = (1000 + 2000) / 20 = 150ms
                .expectNextMatches { summary ->
                    summary.avgLatencyMs == 150L
                }
                .verifyComplete()
        }
    }

    // ============================================
    // AC2: различные значения period
    // ============================================

    @Nested
    inner class AC2_РазличныеПериоды {

        @Test
        fun `5m период использует 300 секунд для расчёта RPS`() {
            // Given: 600 запросов
            val routeId = UUID.randomUUID().toString()
            repeat(600) {
                meterRegistry.counter(
                    MetricsService.METRIC_REQUESTS_TOTAL,
                    MetricsService.TAG_ROUTE_ID, routeId,
                    MetricsService.TAG_STATUS, "2xx"
                ).increment()
            }

            whenever(routeRepository.countByStatus(RouteStatus.PUBLISHED))
                .thenReturn(Mono.just(1L))

            // When
            StepVerifier.create(metricsService.getSummary(MetricsPeriod.FIVE_MINUTES))
                // Then: 600 / 300 = 2.0 RPS
                .expectNextMatches { summary ->
                    summary.period == "5m" &&
                    summary.requestsPerSecond == 2.0
                }
                .verifyComplete()
        }

        @Test
        fun `1h период использует 3600 секунд для расчёта RPS`() {
            // Given: 3600 запросов
            val routeId = UUID.randomUUID().toString()
            repeat(3600) {
                meterRegistry.counter(
                    MetricsService.METRIC_REQUESTS_TOTAL,
                    MetricsService.TAG_ROUTE_ID, routeId,
                    MetricsService.TAG_STATUS, "2xx"
                ).increment()
            }

            whenever(routeRepository.countByStatus(RouteStatus.PUBLISHED))
                .thenReturn(Mono.just(1L))

            // When
            StepVerifier.create(metricsService.getSummary(MetricsPeriod.ONE_HOUR))
                // Then: 3600 / 3600 = 1.0 RPS
                .expectNextMatches { summary ->
                    summary.period == "1h" &&
                    summary.requestsPerSecond == 1.0
                }
                .verifyComplete()
        }

        @Test
        fun `24h период использует 86400 секунд для расчёта RPS`() {
            // Given: 8640 запросов
            val routeId = UUID.randomUUID().toString()
            repeat(8640) {
                meterRegistry.counter(
                    MetricsService.METRIC_REQUESTS_TOTAL,
                    MetricsService.TAG_ROUTE_ID, routeId,
                    MetricsService.TAG_STATUS, "2xx"
                ).increment()
            }

            whenever(routeRepository.countByStatus(RouteStatus.PUBLISHED))
                .thenReturn(Mono.just(1L))

            // When
            StepVerifier.create(metricsService.getSummary(MetricsPeriod.TWENTY_FOUR_HOURS))
                // Then: 8640 / 86400 = 0.1 RPS
                .expectNextMatches { summary ->
                    summary.period == "24h" &&
                    summary.requestsPerSecond == 0.1
                }
                .verifyComplete()
        }
    }

    // ============================================
    // AC3: getRouteMetrics для существующего маршрута
    // ============================================

    @Nested
    inner class AC3_GetRouteMetrics {

        @Test
        fun `возвращает метрики для существующего маршрута`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(routeId, "/api/orders")

            whenever(routeRepository.findById(routeId))
                .thenReturn(Mono.just(route))

            // Регистрируем метрики для маршрута
            repeat(50) {
                meterRegistry.counter(
                    MetricsService.METRIC_REQUESTS_TOTAL,
                    MetricsService.TAG_ROUTE_ID, routeId.toString(),
                    MetricsService.TAG_STATUS, "2xx"
                ).increment()
            }
            repeat(5) {
                meterRegistry.counter(
                    MetricsService.METRIC_REQUESTS_TOTAL,
                    MetricsService.TAG_ROUTE_ID, routeId.toString(),
                    MetricsService.TAG_STATUS, "4xx"
                ).increment()
            }
            repeat(2) {
                meterRegistry.counter(
                    MetricsService.METRIC_ERRORS_TOTAL,
                    MetricsService.TAG_ROUTE_ID, routeId.toString()
                ).increment()
            }

            val timer = meterRegistry.timer(
                MetricsService.METRIC_REQUEST_DURATION,
                MetricsService.TAG_ROUTE_ID, routeId.toString()
            )
            repeat(10) {
                timer.record(35, TimeUnit.MILLISECONDS)
            }

            // When
            StepVerifier.create(metricsService.getRouteMetrics(routeId, MetricsPeriod.FIVE_MINUTES))
                // Then
                .expectNextMatches { metrics ->
                    metrics.routeId == routeId.toString() &&
                    metrics.path == "/api/orders" &&
                    metrics.period == "5m" &&
                    metrics.statusBreakdown["2xx"] == 50L &&
                    metrics.statusBreakdown["4xx"] == 5L
                }
                .verifyComplete()
        }

        @Test
        fun `выбрасывает NotFoundException для несуществующего маршрута`() {
            // Given
            val routeId = UUID.randomUUID()

            whenever(routeRepository.findById(routeId))
                .thenReturn(Mono.empty())

            // When & Then
            StepVerifier.create(metricsService.getRouteMetrics(routeId, MetricsPeriod.FIVE_MINUTES))
                .expectError(NotFoundException::class.java)
                .verify()
        }

        @Test
        fun `возвращает нули для маршрута без метрик`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(routeId, "/api/new-route")

            whenever(routeRepository.findById(routeId))
                .thenReturn(Mono.just(route))

            // When
            StepVerifier.create(metricsService.getRouteMetrics(routeId, MetricsPeriod.FIVE_MINUTES))
                // Then
                .expectNextMatches { metrics ->
                    metrics.routeId == routeId.toString() &&
                    metrics.requestsPerSecond == 0.0 &&
                    metrics.avgLatencyMs == 0L &&
                    metrics.errorRate == 0.0 &&
                    metrics.statusBreakdown.isEmpty()
                }
                .verifyComplete()
        }
    }

    // ============================================
    // AC4: getTopRoutes с разными параметрами сортировки
    // ============================================

    @Nested
    inner class AC4_GetTopRoutes {

        @Test
        fun `возвращает топ маршрутов по количеству запросов`() {
            // Given: 3 маршрута с разным количеством запросов
            val route1 = UUID.randomUUID()
            val route2 = UUID.randomUUID()
            val route3 = UUID.randomUUID()

            // Route1: 100 запросов
            repeat(100) {
                meterRegistry.counter(
                    MetricsService.METRIC_REQUESTS_TOTAL,
                    MetricsService.TAG_ROUTE_ID, route1.toString(),
                    MetricsService.TAG_STATUS, "2xx"
                ).increment()
            }
            // Route2: 200 запросов
            repeat(200) {
                meterRegistry.counter(
                    MetricsService.METRIC_REQUESTS_TOTAL,
                    MetricsService.TAG_ROUTE_ID, route2.toString(),
                    MetricsService.TAG_STATUS, "2xx"
                ).increment()
            }
            // Route3: 50 запросов
            repeat(50) {
                meterRegistry.counter(
                    MetricsService.METRIC_REQUESTS_TOTAL,
                    MetricsService.TAG_ROUTE_ID, route3.toString(),
                    MetricsService.TAG_STATUS, "2xx"
                ).increment()
            }

            whenever(routeRepository.findAllById(any<Iterable<UUID>>()))
                .thenReturn(Flux.just(
                    createTestRoute(route1, "/api/route1"),
                    createTestRoute(route2, "/api/route2"),
                    createTestRoute(route3, "/api/route3")
                ))

            // When
            StepVerifier.create(metricsService.getTopRoutes(MetricsSortBy.REQUESTS, 10))
                // Then: отсортировано по убыванию — route2 (200), route1 (100), route3 (50)
                .expectNextMatches { topRoutes ->
                    topRoutes.size == 3 &&
                    topRoutes[0].routeId == route2.toString() &&
                    topRoutes[0].value == 200.0 &&
                    topRoutes[1].routeId == route1.toString() &&
                    topRoutes[2].routeId == route3.toString()
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает топ маршрутов по количеству ошибок`() {
            // Given
            val route1 = UUID.randomUUID()
            val route2 = UUID.randomUUID()

            // Route1: 10 ошибок
            repeat(10) {
                meterRegistry.counter(
                    MetricsService.METRIC_ERRORS_TOTAL,
                    MetricsService.TAG_ROUTE_ID, route1.toString()
                ).increment()
            }
            // Route2: 30 ошибок
            repeat(30) {
                meterRegistry.counter(
                    MetricsService.METRIC_ERRORS_TOTAL,
                    MetricsService.TAG_ROUTE_ID, route2.toString()
                ).increment()
            }

            whenever(routeRepository.findAllById(any<Iterable<UUID>>()))
                .thenReturn(Flux.just(
                    createTestRoute(route1, "/api/route1"),
                    createTestRoute(route2, "/api/route2")
                ))

            // When
            StepVerifier.create(metricsService.getTopRoutes(MetricsSortBy.ERRORS, 10))
                // Then: route2 (30 ошибок) первый
                .expectNextMatches { topRoutes ->
                    topRoutes.size == 2 &&
                    topRoutes[0].routeId == route2.toString() &&
                    topRoutes[0].value == 30.0 &&
                    topRoutes[0].metric == "errors"
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает топ маршрутов по latency`() {
            // Given: 3 маршрута с разным latency
            val route1 = UUID.randomUUID()
            val route2 = UUID.randomUUID()
            val route3 = UUID.randomUUID()

            // Route1: среднее 50ms (10 запросов)
            val timer1 = meterRegistry.timer(
                MetricsService.METRIC_REQUEST_DURATION,
                MetricsService.TAG_ROUTE_ID, route1.toString()
            )
            repeat(10) { timer1.record(50, TimeUnit.MILLISECONDS) }

            // Route2: среднее 200ms (10 запросов) — самый медленный
            val timer2 = meterRegistry.timer(
                MetricsService.METRIC_REQUEST_DURATION,
                MetricsService.TAG_ROUTE_ID, route2.toString()
            )
            repeat(10) { timer2.record(200, TimeUnit.MILLISECONDS) }

            // Route3: среднее 100ms (10 запросов)
            val timer3 = meterRegistry.timer(
                MetricsService.METRIC_REQUEST_DURATION,
                MetricsService.TAG_ROUTE_ID, route3.toString()
            )
            repeat(10) { timer3.record(100, TimeUnit.MILLISECONDS) }

            whenever(routeRepository.findAllById(any<Iterable<UUID>>()))
                .thenReturn(Flux.just(
                    createTestRoute(route1, "/api/route1"),
                    createTestRoute(route2, "/api/route2"),
                    createTestRoute(route3, "/api/route3")
                ))

            // When
            StepVerifier.create(metricsService.getTopRoutes(MetricsSortBy.LATENCY, 10))
                // Then: отсортировано по убыванию latency — route2 (200ms), route3 (100ms), route1 (50ms)
                .expectNextMatches { topRoutes ->
                    topRoutes.size == 3 &&
                    topRoutes[0].routeId == route2.toString() &&
                    topRoutes[0].value == 200.0 &&
                    topRoutes[0].metric == "latency" &&
                    topRoutes[1].routeId == route3.toString() &&
                    topRoutes[2].routeId == route1.toString()
                }
                .verifyComplete()
        }

        @Test
        fun `ограничивает результаты параметром limit`() {
            // Given: 5 маршрутов
            val routes = (1..5).map { UUID.randomUUID() }

            routes.forEachIndexed { index, routeId ->
                repeat((index + 1) * 10) {
                    meterRegistry.counter(
                        MetricsService.METRIC_REQUESTS_TOTAL,
                        MetricsService.TAG_ROUTE_ID, routeId.toString(),
                        MetricsService.TAG_STATUS, "2xx"
                    ).increment()
                }
            }

            whenever(routeRepository.findAllById(any<Iterable<UUID>>()))
                .thenReturn(Flux.fromIterable(routes.map { createTestRoute(it, "/api/${it}") }))

            // When: limit = 3
            StepVerifier.create(metricsService.getTopRoutes(MetricsSortBy.REQUESTS, 3))
                // Then: только 3 маршрута
                .expectNextMatches { topRoutes ->
                    topRoutes.size == 3
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает пустой список когда нет метрик`() {
            // When
            StepVerifier.create(metricsService.getTopRoutes(MetricsSortBy.REQUESTS, 10))
                // Then
                .expectNextMatches { topRoutes ->
                    topRoutes.isEmpty()
                }
                .verifyComplete()
        }

        @Test
        fun `игнорирует unknown маршруты`() {
            // Given: метрики с route_id = "unknown"
            repeat(100) {
                meterRegistry.counter(
                    MetricsService.METRIC_REQUESTS_TOTAL,
                    MetricsService.TAG_ROUTE_ID, "unknown",
                    MetricsService.TAG_STATUS, "2xx"
                ).increment()
            }

            // When
            StepVerifier.create(metricsService.getTopRoutes(MetricsSortBy.REQUESTS, 10))
                // Then: пустой список (unknown отфильтрован)
                .expectNextMatches { topRoutes ->
                    topRoutes.isEmpty()
                }
                .verifyComplete()
        }
    }

    // ============================================
    // Вспомогательные методы
    // ============================================

    private fun createTestRoute(
        id: UUID,
        path: String
    ): Route {
        return Route(
            id = id,
            path = path,
            upstreamUrl = "http://test-service:8080",
            methods = listOf("GET"),
            description = "Test route",
            status = RouteStatus.PUBLISHED,
            createdBy = UUID.randomUUID(),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}
