package com.company.gateway.common.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RoleTest {

    @Test
    fun `конвертирует роль в значение базы данных в нижнем регистре`() {
        assertThat(Role.DEVELOPER.toDbValue()).isEqualTo("developer")
        assertThat(Role.SECURITY.toDbValue()).isEqualTo("security")
        assertThat(Role.ADMIN.toDbValue()).isEqualTo("admin")
    }

    @Test
    fun `создаёт роль из строки базы данных в нижнем регистре`() {
        assertThat(Role.fromDbValue("developer")).isEqualTo(Role.DEVELOPER)
        assertThat(Role.fromDbValue("security")).isEqualTo(Role.SECURITY)
        assertThat(Role.fromDbValue("admin")).isEqualTo(Role.ADMIN)
    }

    @Test
    fun `создаёт роль из строки в верхнем регистре`() {
        // fromDbValue нечувствителен к регистру
        assertThat(Role.fromDbValue("DEVELOPER")).isEqualTo(Role.DEVELOPER)
        assertThat(Role.fromDbValue("ADMIN")).isEqualTo(Role.ADMIN)
    }

    @Test
    fun `выбрасывает исключение для неизвестной роли`() {
        assertThatThrownBy { Role.fromDbValue("superuser") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("superuser")
    }

    @Test
    fun `содержит ровно три значения`() {
        assertThat(Role.entries).hasSize(3)
        assertThat(Role.entries).containsExactlyInAnyOrder(
            Role.DEVELOPER, Role.SECURITY, Role.ADMIN
        )
    }
}
