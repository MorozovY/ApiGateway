package com.company.gateway.admin.service

import com.company.gateway.admin.client.PrometheusClient
import com.company.gateway.admin.client.dto.PrometheusData
import com.company.gateway.admin.client.dto.PrometheusMetric
import com.company.gateway.admin.client.dto.PrometheusQueryResponse
import com.company.gateway.admin.dto.MetricsPeriod
import com.company.gateway.admin.dto.MetricsSortBy
import com.company.gateway.admin.exception.NotFoundException
import com.company.gateway.admin.exception.PrometheusUnavailableException
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
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

/**
 * Unit тесты для MetricsService (Story 7.0).
 *
 * Покрывает AC1-AC5:
 * - AC1: getSummary возвращает метрики из Prometheus
 * - AC2: getTopRoutes возвращает top маршруты с role-based filtering
 * - AC3: getRouteMetrics возвращает метрики конкретного маршрута
 * - AC4: Graceful degradation при недоступности Prometheus
 * - AC5: API контракт сохранён (те же DTO)
 */
class MetricsServiceTest {

    private lateinit var prometheusClient: PrometheusClient
    private lateinit var routeRepository: RouteRepository
    private lateinit var metricsService: MetricsService

    @BeforeEach
    fun setUp() {
        prometheusClient = mock()
        routeRepository = mock()
        metricsService = MetricsService(prometheusClient, routeRepository)
    }

    // ============================================
    // AC1: getSummary возвращает метрики из Prometheus
    // ============================================

