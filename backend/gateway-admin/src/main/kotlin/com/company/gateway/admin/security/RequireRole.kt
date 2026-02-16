package com.company.gateway.admin.security

import com.company.gateway.common.model.Role

/**
 * Аннотация для проверки роли пользователя на уровне метода или класса.
 *
 * Используется совместно с RoleAuthorizationAspect для реализации RBAC.
 * Поддерживает role hierarchy: Admin > Security > Developer.
 *
 * Пример использования:
 * ```kotlin
 * @RequireRole(Role.ADMIN)
 * fun adminOnlyMethod(): Mono<Response> { ... }
 *
 * @RequireRole(Role.SECURITY, Role.ADMIN)
 * fun securityOrAdminMethod(): Mono<Response> { ... }
 * ```
 *
 * @param roles Массив ролей, которым разрешён доступ. Пользователь должен иметь
 *              хотя бы одну из указанных ролей (или роль выше в иерархии).
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireRole(vararg val roles: Role)
