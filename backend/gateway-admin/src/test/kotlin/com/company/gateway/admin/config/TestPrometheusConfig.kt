package com.company.gateway.admin.config

import com.company.gateway.admin.client.PrometheusClient
import com.company.gateway.admin.client.dto.PrometheusData
import com.company.gateway.admin.client.dto.PrometheusMetric
import com.company.gateway.admin.client.dto.PrometheusQueryResponse
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import reactor.core.publisher.Mono

/**
 * Тестовая конфигурация для PrometheusClient.
 *
 * Используется в интеграционных тестах где Prometheus недоступен.
 * Возвращает mock-данные для всех запросов.
 */
@TestConfiguration
class TestPrometheusConfig {

    @Bean
    @Primary
    fun testPrometheusClient(): PrometheusClient {
        return TestPrometheusClient()
    }
}

/**
 * Mock реализация PrometheusClient для тестов.
 *
 * Возвращает пустые успешные ответы по умолчанию.
 * Тесты могут заменять это поведение через настройку.
 */
class TestPrometheusClient : PrometheusClient {

    // Данные для mock ответов (можно настраивать в тестах)
    var mockResponses: Map<String, PrometheusQueryResponse> = emptyMap()
    var defaultScalarValue: Double = 0.0

    override fun query(query: String): Mono<PrometheusQueryResponse> {
        // Проверяем есть ли настроенный ответ для этого запроса
        val response = mockResponses.entries.find { query.contains(it.key) }?.value
            ?: createDefaultResponse()

        return Mono.just(response)
    }

    override fun queryMultiple(queries: Map<String, String>): Mono<Map<String, PrometheusQueryResponse>> {
        val results = queries.mapValues { (_, promql) ->
            mockResponses.entries.find { promql.contains(it.key) }?.value
                ?: createDefaultResponse()
        }
        return Mono.just(results)
    }

    private fun createDefaultResponse(): PrometheusQueryResponse {
        return PrometheusQueryResponse(
            status = "success",
            data = PrometheusData(
                resultType = "vector",
                result = listOf(
                    PrometheusMetric(
                        metric = emptyMap(),
                        value = listOf(System.currentTimeMillis() / 1000, defaultScalarValue.toString())
                    )
                )
            )
        )
    }

    /**
     * Настраивает mock для возврата данных top routes.
     */
    fun setTopRoutesResponse(vararg routes: Pair<String, Double>) {
        val response = PrometheusQueryResponse(
            status = "success",
            data = PrometheusData(
                resultType = "vector",
                result = routes.map { (routeId, value) ->
                    PrometheusMetric(
                        metric = mapOf("route_id" to routeId),
                        value = listOf(System.currentTimeMillis() / 1000, value.toString())
                    )
                }
            )
        )
        mockResponses = mockResponses + ("topk" to response)
    }

    /**
     * Сбрасывает mock к дефолтному состоянию.
     */
    fun reset() {
        mockResponses = emptyMap()
        defaultScalarValue = 0.0
    }
}
