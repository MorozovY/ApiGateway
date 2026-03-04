package com.company.gateway.admin.dto

import java.time.Instant

/**
 * DTO для списка последних действий на Dashboard.
 *
 * Story 16.2: Наполнение Dashboard полезным контентом
 *
 * @property items список последних действий
 */
data class RecentActivityDto(
    val items: List<ActivityItem>
)

/**
 * Элемент активности в истории действий.
 *
 * @property id уникальный идентификатор записи
 * @property action тип действия: "created", "updated", "approved", "rejected", "published"
 * @property entityType тип сущности: "route"
 * @property entityId идентификатор сущности
 * @property entityName отображаемое имя (путь маршрута)
 * @property performedBy имя пользователя, выполнившего действие
 * @property performedAt время выполнения действия (ISO 8601)
 */
data class ActivityItem(
    val id: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val entityName: String?,
    val performedBy: String,
    val performedAt: Instant
)
