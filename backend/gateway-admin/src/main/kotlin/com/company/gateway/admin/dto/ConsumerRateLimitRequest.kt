package com.company.gateway.admin.dto

import jakarta.validation.constraints.Min

/**
 * Запрос на создание/обновление per-consumer rate limit.
 *
 * @property requestsPerSecond лимит запросов в секунду (> 0)
 * @property burstSize максимальный burst (пик запросов) (> 0)
 */
data class ConsumerRateLimitRequest(
    @field:Min(value = 1, message = "Requests per second must be positive")
    val requestsPerSecond: Int,

    @field:Min(value = 1, message = "Burst size must be positive")
    val burstSize: Int
)
