package com.company.gateway.admin.dto

import com.company.gateway.common.model.RouteStatus

/**
 * Параметры фильтрации и пагинации для списка маршрутов.
 *
 * Используется для GET /api/v1/routes с query parameters:
 * - status: фильтр по статусу маршрута (draft, pending, published, rejected)
 * - createdBy: фильтр по автору ("me" или UUID пользователя)
 * - search: текстовый поиск по path и upstream URL (case-insensitive)
 * - upstream: поиск по части upstream URL (ILIKE, case-insensitive)
 * - upstreamExact: точное совпадение upstream URL (case-sensitive)
 * - offset: смещение от начала списка (default 0)
 * - limit: количество элементов на странице (default 20, max 100)
 *
 * Валидация выполняется в RouteController, так как Spring не применяет
 * JSR-303 аннотации к @RequestParam автоматически.
 *
 * ВАЖНО: upstream и upstreamExact нельзя использовать одновременно (400 Bad Request).
 *
 * @property status статус маршрута для фильтрации (опционально)
 * @property createdBy "me" для своих маршрутов или UUID пользователя (опционально)
 * @property search строка поиска (опционально, min 1, max 100 символов)
 * @property upstream поиск по части upstream URL (ILIKE, case-insensitive)
 * @property upstreamExact точное совпадение upstream URL (case-sensitive)
 * @property offset смещение от начала списка (default 0, min 0)
 * @property limit количество элементов (default 20, min 1, max 100)
 */
data class RouteFilterRequest(
    val status: RouteStatus? = null,
    val createdBy: String? = null,
    val search: String? = null,
    val upstream: String? = null,
    val upstreamExact: String? = null,
    val offset: Int = 0,
    val limit: Int = 20
)
