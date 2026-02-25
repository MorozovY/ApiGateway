package com.company.gateway.admin.dto

/**
 * Response после ротации client secret.
 *
 * ВАЖНО: Secret показывается ТОЛЬКО один раз, старый secret становится невалидным.
 *
 * @property clientId client ID
 * @property secret новый client secret
 * @property message предупреждение о необходимости сохранить secret
 */
data class RotateSecretResponse(
    val clientId: String,
    val secret: String,
    val message: String = "Сохраните новый secret сейчас. Старый secret более недействителен."
)
