package com.company.gateway.admin.security

import com.company.gateway.common.Constants.CORRELATION_ID_HEADER
import com.company.gateway.common.exception.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Entry point для обработки ошибок аутентификации в Keycloak режиме.
 *
 * Возвращает RFC 7807 ответ для различных типов ошибок:
 * - Отсутствующий Bearer token → 401 Unauthorized
 * - Невалидный/истёкший token → 401 Unauthorized
 * - Ошибка валидации JWT → 401 Unauthorized
 *
 * @see com.company.gateway.admin.config.KeycloakSecurityConfig
 */
@Component
class KeycloakAuthenticationEntryPoint(
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

        // Определяем детальное сообщение об ошибке
        val detail = when (ex) {
            is InvalidBearerTokenException -> extractTokenErrorDetail(ex)
            else -> "Authentication required"
        }

        // Формируем RFC 7807 ответ
        val errorResponse = ErrorResponse(
            type = "https://api.gateway/errors/unauthorized",
            title = "Unauthorized",
            status = HttpStatus.UNAUTHORIZED.value(),
            detail = detail,
            instance = exchange.request.path.value(),
            correlationId = correlationId
        )

        // Устанавливаем статус и заголовки
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        exchange.response.headers.contentType = MediaType.APPLICATION_JSON
        exchange.response.headers.add(CORRELATION_ID_HEADER, correlationId)
        // WWW-Authenticate header для OAuth2 compliance
        exchange.response.headers.add(
            "WWW-Authenticate",
            "Bearer realm=\"api-gateway\", error=\"invalid_token\", error_description=\"$detail\""
        )

        // Записываем тело ответа
        val body = objectMapper.writeValueAsBytes(errorResponse)
        val buffer = exchange.response.bufferFactory().wrap(body)

        return exchange.response.writeWith(Mono.just(buffer))
    }

    /**
     * Извлекает понятное описание ошибки из InvalidBearerTokenException.
     */
    private fun extractTokenErrorDetail(ex: InvalidBearerTokenException): String {
        val message = ex.message ?: return "Invalid token"

        return when {
            message.contains("expired", ignoreCase = true) -> "Token has expired"
            message.contains("invalid", ignoreCase = true) -> "Invalid token"
            message.contains("signature", ignoreCase = true) -> "Invalid token signature"
            message.contains("malformed", ignoreCase = true) -> "Malformed token"
            else -> "Invalid token"
        }
    }
}
