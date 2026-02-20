package com.company.gateway.admin.client.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * DTO для ответа Prometheus HTTP API.
 *
 * Prometheus возвращает JSON в формате:
 * {
 *   "status": "success",
 *   "data": {
 *     "resultType": "vector" | "matrix" | "scalar",
 *     "result": [...]
 *   }
 * }
 *
 * @see <a href="https://prometheus.io/docs/prometheus/latest/querying/api/">Prometheus HTTP API</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PrometheusQueryResponse(
    /** Статус запроса: "success" или "error" */
    val status: String,

    /** Данные результата запроса */
    val data: PrometheusData? = null,

    /** Тип ошибки (при status = "error") */
    val errorType: String? = null,

    /** Сообщение об ошибке */
    val error: String? = null
) {
    /** Проверяет, что запрос успешен */
    fun isSuccess(): Boolean = status == "success"

    /** Возвращает первое значение из результата (для instant query) */
    fun getScalarValue(): Double? {
        return data?.result?.firstOrNull()?.getValue()
    }

    /** Возвращает все метрики с их значениями */
    fun getMetricValues(): List<Pair<Map<String, String>, Double>> {
        return data?.result?.map { metric ->
            metric.metric to (metric.getValue() ?: 0.0)
        } ?: emptyList()
    }
}

/**
 * Данные результата Prometheus запроса.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PrometheusData(
    /** Тип результата: "vector", "matrix", "scalar", "string" */
    val resultType: String,

    /** Список метрик с их значениями */
    val result: List<PrometheusMetric> = emptyList()
)

/**
 * Отдельная метрика в результате Prometheus запроса.
 *
 * Для instant query (vector):
 * {
 *   "metric": {"route_id": "xxx", "status": "2xx"},
 *   "value": [1708444800, "42.5"]
 * }
 *
 * Для range query (matrix):
 * {
 *   "metric": {...},
 *   "values": [[1708444800, "42.5"], [1708444860, "43.0"], ...]
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PrometheusMetric(
    /** Labels метрики (route_id, status, etc.) */
    val metric: Map<String, String> = emptyMap(),

    /** Значение для instant query: [timestamp, "value"] */
    val value: List<Any>? = null,

    /** Значения для range query: [[timestamp, "value"], ...] */
    val values: List<List<Any>>? = null
) {
    /**
     * Извлекает числовое значение из instant query.
     *
     * Prometheus возвращает value как [timestamp, "string_value"],
     * поэтому нужно парсить второй элемент как Double.
     */
    fun getValue(): Double? {
        return try {
            value?.getOrNull(1)?.toString()?.toDouble()
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Извлекает последнее значение из range query.
     */
    fun getLastValue(): Double? {
        return try {
            values?.lastOrNull()?.getOrNull(1)?.toString()?.toDouble()
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Извлекает значение конкретного label.
     */
    fun getLabel(name: String): String? = metric[name]
}
