package com.company.gateway.admin.dto

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

/**
 * Ответ с историей изменений маршрута.
 *
 * Story 7.3: Route Change History API (FR23).
 *
 * @param routeId ID маршрута
 * @param currentPath текущий path маршрута
 * @param history список записей истории в хронологическом порядке (oldest first)
 */
data class RouteHistoryResponse(
    val routeId: UUID,
    val currentPath: String,
    val history: List<HistoryEntry>
)

/**
 * Одна запись в истории изменений маршрута.
 *
 * @param timestamp время события (UTC)
 * @param action тип действия (created, updated, deleted, route.submitted, approved, rejected, published)
 * @param user информация о пользователе, выполнившем действие
 * @param changes изменения: { "before": {...}, "after": {...} } — null для действий без изменений
 */
data class HistoryEntry(
    val timestamp: Instant,
    val action: String,
    val user: RouteHistoryUserInfo,
    val changes: JsonNode? = null
)

/**
 * Информация о пользователе в истории маршрута.
 *
 * Отдельный класс от AuditLogResponse.UserInfo для независимости API.
 *
 * @param id UUID пользователя
 * @param username имя пользователя
 */
data class RouteHistoryUserInfo(
    val id: UUID,
    val username: String
)
