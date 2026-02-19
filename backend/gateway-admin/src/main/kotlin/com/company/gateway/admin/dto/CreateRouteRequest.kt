package com.company.gateway.admin.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

/**
 * Запрос на создание нового маршрута.
 *
 * Используется для POST /api/v1/routes.
 *
 * @property path URL path для маршрутизации (обязателен, начинается с /)
 * @property upstreamUrl URL целевого сервиса (обязателен)
 * @property methods список разрешённых HTTP методов (обязателен, не пустой)
 * @property description описание маршрута (опционально)
 * @property rateLimitId ID политики rate limit (опционально, Story 5.5)
 */
data class CreateRouteRequest(
    @field:NotBlank(message = "Path обязателен")
    @field:Size(max = 500, message = "Path не должен превышать 500 символов")
    @field:Pattern(
        regexp = "^/[a-zA-Z0-9/_-]*$",
        message = "Path должен начинаться с / и содержать только буквы, цифры, /, _ и -"
    )
    val path: String,

    @field:NotBlank(message = "Upstream URL обязателен")
    @field:Size(max = 2000, message = "Upstream URL не должен превышать 2000 символов")
    @field:Pattern(
        regexp = "^https?://.*",
        message = "Upstream URL должен быть валидным HTTP(S) URL"
    )
    val upstreamUrl: String,

    @field:NotEmpty(message = "Методы обязательны")
    @field:Size(max = 5, message = "Максимум 5 методов")
    @field:ValidHttpMethods
    val methods: List<String>,

    @field:Size(max = 1000, message = "Описание не должно превышать 1000 символов")
    val description: String? = null,

    /** ID политики rate limit (Story 5.5). Null = без rate limit. */
    val rateLimitId: UUID? = null
)
