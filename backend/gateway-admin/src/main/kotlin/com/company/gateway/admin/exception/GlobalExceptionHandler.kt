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
        // Обрабатываем JWT, AccessDenied, Conflict и NotFound исключения
        return when (ex) {
            is JwtAuthenticationException -> handleJwtAuthenticationException(exchange, ex)
            is AccessDeniedException -> handleAccessDeniedException(exchange, ex)
            is ConflictException -> handleConflictException(exchange, ex)
            is NotFoundException -> handleNotFoundException(exchange, ex)
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

    /**
     * Обрабатывает AccessDeniedException и возвращает 403 Forbidden.
     */
    private fun handleAccessDeniedException(
        exchange: ServerWebExchange,
        ex: AccessDeniedException
    ): Mono<Void> {
        val correlationId = exchange.request.headers
            .getFirst(CORRELATION_ID_HEADER)
            ?: UUID.randomUUID().toString()

        val errorResponse = ErrorResponse(
            type = "https://api.gateway/errors/forbidden",
            title = "Forbidden",
            status = HttpStatus.FORBIDDEN.value(),
            detail = ex.detail,
            instance = exchange.request.path.value(),
            correlationId = correlationId
        )

        exchange.response.statusCode = HttpStatus.FORBIDDEN
        exchange.response.headers.contentType = MediaType.APPLICATION_JSON
        exchange.response.headers.add(CORRELATION_ID_HEADER, correlationId)

        val body = objectMapper.writeValueAsBytes(errorResponse)
        val buffer = exchange.response.bufferFactory().wrap(body)

        return exchange.response.writeWith(Mono.just(buffer))
    }

    /**
     * Обрабатывает ConflictException и возвращает 409 Conflict.
     */
    private fun handleConflictException(
        exchange: ServerWebExchange,
        ex: ConflictException
    ): Mono<Void> {
        val correlationId = exchange.request.headers
            .getFirst(CORRELATION_ID_HEADER)
            ?: UUID.randomUUID().toString()

        val errorResponse = ErrorResponse(
            type = "https://api.gateway/errors/conflict",
            title = "Conflict",
            status = HttpStatus.CONFLICT.value(),
            detail = ex.detail,
            instance = exchange.request.path.value(),
            correlationId = correlationId
        )

        exchange.response.statusCode = HttpStatus.CONFLICT
        exchange.response.headers.contentType = MediaType.APPLICATION_JSON
        exchange.response.headers.add(CORRELATION_ID_HEADER, correlationId)

        val body = objectMapper.writeValueAsBytes(errorResponse)
        val buffer = exchange.response.bufferFactory().wrap(body)

        return exchange.response.writeWith(Mono.just(buffer))
    }

    /**
     * Обрабатывает NotFoundException и возвращает 404 Not Found.
     */
    private fun handleNotFoundException(
        exchange: ServerWebExchange,
        ex: NotFoundException
    ): Mono<Void> {
        val correlationId = exchange.request.headers
            .getFirst(CORRELATION_ID_HEADER)
            ?: UUID.randomUUID().toString()

        val errorResponse = ErrorResponse(
            type = "https://api.gateway/errors/not-found",
            title = "Not Found",
            status = HttpStatus.NOT_FOUND.value(),
            detail = ex.detail,
            instance = exchange.request.path.value(),
            correlationId = correlationId
        )

        exchange.response.statusCode = HttpStatus.NOT_FOUND
        exchange.response.headers.contentType = MediaType.APPLICATION_JSON
        exchange.response.headers.add(CORRELATION_ID_HEADER, correlationId)

        val body = objectMapper.writeValueAsBytes(errorResponse)
        val buffer = exchange.response.bufferFactory().wrap(body)

        return exchange.response.writeWith(Mono.just(buffer))
    }
}
