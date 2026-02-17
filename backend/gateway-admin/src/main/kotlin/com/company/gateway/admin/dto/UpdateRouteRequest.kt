package com.company.gateway.admin.dto

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Запрос на обновление маршрута.
 *
 * Используется для PUT /api/v1/routes/{id}.
 * Все поля опциональны — обновляются только переданные поля.
 *
 * @property path новый URL path для маршрутизации (опционально)
 * @property upstreamUrl новый URL целевого сервиса (опционально)
 * @property methods новый список разрешённых HTTP методов (опционально)
 * @property description новое описание маршрута (опционально)
 */
data class UpdateRouteRequest(
    @field:Size(max = 500, message = "Path не должен превышать 500 символов")
    @field:Pattern(
        regexp = "^/[a-zA-Z0-9/_-]*$",
        message = "Path должен начинаться с / и содержать только буквы, цифры, /, _ и -"
    )
    val path: String? = null,

    @field:Size(max = 2000, message = "Upstream URL не должен превышать 2000 символов")
    @field:Pattern(
        regexp = "^https?://.*",
        message = "Upstream URL должен быть валидным HTTP(S) URL"
    )
    val upstreamUrl: String? = null,

    @field:Size(max = 5, message = "Максимум 5 методов")
    @field:ValidHttpMethods
    val methods: List<String>? = null,

    @field:Size(max = 1000, message = "Описание не должно превышать 1000 символов")
    val description: String? = null
)
