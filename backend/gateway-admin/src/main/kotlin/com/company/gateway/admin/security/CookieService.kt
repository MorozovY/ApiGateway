package com.company.gateway.admin.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Сервис для управления HTTP-only cookies с JWT токенами.
 *
 * Создаёт безопасные cookies с атрибутами:
 * - HttpOnly: защита от XSS атак
 * - Secure: только HTTPS в production
 * - SameSite=Strict: защита от CSRF
 * - Path=/: доступ ко всем endpoint'ам
 */
@Service
class CookieService(
    @Value("\${spring.profiles.active:dev}")
    private val activeProfile: String,
    @Value("\${jwt.expiration:86400000}")
    private val tokenExpiration: Long
) {
    companion object {
        const val AUTH_COOKIE_NAME = "auth_token"
    }

    // Проверяем, работаем ли в production окружении
    // Поддерживает несколько профилей разделённых запятыми (например: "prod,kubernetes")
    private val isProduction: Boolean
        get() = activeProfile.split(",").any { it.trim() == "prod" }

    /**
     * Создаёт cookie с JWT токеном для аутентификации.
     *
     * @param token JWT токен
     * @return ResponseCookie с настроенными атрибутами безопасности
     */
    fun createAuthCookie(token: String): ResponseCookie {
        return ResponseCookie.from(AUTH_COOKIE_NAME, token)
            .httpOnly(true)
            .secure(isProduction)
            .sameSite("Strict")
            .path("/")
            .maxAge(Duration.ofMillis(tokenExpiration))
            .build()
    }

    /**
     * Создаёт cookie для выхода из системы (очистка токена).
     *
     * @return ResponseCookie с maxAge=0 для удаления cookie
     */
    fun createLogoutCookie(): ResponseCookie {
        return ResponseCookie.from(AUTH_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(isProduction)
            .sameSite("Strict")
            .path("/")
            .maxAge(0)
            .build()
    }

    /**
     * Извлекает JWT токен из cookies запроса.
     *
     * @param cookies список cookies из запроса
     * @return JWT токен или null если cookie отсутствует
     */
    fun extractToken(cookies: List<org.springframework.http.HttpCookie>?): String? {
        return cookies
            ?.firstOrNull { it.name == AUTH_COOKIE_NAME }
            ?.value
    }
}
