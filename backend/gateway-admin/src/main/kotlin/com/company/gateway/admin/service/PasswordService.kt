package com.company.gateway.admin.service

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service

@Service
class PasswordService {

    private val encoder = BCryptPasswordEncoder()

    // Хешируем пароль с использованием BCrypt
    // Пароль не может быть пустым
    fun hash(rawPassword: String): String {
        require(rawPassword.isNotBlank()) { "Пароль не может быть пустым" }
        return encoder.encode(rawPassword)
    }

    // Проверяем соответствие пароля хешу
    fun verify(rawPassword: String, hashedPassword: String): Boolean =
        encoder.matches(rawPassword, hashedPassword)
}
