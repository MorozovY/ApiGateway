package com.company.gateway.admin.client

import com.company.gateway.admin.client.dto.PrometheusQueryResponse
import reactor.core.publisher.Mono

/**
 * Клиент для взаимодействия с Prometheus HTTP API.
 *
 * Используется MetricsService для получения реальных метрик gateway-core
 * вместо чтения из локального MeterRegistry.
 *
 * @see <a href="https://prometheus.io/docs/prometheus/latest/querying/api/">Prometheus HTTP API</a>
 */
interface PrometheusClient {

    /**
     * Выполняет instant query к Prometheus.
     *
     * Эквивалент:
     * ```
     * GET /api/v1/query?query={promql}
     * ```
     *
     * @param query PromQL запрос
     * @return результат запроса с метриками
     * @throws PrometheusUnavailableException если Prometheus недоступен
     */
    fun query(query: String): Mono<PrometheusQueryResponse>

    /**
     * Выполняет несколько instant queries параллельно.
     *
     * Оптимизирует сетевые вызовы через параллельное выполнение.
     *
     * @param queries карта имён запросов к PromQL
     * @return карта имён к результатам
     */
    fun queryMultiple(queries: Map<String, String>): Mono<Map<String, PrometheusQueryResponse>>
}
