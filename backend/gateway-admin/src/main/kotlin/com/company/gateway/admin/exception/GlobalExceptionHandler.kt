package com.company.gateway.admin.exception

import com.company.gateway.admin.security.JwtAuthenticationException
import com.company.gateway.common.Constants.CORRELATION_ID_HEADER
import com.company.gateway.common.exception.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Глобальный обработчик исключений для WebFlux filter chain.
 *
 * Обрабатывает исключения, выброшенные из WebFilter (например, JwtAuthenticationFilter).
 * RestControllerAdvice не перехватывает исключения из filter chain.
 */
@Component
@Order(-2) // Приоритет выше стандартного ErrorWebExceptionHandler
class GlobalExceptionHandler(
    private val objectMapper: ObjectMapper
) : ErrorWebExceptionHandler {

    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        // Обрабатываем только JwtAuthenticationException
        return when (ex) {
            is JwtAuthenticationException -> handleJwtAuthenticationException(exchange, ex)
            else -> {
                // Пропускаем другие исключения для обработки стандартным механизмом
                Mono.error(ex)
            }
        }
    }

    private fun handleJwtAuthenticationException(
        exchange: ServerWebExchange,
        ex: JwtAuthenticationException
    ): Mono<Void> {
        val correlationId = exchange.request.headers
            .getFirst(CORRELATION_ID_HEADER)
            ?: UUID.randomUUID().toString()

        val errorResponse = ErrorResponse(
            type = "https://api.gateway/errors/unauthorized",
            title = "Unauthorized",
            status = HttpStatus.UNAUTHORIZED.value(),
            detail = ex.detail,
            instance = exchange.request.path.value(),
            correlationId = correlationId
        )

        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        exchange.response.headers.contentType = MediaType.APPLICATION_JSON
        exchange.response.headers.add(CORRELATION_ID_HEADER, correlationId)

        val body = objectMapper.writeValueAsBytes(errorResponse)
        val buffer = exchange.response.bufferFactory().wrap(body)

        return exchange.response.writeWith(Mono.just(buffer))
    }
}
