package com.company.gateway.common.model

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

/**
 * Запись аудит-лога.
 *
 * Хранит информацию об изменениях сущностей в системе.
 * Используется для отслеживания действий пользователей (AC3 Story 2.6, Epic 7).
 */
@Table("audit_logs")
data class AuditLog(
    @Id
    val id: UUID? = null,

    /** Тип сущности (user, route, rate_limit) */
    @Column("entity_type")
    val entityType: String,

    /** ID изменённой сущности */
    @Column("entity_id")
    val entityId: String,

    /** Действие (created, updated, deleted, role_changed) */
    @Column("action")
    val action: String,

    /** ID пользователя, выполнившего действие */
    @Column("user_id")
    val userId: UUID,

    /** Username пользователя для удобства просмотра */
    @Column("username")
    val username: String,

    /** JSON с изменёнными полями */
    @Column("changes")
    val changes: String? = null,

    @CreatedDate
    @Column("created_at")
    val createdAt: Instant? = null
)
