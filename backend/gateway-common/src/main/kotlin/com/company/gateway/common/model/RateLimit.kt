package com.company.gateway.common.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

/**
 * Сущность политики rate limiting.
 *
 * Определяет конфигурацию ограничения запросов для маршрутов.
 * Используется как reusable конфигурация, которую можно назначить
 * нескольким маршрутам (Epic 5).
 *
 * @property id уникальный идентификатор политики (UUID)
 * @property name уникальное имя политики
 * @property description описание политики (опционально)
 * @property requestsPerSecond лимит запросов в секунду (> 0)
 * @property burstSize максимальный размер burst (>= requestsPerSecond)
 * @property createdBy ID пользователя, создавшего политику
 * @property createdAt дата и время создания
 * @property updatedAt дата и время последнего обновления
 */
@Table("rate_limits")
data class RateLimit(
    @Id
    val id: UUID? = null,

    /** Уникальное имя политики */
    val name: String,

    /** Описание политики (опционально) */
    val description: String? = null,

    /** Лимит запросов в секунду */
    @Column("requests_per_second")
    val requestsPerSecond: Int,

    /** Максимальный размер burst */
    @Column("burst_size")
    val burstSize: Int,

    /** ID пользователя, создавшего политику */
    @Column("created_by")
    val createdBy: UUID,

    /** Дата и время создания */
    @Column("created_at")
    val createdAt: Instant? = null,

    /** Дата и время последнего обновления */
    @Column("updated_at")
    val updatedAt: Instant? = null
)
