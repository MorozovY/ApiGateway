package com.company.gateway.common

/**
 * Общие константы проекта ApiGateway.
 */
object Constants {

    /**
     * HTTP заголовок для трассировки запросов.
     * Используется во всех error responses и логах.
     */
    const val CORRELATION_ID_HEADER = "X-Correlation-ID"

    /**
     * Имя cookie для хранения JWT токена аутентификации.
     */
    const val AUTH_COOKIE_NAME = "auth_token"
}
