package com.company.gateway.admin.exception

/**
 * Исключение для ошибок авторизации (HTTP 403 Forbidden).
 *
 * Выбрасывается когда аутентифицированный пользователь не имеет
 * достаточных прав для выполнения запрошенной операции.
 *
 * @param message Сообщение об ошибке (используется для логирования)
 * @param detail Детальное описание для RFC 7807 ответа
 */
class AccessDeniedException(
    override val message: String,
    val detail: String = message
) : RuntimeException(message)
