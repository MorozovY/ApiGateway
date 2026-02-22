package com.company.gateway.admin.dto

/**
 * Ответ на запрос сброса паролей демо-пользователей.
 *
 * @property message сообщение об успехе
 * @property users список пользователей, у которых сброшены пароли
 */
data class ResetDemoPasswordsResponse(
    val message: String,
    val users: List<String>
)
