package com.company.gateway.admin.exception

/**
 * Исключение для конфликтов состояния ресурса (HTTP 409 Conflict).
 *
 * Выбрасывается когда операция не может быть выполнена из-за
 * текущего состояния ресурса (например, удаление не-draft маршрута).
 *
 * @param message Сообщение об ошибке (используется для логирования)
 * @param detail Детальное описание для RFC 7807 ответа
 */
class ConflictException(
    override val message: String,
    val detail: String = message
) : RuntimeException(message)
