package com.company.gateway.admin.client

import com.company.gateway.admin.client.dto.PrometheusQueryResponse
import com.company.gateway.admin.exception.PrometheusUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.net.ConnectException
import java.time.Duration
import java.util.concurrent.TimeoutException

/**
 * Реализация PrometheusClient с WebClient.
 *
 * Поддерживает:
 * - Настраиваемый URL Prometheus
 * - Timeout для запросов
 * - Retry с exponential backoff при transient errors
 * - Graceful degradation при недоступности Prometheus
 */
@Component
class PrometheusClientImpl(
    webClientBuilder: WebClient.Builder,

    @Value("\${prometheus.url:http://localhost:9090}")
    private val prometheusUrl: String,

    @Value("\${prometheus.timeout:5s}")
    private val timeout: Duration,

    @Value("\${prometheus.retry.max-attempts:3}")
    private val maxRetryAttempts: Int,

    @Value("\${prometheus.retry.delay:1s}")
    private val retryDelay: Duration
) : PrometheusClient {

    private val logger = LoggerFactory.getLogger(PrometheusClientImpl::class.java)

    private val webClient: WebClient = webClientBuilder
        .baseUrl(prometheusUrl)
        .build()

    override fun query(query: String): Mono<PrometheusQueryResponse> {
        logger.debug("Выполняем PromQL запрос: {}", query)

        return webClient.get()
            .uri { builder ->
                builder
                    .path("/api/v1/query")
                    .queryParam("query", query)
                    .build()
            }
            .retrieve()
            .bodyToMono(PrometheusQueryResponse::class.java)
            .timeout(timeout)
            .retryWhen(createRetrySpec())
            .doOnNext { response ->
                if (!response.isSuccess()) {
                    logger.warn("Prometheus вернул ошибку: {} - {}", response.errorType, response.error)
                }
            }
            .onErrorMap(TimeoutException::class.java) { ex ->
                logger.error("Timeout при запросе к Prometheus: {}", ex.message)
                PrometheusUnavailableException("Prometheus query timeout after $timeout", ex)
            }
            .onErrorMap(WebClientRequestException::class.java) { ex ->
                logger.error("Ошибка соединения с Prometheus: {}", ex.message)
                PrometheusUnavailableException("Cannot connect to Prometheus at $prometheusUrl", ex)
            }
            .onErrorMap(ConnectException::class.java) { ex ->
                logger.error("Prometheus недоступен: {}", ex.message)
                PrometheusUnavailableException("Prometheus is unavailable at $prometheusUrl", ex)
            }
            .onErrorMap({ it !is PrometheusUnavailableException }) { ex ->
                logger.error("Неожиданная ошибка при запросе к Prometheus: {}", ex.message)
                PrometheusUnavailableException("Unexpected error querying Prometheus: ${ex.message}", ex)
            }
            .doOnSuccess { response ->
                logger.debug("Prometheus ответ: status={}, resultType={}, results={}",
                    response.status,
                    response.data?.resultType,
                    response.data?.result?.size ?: 0
                )
            }
    }

    override fun queryMultiple(queries: Map<String, String>): Mono<Map<String, PrometheusQueryResponse>> {
        if (queries.isEmpty()) {
            return Mono.just(emptyMap())
        }

        logger.debug("Выполняем {} параллельных PromQL запросов", queries.size)

        // Выполняем все запросы параллельно и собираем результаты
        val queryMonos = queries.map { (name, promql) ->
            query(promql).map { response -> name to response }
        }

        // Используем Flux.merge для параллельного выполнения и collectMap для сборки результата
        return reactor.core.publisher.Flux.merge(queryMonos)
            .collectMap({ it.first }, { it.second })
    }

    /**
     * Создаёт Retry spec с exponential backoff для transient errors.
     *
     * Повторяем только при WebClientRequestException (сетевые ошибки),
     * не при application-level ошибках.
     */
    private fun createRetrySpec(): Retry {
        return Retry.backoff(maxRetryAttempts.toLong(), retryDelay)
            .filter { throwable ->
                // Повторяем только при сетевых ошибках
                throwable is WebClientRequestException ||
                    throwable is ConnectException ||
                    throwable is TimeoutException
            }
            .doBeforeRetry { signal ->
                logger.warn("Повторная попытка запроса к Prometheus ({}/{}): {}",
                    signal.totalRetries() + 1,
                    maxRetryAttempts,
                    signal.failure().message
                )
            }
            .onRetryExhaustedThrow { _, signal ->
                PrometheusUnavailableException(
                    "Prometheus unavailable after $maxRetryAttempts retry attempts",
                    signal.failure()
                )
            }
    }
}
