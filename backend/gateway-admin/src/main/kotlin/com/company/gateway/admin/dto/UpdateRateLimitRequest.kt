package com.company.gateway.admin.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

/**
 * Запрос на обновление политики rate limiting.
 *
 * Все поля опциональны — обновляются только переданные значения.
 *
 * @property name новое имя политики (опционально)
 * @property description новое описание политики (опционально)
 * @property requestsPerSecond новый лимит запросов в секунду (опционально, > 0)
 * @property burstSize новый размер burst (опционально, > 0)
 */
data class UpdateRateLimitRequest(
    @field:Size(max = 100, message = "Name must not exceed 100 characters")
    val name: String? = null,

    val description: String? = null,

    @field:Min(value = 1, message = "Requests per second must be positive")
    val requestsPerSecond: Int? = null,

    @field:Min(value = 1, message = "Burst size must be positive")
    val burstSize: Int? = null
)
