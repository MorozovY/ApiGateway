package com.company.gateway.admin.security

import com.company.gateway.common.Constants.CORRELATION_ID_HEADER
import com.company.gateway.common.exception.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Entry point для обработки неаутентифицированных запросов.
 *
 * Вызывается Spring Security когда неаутентифицированный пользователь
 * пытается получить доступ к защищённому ресурсу.
 * Возвращает HTTP 401 Unauthorized с телом в формате RFC 7807.
 */
@Component
class JwtAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper
) : ServerAuthenticationEntryPoint {

    override fun commence(
        exchange: ServerWebExchange,
        ex: AuthenticationException
    ): Mono<Void> {
        // Извлекаем или генерируем Correlation-ID
        val correlationId = exchange.request.headers
            .getFirst(CORRELATION_ID_HEADER)
            ?: UUID.randomUUID().toString()

        // Формируем RFC 7807 ответ
        val errorResponse = ErrorResponse(
            type = "https://api.gateway/errors/unauthorized",
            title = "Unauthorized",
            status = HttpStatus.UNAUTHORIZED.value(),
            detail = "Authentication required",
            instance = exchange.request.path.value(),
            correlationId = correlationId
        )

        // Устанавливаем статус и заголовки
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        exchange.response.headers.contentType = MediaType.APPLICATION_JSON
        exchange.response.headers.add(CORRELATION_ID_HEADER, correlationId)

        // Записываем тело ответа
        val body = objectMapper.writeValueAsBytes(errorResponse)
        val buffer = exchange.response.bufferFactory().wrap(body)

        return exchange.response.writeWith(Mono.just(buffer))
    }
}
