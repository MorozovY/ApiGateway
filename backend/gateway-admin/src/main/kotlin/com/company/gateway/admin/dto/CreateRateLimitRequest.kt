package com.company.gateway.admin.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Запрос на создание политики rate limiting.
 *
 * @property name уникальное имя политики (обязательное)
 * @property description описание политики (опционально)
 * @property requestsPerSecond лимит запросов в секунду (> 0)
 * @property burstSize максимальный размер burst (> 0, >= requestsPerSecond)
 */
data class CreateRateLimitRequest(
    @field:NotBlank(message = "Name is required")
    @field:Size(max = 100, message = "Name must not exceed 100 characters")
    val name: String,

    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    val description: String? = null,

    @field:Min(value = 1, message = "Requests per second must be positive")
    val requestsPerSecond: Int,

    @field:Min(value = 1, message = "Burst size must be positive")
    val burstSize: Int
)
