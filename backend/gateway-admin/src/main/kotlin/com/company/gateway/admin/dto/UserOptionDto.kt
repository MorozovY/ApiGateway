package com.company.gateway.admin.dto

import java.util.UUID

/**
 * Минимальные данные пользователя для dropdowns и фильтров.
 *
 * Используется для GET /api/v1/users/options.
 * Не содержит чувствительную информацию (email, role, isActive).
 * Доступен для security и admin ролей.
 */
data class UserOption(
    val id: UUID,
    val username: String
)

/**
 * Ответ со списком пользователей для dropdowns и фильтров.
 *
 * Используется в audit logs для фильтрации по пользователю.
 */
data class UserOptionsResponse(
    val items: List<UserOption>
)
