package com.company.gateway.admin.dto

import jakarta.validation.constraints.NotBlank

/**
 * DTO для запроса отклонения маршрута.
 *
 * Story 4.2, AC3: Успешное отклонение маршрута
 * Story 4.2, AC4: Отклонение без причины
 *
 * @property reason причина отклонения (обязательна, не может быть пустой)
 */
data class RejectRouteRequest(
    @field:NotBlank(message = "Rejection reason is required")
    val reason: String
)
