package com.company.gateway.admin.dto

import java.time.Instant
import java.util.UUID

/**
 * Детальные данные маршрута для API response с информацией о создателе.
 *
 * Используется для GET /api/v1/routes/{id} — возвращает полную информацию о маршруте
 * включая username создателя (AC1).
 *
 * @property id уникальный идентификатор маршрута
 * @property path URL path для маршрутизации
 * @property upstreamUrl URL целевого сервиса
 * @property methods список разрешённых HTTP методов
 * @property description описание маршрута
 * @property status статус маршрута (draft, pending, published, rejected)
 * @property createdBy ID пользователя, создавшего маршрут
 * @property creatorUsername username создателя (JOIN с таблицей users)
 * @property createdAt дата создания маршрута
 * @property updatedAt дата последнего обновления
 * @property rateLimitId ID политики rate limiting (если назначена)
 */
data class RouteDetailResponse(
    val id: UUID,
    val path: String,
    val upstreamUrl: String,
    val methods: List<String>,
    val description: String?,
    val status: String,
    val createdBy: UUID?,
    val creatorUsername: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val submittedAt: Instant? = null,
    val approvedBy: UUID? = null,
    val approvedAt: Instant? = null,
    val rejectedBy: UUID? = null,
    val rejectedAt: Instant? = null,
    val rejectionReason: String? = null,
    val rateLimitId: UUID? = null
)

/**
 * Промежуточная модель для маппинга результата JOIN запроса.
 *
 * Используется в RouteRepositoryCustomImpl для передачи данных из SQL в сервис.
 */
data class RouteWithCreator(
    val id: UUID,
    val path: String,
    val upstreamUrl: String,
    val methods: List<String>,
    val description: String?,
    val status: String,
    val createdBy: UUID?,
    val creatorUsername: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val submittedAt: Instant? = null,
    val approvedBy: UUID? = null,
    val approvedAt: Instant? = null,
    val rejectedBy: UUID? = null,
    val rejectedAt: Instant? = null,
    val rejectionReason: String? = null,
    val rateLimitId: UUID? = null
) {
    /**
     * Преобразует в RouteDetailResponse для API.
     */
    fun toResponse(): RouteDetailResponse {
        return RouteDetailResponse(
            id = id,
            path = path,
            upstreamUrl = upstreamUrl,
            methods = methods,
            description = description,
            status = status,
            createdBy = createdBy,
            creatorUsername = creatorUsername,
            createdAt = createdAt,
            updatedAt = updatedAt,
            submittedAt = submittedAt,
            approvedBy = approvedBy,
            approvedAt = approvedAt,
            rejectedBy = rejectedBy,
            rejectedAt = rejectedAt,
            rejectionReason = rejectionReason,
            rateLimitId = rateLimitId
        )
    }
}
