package com.company.gateway.common.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

/**
 * Сущность маршрута API Gateway.
 *
 * Представляет конфигурацию маршрутизации запросов от клиентов к upstream сервисам.
 * Маршрут проходит через workflow: DRAFT → PENDING → PUBLISHED/REJECTED.
 *
 * @property id уникальный идентификатор маршрута (UUID)
 * @property path URL path для маршрутизации (уникальный, начинается с /)
 * @property upstreamUrl URL целевого сервиса
 * @property methods список разрешённых HTTP методов (GET, POST, PUT, DELETE, PATCH)
 * @property description описание маршрута (опционально, до 1000 символов)
 * @property status текущий статус маршрута в workflow
 * @property createdBy ID пользователя, создавшего маршрут
 * @property createdAt дата и время создания
 * @property updatedAt дата и время последнего обновления
 */
@Table("routes")
data class Route(
    @Id
    val id: UUID? = null,

    /** URL path для маршрутизации (уникальный, начинается с /) */
    val path: String,

    /** URL целевого сервиса (http:// или https://) */
    @Column("upstream_url")
    val upstreamUrl: String,

    /** Список разрешённых HTTP методов */
    val methods: List<String> = emptyList(),

    /** Описание маршрута (опционально) */
    val description: String? = null,

    /** Текущий статус маршрута в workflow */
    val status: RouteStatus = RouteStatus.DRAFT,

    /** ID пользователя, создавшего маршрут */
    @Column("created_by")
    val createdBy: UUID? = null,

    /** Дата и время создания */
    @Column("created_at")
    val createdAt: Instant? = null,

    /** Дата и время последнего обновления */
    @Column("updated_at")
    val updatedAt: Instant? = null
)

/**
 * Статусы маршрута в approval workflow.
 *
 * - DRAFT: черновик, доступен для редактирования владельцем
 * - PENDING: отправлен на review, ожидает одобрения Security
 * - PUBLISHED: одобрен и активен в Gateway
 * - REJECTED: отклонён Security, требует доработки
 */
enum class RouteStatus {
    DRAFT, PENDING, PUBLISHED, REJECTED
}