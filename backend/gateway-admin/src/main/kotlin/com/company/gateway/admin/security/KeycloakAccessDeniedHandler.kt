package com.company.gateway.admin.security

import com.company.gateway.common.Constants.CORRELATION_ID_HEADER
import com.company.gateway.common.exception.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Handler для обработки ошибок авторизации (403 Forbidden) в Keycloak режиме.
 *
 * Вызывается когда аутентифицированный пользователь не имеет
 * необходимых прав для доступа к ресурсу.
 * Возвращает HTTP 403 Forbidden с телом в формате RFC 7807.
 *
 * @see com.company.gateway.admin.config.KeycloakSecurityConfig
 */
@Component
class KeycloakAccessDeniedHandler(
    private val objectMapper: ObjectMapper
) : ServerAccessDeniedHandler {

    override fun handle(
        exchange: ServerWebExchange,
        denied: AccessDeniedException
    ): Mono<Void> {
        // Извлекаем или генерируем Correlation-ID
        val correlationId = exchange.request.headers
            .getFirst(CORRELATION_ID_HEADER)
            ?: UUID.randomUUID().toString()

        // Формируем RFC 7807 ответ
        val errorResponse = ErrorResponse(
            type = "https://api.gateway/errors/forbidden",
            title = "Forbidden",
            status = HttpStatus.FORBIDDEN.value(),
            detail = "Access denied: insufficient permissions",
            instance = exchange.request.path.value(),
            correlationId = correlationId
        )

        // Устанавливаем статус и заголовки
        exchange.response.statusCode = HttpStatus.FORBIDDEN
        exchange.response.headers.contentType = MediaType.APPLICATION_JSON
        exchange.response.headers.add(CORRELATION_ID_HEADER, correlationId)

        // Записываем тело ответа
        val body = objectMapper.writeValueAsBytes(errorResponse)
        val buffer = exchange.response.bufferFactory().wrap(body)

        return exchange.response.writeWith(Mono.just(buffer))
    }
}
