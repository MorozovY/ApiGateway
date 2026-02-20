package com.company.gateway.admin.exception

/**
 * Исключение, выбрасываемое когда Prometheus недоступен.
 *
 * Обрабатывается GlobalExceptionHandler для возврата HTTP 503 Service Unavailable
 * с RFC 7807 форматом и retry-after header.
 *
 * @param message описание ошибки
 * @param cause оригинальное исключение (ConnectException, TimeoutException, etc.)
 */
class PrometheusUnavailableException(
    override val message: String,
    override val cause: Throwable? = null
) : RuntimeException(message, cause) {

    /** Рекомендуемый интервал повторной попытки в секундах */
    val retryAfterSeconds: Int = 30
}
