package com.company.gateway.admin.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSetter
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

/**
 * Запрос на обновление маршрута.
 *
 * Используется для PUT /api/v1/routes/{id}.
 * Все поля опциональны — обновляются только переданные поля (partial update).
 *
 * Для rateLimitId используется специальная логика:
 * - Поле отсутствует в JSON → политика не изменяется (rateLimitIdProvided = false)
 * - Поле = null → политика удаляется с маршрута (AC3)
 * - Поле = UUID → политика назначается (AC1)
 *
 * @property path новый URL path для маршрутизации (опционально)
 * @property upstreamUrl новый URL целевого сервиса (опционально)
 * @property methods новый список разрешённых HTTP методов (опционально)
 * @property description новое описание маршрута (опционально)
 * @property rateLimitId ID политики rate limiting (null для удаления политики с маршрута)
 * @property rateLimitIdProvided true если rateLimitId был явно передан в JSON
 */
class UpdateRouteRequest(
    path: String? = null,
    upstreamUrl: String? = null,
    methods: List<String>? = null,
    description: String? = null
) {
    @field:Size(max = 500, message = "Path не должен превышать 500 символов")
    @field:Pattern(
        regexp = "^/[a-zA-Z0-9/_-]*$",
        message = "Path должен начинаться с / и содержать только буквы, цифры, /, _ и -"
    )
    var path: String? = path

    @field:Size(max = 2000, message = "Upstream URL не должен превышать 2000 символов")
    @field:Pattern(
        regexp = "^https?://.*",
        message = "Upstream URL должен быть валидным HTTP(S) URL"
    )
    var upstreamUrl: String? = upstreamUrl

    @field:Size(max = 5, message = "Максимум 5 методов")
    @field:ValidHttpMethods
    var methods: List<String>? = methods

    @field:Size(max = 1000, message = "Описание не должно превышать 1000 символов")
    var description: String? = description

    /**
     * Внутреннее хранилище для rateLimitId.
     * Используется @JsonSetter для установки значения и флага rateLimitIdProvided.
     */
    @JsonIgnore
    private var _rateLimitId: UUID? = null

    /**
     * ID политики rate limiting.
     * Getter публичный для сериализации, setter только через @JsonSetter.
     */
    val rateLimitId: UUID?
        get() = _rateLimitId

    /**
     * Флаг, указывающий был ли rateLimitId явно передан в JSON.
     * Используется для различения "не передано" (partial update) от "передано null" (удаление).
     */
    @JsonIgnore
    var rateLimitIdProvided: Boolean = false
        private set

    /**
     * Jackson setter для rateLimitId.
     * Вызывается только когда поле присутствует в JSON, что позволяет
     * отличить "не передано" от "передано null".
     */
    @JsonSetter("rateLimitId")
    fun setRateLimitIdFromJson(value: UUID?) {
        this._rateLimitId = value
        this.rateLimitIdProvided = true
    }

    // === JWT Authentication Fields (Story 12.7) ===

    /**
     * Внутреннее хранилище для authRequired.
     */
    @JsonIgnore
    private var _authRequired: Boolean? = null

    /**
     * Требуется ли JWT аутентификация.
     */
    val authRequired: Boolean?
        get() = _authRequired

    /**
     * Флаг, указывающий был ли authRequired явно передан в JSON.
     */
    @JsonIgnore
    var authRequiredProvided: Boolean = false
        private set

    /**
     * Jackson setter для authRequired.
     */
    @JsonSetter("authRequired")
    fun setAuthRequiredFromJson(value: Boolean?) {
        this._authRequired = value
        this.authRequiredProvided = true
    }

    /**
     * Внутреннее хранилище для allowedConsumers.
     */
    @JsonIgnore
    private var _allowedConsumers: List<String>? = null

    /**
     * Whitelist consumer IDs.
     */
    val allowedConsumers: List<String>?
        get() = _allowedConsumers

    /**
     * Флаг, указывающий был ли allowedConsumers явно передан в JSON.
     */
    @JsonIgnore
    var allowedConsumersProvided: Boolean = false
        private set

    /**
     * Jackson setter для allowedConsumers.
     */
    @JsonSetter("allowedConsumers")
    fun setAllowedConsumersFromJson(value: List<String>?) {
        this._allowedConsumers = value
        this.allowedConsumersProvided = true
    }

    companion object {
        /**
         * Создаёт запрос с явно указанным rateLimitId для использования в тестах.
         * Эквивалентно получению JSON с полем "rateLimitId".
         */
        fun withRateLimitId(
            rateLimitId: UUID?,
            path: String? = null,
            upstreamUrl: String? = null,
            methods: List<String>? = null,
            description: String? = null
        ): UpdateRouteRequest {
            val request = UpdateRouteRequest(
                path = path,
                upstreamUrl = upstreamUrl,
                methods = methods,
                description = description
            )
            // Эмулируем поведение @JsonSetter
            request.setRateLimitIdFromJson(rateLimitId)
            return request
        }

        /**
         * Создаёт запрос с явно указанными auth полями для использования в тестах (Story 12.7).
         * Эквивалентно получению JSON с полями "authRequired" и/или "allowedConsumers".
         */
        fun withAuthFields(
            authRequired: Boolean? = null,
            allowedConsumers: List<String>? = null,
            path: String? = null,
            upstreamUrl: String? = null,
            methods: List<String>? = null,
            description: String? = null
        ): UpdateRouteRequest {
            val request = UpdateRouteRequest(
                path = path,
                upstreamUrl = upstreamUrl,
                methods = methods,
                description = description
            )
            if (authRequired != null) {
                request.setAuthRequiredFromJson(authRequired)
            }
            if (allowedConsumers != null) {
                request.setAllowedConsumersFromJson(allowedConsumers)
            }
            return request
        }
    }
}
