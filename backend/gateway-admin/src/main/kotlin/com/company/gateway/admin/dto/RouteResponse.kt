package com.company.gateway.admin.dto

import com.company.gateway.common.model.Route
import java.time.Instant
import java.util.UUID

/**
 * Данные маршрута для API response.
 *
 * Используется для GET /api/v1/routes/{id} и в списке маршрутов.
 *
 * @property id уникальный идентификатор маршрута
 * @property path URL path для маршрутизации
 * @property upstreamUrl URL целевого сервиса
 * @property methods список разрешённых HTTP методов
 * @property description описание маршрута
 * @property status статус маршрута (draft, pending, published, rejected)
 * @property createdBy ID пользователя, создавшего маршрут
 * @property createdAt дата создания маршрута
 * @property updatedAt дата последнего обновления
 * @property submittedAt время отправки на согласование
 * @property approvedBy ID пользователя, одобрившего маршрут
 * @property approvedAt время одобрения
 * @property rejectedBy ID пользователя, отклонившего маршрут
 * @property rejectedAt время отклонения
 * @property rejectionReason причина отклонения
 */
data class RouteResponse(
    val id: UUID,
    val path: String,
    val upstreamUrl: String,
    val methods: List<String>,
    val description: String?,
    val status: String,
    val createdBy: UUID?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val submittedAt: Instant? = null,
    val approvedBy: UUID? = null,
    val approvedAt: Instant? = null,
    val rejectedBy: UUID? = null,
    val rejectedAt: Instant? = null,
    val rejectionReason: String? = null
) {
    companion object {
        /**
         * Создаёт RouteResponse из Route entity.
         */
        fun from(route: Route): RouteResponse {
            return RouteResponse(
                id = route.id!!,
                path = route.path,
                upstreamUrl = route.upstreamUrl,
                methods = route.methods,
                description = route.description,
                status = route.status.name.lowercase(),
                createdBy = route.createdBy,
                createdAt = route.createdAt,
                updatedAt = route.updatedAt,
                submittedAt = route.submittedAt,
                approvedBy = route.approvedBy,
                approvedAt = route.approvedAt,
                rejectedBy = route.rejectedBy,
                rejectedAt = route.rejectedAt,
                rejectionReason = route.rejectionReason
            )
        }
    }
}
