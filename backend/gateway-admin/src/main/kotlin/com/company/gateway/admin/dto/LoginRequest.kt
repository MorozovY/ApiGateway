package com.company.gateway.admin.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Запрос на аутентификацию пользователя.
 *
 * @property username имя пользователя (обязательное поле, 1-100 символов)
 * @property password пароль в открытом виде (обязательное поле, 1-100 символов)
 */
data class LoginRequest(
    @field:NotBlank(message = "Username is required")
    @field:Size(max = 100, message = "Username must not exceed 100 characters")
    val username: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(max = 100, message = "Password must not exceed 100 characters")
    val password: String
)
