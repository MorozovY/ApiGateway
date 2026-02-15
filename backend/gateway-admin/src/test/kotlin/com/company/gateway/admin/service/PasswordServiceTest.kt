package com.company.gateway.admin.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PasswordServiceTest {

    private val service = PasswordService()

    @Test
    fun `хеширует пароль с BCrypt`() {
        val hashed = service.hash("password123")

        // BCrypt хеш начинается с $2a$ и имеет длину 60 символов
        assertThat(hashed).startsWith("\$2a\$")
        assertThat(hashed).hasSize(60)
    }

    @Test
    fun `проверяет корректный пароль`() {
        val hashed = service.hash("password123")

        assertThat(service.verify("password123", hashed)).isTrue()
    }

    @Test
    fun `отклоняет неверный пароль`() {
        val hashed = service.hash("password123")

        assertThat(service.verify("wrongpassword", hashed)).isFalse()
    }

    @Test
    fun `генерирует уникальные хеши для одинаковых паролей`() {
        val hash1 = service.hash("samepassword")
        val hash2 = service.hash("samepassword")

        // BCrypt добавляет случайную соль, поэтому хеши должны быть разными
        assertThat(hash1).isNotEqualTo(hash2)
        // Но оба должны верифицироваться с оригинальным паролем
        assertThat(service.verify("samepassword", hash1)).isTrue()
        assertThat(service.verify("samepassword", hash2)).isTrue()
    }

    @Test
    fun `не хранит пароль в plaintext в хеше`() {
        val rawPassword = "secretpassword"
        val hashed = service.hash(rawPassword)

        assertThat(hashed).doesNotContain(rawPassword)
    }

    @Test
    fun `выбрасывает исключение для пустого пароля`() {
        assertThatThrownBy { service.hash("") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("пустым")
    }

    @Test
    fun `выбрасывает исключение для пароля из пробелов`() {
        assertThatThrownBy { service.hash("   ") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("пустым")
    }
}
