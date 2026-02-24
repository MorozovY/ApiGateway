package com.company.gateway.admin.security

import com.company.gateway.common.Constants.AUTH_COOKIE_NAME
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * WebFilter для JWT аутентификации.
 *
 * Извлекает JWT токен из cookie `auth_token`, валидирует его
 * через JwtService и заполняет SecurityContext при успешной валидации.
 *
 * Если токен отсутствует — пропускает запрос для дальнейшей обработки SecurityConfig.
 * Если токен невалиден или истёк — выбрасывает JwtAuthenticationException.
 *
 * Активируется только при keycloak.enabled=false (по умолчанию).
 * При keycloak.enabled=true JWT валидация выполняется через OAuth2 Resource Server.
 */
@Component
@ConditionalOnProperty(name = ["keycloak.enabled"], havingValue = "false", matchIfMissing = true)
class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        // Извлекаем токен из cookie или header
        val token = extractToken(exchange)
            ?: return chain.filter(exchange)  // Нет токена — пропускаем (SecurityConfig решит)

        // Валидируем токен с определением типа ошибки
        return when (val result = jwtService.validateTokenWithResult(token)) {
            is TokenValidationResult.Valid -> {
                // Создаём Authentication из claims
                val authenticatedUser = AuthenticatedUser.fromClaims(result.claims)
                val authentication = UsernamePasswordAuthenticationToken(
                    authenticatedUser,
                    null,
                    authenticatedUser.authorities
                )

                // Устанавливаем в SecurityContext и продолжаем цепочку
                chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            }
            is TokenValidationResult.Expired -> {
                // Токен истёк
                Mono.error(JwtAuthenticationException.TokenExpired())
            }
            is TokenValidationResult.Invalid -> {
                // Токен невалиден
                Mono.error(JwtAuthenticationException.TokenInvalid())
            }
        }
    }

    /**
     * Извлекает JWT токен из cookie auth_token или Authorization header.
     *
     * Приоритет: cookie > Authorization header.
     * Authorization header поддерживает формат "Bearer <token>".
     */
    private fun extractToken(exchange: ServerWebExchange): String? {
        // Сначала пробуем cookie
        val cookieToken = exchange.request.cookies
            .getFirst(AUTH_COOKIE_NAME)
            ?.value

        if (cookieToken != null) {
            return cookieToken
        }

        // Если нет cookie, пробуем Authorization header
        val authHeader = exchange.request.headers.getFirst("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7)
        }

        return null
    }
}
