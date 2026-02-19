package com.company.gateway.admin.dto

import com.company.gateway.common.model.RateLimit
import java.util.UUID

/**
 * Краткая информация о политике rate limiting для встраивания в RouteResponse.
 *
 * В отличие от RateLimitResponse, не содержит usageCount и audit поля,
 * так как используется как вложенный объект в ответах маршрутов.
 *
 * @property id уникальный идентификатор политики
 * @property name уникальное имя политики
 * @property requestsPerSecond лимит запросов в секунду
 * @property burstSize максимальный размер burst
 */
data class RateLimitInfo(
    val id: UUID,
    val name: String,
    val requestsPerSecond: Int,
    val burstSize: Int
) {
    companion object {
        /**
         * Создаёт RateLimitInfo из RateLimit entity.
         */
        fun from(rateLimit: RateLimit): RateLimitInfo {
            return RateLimitInfo(
                id = rateLimit.id!!,
                name = rateLimit.name,
                requestsPerSecond = rateLimit.requestsPerSecond,
                burstSize = rateLimit.burstSize
            )
        }
    }
}
