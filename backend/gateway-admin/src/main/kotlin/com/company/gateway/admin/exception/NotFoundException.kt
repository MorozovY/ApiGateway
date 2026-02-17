package com.company.gateway.admin.exception

/**
 * Исключение для отсутствующих ресурсов (HTTP 404 Not Found).
 *
 * Выбрасывается когда запрашиваемый ресурс не найден в системе.
 *
 * @param message Сообщение об ошибке (используется для логирования)
 * @param detail Детальное описание для RFC 7807 ответа
 */
class NotFoundException(
    override val message: String,
    val detail: String = message
) : RuntimeException(message)
