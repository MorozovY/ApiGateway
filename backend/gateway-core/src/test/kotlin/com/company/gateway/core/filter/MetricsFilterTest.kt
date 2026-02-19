package com.company.gateway.core.filter

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR
import org.springframework.core.Ordered
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.net.URI

/**
 * Unit тесты для MetricsFilter (Story 6.1)
 *
 * Тесты:
 * - AC1: Сбор gateway_requests_total, gateway_request_duration_seconds, gateway_errors_total
 * - AC3: Histogram записывается с правильными labels
 * - AC4: Error types классифицируются корректно
 */
class MetricsFilterTest {

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var filter: MetricsFilter
    private lateinit var chain: GatewayFilterChain

    @BeforeEach
    fun setup() {
        meterRegistry = SimpleMeterRegistry()
        filter = MetricsFilter(meterRegistry)
        chain = mock()
        whenever(chain.filter(any())).thenReturn(Mono.empty())
    }

    @Nested
    inner class RequestsCounter {

        @Test
        fun `записывает gateway_requests_total counter при успешном запросе`() {
            val exchange = createExchangeWithRoute("test-route", HttpStatus.OK)

            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            val counter = meterRegistry.find(MetricsFilter.METRIC_REQUESTS_TOTAL)
                .tag(MetricsFilter.TAG_ROUTE_ID, "test-route")
                .tag(MetricsFilter.TAG_METHOD, "GET")
                .tag(MetricsFilter.TAG_STATUS, "2xx")
                .counter()

            assert(counter != null) { "Counter gateway_requests_total должен быть записан" }
            assert(counter!!.count() == 1.0) { "Counter должен быть равен 1, получено: ${counter.count()}" }
        }

        @Test
        fun `записывает разные status категории`() {
            val statuses = listOf(
                HttpStatus.OK to "2xx",
                HttpStatus.MOVED_PERMANENTLY to "3xx",
                HttpStatus.NOT_FOUND to "4xx",
                HttpStatus.INTERNAL_SERVER_ERROR to "5xx"
            )

            statuses.forEach { (status, expectedCategory) ->
                val localRegistry = SimpleMeterRegistry()
                val localFilter = MetricsFilter(localRegistry)
                val exchange = createExchangeWithRoute("route-$expectedCategory", status)

                StepVerifier.create(localFilter.filter(exchange, chain))
                    .verifyComplete()

                val counter = localRegistry.find(MetricsFilter.METRIC_REQUESTS_TOTAL)
                    .tag(MetricsFilter.TAG_STATUS, expectedCategory)
                    .counter()

                assert(counter != null) { "Counter для status $expectedCategory должен существовать" }
                assert(counter!!.count() == 1.0) {
                    "Counter для status $expectedCategory должен быть 1, получено: ${counter.count()}"
                }
            }
        }

        @Test
        fun `использует unknown для route_id когда Route не задан`() {
            val request = MockServerHttpRequest.get("/api/test").build()
            val exchange = MockServerWebExchange.from(request)
            exchange.response.statusCode = HttpStatus.OK

            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            val counter = meterRegistry.find(MetricsFilter.METRIC_REQUESTS_TOTAL)
                .tag(MetricsFilter.TAG_ROUTE_ID, "unknown")
                .counter()

            assert(counter != null) { "Counter с route_id=unknown должен существовать" }
        }
    }

    @Nested
    inner class DurationHistogram {

        @Test
        fun `записывает gateway_request_duration_seconds timer`() {
            val exchange = createExchangeWithRoute("test-route", HttpStatus.OK)

            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            val timer = meterRegistry.find(MetricsFilter.METRIC_REQUEST_DURATION)
                .tag(MetricsFilter.TAG_ROUTE_ID, "test-route")
                .tag(MetricsFilter.TAG_METHOD, "GET")
                .timer()

            assert(timer != null) { "Timer gateway_request_duration_seconds должен быть записан" }
            assert(timer!!.count() == 1L) { "Timer должен записать 1 событие, получено: ${timer.count()}" }
            assert(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS) > 0) {
                "Timer должен записать время > 0"
            }
        }

