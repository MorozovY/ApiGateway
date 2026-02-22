package com.company.gateway.admin.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Запрос на смену пароля пользователя.
 *
 * @property currentPassword текущий пароль пользователя (обязательное поле)
 * @property newPassword новый пароль (минимум 8 символов)
 */
data class ChangePasswordRequest(
    @field:NotBlank(message = "Текущий пароль обязателен")
    val currentPassword: String,

    @field:NotBlank(message = "Новый пароль обязателен")
    @field:Size(min = 8, message = "Новый пароль должен содержать минимум 8 символов")
    val newPassword: String
)
