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
    val updatedAt: Instant? = null,

    // === Approval Workflow Fields (Epic 4) ===

    /** Время отправки на согласование */
    @Column("submitted_at")
    val submittedAt: Instant? = null,

    /** ID пользователя, одобрившего маршрут */
    @Column("approved_by")
    val approvedBy: UUID? = null,

    /** Время одобрения */
    @Column("approved_at")
    val approvedAt: Instant? = null,

    /** ID пользователя, отклонившего маршрут */
    @Column("rejected_by")
    val rejectedBy: UUID? = null,

    /** Время отклонения */
    @Column("rejected_at")
    val rejectedAt: Instant? = null,

    /** Причина отклонения */
    @Column("rejection_reason")
    val rejectionReason: String? = null,

    // === Rate Limiting Fields (Epic 5) ===

    /** ID политики rate limiting, назначенной маршруту */
    @Column("rate_limit_id")
    val rateLimitId: UUID? = null,

    // === JWT Authentication Fields (Epic 12) ===

    /** Требуется ли JWT аутентификация для маршрута */
    @Column("auth_required")
    val authRequired: Boolean = true,

    /** Whitelist consumer IDs (null = все разрешены) */
    @Column("allowed_consumers")
    val allowedConsumers: List<String>? = null
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