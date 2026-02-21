package com.company.gateway.admin.controller

import com.company.gateway.admin.dto.LoginRequest
import com.company.gateway.admin.dto.LoginResponse
import com.company.gateway.admin.security.CookieService
import com.company.gateway.admin.security.JwtService
import com.company.gateway.admin.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * Контроллер аутентификации.
 *
 * Предоставляет endpoints для входа и выхода из системы:
 * - POST /api/v1/auth/login - аутентификация пользователя
 * - POST /api/v1/auth/logout - выход из системы
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val jwtService: JwtService,
    private val cookieService: CookieService
) {
    /**
     * Аутентификация пользователя.
     *
     * При успешной аутентификации:
     * - Генерируется JWT токен
     * - Токен устанавливается в HTTP-only cookie
     * - Возвращается информация о пользователе
     *
     * @param request запрос с username и password
     * @return LoginResponse с userId, username и role
     */
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): Mono<ResponseEntity<LoginResponse>> {
        return authService.authenticate(request.username, request.password)
            .map { user ->
                val token = jwtService.generateToken(user)
                val cookie = cookieService.createAuthCookie(token)

                ResponseEntity.ok()
                    .header("Set-Cookie", cookie.toString())
                    .body(
                        LoginResponse(
                            userId = user.id!!.toString(),
                            username = user.username,
                            role = user.role.name.lowercase()
                        )
                    )
            }
    }

    /**
     * Выход из системы.
     *
     * Очищает cookie с JWT токеном (устанавливает MaxAge=0).
     *
     * @return пустой ответ с HTTP 200
     */
    @PostMapping("/logout")
    fun logout(): Mono<ResponseEntity<Void>> {
        val cookie = cookieService.createLogoutCookie()
        return Mono.just(
            ResponseEntity.ok()
                .header("Set-Cookie", cookie.toString())
                .build()
        )
    }

    /**
     * Получение информации о текущем пользователе.
     *
     * Используется для восстановления сессии при перезагрузке страницы.
     * Читает JWT из cookie и возвращает данные пользователя.
     *
     * @param exchange ServerWebExchange для доступа к cookies
     * @return LoginResponse с userId, username и role, или 401 если токен невалиден
     */
    @GetMapping("/me")
    fun getCurrentUser(exchange: ServerWebExchange): Mono<ResponseEntity<LoginResponse>> {
        // Извлекаем токен из cookie
        val cookies = exchange.request.cookies[CookieService.AUTH_COOKIE_NAME]
        val token = cookieService.extractToken(cookies)
            ?: return Mono.just(ResponseEntity.status(401).build())

        // Валидируем токен
        val claims = jwtService.validateToken(token)
            ?: return Mono.just(ResponseEntity.status(401).build())

        // Возвращаем данные пользователя из claims
        return Mono.just(
            ResponseEntity.ok(
                LoginResponse(
                    userId = claims.subject,
                    username = claims.get("username", String::class.java),
                    role = claims.get("role", String::class.java)
                )
            )
        )
    }
}
