package com.company.gateway.admin.config

import com.company.gateway.common.model.Role

/**
 * Определение иерархии ролей в системе.
 *
 * Иерархия: ADMIN > SECURITY > DEVELOPER
 * - ADMIN имеет все права SECURITY и DEVELOPER
 * - SECURITY имеет все права DEVELOPER
 * - DEVELOPER имеет только собственные права
 */
object RoleHierarchy {

    /**
     * Возвращает все роли, которые включены в указанную роль.
     *
     * @param role Роль пользователя
     * @return Множество ролей, доступных пользователю с данной ролью
     */
    fun getIncludedRoles(role: Role): Set<Role> = when (role) {
        Role.ADMIN -> setOf(Role.ADMIN, Role.SECURITY, Role.DEVELOPER)
        Role.SECURITY -> setOf(Role.SECURITY, Role.DEVELOPER)
        Role.DEVELOPER -> setOf(Role.DEVELOPER)
    }

    /**
     * Проверяет, имеет ли пользователь достаточные права для требуемой роли.
     *
     * Учитывает иерархию ролей: если пользователь имеет роль выше требуемой,
     * доступ разрешается.
     *
     * @param userRole Роль текущего пользователя
     * @param requiredRole Требуемая роль для доступа
     * @return true если пользователь имеет достаточные права
     */
    fun hasRequiredRole(userRole: Role, requiredRole: Role): Boolean {
        return getIncludedRoles(userRole).contains(requiredRole)
    }
}