        @Test
        fun `записывает timer для разных HTTP методов`() {
            val methods = listOf("GET", "POST", "PUT", "DELETE")

            methods.forEach { method ->
                val localRegistry = SimpleMeterRegistry()
                val localFilter = MetricsFilter(localRegistry)
                val request = MockServerHttpRequest.method(
                    org.springframework.http.HttpMethod.valueOf(method),
                    "/api/test"
                ).build()
                val exchange = MockServerWebExchange.from(request)
                exchange.attributes[GATEWAY_ROUTE_ATTR] = createRoute("route-$method")
                exchange.response.statusCode = HttpStatus.OK

                StepVerifier.create(localFilter.filter(exchange, chain))
                    .verifyComplete()

                val timer = localRegistry.find(MetricsFilter.METRIC_REQUEST_DURATION)
                    .tag(MetricsFilter.TAG_METHOD, method)
                    .timer()

                assert(timer != null) { "Timer для метода $method должен существовать" }
            }
        }
    }

    @Nested
    inner class ErrorsCounter {

        @Test
        fun `записывает gateway_errors_total для 4xx и 5xx ответов`() {
            val errorStatuses = listOf(
                HttpStatus.NOT_FOUND,
                HttpStatus.INTERNAL_SERVER_ERROR,
                HttpStatus.BAD_GATEWAY,
                HttpStatus.TOO_MANY_REQUESTS
            )

            errorStatuses.forEach { status ->
                val localRegistry = SimpleMeterRegistry()
                val localFilter = MetricsFilter(localRegistry)
                val exchange = createExchangeWithRoute("error-route", status)

                StepVerifier.create(localFilter.filter(exchange, chain))
                    .verifyComplete()

                val counter = localRegistry.find(MetricsFilter.METRIC_ERRORS_TOTAL)
                    .tag(MetricsFilter.TAG_ROUTE_ID, "error-route")
                    .counter()

                assert(counter != null) {
                    "Counter gateway_errors_total должен существовать для status ${status.value()}"
                }
                assert(counter!!.count() == 1.0) {
                    "Counter должен быть 1 для status ${status.value()}, получено: ${counter.count()}"
                }
            }
        }

        @Test
        fun `не записывает gateway_errors_total для успешных ответов`() {
            val exchange = createExchangeWithRoute("success-route", HttpStatus.OK)

            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            val counter = meterRegistry.find(MetricsFilter.METRIC_ERRORS_TOTAL)
                .counter()

            assert(counter == null) { "Counter gateway_errors_total не должен существовать для успешных ответов" }
        }
    }

    @Nested
    inner class ErrorClassification {

        @Test
        fun `classifyError возвращает rate_limited для 429`() {
            assert(filter.classifyError(429) == "rate_limited") {
                "429 должен классифицироваться как rate_limited"
            }
        }

        @Test
        fun `classifyError возвращает not_found для 404`() {
            assert(filter.classifyError(404) == "not_found") {
                "404 должен классифицироваться как not_found"
            }
        }

        @Test
        fun `classifyError возвращает upstream_error для 502`() {
            assert(filter.classifyError(502) == "upstream_error") {
                "502 должен классифицироваться как upstream_error"
            }
        }

        @Test
        fun `classifyError возвращает upstream_error для 504`() {
            assert(filter.classifyError(504) == "upstream_error") {
                "504 должен классифицироваться как upstream_error"
            }
        }

        @Test
        fun `classifyError возвращает auth_error для 401 и 403`() {
            val authErrors = listOf(401, 403)

            authErrors.forEach { statusCode ->
                val result = filter.classifyError(statusCode)
                assert(result == "auth_error") {
                    "$statusCode должен классифицироваться как auth_error, получено: $result"
                }
            }
        }

        @Test
        fun `classifyError возвращает client_error для других 4xx`() {
            val clientErrors = listOf(400, 405, 406, 409, 422)

            clientErrors.forEach { statusCode ->
                val result = filter.classifyError(statusCode)
                assert(result == "client_error") {
                    "$statusCode должен классифицироваться как client_error, получено: $result"
                }
            }
        }

        @Test
        fun `classifyError возвращает internal_error для 5xx`() {
            val serverErrors = listOf(500, 501, 503)

            serverErrors.forEach { statusCode ->
                val result = filter.classifyError(statusCode)
                assert(result == "internal_error") {
                    "$statusCode должен классифицироваться как internal_error, получено: $result"
                }
            }
        }

        @Test
        fun `записывает правильный error_type label для каждого типа ошибки`() {
            val errorTypeMappings = listOf(
                HttpStatus.TOO_MANY_REQUESTS to "rate_limited",
                HttpStatus.NOT_FOUND to "not_found",
                HttpStatus.BAD_GATEWAY to "upstream_error",
                HttpStatus.GATEWAY_TIMEOUT to "upstream_error",
                HttpStatus.UNAUTHORIZED to "auth_error",
                HttpStatus.FORBIDDEN to "auth_error",
                HttpStatus.BAD_REQUEST to "client_error",
                HttpStatus.INTERNAL_SERVER_ERROR to "internal_error"
            )

            errorTypeMappings.forEach { (status, expectedErrorType) ->
                val localRegistry = SimpleMeterRegistry()
                val localFilter = MetricsFilter(localRegistry)
                val exchange = createExchangeWithRoute("error-route", status)

                StepVerifier.create(localFilter.filter(exchange, chain))
                    .verifyComplete()

                val counter = localRegistry.find(MetricsFilter.METRIC_ERRORS_TOTAL)
                    .tag(MetricsFilter.TAG_ERROR_TYPE, expectedErrorType)
                    .counter()

                assert(counter != null) {
                    "Counter с error_type=$expectedErrorType должен существовать для status ${status.value()}"
                }
            }
        }
    }

    @Nested
    inner class FilterOrdering {

        @Test
        fun `имеет order HIGHEST_PRECEDENCE + 10`() {
            assert(filter.order == Ordered.HIGHEST_PRECEDENCE + 10) {
                "MetricsFilter должен иметь order HIGHEST_PRECEDENCE + 10, получено: ${filter.order}"
            }
        }

        @Test
        fun `выполняется после CorrelationIdFilter`() {
            val correlationIdFilterOrder = Ordered.HIGHEST_PRECEDENCE
            assert(filter.order > correlationIdFilterOrder) {
                "MetricsFilter order (${filter.order}) должен быть больше CorrelationIdFilter order ($correlationIdFilterOrder)"
            }
        }
    }

    @Nested
    inner class ActiveConnectionsGauge {

        @Test
        fun `регистрирует gateway_active_connections gauge при инициализации`() {
            val gauge = meterRegistry.find(MetricsFilter.METRIC_ACTIVE_CONNECTIONS).gauge()

            assert(gauge != null) { "Gauge gateway_active_connections должен быть зарегистрирован" }
        }

        @Test
        fun `gauge показывает 0 когда нет активных соединений`() {
            val gauge = meterRegistry.find(MetricsFilter.METRIC_ACTIVE_CONNECTIONS).gauge()

            assert(gauge != null) { "Gauge должен существовать" }
            assert(gauge!!.value() == 0.0) {
                "Gauge должен показывать 0 при отсутствии активных соединений, получено: ${gauge.value()}"
            }
        }

        @Test
        fun `gauge увеличивается во время обработки запроса`() {
            val blockingChain = mock<GatewayFilterChain>()
            var gaugeValueDuringRequest = 0.0

            // Создаём chain, который проверяет gauge во время обработки
            whenever(blockingChain.filter(any())).thenAnswer {
                gaugeValueDuringRequest = meterRegistry.find(MetricsFilter.METRIC_ACTIVE_CONNECTIONS)
                    .gauge()?.value() ?: 0.0
                Mono.empty<Void>()
            }

            val exchange = createExchangeWithRoute("test-route", HttpStatus.OK)

            StepVerifier.create(filter.filter(exchange, blockingChain))
                .verifyComplete()

            assert(gaugeValueDuringRequest == 1.0) {
                "Gauge должен показывать 1 во время обработки запроса, получено: $gaugeValueDuringRequest"
            }

            // После завершения gauge должен вернуться к 0
            val gaugeAfter = meterRegistry.find(MetricsFilter.METRIC_ACTIVE_CONNECTIONS).gauge()
            assert(gaugeAfter!!.value() == 0.0) {
                "Gauge должен вернуться к 0 после завершения запроса, получено: ${gaugeAfter.value()}"
            }
        }
    }

    // Helper методы

    private fun createExchangeWithRoute(routeId: String, status: HttpStatus): MockServerWebExchange {
        val request = MockServerHttpRequest.get("/api/test").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.attributes[GATEWAY_ROUTE_ATTR] = createRoute(routeId)
        exchange.response.statusCode = status
        return exchange
    }

    private fun createRoute(routeId: String): Route {
        return Route.async()
            .id(routeId)
            .uri(URI.create("http://localhost:8080"))
            .predicate { true }
            .build()
    }
}
