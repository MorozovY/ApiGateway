package com.company.gateway.admin.dto

/**
 * Ответ при успешной аутентификации.
 *
 * @property userId идентификатор пользователя (UUID в строковом формате)
 * @property username имя пользователя
 * @property role роль пользователя в lowercase (developer, security, admin)
 */
data class LoginResponse(
    val userId: String,
    val username: String,
    val role: String
)
