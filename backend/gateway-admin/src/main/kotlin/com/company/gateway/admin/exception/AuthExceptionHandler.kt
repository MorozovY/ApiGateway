package com.company.gateway.admin.exception

import com.company.gateway.admin.service.AuthenticationException
import com.company.gateway.common.exception.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Обработчик исключений аутентификации.
 *
 * Преобразует AuthenticationException и ошибки валидации в RFC 7807 формат ответа.
 * Включает X-Correlation-ID в каждый error response для трассировки.
 */
@RestControllerAdvice
class AuthExceptionHandler {

    companion object {
        private const val CORRELATION_ID_HEADER = "X-Correlation-ID"
    }

    /**
     * Обрабатывает исключения аутентификации.
     *
     * Возвращает HTTP 401 Unauthorized с RFC 7807 форматом.
     */
    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(
        ex: AuthenticationException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {
        val requestPath = exchange.request.path.value()
        val correlationId = extractOrGenerateCorrelationId(exchange)

        val errorResponse = ErrorResponse(
            type = "https://api.gateway/errors/authentication-failed",
            title = "Unauthorized",
            status = HttpStatus.UNAUTHORIZED.value(),
            detail = ex.message ?: "Authentication failed",
            instance = requestPath,
            correlationId = correlationId
        )

        return Mono.just(
            ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .header(CORRELATION_ID_HEADER, correlationId)
                .body(errorResponse)
        )
    }

    /**
     * Обрабатывает ошибки валидации входных данных.
     *
     * Возвращает HTTP 400 Bad Request с RFC 7807 форматом.
     */
    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidationException(
        ex: WebExchangeBindException,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ErrorResponse>> {
        val requestPath = exchange.request.path.value()
        val correlationId = extractOrGenerateCorrelationId(exchange)

        // Собираем все ошибки валидации
        val errors = ex.bindingResult.fieldErrors
            .map { "${it.field}: ${it.defaultMessage}" }
            .joinToString("; ")

        val errorResponse = ErrorResponse(
            type = "https://api.gateway/errors/validation-failed",
            title = "Bad Request",
            status = HttpStatus.BAD_REQUEST.value(),
            detail = errors.ifEmpty { "Validation failed" },
            instance = requestPath,
            correlationId = correlationId
        )

        return Mono.just(
            ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .header(CORRELATION_ID_HEADER, correlationId)
                .body(errorResponse)
        )
    }

    /**
     * Извлекает Correlation-ID из заголовка запроса или генерирует новый.
     */
    private fun extractOrGenerateCorrelationId(exchange: ServerWebExchange): String {
        return exchange.request.headers.getFirst(CORRELATION_ID_HEADER)
            ?: UUID.randomUUID().toString()
    }
}
