package com.company.gateway.admin.dto

import com.company.gateway.common.model.RateLimit
import java.time.Instant
import java.util.UUID

/**
 * Данные политики rate limiting для API response.
 *
 * @property id уникальный идентификатор политики
 * @property name уникальное имя политики
 * @property description описание политики
 * @property requestsPerSecond лимит запросов в секунду
 * @property burstSize максимальный размер burst
 * @property usageCount количество маршрутов, использующих политику
 * @property createdBy ID пользователя, создавшего политику
 * @property createdAt дата создания
 * @property updatedAt дата последнего обновления
 */
data class RateLimitResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val requestsPerSecond: Int,
    val burstSize: Int,
    val usageCount: Long,
    val createdBy: UUID,
    val createdAt: Instant?,
    val updatedAt: Instant?
) {
    companion object {
        /**
         * Создаёт RateLimitResponse из RateLimit entity с указанным usageCount.
         */
        fun from(rateLimit: RateLimit, usageCount: Long): RateLimitResponse {
            return RateLimitResponse(
                id = rateLimit.id!!,
                name = rateLimit.name,
                description = rateLimit.description,
                requestsPerSecond = rateLimit.requestsPerSecond,
                burstSize = rateLimit.burstSize,
                usageCount = usageCount,
                createdBy = rateLimit.createdBy,
                createdAt = rateLimit.createdAt,
                updatedAt = rateLimit.updatedAt
            )
        }
    }
}
