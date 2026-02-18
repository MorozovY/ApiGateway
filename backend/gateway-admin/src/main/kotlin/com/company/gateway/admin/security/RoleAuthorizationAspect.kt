package com.company.gateway.admin.security

import com.company.gateway.admin.config.RoleHierarchy
import com.company.gateway.admin.exception.AccessDeniedException
import com.company.gateway.common.model.Role
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Аспект для проверки ролей на методах с @RequireRole аннотацией.
 *
 * Перехватывает вызовы методов и проверяет, имеет ли текущий пользователь
 * одну из требуемых ролей (или роль выше в иерархии).
 *
 * Поддерживает аннотации как на уровне метода, так и на уровне класса.
 * Аннотация на методе имеет приоритет над аннотацией на классе.
 */
@Aspect
@Component
class RoleAuthorizationAspect {

    /**
     * Проверяет роли для методов с @RequireRole аннотацией.
     * Работает с аннотациями на методе и на классе.
     */
    @Around("@within(com.company.gateway.admin.security.RequireRole) || @annotation(com.company.gateway.admin.security.RequireRole)")
    fun checkRole(joinPoint: ProceedingJoinPoint): Any? {
        val annotation = getRequireRoleAnnotation(joinPoint)
            ?: return joinPoint.proceed()

        val requiredRoles = annotation.roles.toSet()

        // Выполняем метод и получаем результат
        val result = joinPoint.proceed()

        // Для reactive типов оборачиваем проверку в chain
        return when (result) {
            is Mono<*> -> wrapMonoWithRoleCheck(result, requiredRoles)
            is Flux<*> -> wrapFluxWithRoleCheck(result, requiredRoles)
            else -> {
                // Non-reactive методы не поддерживаются в WebFlux контексте
                throw UnsupportedOperationException(
                    "Non-reactive методы с @RequireRole не поддерживаются"
                )
            }
        }
    }

    /**
     * Оборачивает Mono в проверку ролей.
     *
     * ВАЖНО: switchIfEmpty применяется к проверке аутентификации ДО flatMap,
     * чтобы не перехватывать Mono<Void> (DELETE endpoints), которые завершаются
     * без эмиссии элемента — это нормальное поведение для 204 No Content.
     */
    private fun wrapMonoWithRoleCheck(mono: Mono<*>, requiredRoles: Set<Role>): Mono<*> {
        return SecurityContextUtils.currentUser()
            // Если пользователь не аутентифицирован — 401 Unauthorized
            .switchIfEmpty(Mono.error(JwtAuthenticationException.TokenMissing()))
            .flatMap { user ->
                val hasAccess = requiredRoles.any { requiredRole ->
                    RoleHierarchy.hasRequiredRole(user.role, requiredRole)
                }
                if (hasAccess) {
                    @Suppress("UNCHECKED_CAST")
                    mono as Mono<Any>
                } else {
                    Mono.error(AccessDeniedException("Insufficient permissions"))
                }
            }
    }

    /**
     * Оборачивает Flux в проверку ролей.
     *
     * ВАЖНО: switchIfEmpty применяется к проверке аутентификации ДО flatMapMany,
     * чтобы корректно работать с пустыми Flux результатами (легитимный пустой список).
     */
    private fun wrapFluxWithRoleCheck(flux: Flux<*>, requiredRoles: Set<Role>): Flux<*> {
        return SecurityContextUtils.currentUser()
            // Если пользователь не аутентифицирован — 401 Unauthorized
            .switchIfEmpty(Mono.error(JwtAuthenticationException.TokenMissing()))
            .flatMapMany { user ->
                val hasAccess = requiredRoles.any { requiredRole ->
                    RoleHierarchy.hasRequiredRole(user.role, requiredRole)
                }
                if (hasAccess) {
                    @Suppress("UNCHECKED_CAST")
                    flux as Flux<Any>
                } else {
                    Flux.error(AccessDeniedException("Insufficient permissions"))
                }
            }
    }

    /**
     * Получает @RequireRole аннотацию с метода или класса.
     * Приоритет: метод > класс.
     */
    private fun getRequireRoleAnnotation(joinPoint: ProceedingJoinPoint): RequireRole? {
        val signature = joinPoint.signature as? MethodSignature
            ?: return null

        // Сначала проверяем аннотацию на методе
        val methodAnnotation = signature.method.getAnnotation(RequireRole::class.java)
        if (methodAnnotation != null) {
            return methodAnnotation
        }

        // Затем проверяем аннотацию на классе
        return joinPoint.target.javaClass.getAnnotation(RequireRole::class.java)
    }
}