    @Nested
    inner class AC1_GetSummary {

        @Test
        fun `возвращает корректную сводку метрик из Prometheus`() {
            // Given: Prometheus возвращает валидные данные
            val prometheusResponses = mapOf(
                "totalRequests" to createScalarResponse(100.0),
                "rps" to createScalarResponse(1.5),
                "avgLatency" to createScalarResponse(0.050),  // 50ms в секундах
                "p95Latency" to createScalarResponse(0.150),  // 150ms
                "p99Latency" to createScalarResponse(0.250),  // 250ms
                "errorRate" to createScalarResponse(0.05),    // 5%
                "totalErrors" to createScalarResponse(5.0)
            )

            whenever(prometheusClient.queryMultiple(any()))
                .thenReturn(Mono.just(prometheusResponses))

            whenever(routeRepository.countByStatus(RouteStatus.PUBLISHED))
                .thenReturn(Mono.just(10L))

            // When
            StepVerifier.create(metricsService.getSummary(MetricsPeriod.FIVE_MINUTES))
                // Then
                .expectNextMatches { summary ->
                    summary.period == "5m" &&
                    summary.totalRequests == 100L &&
                    summary.requestsPerSecond == 1.5 &&
                    summary.avgLatencyMs == 50L &&
                    summary.p95LatencyMs == 150L &&
                    summary.p99LatencyMs == 250L &&
                    summary.errorRate == 0.05 &&
                    summary.errorCount == 5L &&
                    summary.activeRoutes == 10
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает нули когда Prometheus возвращает пустые данные`() {
            // Given: Prometheus возвращает пустые результаты
            val emptyResponses = mapOf(
                "totalRequests" to createEmptyResponse(),
                "rps" to createEmptyResponse(),
                "avgLatency" to createEmptyResponse(),
                "p95Latency" to createEmptyResponse(),
                "p99Latency" to createEmptyResponse(),
                "errorRate" to createEmptyResponse(),
                "totalErrors" to createEmptyResponse()
            )

            whenever(prometheusClient.queryMultiple(any()))
                .thenReturn(Mono.just(emptyResponses))

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
                    summary.p95LatencyMs == 0L &&
                    summary.p99LatencyMs == 0L &&
                    summary.errorRate == 0.0 &&
                    summary.errorCount == 0L &&
                    summary.activeRoutes == 0
                }
                .verifyComplete()
        }

        @Test
        fun `обрабатывает NaN значения gracefully`() {
            // Given: Prometheus возвращает NaN (например, при делении на 0)
            val nanResponses = mapOf(
                "totalRequests" to createScalarResponse(100.0),
                "rps" to createScalarResponse(Double.NaN),
                "avgLatency" to createScalarResponse(Double.NaN),
                "p95Latency" to createScalarResponse(Double.POSITIVE_INFINITY),
                "p99Latency" to createScalarResponse(0.0),
                "errorRate" to createScalarResponse(Double.NaN),
                "totalErrors" to createScalarResponse(0.0)
            )

            whenever(prometheusClient.queryMultiple(any()))
                .thenReturn(Mono.just(nanResponses))

            whenever(routeRepository.countByStatus(RouteStatus.PUBLISHED))
                .thenReturn(Mono.just(5L))

            // When
            StepVerifier.create(metricsService.getSummary(MetricsPeriod.FIVE_MINUTES))
                // Then: NaN и Infinity заменяются на 0
                .expectNextMatches { summary ->
                    summary.requestsPerSecond == 0.0 &&
                    summary.avgLatencyMs == 0L &&
                    summary.p95LatencyMs == 0L &&
                    summary.errorRate == 0.0
                }
                .verifyComplete()
        }
    }

    // ============================================
    // AC2: getTopRoutes возвращает top маршруты
    // ============================================

    @Nested
    inner class AC2_GetTopRoutes {

        @Test
        fun `возвращает топ маршруты по количеству запросов`() {
            // Given: В БД есть published маршруты
            val route1 = UUID.randomUUID()
            val route2 = UUID.randomUUID()
            val route3 = UUID.randomUUID()

            whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
                .thenReturn(Flux.just(
                    createTestRoute(route1, "/api/route1"),
                    createTestRoute(route2, "/api/route2"),
                    createTestRoute(route3, "/api/route3")
                ))

            // Prometheus возвращает метрики для этих маршрутов
            val response = createTopRoutesResponse(
                route2.toString() to 200.0,
                route1.toString() to 100.0,
                route3.toString() to 50.0
            )

            whenever(prometheusClient.query(any()))
                .thenReturn(Mono.just(response))

            // When
            StepVerifier.create(metricsService.getTopRoutes(MetricsSortBy.REQUESTS, 10))
                // Then: отсортировано по value (убывание)
                .expectNextMatches { topRoutes ->
                    topRoutes.size == 3 &&
                    topRoutes[0].routeId == route2.toString() &&
                    topRoutes[0].value == 200.0 &&
                    topRoutes[0].metric == "requests" &&
                    topRoutes[1].routeId == route1.toString() &&
                    topRoutes[2].routeId == route3.toString()
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает топ маршруты по latency`() {
            // Given
            val route1 = UUID.randomUUID()
            val route2 = UUID.randomUUID()

            whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
                .thenReturn(Flux.just(
                    createTestRoute(route1, "/api/slow"),
                    createTestRoute(route2, "/api/fast")
                ))

            val response = createTopRoutesResponse(
                route1.toString() to 0.200,  // 200ms (самый медленный)
                route2.toString() to 0.050   // 50ms
            )

            whenever(prometheusClient.query(any()))
                .thenReturn(Mono.just(response))

            // When
            StepVerifier.create(metricsService.getTopRoutes(MetricsSortBy.LATENCY, 10))
                // Then
                .expectNextMatches { topRoutes ->
                    topRoutes.size == 2 &&
                    topRoutes[0].routeId == route1.toString() &&
                    topRoutes[0].metric == "latency"
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает пустой список когда нет маршрутов в БД`() {
            // Given: БД пустая
            whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
                .thenReturn(Flux.empty())

            // When
            StepVerifier.create(metricsService.getTopRoutes(MetricsSortBy.REQUESTS, 10))
                // Then
                .expectNextMatches { topRoutes ->
                    topRoutes.isEmpty()
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает маршруты с value 0 когда нет метрик в Prometheus`() {
            // Given: В БД есть маршрут, но в Prometheus нет метрик для него
            val route1 = UUID.randomUUID()

            whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
                .thenReturn(Flux.just(createTestRoute(route1, "/api/new-route")))

            // Prometheus возвращает пустой результат
            whenever(prometheusClient.query(any()))
                .thenReturn(Mono.just(createEmptyResponse()))

            // When
            StepVerifier.create(metricsService.getTopRoutes(MetricsSortBy.REQUESTS, 10))
                // Then: маршрут возвращается с value=0
                .expectNextMatches { topRoutes ->
                    topRoutes.size == 1 &&
                    topRoutes[0].routeId == route1.toString() &&
                    topRoutes[0].value == 0.0
                }
                .verifyComplete()
        }
    }

    // ============================================
    // Story 6.5.1: Role-based filtering for top-routes
    // ============================================

    @Nested
    inner class RoleBasedFiltering {

        @Test
        fun `getTopRoutes с ownerId фильтрует результаты`() {
            // Given: два маршрута от разных владельцев
            val developer1Id = UUID.randomUUID()
            val developer2Id = UUID.randomUUID()

            val route1 = UUID.randomUUID()
            val route2 = UUID.randomUUID()

            // БД возвращает ОБА маршрута (findByStatus не фильтрует по owner)
            whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
                .thenReturn(Flux.just(
                    createTestRouteWithCreator(route1, "/api/route1", developer1Id),
                    createTestRouteWithCreator(route2, "/api/route2", developer2Id)
                ))

            val response = createTopRoutesResponse(
                route1.toString() to 100.0,
                route2.toString() to 50.0
            )

            whenever(prometheusClient.query(any()))
                .thenReturn(Mono.just(response))

            // When: запрос с фильтрацией по developer1
            StepVerifier.create(metricsService.getTopRoutes(MetricsSortBy.REQUESTS, 10, developer1Id))
                // Then: возвращается только route1 (фильтрация по ownerId в getTopRoutes)
                .expectNextMatches { topRoutes ->
                    topRoutes.size == 1 &&
                    topRoutes[0].routeId == route1.toString() &&
                    topRoutes[0].value == 100.0
                }
                .verifyComplete()
        }

        @Test
        fun `getTopRoutes без ownerId возвращает все маршруты`() {
            // Given
            val developer1Id = UUID.randomUUID()
            val developer2Id = UUID.randomUUID()

            val route1 = UUID.randomUUID()
            val route2 = UUID.randomUUID()

            whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
                .thenReturn(Flux.just(
                    createTestRouteWithCreator(route1, "/api/route1", developer1Id),
                    createTestRouteWithCreator(route2, "/api/route2", developer2Id)
                ))

            val response = createTopRoutesResponse(
                route1.toString() to 80.0,
                route2.toString() to 60.0
            )

            whenever(prometheusClient.query(any()))
                .thenReturn(Mono.just(response))

            // When: запрос без фильтрации (ownerId = null)
            StepVerifier.create(metricsService.getTopRoutes(MetricsSortBy.REQUESTS, 10, null))
                // Then: возвращаются все маршруты
                .expectNextMatches { topRoutes ->
                    topRoutes.size == 2
                }
                .verifyComplete()
        }

        @Test
        fun `getTopRoutes с ownerId возвращает пустой список когда нет маршрутов пользователя`() {
            // Given: маршрут принадлежит другому пользователю
            val otherUserId = UUID.randomUUID()
            val currentUserId = UUID.randomUUID()
            val route1 = UUID.randomUUID()

            whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
                .thenReturn(Flux.just(
                    createTestRouteWithCreator(route1, "/api/route1", otherUserId)
                ))

            // Prometheus не будет вызван — filter по ownerId отсеет все маршруты до query

            // When
            StepVerifier.create(metricsService.getTopRoutes(MetricsSortBy.REQUESTS, 10, currentUserId))
                // Then: пустой список
                .expectNextMatches { topRoutes ->
                    topRoutes.isEmpty()
                }
                .verifyComplete()
        }
    }

    // ============================================
    // AC3: getRouteMetrics для конкретного маршрута
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

            val prometheusResponses = mapOf(
                "rps" to createScalarResponse(2.5),
                "avgLatency" to createScalarResponse(0.035),  // 35ms
                "p95Latency" to createScalarResponse(0.100),  // 100ms
                "errorRate" to createScalarResponse(0.02),    // 2%
                "statusBreakdown" to createStatusBreakdownResponse(
                    "2xx" to 50.0,
                    "4xx" to 5.0
                )
            )

            whenever(prometheusClient.queryMultiple(any()))
                .thenReturn(Mono.just(prometheusResponses))

            // When
            StepVerifier.create(metricsService.getRouteMetrics(routeId, MetricsPeriod.FIVE_MINUTES))
                // Then
                .expectNextMatches { metrics ->
                    metrics.routeId == routeId.toString() &&
                    metrics.path == "/api/orders" &&
                    metrics.period == "5m" &&
                    metrics.requestsPerSecond == 2.5 &&
                    metrics.avgLatencyMs == 35L &&
                    metrics.p95LatencyMs == 100L &&
                    metrics.errorRate == 0.02 &&
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

            val emptyResponses = mapOf(
                "rps" to createEmptyResponse(),
                "avgLatency" to createEmptyResponse(),
                "p95Latency" to createEmptyResponse(),
                "errorRate" to createEmptyResponse(),
                "statusBreakdown" to createEmptyResponse()
            )

            whenever(prometheusClient.queryMultiple(any()))
                .thenReturn(Mono.just(emptyResponses))

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
    // AC4: Graceful Degradation при недоступности Prometheus
    // ============================================

    @Nested
    inner class AC4_GracefulDegradation {

        @Test
        fun `getSummary пробрасывает PrometheusUnavailableException`() {
            // Given: Prometheus недоступен
            whenever(prometheusClient.queryMultiple(any()))
                .thenReturn(Mono.error(PrometheusUnavailableException("Prometheus is unavailable")))

            // When & Then
            StepVerifier.create(metricsService.getSummary(MetricsPeriod.FIVE_MINUTES))
                .expectError(PrometheusUnavailableException::class.java)
                .verify()
        }

        @Test
        fun `getTopRoutes пробрасывает PrometheusUnavailableException`() {
            // Given: есть маршруты в БД
            val route1 = UUID.randomUUID()
            whenever(routeRepository.findByStatus(RouteStatus.PUBLISHED))
                .thenReturn(Flux.just(createTestRoute(route1, "/api/test")))

            // Prometheus недоступен
            whenever(prometheusClient.query(any()))
                .thenReturn(Mono.error(PrometheusUnavailableException("Connection timeout")))

            // When & Then
            StepVerifier.create(metricsService.getTopRoutes(MetricsSortBy.REQUESTS, 10))
                .expectError(PrometheusUnavailableException::class.java)
                .verify()
        }

        @Test
        fun `getRouteMetrics пробрасывает PrometheusUnavailableException`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(routeId, "/api/test")

            whenever(routeRepository.findById(routeId))
                .thenReturn(Mono.just(route))

            whenever(prometheusClient.queryMultiple(any()))
                .thenReturn(Mono.error(PrometheusUnavailableException("Cannot connect to Prometheus")))

            // When & Then
            StepVerifier.create(metricsService.getRouteMetrics(routeId, MetricsPeriod.FIVE_MINUTES))
                .expectError(PrometheusUnavailableException::class.java)
                .verify()
        }
    }

    // ============================================
    // AC5: Различные периоды (API контракт сохранён)
    // ============================================

    @Nested
    inner class AC5_DifferentPeriods {

        @Test
        fun `getSummary поддерживает все периоды`() {
            val responses = createMinimalPrometheusResponses()

            whenever(prometheusClient.queryMultiple(any()))
                .thenReturn(Mono.just(responses))

            whenever(routeRepository.countByStatus(RouteStatus.PUBLISHED))
                .thenReturn(Mono.just(1L))

            // Проверяем все периоды
            listOf(
                MetricsPeriod.FIVE_MINUTES to "5m",
                MetricsPeriod.FIFTEEN_MINUTES to "15m",
                MetricsPeriod.ONE_HOUR to "1h",
                MetricsPeriod.SIX_HOURS to "6h",
                MetricsPeriod.TWENTY_FOUR_HOURS to "24h"
            ).forEach { (period, expectedValue) ->
                StepVerifier.create(metricsService.getSummary(period))
                    .expectNextMatches { summary ->
                        summary.period == expectedValue
                    }
                    .verifyComplete()
            }
        }
    }

    // ============================================
    // Вспомогательные методы
    // ============================================

    private fun createScalarResponse(value: Double): PrometheusQueryResponse {
        return PrometheusQueryResponse(
            status = "success",
            data = PrometheusData(
                resultType = "vector",
                result = listOf(
                    PrometheusMetric(
                        metric = emptyMap(),
                        value = listOf(1708444800, value.toString())
                    )
                )
            )
        )
    }

    private fun createEmptyResponse(): PrometheusQueryResponse {
        return PrometheusQueryResponse(
            status = "success",
            data = PrometheusData(
                resultType = "vector",
                result = emptyList()
            )
        )
    }

    private fun createTopRoutesResponse(vararg routes: Pair<String, Double>): PrometheusQueryResponse {
        return PrometheusQueryResponse(
            status = "success",
            data = PrometheusData(
                resultType = "vector",
                result = routes.map { (routeId, value) ->
                    PrometheusMetric(
                        metric = mapOf("route_id" to routeId),
                        value = listOf(1708444800, value.toString())
                    )
                }
            )
        )
    }

    private fun createStatusBreakdownResponse(vararg statuses: Pair<String, Double>): PrometheusQueryResponse {
        return PrometheusQueryResponse(
            status = "success",
            data = PrometheusData(
                resultType = "vector",
                result = statuses.map { (status, value) ->
                    PrometheusMetric(
                        metric = mapOf("status" to status),
                        value = listOf(1708444800, value.toString())
                    )
                }
            )
        )
    }

    private fun createMinimalPrometheusResponses(): Map<String, PrometheusQueryResponse> {
        return mapOf(
            "totalRequests" to createScalarResponse(100.0),
            "rps" to createScalarResponse(1.0),
            "avgLatency" to createScalarResponse(0.050),
            "p95Latency" to createScalarResponse(0.100),
            "p99Latency" to createScalarResponse(0.200),
            "errorRate" to createScalarResponse(0.01),
            "totalErrors" to createScalarResponse(1.0)
        )
    }

    private fun createTestRoute(id: UUID, path: String): Route {
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

    private fun createTestRouteWithCreator(id: UUID, path: String, createdBy: UUID): Route {
        return Route(
            id = id,
            path = path,
            upstreamUrl = "http://test-service:8080",
            methods = listOf("GET"),
            description = "Test route",
            status = RouteStatus.PUBLISHED,
            createdBy = createdBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}
