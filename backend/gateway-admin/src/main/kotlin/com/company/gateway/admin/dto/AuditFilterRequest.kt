package com.company.gateway.admin.dto

import java.time.LocalDate
import java.util.UUID

/**
 * Параметры фильтрации и пагинации для списка audit logs.
 *
 * Story 7.2: Audit Log API with Filtering.
 *
 * @property userId фильтр по ID пользователя (AC2)
 * @property action фильтр по действию (AC3)
 * @property entityType фильтр по типу сущности (AC4)
 * @property dateFrom начало диапазона дат (AC5)
 * @property dateTo конец диапазона дат (AC5)
 * @property offset смещение для пагинации
 * @property limit количество элементов на странице
 */
data class AuditFilterRequest(
    val userId: UUID? = null,
    val action: String? = null,
    val entityType: String? = null,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val offset: Int = DEFAULT_OFFSET,
    val limit: Int = DEFAULT_LIMIT
) {
    companion object {
        /** Значение offset по умолчанию */
        const val DEFAULT_OFFSET = 0

        /** Значение limit по умолчанию */
        const val DEFAULT_LIMIT = 50

        /** Максимальное значение limit */
        const val MAX_LIMIT = 100
    }
}
