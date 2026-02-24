package com.company.gateway.admin.controller

import com.company.gateway.admin.dto.ChangePasswordRequest
import com.company.gateway.admin.dto.ChangePasswordResponse
import com.company.gateway.admin.dto.LoginRequest
import com.company.gateway.admin.dto.LoginResponse
import com.company.gateway.admin.dto.ResetDemoPasswordsResponse
import com.company.gateway.admin.properties.KeycloakProperties
import com.company.gateway.admin.security.CookieService
import com.company.gateway.admin.security.JwtService
import com.company.gateway.admin.security.SecurityContextUtils
import com.company.gateway.admin.service.AuthService
import com.company.gateway.admin.service.KeycloakAdminService
import com.company.gateway.admin.service.UserService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.Optional
import java.util.UUID

/**
 * Контроллер аутентификации.
 *
 * Предоставляет endpoints для входа и выхода из системы:
 * - POST /api/v1/auth/login - аутентификация пользователя
 * - POST /api/v1/auth/logout - выход из системы
 * - POST /api/v1/auth/change-password - смена пароля текущего пользователя
 * - POST /api/v1/auth/reset-demo-passwords - сброс паролей демо-пользователей
 * - GET /api/v1/auth/me - получение информации о текущем пользователе
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val userService: UserService,
    private val jwtService: JwtService,
    private val cookieService: CookieService,
    private val keycloakProperties: Optional<KeycloakProperties>,
    private val keycloakAdminService: Optional<KeycloakAdminService>
) {
    /**
     * Проверяет, включен ли режим Keycloak.
     */
    private fun isKeycloakEnabled(): Boolean =
        keycloakProperties.map { it.enabled }.orElse(false)
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
     * Смена пароля текущего пользователя (Story 9.4).
     *
     * Проверяет текущий пароль и обновляет на новый.
     * Записывает audit log при успешной смене.
     *
     * При keycloak.enabled=true использует Keycloak Admin API.
     * При keycloak.enabled=false использует локальную БД.
     *
     * @param request запрос с текущим и новым паролем
     * @param exchange ServerWebExchange для доступа к cookies и JWT
     * @return ChangePasswordResponse при успехе, 401 если текущий пароль неверный
     */
    @PostMapping("/change-password")
    fun changePassword(
        @Valid @RequestBody request: ChangePasswordRequest,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ChangePasswordResponse>> {
        return if (isKeycloakEnabled() && keycloakAdminService.isPresent) {
            changePasswordViaKeycloak(request)
        } else {
            changePasswordViaLegacy(request, exchange)
        }
    }

    /**
     * Смена пароля через Keycloak Admin API.
     */
    private fun changePasswordViaKeycloak(
        request: ChangePasswordRequest
    ): Mono<ResponseEntity<ChangePasswordResponse>> {
        val adminService = keycloakAdminService.get()

        return SecurityContextUtils.currentUser()
            .flatMap { user ->
                // Проверяем текущий пароль через Keycloak token endpoint
                adminService.verifyPassword(user.username, request.currentPassword)
                    .flatMap { valid ->
                        if (!valid) {
                            Mono.just(
                                ResponseEntity.status(401)
                                    .body(ChangePasswordResponse("Current password is incorrect"))
                            )
                        } else {
                            // Сбрасываем пароль через Admin API
                            adminService.resetPasswordByUsername(user.username, request.newPassword)
                                .map {
                                    ResponseEntity.ok(
                                        ChangePasswordResponse("Password changed successfully")
                                    )
                                }
                        }
                    }
            }
            .switchIfEmpty(Mono.just(ResponseEntity.status(401).build()))
    }

    /**
     * Смена пароля через локальную БД (legacy режим).
     */
    private fun changePasswordViaLegacy(
        request: ChangePasswordRequest,
        exchange: ServerWebExchange
    ): Mono<ResponseEntity<ChangePasswordResponse>> {
        val claims = extractAndValidateToken(exchange)
            ?: return Mono.just(ResponseEntity.status(401).build())

        val userId = UUID.fromString(claims.subject)
        val username = claims.get("username", String::class.java)

        return userService.changePassword(
            userId = userId,
            currentPassword = request.currentPassword,
            newPassword = request.newPassword,
            username = username
        ).thenReturn(
            ResponseEntity.ok(
                ChangePasswordResponse("Password changed successfully")
            )
        )
    }

    /**
     * Получение информации о текущем пользователе.
     *
     * Используется для восстановления сессии при перезагрузке страницы.
     *
     * При keycloak.enabled=true читает из SecurityContext (Keycloak JWT).
     * При keycloak.enabled=false читает JWT из cookie.
     *
     * @param exchange ServerWebExchange для доступа к cookies
     * @return LoginResponse с userId, username и role, или 401 если токен невалиден
     */
    @GetMapping("/me")
    fun getCurrentUser(exchange: ServerWebExchange): Mono<ResponseEntity<LoginResponse>> {
        return if (isKeycloakEnabled()) {
            getCurrentUserFromSecurityContext()
        } else {
            getCurrentUserFromCookie(exchange)
        }
    }

    /**
     * Получение пользователя из SecurityContext (Keycloak режим).
     */
    private fun getCurrentUserFromSecurityContext(): Mono<ResponseEntity<LoginResponse>> {
        return SecurityContextUtils.currentUser()
            .map { user ->
                ResponseEntity.ok(
                    LoginResponse(
                        userId = user.userId.toString(),
                        username = user.username,
                        role = user.role.name.lowercase()
                    )
                )
            }
            .switchIfEmpty(Mono.just(ResponseEntity.status(401).build()))
    }

    /**
     * Получение пользователя из cookie (legacy режим).
     */
    private fun getCurrentUserFromCookie(exchange: ServerWebExchange): Mono<ResponseEntity<LoginResponse>> {
        val claims = extractAndValidateToken(exchange)
            ?: return Mono.just(ResponseEntity.status(401).build())

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

    /**
     * Сброс паролей демо-пользователей (Story 9.5).
     *
     * Публичный endpoint, доступный без аутентификации.
     * Сбрасывает пароли для developer, security, admin на дефолтные.
     *
     * @return ResetDemoPasswordsResponse со списком обновлённых пользователей
     */
    @PostMapping("/reset-demo-passwords")
    fun resetDemoPasswords(): Mono<ResponseEntity<ResetDemoPasswordsResponse>> {
        return userService.resetDemoPasswords()
            .map { users ->
                ResponseEntity.ok(
                    ResetDemoPasswordsResponse(
                        message = "Demo passwords reset successfully",
                        users = users
                    )
                )
            }
    }

    /**
     * Извлекает и валидирует JWT токен из cookie.
     *
     * @param exchange ServerWebExchange для доступа к cookies
     * @return Claims при успешной валидации, null если токен отсутствует или невалиден
     */
    private fun extractAndValidateToken(exchange: ServerWebExchange): io.jsonwebtoken.Claims? {
        val cookies = exchange.request.cookies[CookieService.AUTH_COOKIE_NAME]
        val token = cookieService.extractToken(cookies) ?: return null
        return jwtService.validateToken(token)
    }
}
