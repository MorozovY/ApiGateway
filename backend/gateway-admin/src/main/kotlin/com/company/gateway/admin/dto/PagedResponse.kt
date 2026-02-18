package com.company.gateway.admin.dto

/**
 * Универсальный пагинированный ответ.
 *
 * Используется для возврата пагинированных списков в API.
 * Story 4.3 — список pending маршрутов.
 *
 * @property items список элементов на текущей странице
 * @property total общее количество элементов
 * @property offset смещение от начала списка
 * @property limit максимальное количество элементов на странице
 */
data class PagedResponse<T>(
    val items: List<T>,
    val total: Long,
    val offset: Int,
    val limit: Int
)
