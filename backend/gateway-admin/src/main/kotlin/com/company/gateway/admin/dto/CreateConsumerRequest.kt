package com.company.gateway.admin.dto

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Запрос на создание нового consumer в Keycloak.
 *
 * @property clientId уникальный client ID (lowercase, numbers, hyphens)
 * @property description описание consumer
 */
data class CreateConsumerRequest(
    @field:Pattern(
        regexp = "^[a-z0-9](-?[a-z0-9])*$",
        message = "Client ID must be lowercase letters, numbers, and hyphens (no leading/trailing hyphen)"
    )
    @field:Size(min = 3, max = 63, message = "Client ID must be 3-63 characters")
    val clientId: String,

    @field:Size(max = 255, message = "Description must be at most 255 characters")
    val description: String? = null
)
