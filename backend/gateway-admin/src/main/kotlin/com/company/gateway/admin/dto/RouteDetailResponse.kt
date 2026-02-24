package com.company.gateway.admin.dto

import java.time.Instant
import java.util.UUID

/**
 * Детальные данные маршрута для API response с информацией о создателе.
 *
 * Используется для GET /api/v1/routes/{id} — возвращает полную информацию о маршруте
 * включая username создателя, одобрившего и отклонившего (AC1, AC2, AC3).
 *
 * @property id уникальный идентификатор маршрута
 * @property path URL path для маршрутизации
 * @property upstreamUrl URL целевого сервиса
 * @property methods список разрешённых HTTP методов
 * @property description описание маршрута
 * @property status статус маршрута (draft, pending, published, rejected)
 * @property createdBy ID пользователя, создавшего маршрут
 * @property creatorUsername username создателя (JOIN с таблицей users)
 * @property approverUsername username пользователя, одобрившего маршрут (AC3)
 * @property rejectorUsername username пользователя, отклонившего маршрут (AC2)
 * @property createdAt дата создания маршрута
 * @property updatedAt дата последнего обновления
 * @property rateLimitId ID политики rate limiting (если назначена)
 * @property rateLimit детали политики rate limiting (если назначена)
 * @property authRequired требуется ли JWT аутентификация для маршрута (Story 12.7)
 * @property allowedConsumers whitelist consumer IDs (Story 12.7)
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
    val approverUsername: String? = null,
    val rejectorUsername: String? = null,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val submittedAt: Instant? = null,
    val approvedBy: UUID? = null,
    val approvedAt: Instant? = null,
    val rejectedBy: UUID? = null,
    val rejectedAt: Instant? = null,
    val rejectionReason: String? = null,
    val rateLimitId: UUID? = null,
    val rateLimit: RateLimitInfo? = null,
    val authRequired: Boolean = true,
    val allowedConsumers: List<String>? = null
)

/**
 * Промежуточная модель для маппинга результата JOIN запроса.
 *
 * Используется в RouteRepositoryCustomImpl для передачи данных из SQL в сервис.
 * Включает username создателя, одобрившего и отклонившего маршрут (AC2, AC3),
 * а также данные rate limit политики (Story 5.2).
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
    val approverUsername: String? = null,
    val rejectorUsername: String? = null,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val submittedAt: Instant? = null,
    val approvedBy: UUID? = null,
    val approvedAt: Instant? = null,
    val rejectedBy: UUID? = null,
    val rejectedAt: Instant? = null,
    val rejectionReason: String? = null,
    val rateLimitId: UUID? = null,
    val rateLimitName: String? = null,
    val rateLimitRequestsPerSecond: Int? = null,
    val rateLimitBurstSize: Int? = null,
    val authRequired: Boolean = true,
    val allowedConsumers: List<String>? = null
) {
    /**
     * Преобразует в RouteDetailResponse для API.
     */
    fun toResponse(): RouteDetailResponse {
        // Собираем RateLimitInfo если есть данные rate limit
        val rateLimitInfo = if (rateLimitId != null && rateLimitName != null) {
            RateLimitInfo(
                id = rateLimitId,
                name = rateLimitName,
                requestsPerSecond = rateLimitRequestsPerSecond!!,
                burstSize = rateLimitBurstSize!!
            )
        } else null

        return RouteDetailResponse(
            id = id,
            path = path,
            upstreamUrl = upstreamUrl,
            methods = methods,
            description = description,
            status = status,
            createdBy = createdBy,
            creatorUsername = creatorUsername,
            approverUsername = approverUsername,
            rejectorUsername = rejectorUsername,
            createdAt = createdAt,
            updatedAt = updatedAt,
            submittedAt = submittedAt,
            approvedBy = approvedBy,
            approvedAt = approvedAt,
            rejectedBy = rejectedBy,
            rejectedAt = rejectedAt,
            rejectionReason = rejectionReason,
            rateLimitId = rateLimitId,
            rateLimit = rateLimitInfo,
            authRequired = authRequired,
            allowedConsumers = allowedConsumers
        )
    }
}
