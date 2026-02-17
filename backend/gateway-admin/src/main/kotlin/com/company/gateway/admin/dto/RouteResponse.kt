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
    val updatedAt: Instant?
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
                updatedAt = route.updatedAt
            )
        }
    }
}
