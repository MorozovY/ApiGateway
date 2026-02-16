package com.company.gateway.admin.security

/**
 * Исключения аутентификации JWT.
 *
 * Sealed class с подтипами для разных типов ошибок JWT:
 * - TokenMissing: токен отсутствует в запросе
 * - TokenExpired: токен истёк
 * - TokenInvalid: токен невалиден (неверная подпись, формат и т.д.)
 */
sealed class JwtAuthenticationException(
    override val message: String,
    val detail: String
) : RuntimeException(message) {

    /**
     * Токен отсутствует в cookie auth_token.
     */
    class TokenMissing : JwtAuthenticationException(
        message = "Authentication required",
        detail = "Authentication token is missing"
    )

    /**
     * Токен истёк (exp claim в прошлом).
     */
    class TokenExpired : JwtAuthenticationException(
        message = "Token expired",
        detail = "Token expired"
    )

    /**
     * Токен невалиден (неверная подпись, формат, claims и т.д.).
     */
    class TokenInvalid : JwtAuthenticationException(
        message = "Invalid token",
        detail = "Invalid token"
    )
}
