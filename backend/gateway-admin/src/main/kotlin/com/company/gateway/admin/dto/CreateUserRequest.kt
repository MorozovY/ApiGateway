package com.company.gateway.admin.dto

import com.company.gateway.common.model.Role
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * Запрос на создание нового пользователя.
 *
 * Используется для POST /api/v1/users.
 * Все поля обязательны для заполнения.
 *
 * @property username имя пользователя (от 3 до 50 символов, уникальное)
 * @property email адрес электронной почты (уникальный)
 * @property password пароль (минимум 8 символов, будет захеширован BCrypt)
 * @property role роль пользователя
 */
data class CreateUserRequest(
    @field:NotBlank(message = "Username обязателен")
    @field:Size(min = 3, max = 50, message = "Username должен быть от 3 до 50 символов")
    val username: String,

    @field:NotBlank(message = "Email обязателен")
    @field:Email(message = "Некорректный формат email")
    val email: String,

    @field:NotBlank(message = "Пароль обязателен")
    @field:Size(min = 8, message = "Пароль должен быть минимум 8 символов")
    val password: String,

    @field:NotNull(message = "Роль обязательна")
    val role: Role
)
