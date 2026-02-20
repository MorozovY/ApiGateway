package com.company.gateway.admin.repository

import com.company.gateway.admin.dto.AuditFilterRequest
import com.company.gateway.common.model.AuditLog
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Custom методы репозитория для работы с аудит-логами.
 *
 * Предоставляет методы с динамической фильтрацией и пагинацией.
 * Story 7.2: Audit Log API with Filtering.
 * Story 7.3: Route Change History API — findByEntityIdWithFilters.
 */
interface AuditLogRepositoryCustom {

    /**
     * Получение записей аудит-лога с фильтрацией и пагинацией.
     *
     * Поддерживает фильтрацию по: userId, action, entityType, dateFrom, dateTo.
     * Сортировка по created_at DESC (новые записи первыми).
     *
     * @param filter параметры фильтрации и пагинации
     * @return Flux записей аудит-лога
     */
    fun findAllWithFilters(filter: AuditFilterRequest): Flux<AuditLog>

    /**
     * Подсчёт количества записей с учётом фильтров.
     *
     * @param filter параметры фильтрации (offset/limit игнорируются)
     * @return Mono с общим количеством записей
     */
    fun countWithFilters(filter: AuditFilterRequest): Mono<Long>

    /**
     * Получение истории изменений для конкретной сущности.
     *
     * Story 7.3: Route Change History API.
     * Используется для получения истории маршрута по entityId.
     * Сортировка по created_at ASC (хронологический порядок — старые первыми).
     *
     * @param entityType тип сущности (например, "route")
     * @param entityId ID сущности
     * @param dateFrom начало периода (опционально)
     * @param dateTo конец периода (опционально)
     * @return Flux записей аудит-лога в хронологическом порядке
     */
    fun findByEntityIdWithFilters(
        entityType: String,
        entityId: String,
        dateFrom: java.time.LocalDate? = null,
        dateTo: java.time.LocalDate? = null
    ): Flux<AuditLog>
}
