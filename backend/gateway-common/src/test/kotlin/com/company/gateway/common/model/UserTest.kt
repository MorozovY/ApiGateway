package com.company.gateway.common.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class UserTest {

    @Test
    fun `создаёт пользователя с обязательными полями`() {
        val user = User(
            username = "testuser",
            email = "test@example.com",
            passwordHash = "\$2a\$10\$somehashedpassword"
        )

        assertThat(user.username).isEqualTo("testuser")
        assertThat(user.email).isEqualTo("test@example.com")
        assertThat(user.passwordHash).isEqualTo("\$2a\$10\$somehashedpassword")
    }

    @Test
    fun `устанавливает роль DEVELOPER по умолчанию`() {
        val user = User(
            username = "dev",
            email = "dev@example.com",
            passwordHash = "hash"
        )

        assertThat(user.role).isEqualTo(Role.DEVELOPER)
    }

    @Test
    fun `устанавливает is_active = true по умолчанию`() {
        val user = User(
            username = "active",
            email = "active@example.com",
            passwordHash = "hash"
        )

        assertThat(user.isActive).isTrue()
    }

    @Test
    fun `id равен null для нового несохранённого пользователя`() {
        val user = User(
            username = "newuser",
            email = "new@example.com",
            passwordHash = "hash"
        )

        assertThat(user.id).isNull()
    }

    @Test
    fun `создаёт пользователя с указанной ролью`() {
        val user = User(
            username = "admin",
            email = "admin@example.com",
            passwordHash = "hash",
            role = Role.ADMIN
        )

        assertThat(user.role).isEqualTo(Role.ADMIN)
    }

    @Test
    fun `копирует пользователя с изменённым полем`() {
        val original = User(
            id = UUID.randomUUID(),
            username = "original",
            email = "orig@example.com",
            passwordHash = "hash"
        )

        val updated = original.copy(isActive = false)

        assertThat(updated.id).isEqualTo(original.id)
        assertThat(updated.username).isEqualTo(original.username)
        assertThat(updated.isActive).isFalse()
    }
}
