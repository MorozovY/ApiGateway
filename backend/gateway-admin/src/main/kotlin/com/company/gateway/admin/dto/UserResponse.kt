package com.company.gateway.admin.dto

import java.time.Instant
import java.util.UUID

/**
 * Данные пользователя для API response.
 *
 * Используется для GET /api/v1/users/{id} и в списке пользователей.
 * Не включает passwordHash для безопасности.
 *
 * @property id уникальный идентификатор пользователя
 * @property username имя пользователя
 * @property email адрес электронной почты
 * @property role роль пользователя (developer, security, admin)
 * @property isActive статус активности пользователя
 * @property createdAt дата создания пользователя
 */
data class UserResponse(
    val id: UUID,
    val username: String,
    val email: String,
    val role: String,
    val isActive: Boolean,
    val createdAt: Instant
)
