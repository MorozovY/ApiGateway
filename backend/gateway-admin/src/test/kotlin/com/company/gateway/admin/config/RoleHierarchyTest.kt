package com.company.gateway.admin.config

import com.company.gateway.common.model.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit тесты для RoleHierarchy.
 */
class RoleHierarchyTest {

    @Test
    fun `admin включает все роли`() {
        val included = RoleHierarchy.getIncludedRoles(Role.ADMIN)
        assertThat(included).containsExactlyInAnyOrder(Role.ADMIN, Role.SECURITY, Role.DEVELOPER)
    }

    @Test
    fun `security включает security и developer`() {
        val included = RoleHierarchy.getIncludedRoles(Role.SECURITY)
        assertThat(included).containsExactlyInAnyOrder(Role.SECURITY, Role.DEVELOPER)
    }

    @Test
    fun `developer включает только себя`() {
        val included = RoleHierarchy.getIncludedRoles(Role.DEVELOPER)
        assertThat(included).containsExactly(Role.DEVELOPER)
    }

    @Test
    fun `admin имеет доступ к ADMIN endpoint`() {
        assertThat(RoleHierarchy.hasRequiredRole(Role.ADMIN, Role.ADMIN)).isTrue()
    }

    @Test
    fun `admin имеет доступ к SECURITY endpoint через иерархию`() {
        assertThat(RoleHierarchy.hasRequiredRole(Role.ADMIN, Role.SECURITY)).isTrue()
    }

    @Test
    fun `admin имеет доступ к DEVELOPER endpoint через иерархию`() {
        assertThat(RoleHierarchy.hasRequiredRole(Role.ADMIN, Role.DEVELOPER)).isTrue()
    }

    @Test
    fun `security имеет доступ к SECURITY endpoint`() {
        assertThat(RoleHierarchy.hasRequiredRole(Role.SECURITY, Role.SECURITY)).isTrue()
    }

    @Test
    fun `security имеет доступ к DEVELOPER endpoint через иерархию`() {
        assertThat(RoleHierarchy.hasRequiredRole(Role.SECURITY, Role.DEVELOPER)).isTrue()
    }

    @Test
    fun `security не имеет доступа к ADMIN endpoint`() {
        assertThat(RoleHierarchy.hasRequiredRole(Role.SECURITY, Role.ADMIN)).isFalse()
    }

    @Test
    fun `developer имеет доступ к DEVELOPER endpoint`() {
        assertThat(RoleHierarchy.hasRequiredRole(Role.DEVELOPER, Role.DEVELOPER)).isTrue()
    }

    @Test
    fun `developer не имеет доступа к SECURITY endpoint`() {
        assertThat(RoleHierarchy.hasRequiredRole(Role.DEVELOPER, Role.SECURITY)).isFalse()
    }

    @Test
    fun `developer не имеет доступа к ADMIN endpoint`() {
        assertThat(RoleHierarchy.hasRequiredRole(Role.DEVELOPER, Role.ADMIN)).isFalse()
    }

    @Test
    fun `иерархия транзитивна - admin через security имеет все права developer`() {
        // Проверяем транзитивность: ADMIN > SECURITY > DEVELOPER
        val adminRoles = RoleHierarchy.getIncludedRoles(Role.ADMIN)
        val securityRoles = RoleHierarchy.getIncludedRoles(Role.SECURITY)

        // Все роли security должны быть в admin
        assertThat(adminRoles).containsAll(securityRoles)

        // Все роли developer должны быть в security
        val developerRoles = RoleHierarchy.getIncludedRoles(Role.DEVELOPER)
        assertThat(securityRoles).containsAll(developerRoles)
    }
}
