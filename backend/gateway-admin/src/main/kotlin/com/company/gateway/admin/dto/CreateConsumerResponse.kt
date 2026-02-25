package com.company.gateway.admin.dto

/**
 * Response после создания consumer с client secret.
 *
 * ВАЖНО: Secret показывается ТОЛЬКО один раз и не может быть прочитан позже.
 *
 * @property clientId созданный client ID
 * @property secret client secret (показывается только один раз)
 * @property message предупреждение о необходимости сохранить secret
 */
data class CreateConsumerResponse(
    val clientId: String,
    val secret: String,
    val message: String = "Сохраните этот secret сейчас. Он больше не будет показан."
)
