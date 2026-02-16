package com.company.gateway.core.exception

import com.company.gateway.common.exception.ErrorResponse
import com.company.gateway.core.filter.CorrelationIdFilter
import com.fasterxml.jackson.databind.ObjectMapper
import io.netty.channel.ConnectTimeoutException
import io.netty.handler.timeout.ReadTimeoutException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler
import org.springframework.cloud.gateway.support.NotFoundException
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.net.ConnectException
import java.util.concurrent.TimeoutException

/**
 * Глобальный обработчик исключений для шлюза.
 *
 * Обрабатывает ошибки upstream (отказ в соединении, таймаут) и возвращает ответы в формате RFC 7807.
 * Безопасность: НЕ раскрывает внутренние детали (имена хостов, stack traces, имена классов исключений).
 */
@Component
@Order(-1) // Intercept before default Spring error handlers (which have Order(0))
class GlobalExceptionHandler(
    private val objectMapper: ObjectMapper
) : ErrorWebExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        return Mono.deferContextual { context ->
            val response = exchange.response
            val requestPath = exchange.request.path.value()

            // Извлекаем correlation ID из Reactor Context, атрибутов exchange или заголовка запроса
            // Приоритет: Context > Exchange Attribute > Request Header > Генерация нового
            val correlationId: String = (context.getOrDefault<String>(
                CorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY,
                exchange.getAttribute<String>(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE)
                    ?: exchange.request.headers.getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER)
            ) ?: java.util.UUID.randomUUID().toString())

            // Устанавливаем MDC для структурированного логирования ошибки
            MDC.put("correlationId", correlationId)
            try {
                // Логируем ошибку для отладки (внутренние детали допустимы в логах, не в ответе)
                logUpstreamError(requestPath, ex)

                val (httpStatus, errorResponse) = resolveException(ex, requestPath, correlationId)

                response.statusCode = httpStatus
                response.headers.contentType = MediaType.APPLICATION_JSON

                // Убеждаемся, что correlation ID в заголовке ответа (может уже быть установлен фильтром)
                if (!response.headers.containsKey(CorrelationIdFilter.CORRELATION_ID_HEADER)) {
                    response.headers.add(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId)
                }

                val bytes = objectMapper.writeValueAsBytes(errorResponse)
                val buffer = response.bufferFactory().wrap(bytes)
                response.writeWith(Mono.just(buffer))
            } finally {
                MDC.clear()
            }
        }
    }

    /**
     * Преобразует исключение в HTTP статус и RFC 7807 error response.
     * Проверяет root cause для обёрнутых исключений (WebClientRequestException).
     */
    private fun resolveException(ex: Throwable, requestPath: String, correlationId: String): Pair<HttpStatus, ErrorResponse> {
        // Сначала проверяем, является ли это обёрнутым исключением и получаем root cause
        val rootCause = getRootCause(ex)

        // Проверяем root cause на ошибки upstream
        return when {
            // Ошибки соединения -> 502 Bad Gateway
            isConnectionError(rootCause) -> createUpstreamUnavailableResponse(requestPath, correlationId)

            // Ошибки таймаута -> 504 Gateway Timeout
            isTimeoutError(rootCause) -> createUpstreamTimeoutResponse(requestPath, correlationId)

            // Проверяем оригинальное исключение для не-upstream ошибок
            else -> resolveNonUpstreamException(ex, requestPath, correlationId)
        }
    }

    /**
     * Преобразует не-upstream исключения (route not found, response status и т.д.)
     */
    private fun resolveNonUpstreamException(ex: Throwable, requestPath: String, correlationId: String): Pair<HttpStatus, ErrorResponse> {
        return when (ex) {
            is RouteNotFoundException -> Pair(
                HttpStatus.NOT_FOUND,
                ErrorResponse(
                    type = "https://api.gateway/errors/route-not-found",
                    title = "Not Found",
                    status = HttpStatus.NOT_FOUND.value(),
                    detail = "No route found for path: ${ex.path}",
                    instance = requestPath,
                    correlationId = correlationId
                )
            )
            is NotFoundException -> Pair(
                HttpStatus.NOT_FOUND,
                ErrorResponse(
                    type = "https://api.gateway/errors/route-not-found",
                    title = "Not Found",
                    status = HttpStatus.NOT_FOUND.value(),
                    detail = "No route found for path: $requestPath",
                    instance = requestPath,
                    correlationId = correlationId
                )
            )
            is ResponseStatusException -> {
                // Spring 6: statusCode возвращает интерфейс HttpStatusCode, не enum HttpStatus
                val status = HttpStatus.resolve(ex.statusCode.value()) ?: HttpStatus.INTERNAL_SERVER_ERROR

                when (status) {
                    // Обрабатываем generic 404 от WebFlux как route not found
                    HttpStatus.NOT_FOUND -> Pair(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse(
                            type = "https://api.gateway/errors/route-not-found",
                            title = "Not Found",
                            status = HttpStatus.NOT_FOUND.value(),
                            detail = "No route found for path: $requestPath",
                            instance = requestPath,
                            correlationId = correlationId
                        )
                    )
                    // Spring Cloud Gateway оборачивает таймауты в ResponseStatusException с 504
                    HttpStatus.GATEWAY_TIMEOUT -> createUpstreamTimeoutResponse(requestPath, correlationId)
                    // Spring Cloud Gateway может оборачивать ошибки соединения в ResponseStatusException с 502
                    HttpStatus.BAD_GATEWAY -> createUpstreamUnavailableResponse(requestPath, correlationId)
                    else -> Pair(
                        status,
                        ErrorResponse(
                            type = "https://api.gateway/errors/request-error",
                            title = ex.reason ?: status.reasonPhrase,
                            status = status.value(),
                            detail = ex.message ?: "Request error",
                            instance = requestPath,
                            correlationId = correlationId
                        )
                    )
                }
            }
            else -> Pair(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorResponse(
                    type = "https://api.gateway/errors/internal-error",
                    title = "Internal Server Error",
                    status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    detail = "An unexpected error occurred",
                    instance = requestPath,
                    correlationId = correlationId
                )
            )
        }
    }

    /**
     * Проверяет, является ли исключение ошибкой соединения (connection refused, connect timeout).
     */
    private fun isConnectionError(ex: Throwable): Boolean {
        return ex is ConnectException || ex is ConnectTimeoutException
    }

    /**
     * Проверяет, является ли исключение ошибкой таймаута (read timeout, general timeout).
     */
    private fun isTimeoutError(ex: Throwable): Boolean {
        return ex is ReadTimeoutException || ex is TimeoutException
    }

    /**
     * Получает root cause цепочки исключений.
     * WebClientRequestException и другие обёртки могут иметь глубоко вложенные causes.
     * Разворачивает всю цепочку для нахождения фактической сетевой ошибки.
     */
    private fun getRootCause(ex: Throwable): Throwable {
        var current: Throwable = ex
        val seen = mutableSetOf<Throwable>() // Предотвращаем бесконечные циклы от circular references

        while (current.cause != null && current.cause !in seen) {
            seen.add(current)
            current = current.cause!!
        }

        return current
    }

    /**
     * Создаёт ответ 502 Bad Gateway для недоступного upstream.
     * НЕ включает внутренние детали (hostname, port, stack trace).
     */
    private fun createUpstreamUnavailableResponse(requestPath: String, correlationId: String): Pair<HttpStatus, ErrorResponse> {
        return Pair(
            HttpStatus.BAD_GATEWAY,
            ErrorResponse(
                type = "https://api.gateway/errors/upstream-unavailable",
                title = "Bad Gateway",
                status = HttpStatus.BAD_GATEWAY.value(),
                detail = "Upstream service is unavailable",
                instance = requestPath,
                correlationId = correlationId
            )
        )
    }

    /**
     * Создаёт ответ 504 Gateway Timeout для таймаута upstream.
     * НЕ включает внутренние детали.
     */
    private fun createUpstreamTimeoutResponse(requestPath: String, correlationId: String): Pair<HttpStatus, ErrorResponse> {
        return Pair(
            HttpStatus.GATEWAY_TIMEOUT,
            ErrorResponse(
                type = "https://api.gateway/errors/upstream-timeout",
                title = "Gateway Timeout",
                status = HttpStatus.GATEWAY_TIMEOUT.value(),
                detail = "Upstream service did not respond in time",
                instance = requestPath,
                correlationId = correlationId
            )
        )
    }

    /**
     * Логирует ошибки upstream для отладки.
     * Внутренние детали допустимы в логах (не в ответе клиенту).
     * Логирует как wrapper исключение, так и root cause для лучшей отладки.
     */
    private fun logUpstreamError(requestPath: String, ex: Throwable) {
        val rootCause = getRootCause(ex)
        val rootCauseInfo = if (rootCause !== ex) {
            " (root cause: ${rootCause.javaClass.simpleName})"
        } else {
            ""
        }

        when {
            isConnectionError(rootCause) -> {
                logger.warn("Upstream connection failed for path: $requestPath - ${ex.javaClass.simpleName}$rootCauseInfo")
            }
            isTimeoutError(rootCause) -> {
                logger.warn("Upstream timeout for path: $requestPath - ${ex.javaClass.simpleName}$rootCauseInfo")
            }
            ex !is RouteNotFoundException && ex !is NotFoundException && ex !is ResponseStatusException -> {
                logger.error("Unexpected error for path: $requestPath", ex)
            }
        }
    }
}
