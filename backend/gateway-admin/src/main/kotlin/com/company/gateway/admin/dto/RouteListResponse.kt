package com.company.gateway.admin.dto

/**
 * Пагинированный список маршрутов.
 *
 * Используется для GET /api/v1/routes с поддержкой пагинации.
 *
 * @property items список маршрутов на текущей странице
 * @property total общее количество маршрутов
 * @property offset смещение от начала списка
 * @property limit максимальное количество элементов на странице
 */
data class RouteListResponse(
    val items: List<RouteResponse>,
    val total: Long,
    val offset: Int,
    val limit: Int
)
