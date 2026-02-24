package com.company.gateway.admin.dto

import com.company.gateway.common.model.ConsumerRateLimit
import java.time.Instant
import java.util.UUID

/**
 * Данные per-consumer rate limit для API response.
 *
 * @property id уникальный идентификатор записи
 * @property consumerId идентификатор consumer (Keycloak client_id)
 * @property requestsPerSecond лимит запросов в секунду
 * @property burstSize максимальный burst (пик запросов)
 * @property createdAt дата создания
 * @property updatedAt дата последнего обновления
 * @property createdBy информация о создателе
 */
data class ConsumerRateLimitResponse(
    val id: UUID,
    val consumerId: String,
    val requestsPerSecond: Int,
    val burstSize: Int,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val createdBy: CreatorInfo?
) {
    /**
     * Информация о создателе rate limit.
     */
    data class CreatorInfo(
        val id: UUID,
        val username: String
    )

    companion object {
        /**
         * Создаёт ConsumerRateLimitResponse из entity.
         *
         * @param entity ConsumerRateLimit entity
         * @param creatorUsername username создателя (или null если не найден)
         */
        fun from(entity: ConsumerRateLimit, creatorUsername: String?): ConsumerRateLimitResponse {
            return ConsumerRateLimitResponse(
                id = entity.id!!,
                consumerId = entity.consumerId,
                requestsPerSecond = entity.requestsPerSecond,
                burstSize = entity.burstSize,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                createdBy = entity.createdBy?.let { createdById ->
                    creatorUsername?.let { username ->
                        CreatorInfo(createdById, username)
                    }
                }
            )
        }
    }
}
