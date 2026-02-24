package com.company.gateway.admin.security

import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Утилиты для работы с SecurityContext в reactive контексте.
 *
 * Предоставляет удобные методы для получения информации
 * о текущем аутентифицированном пользователе.
 *
 * Поддерживает два типа аутентификации:
 * - Legacy HMAC JWT → principal = AuthenticatedUser
 * - Keycloak OAuth2 JWT → principal = Jwt (конвертируется в AuthenticatedUser)
 */
object SecurityContextUtils {

    /**
     * Получает текущего аутентифицированного пользователя.
     *
     * Поддерживает:
     * - AuthenticatedUser principal (legacy JWT)
     * - Jwt principal (Keycloak OAuth2)
     *
     * @return Mono с AuthenticatedUser или empty если пользователь не аутентифицирован
     */
    fun currentUser(): Mono<AuthenticatedUser> {
        return ReactiveSecurityContextHolder.getContext()
            .filter { it.authentication != null }
            .flatMap { context ->
                val principal = context.authentication.principal
                when (principal) {
                    is AuthenticatedUser -> Mono.just(principal)
                    is Jwt -> {
                        // Конвертируем Keycloak JWT в AuthenticatedUser
                        try {
                            Mono.just(AuthenticatedUser.fromKeycloakJwt(principal))
                        } catch (e: Exception) {
                            Mono.empty()
                        }
                    }
                    else -> Mono.empty()
                }
            }
    }

    /**
     * Получает ID текущего пользователя.
     *
     * @return Mono с UUID пользователя или empty если пользователь не аутентифицирован
     */
    fun currentUserId(): Mono<UUID> {
        return currentUser().map { it.userId }
    }

    /**
     * Получает username текущего пользователя.
     *
     * @return Mono с username или empty если пользователь не аутентифицирован
     */
    fun currentUsername(): Mono<String> {
        return currentUser().map { it.username }
    }
}
