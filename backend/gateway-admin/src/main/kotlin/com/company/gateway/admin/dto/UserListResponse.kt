package com.company.gateway.admin.dto

/**
 * Пагинированный список пользователей.
 *
 * Используется для GET /api/v1/users с поддержкой пагинации.
 *
 * @property items список пользователей на текущей странице
 * @property total общее количество пользователей
 * @property offset смещение от начала списка
 * @property limit максимальное количество элементов на странице
 */
data class UserListResponse(
    val items: List<UserResponse>,
    val total: Long,
    val offset: Int,
    val limit: Int
)
