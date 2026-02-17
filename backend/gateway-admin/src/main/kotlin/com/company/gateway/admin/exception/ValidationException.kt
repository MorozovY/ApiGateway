package com.company.gateway.admin.exception

/**
 * Исключение для ошибок валидации входных параметров (HTTP 400 Bad Request).
 *
 * Выбрасывается когда входные параметры не проходят валидацию
 * (невалидный формат, выход за пределы допустимых значений и т.д.).
 *
 * @param message Сообщение об ошибке (используется для логирования)
 * @param detail Детальное описание для RFC 7807 ответа
 */
class ValidationException(
    override val message: String,
    val detail: String = message
) : RuntimeException(message)
