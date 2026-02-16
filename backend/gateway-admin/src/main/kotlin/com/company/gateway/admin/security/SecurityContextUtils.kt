package com.company.gateway.admin.security

import org.springframework.security.core.context.ReactiveSecurityContextHolder
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Утилиты для работы с SecurityContext в reactive контексте.
 *
 * Предоставляет удобные методы для получения информации
 * о текущем аутентифицированном пользователе.
 */
object SecurityContextUtils {

    /**
     * Получает текущего аутентифицированного пользователя.
     *
     * @return Mono с AuthenticatedUser или empty если пользователь не аутентифицирован
     */
    fun currentUser(): Mono<AuthenticatedUser> {
        return ReactiveSecurityContextHolder.getContext()
            .filter { it.authentication != null }
            .filter { it.authentication.principal is AuthenticatedUser }
            .map { it.authentication.principal as AuthenticatedUser }
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
