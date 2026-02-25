package com.company.gateway.admin.dto

/**
 * Данные API consumer для response.
 *
 * Consumer — это Keycloak client с serviceAccountsEnabled=true,
 * используется для аутентификации внешних сервисов через Client Credentials flow.
 *
 * @property clientId Keycloak client_id (уникальный идентификатор consumer)
 * @property description описание consumer
 * @property enabled статус: true = Active, false = Disabled
 * @property createdTimestamp Unix timestamp создания в Keycloak (миллисекунды)
 * @property rateLimit rate limit настройки (если установлены)
 */
data class ConsumerResponse(
    val clientId: String,
    val description: String?,
    val enabled: Boolean,
    val createdTimestamp: Long,
    val rateLimit: ConsumerRateLimitResponse?
)
