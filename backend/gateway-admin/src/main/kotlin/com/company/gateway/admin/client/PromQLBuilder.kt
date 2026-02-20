package com.company.gateway.admin.client

import com.company.gateway.admin.dto.MetricsPeriod

/**
 * Builder для формирования PromQL запросов.
 *
 * Инкапсулирует знание о названиях метрик gateway-core и формате PromQL.
 * Названия метрик соответствуют MetricsFilter в gateway-core.
 */
object PromQLBuilder {

    // Названия метрик (должны совпадать с MetricsFilter в gateway-core)
    private const val METRIC_REQUESTS_TOTAL = "gateway_requests_total"
    private const val METRIC_REQUEST_DURATION = "gateway_request_duration_seconds"
    private const val METRIC_ERRORS_TOTAL = "gateway_errors_total"

    // Labels
    private const val LABEL_ROUTE_ID = "route_id"
    private const val LABEL_STATUS = "status"

    /**
     * Общее количество запросов за период.
     *
     * PromQL: sum(increase(gateway_requests_total[5m]))
     */
    fun totalRequests(period: MetricsPeriod): String {
        return "sum(increase(${METRIC_REQUESTS_TOTAL}[${period.value}]))"
    }

    /**
     * Requests Per Second (RPS) за период.
     *
     * PromQL: sum(rate(gateway_requests_total[5m]))
     */
    fun requestsPerSecond(period: MetricsPeriod): String {
        return "sum(rate(${METRIC_REQUESTS_TOTAL}[${period.value}]))"
    }

    /**
     * Средняя latency в секундах за период.
     *
     * PromQL: sum(rate(gateway_request_duration_seconds_sum[5m])) / sum(rate(gateway_request_duration_seconds_count[5m]))
     */
    fun avgLatencySeconds(period: MetricsPeriod): String {
        return "sum(rate(${METRIC_REQUEST_DURATION}_sum[${period.value}])) / " +
            "sum(rate(${METRIC_REQUEST_DURATION}_count[${period.value}]))"
    }

    /**
     * Percentile latency в секундах за период.
     *
     * PromQL: histogram_quantile(0.95, sum(rate(gateway_request_duration_seconds_bucket[5m])) by (le))
     *
     * @param percentile значение от 0 до 1 (например, 0.95 для p95)
     */
    fun latencyPercentile(period: MetricsPeriod, percentile: Double): String {
        return "histogram_quantile($percentile, sum(rate(${METRIC_REQUEST_DURATION}_bucket[${period.value}])) by (le))"
    }

    /**
     * Error rate (доля ошибок) за период.
     *
     * PromQL: sum(rate(gateway_errors_total[5m])) / sum(rate(gateway_requests_total[5m]))
     */
    fun errorRate(period: MetricsPeriod): String {
        return "sum(rate(${METRIC_ERRORS_TOTAL}[${period.value}])) / sum(rate(${METRIC_REQUESTS_TOTAL}[${period.value}]))"
    }

    /**
     * Общее количество ошибок за период.
     *
     * PromQL: sum(increase(gateway_errors_total[5m]))
     */
    fun totalErrors(period: MetricsPeriod): String {
        return "sum(increase(${METRIC_ERRORS_TOTAL}[${period.value}]))"
    }

    /**
     * Top маршруты по количеству запросов.
     *
     * PromQL: topk(10, sum(rate(gateway_requests_total[5m])) by (route_id))
     */
    fun topRoutesByRequests(period: MetricsPeriod, limit: Int): String {
        return "topk($limit, sum(rate(${METRIC_REQUESTS_TOTAL}[${period.value}])) by ($LABEL_ROUTE_ID))"
    }

    /**
     * Top маршруты по latency (p95).
     *
     * PromQL: topk(10, histogram_quantile(0.95, sum(rate(gateway_request_duration_seconds_bucket[5m])) by (route_id, le)))
     */
    fun topRoutesByLatency(period: MetricsPeriod, limit: Int): String {
        return "topk($limit, histogram_quantile(0.95, sum(rate(${METRIC_REQUEST_DURATION}_bucket[${period.value}])) by ($LABEL_ROUTE_ID, le)))"
    }

    /**
     * Top маршруты по количеству ошибок.
     *
     * PromQL: topk(10, sum(rate(gateway_errors_total[5m])) by (route_id))
     */
    fun topRoutesByErrors(period: MetricsPeriod, limit: Int): String {
        return "topk($limit, sum(rate(${METRIC_ERRORS_TOTAL}[${period.value}])) by ($LABEL_ROUTE_ID))"
    }

    /**
     * Количество запросов для конкретного маршрута.
     *
     * PromQL: sum(rate(gateway_requests_total{route_id="xxx"}[5m]))
     */
    fun routeRequestsPerSecond(routeId: String, period: MetricsPeriod): String {
        return "sum(rate(${METRIC_REQUESTS_TOTAL}{$LABEL_ROUTE_ID=\"$routeId\"}[${period.value}]))"
    }

    /**
     * Средняя latency для конкретного маршрута.
     *
     * PromQL: sum(rate(gateway_request_duration_seconds_sum{route_id="xxx"}[5m])) /
     *         sum(rate(gateway_request_duration_seconds_count{route_id="xxx"}[5m]))
     */
    fun routeAvgLatencySeconds(routeId: String, period: MetricsPeriod): String {
        return "sum(rate(${METRIC_REQUEST_DURATION}_sum{$LABEL_ROUTE_ID=\"$routeId\"}[${period.value}])) / " +
            "sum(rate(${METRIC_REQUEST_DURATION}_count{$LABEL_ROUTE_ID=\"$routeId\"}[${period.value}]))"
    }

    /**
     * Percentile latency для конкретного маршрута.
     */
    fun routeLatencyPercentile(routeId: String, period: MetricsPeriod, percentile: Double): String {
        return "histogram_quantile($percentile, sum(rate(${METRIC_REQUEST_DURATION}_bucket{$LABEL_ROUTE_ID=\"$routeId\"}[${period.value}])) by (le))"
    }

    /**
     * Error rate для конкретного маршрута.
     */
    fun routeErrorRate(routeId: String, period: MetricsPeriod): String {
        return "sum(rate(${METRIC_ERRORS_TOTAL}{$LABEL_ROUTE_ID=\"$routeId\"}[${period.value}])) / " +
            "sum(rate(${METRIC_REQUESTS_TOTAL}{$LABEL_ROUTE_ID=\"$routeId\"}[${period.value}]))"
    }

    /**
     * Breakdown запросов по статусам для конкретного маршрута.
     *
     * PromQL: sum(increase(gateway_requests_total{route_id="xxx"}[5m])) by (status)
     */
    fun routeStatusBreakdown(routeId: String, period: MetricsPeriod): String {
        return "sum(increase(${METRIC_REQUESTS_TOTAL}{$LABEL_ROUTE_ID=\"$routeId\"}[${period.value}])) by ($LABEL_STATUS)"
    }
}
