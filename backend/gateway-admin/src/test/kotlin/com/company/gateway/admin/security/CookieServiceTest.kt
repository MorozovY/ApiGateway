package com.company.gateway.admin.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class CookieServiceTest {

    private val tokenExpiration = 86400000L // 24 часа

    @Test
    fun `createAuthCookie создаёт cookie с HttpOnly атрибутом`() {
        val cookieService = CookieService("dev", tokenExpiration)

        val cookie = cookieService.createAuthCookie("test-token")

        assertThat(cookie.isHttpOnly).isTrue()
    }

    @Test
    fun `createAuthCookie создаёт cookie с SameSite Strict`() {
        val cookieService = CookieService("dev", tokenExpiration)

        val cookie = cookieService.createAuthCookie("test-token")

        assertThat(cookie.sameSite).isEqualTo("Strict")
    }

    @Test
    fun `createAuthCookie устанавливает Path равным корню`() {
        val cookieService = CookieService("dev", tokenExpiration)

        val cookie = cookieService.createAuthCookie("test-token")

        assertThat(cookie.path).isEqualTo("/")
    }

    @Test
    fun `createAuthCookie устанавливает корректное имя cookie`() {
        val cookieService = CookieService("dev", tokenExpiration)

        val cookie = cookieService.createAuthCookie("test-token")

        assertThat(cookie.name).isEqualTo("auth_token")
    }

    @Test
    fun `createAuthCookie устанавливает значение токена`() {
        val cookieService = CookieService("dev", tokenExpiration)
        val token = "my-jwt-token"

        val cookie = cookieService.createAuthCookie(token)

        assertThat(cookie.value).isEqualTo(token)
    }

    @Test
    fun `createAuthCookie устанавливает maxAge из конфигурации`() {
        val cookieService = CookieService("dev", tokenExpiration)

        val cookie = cookieService.createAuthCookie("test-token")

        assertThat(cookie.maxAge).isEqualTo(Duration.ofMillis(tokenExpiration))
    }

    @Test
    fun `createAuthCookie НЕ устанавливает Secure в dev окружении`() {
        val cookieService = CookieService("dev", tokenExpiration)

        val cookie = cookieService.createAuthCookie("test-token")

        assertThat(cookie.isSecure).isFalse()
    }

    @Test
    fun `createAuthCookie устанавливает Secure в prod окружении`() {
        val cookieService = CookieService("prod", tokenExpiration)

        val cookie = cookieService.createAuthCookie("test-token")

        assertThat(cookie.isSecure).isTrue()
    }

    @Test
    fun `createAuthCookie НЕ устанавливает Secure в test окружении`() {
        val cookieService = CookieService("test", tokenExpiration)

        val cookie = cookieService.createAuthCookie("test-token")

        assertThat(cookie.isSecure).isFalse()
    }

    @Test
    fun `createLogoutCookie создаёт cookie с пустым значением`() {
        val cookieService = CookieService("dev", tokenExpiration)

        val cookie = cookieService.createLogoutCookie()

        assertThat(cookie.value).isEmpty()
    }

    @Test
    fun `createLogoutCookie устанавливает maxAge равным 0`() {
        val cookieService = CookieService("dev", tokenExpiration)

        val cookie = cookieService.createLogoutCookie()

        assertThat(cookie.maxAge).isEqualTo(Duration.ZERO)
    }

    @Test
    fun `createLogoutCookie сохраняет все атрибуты безопасности`() {
        val cookieService = CookieService("dev", tokenExpiration)

        val cookie = cookieService.createLogoutCookie()

        assertThat(cookie.isHttpOnly).isTrue()
        assertThat(cookie.sameSite).isEqualTo("Strict")
        assertThat(cookie.path).isEqualTo("/")
        assertThat(cookie.name).isEqualTo("auth_token")
    }

    @Test
    fun `createLogoutCookie устанавливает Secure в prod окружении`() {
        val cookieService = CookieService("prod", tokenExpiration)

        val cookie = cookieService.createLogoutCookie()

        assertThat(cookie.isSecure).isTrue()
    }

    @Test
    fun `createAuthCookie устанавливает Secure когда prod в списке профилей`() {
        val cookieService = CookieService("prod,kubernetes", tokenExpiration)

        val cookie = cookieService.createAuthCookie("test-token")

        assertThat(cookie.isSecure).isTrue()
    }

    @Test
    fun `createAuthCookie устанавливает Secure когда prod в списке профилей с пробелами`() {
        val cookieService = CookieService("prod, kubernetes, monitoring", tokenExpiration)

        val cookie = cookieService.createAuthCookie("test-token")

        assertThat(cookie.isSecure).isTrue()
    }

    @Test
    fun `createAuthCookie НЕ устанавливает Secure когда prod отсутствует в списке`() {
        val cookieService = CookieService("staging,kubernetes", tokenExpiration)

        val cookie = cookieService.createAuthCookie("test-token")

        assertThat(cookie.isSecure).isFalse()
    }
}
