package com.company.gateway.admin.dto

import com.company.gateway.common.model.Role
import jakarta.validation.constraints.Email

/**
 * Запрос на обновление пользователя.
 *
 * Используется для PUT /api/v1/users/{id}.
 * Все поля опциональны — обновляются только переданные поля.
 * Пароль нельзя обновить через этот endpoint (требуется отдельный flow).
 *
 * @property email новый адрес электронной почты (опционально)
 * @property role новая роль пользователя (опционально)
 * @property isActive статус активности пользователя (опционально)
 */
data class UpdateUserRequest(
    @field:Email(message = "Некорректный формат email")
    val email: String? = null,

    val role: Role? = null,

    val isActive: Boolean? = null
)
