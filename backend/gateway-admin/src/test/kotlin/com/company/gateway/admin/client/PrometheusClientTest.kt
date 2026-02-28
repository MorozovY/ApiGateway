package com.company.gateway.admin.client

import com.company.gateway.admin.client.dto.PrometheusData
import com.company.gateway.admin.client.dto.PrometheusMetric
import com.company.gateway.admin.client.dto.PrometheusQueryResponse
import com.company.gateway.admin.exception.PrometheusUnavailableException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.test.StepVerifier
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Unit тесты для PrometheusClientImpl (Story 7.0 Task 7).
 *
 * Использует MockWebServer для эмуляции Prometheus HTTP API.
 */
class PrometheusClientTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var prometheusClient: PrometheusClient
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        // Используем порт 0 для автоматического выбора свободного порта
        mockWebServer.start(0)

        // Используем явный IP-адрес чтобы избежать проблем с hosts файлом
        // (ymorozov.ru -> 127.0.0.1 в hosts может ломать тесты)
        val baseUrl = "http://127.0.0.1:${mockWebServer.port}"

        // Настраиваем WebClient с Kotlin ObjectMapper для корректной десериализации List<Any>
        val exchangeStrategies = ExchangeStrategies.builder()
            .codecs { configurer ->
                configurer.defaultCodecs().jackson2JsonEncoder(Jackson2JsonEncoder(objectMapper))
                configurer.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder(objectMapper))
            }
            .build()

        prometheusClient = PrometheusClientImpl(
            webClientBuilder = WebClient.builder().exchangeStrategies(exchangeStrategies),
            prometheusUrl = baseUrl,
            timeout = Duration.ofSeconds(5),
            maxRetryAttempts = 2,
            retryDelay = Duration.ofMillis(100)
        )
    }

    @AfterEach
    fun tearDown() {
        try {
            mockWebServer.shutdown()
            // Даём время на освобождение порта
            Thread.sleep(50)
        } catch (_: Exception) {
            // Игнорируем ошибки shutdown
        }
    }

    @Nested
    inner class QueryTests {

        @Test
        fun `query возвращает корректные данные при успешном ответе`() {
            // Given: Prometheus возвращает валидный ответ (raw JSON)
            mockWebServer.enqueue(createMockResponse(createSuccessResponseJson(42.5)))

            // When
            val result = prometheusClient.query("sum(rate(gateway_requests_total[5m]))")

            // Then
            StepVerifier.create(result)
                .expectNextMatches { resp ->
                    resp.isSuccess() &&
                    resp.getScalarValue() == 42.5 &&
                    resp.data?.resultType == "vector"
                }
                .verifyComplete()

            // Проверяем, что запрос был сформирован правильно
            val request = mockWebServer.takeRequest()
            assert(request.path?.contains("query=sum") == true)
        }

        @Test
        fun `query возвращает ошибку при status error`() {
            // Given: Prometheus возвращает ошибку
            val response = PrometheusQueryResponse(
                status = "error",
                errorType = "bad_data",
                error = "invalid query syntax"
            )

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(objectMapper.writeValueAsString(response))
            )

            // When
            val result = prometheusClient.query("invalid[query")

            // Then: ответ возвращается, но isSuccess = false
            StepVerifier.create(result)
                .expectNextMatches { resp ->
                    !resp.isSuccess() &&
                    resp.errorType == "bad_data" &&
                    resp.error == "invalid query syntax"
                }
                .verifyComplete()
        }

        @Test
        fun `query возвращает пустой результат при отсутствии данных`() {
            // Given: Prometheus возвращает пустой результат
            val response = PrometheusQueryResponse(
                status = "success",
                data = PrometheusData(
                    resultType = "vector",
                    result = emptyList()
                )
            )

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(objectMapper.writeValueAsString(response))
            )

            // When
            val result = prometheusClient.query("sum(rate(nonexistent_metric[5m]))")

            // Then
            StepVerifier.create(result)
                .expectNextMatches { resp ->
                    resp.isSuccess() &&
                    resp.data?.result?.isEmpty() == true &&
                    resp.getScalarValue() == null
                }
                .verifyComplete()
        }

        @Test
        fun `query корректно парсит метрики с labels`() {
            // Given: Prometheus возвращает метрики с labels (raw JSON)
            val jsonResponse = """
                {
                    "status": "success",
                    "data": {
                        "resultType": "vector",
                        "result": [
                            {
                                "metric": {"route_id": "uuid-1", "status": "2xx"},
                                "value": [1708444800, "100"]
                            },
                            {
                                "metric": {"route_id": "uuid-2", "status": "4xx"},
                                "value": [1708444800, "5"]
                            }
                        ]
                    }
                }
            """.trimIndent()

            mockWebServer.enqueue(createMockResponse(jsonResponse))

            // When
            val result = prometheusClient.query("sum by (route_id, status) (gateway_requests_total)")

            // Then
            StepVerifier.create(result)
                .expectNextMatches { resp ->
                    resp.isSuccess() &&
                    resp.data?.result?.size == 2 &&
                    resp.data?.result?.get(0)?.getLabel("route_id") == "uuid-1" &&
                    resp.data?.result?.get(0)?.getValue() == 100.0 &&
                    resp.data?.result?.get(1)?.getLabel("status") == "4xx" &&
                    resp.data?.result?.get(1)?.getValue() == 5.0
                }
                .verifyComplete()
        }
    }

    @Nested
    inner class TimeoutTests {

        @Test
        fun `query обрабатывает медленный ответ корректно`() {
            // Given: Prometheus отвечает с задержкой, но успешно (raw JSON)
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBodyDelay(500, TimeUnit.MILLISECONDS)  // 500ms задержка
                    .setBody(createSuccessResponseJson(42.5))
            )

            // Клиент с 5s timeout — должен успеть
            val result = prometheusClient.query("sum(rate(gateway_requests_total[5m]))")

            // Then: успешный результат
            StepVerifier.create(result)
                .expectNextMatches { resp -> resp.isSuccess() && resp.getScalarValue() == 42.5 }
                .verifyComplete()
        }
    }

    @Nested
    inner class RetryTests {

        @Test
        fun `query успешно завершается при первом успешном ответе`() {
            // Given: успешный ответ (raw JSON)
            mockWebServer.enqueue(createMockResponse(createSuccessResponseJson(42.5)))

            // When
            val result = prometheusClient.query("sum(rate(gateway_requests_total[5m]))")

            // Then
            StepVerifier.create(result)
                .expectNextMatches { resp ->
                    resp.isSuccess() && resp.getScalarValue() == 42.5
                }
                .verifyComplete()

            // Проверяем, что был 1 запрос
            assert(mockWebServer.requestCount == 1)
        }

        @Test
        fun `query выбрасывает исключение при ошибке сервера`() {
            // Given: сервер возвращает 500
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setBody("Internal Server Error")
            )

            // When
            val result = prometheusClient.query("sum(rate(gateway_requests_total[5m]))")

            // Then: WebClient бросит исключение при 5xx ошибке
            StepVerifier.create(result)
                .expectError(PrometheusUnavailableException::class.java)
                .verify()
        }
    }

    @Nested
    inner class QueryMultipleTests {

        @Test
        fun `queryMultiple выполняет несколько запросов параллельно`() {
            // Given: готовим ответы для нескольких запросов (raw JSON)
            // Добавляем ответы в очередь
            mockWebServer.enqueue(createMockResponse(createSuccessResponseJson(100.0)))
            mockWebServer.enqueue(createMockResponse(createSuccessResponseJson(42.5)))
            mockWebServer.enqueue(createMockResponse(createSuccessResponseJson(0.05)))

            val queries = mapOf(
                "totalRequests" to "sum(increase(gateway_requests_total[5m]))",
                "rps" to "sum(rate(gateway_requests_total[5m]))",
                "errorRate" to "sum(rate(gateway_errors_total[5m])) / sum(rate(gateway_requests_total[5m]))"
            )

            // When
            val result = prometheusClient.queryMultiple(queries)

            // Then
            StepVerifier.create(result)
                .expectNextMatches { results ->
                    results.size == 3 &&
                    results.containsKey("totalRequests") &&
                    results.containsKey("rps") &&
                    results.containsKey("errorRate")
                }
                .verifyComplete()
        }

        @Test
        fun `queryMultiple возвращает пустую карту при пустом входе`() {
            // When
            val result = prometheusClient.queryMultiple(emptyMap())

            // Then
            StepVerifier.create(result)
                .expectNextMatches { it.isEmpty() }
                .verifyComplete()
        }
    }

    // Вспомогательные методы

    /**
     * Создаёт JSON строку для успешного Prometheus ответа.
     * Используем raw JSON чтобы избежать проблем с десериализацией List<Any>.
     */
    private fun createSuccessResponseJson(value: Double): String {
        return """
            {
                "status": "success",
                "data": {
                    "resultType": "vector",
                    "result": [
                        {
                            "metric": {},
                            "value": [1708444800, "$value"]
                        }
                    ]
                }
            }
        """.trimIndent()
    }

    private fun createMockResponse(jsonBody: String): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .setBody(jsonBody)
    }
}
