package com.company.gateway.admin.service

import com.company.gateway.admin.repository.AuditLogRepository
import com.company.gateway.common.model.AuditLog
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Сервис для записи аудит-логов.
 *
 * Используется для фиксации изменений сущностей в системе.
 * Реализует требование AC3 Story 2.6 (аудит при смене роли).
 * Полная реализация с поиском и фильтрацией будет добавлена в Epic 7.
 */
@Service
class AuditService(
    private val auditLogRepository: AuditLogRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(AuditService::class.java)

    /**
     * Записать событие аудит-лога.
     *
     * @param entityType тип сущности (user, route, rate_limit)
     * @param entityId ID сущности
     * @param action действие (created, updated, deleted, role_changed)
     * @param userId ID пользователя, выполнившего действие
     * @param username имя пользователя
     * @param changes карта изменений (oldValue, newValue)
     * @return Mono<AuditLog> сохранённая запись
     */
    fun log(
        entityType: String,
        entityId: String,
        action: String,
        userId: UUID,
        username: String,
        changes: Map<String, Any?>? = null
    ): Mono<AuditLog> {
        val changesJson = changes?.let {
            try {
                objectMapper.writeValueAsString(it)
            } catch (e: Exception) {
                logger.warn("Ошибка сериализации changes для аудит-лога: {}", e.message)
                null
            }
        }

        val auditLog = AuditLog(
            entityType = entityType,
            entityId = entityId,
            action = action,
            userId = userId,
            username = username,
            changes = changesJson
        )

        return auditLogRepository.save(auditLog)
            .doOnSuccess {
                logger.info(
                    "Аудит-лог: action={}, entityType={}, entityId={}, userId={}",
                    action, entityType, entityId, userId
                )
            }
            .doOnError { e ->
                logger.error(
                    "Ошибка записи аудит-лога: action={}, entityType={}, entityId={}, error={}",
                    action, entityType, entityId, e.message
                )
            }
    }

    /**
     * Записать событие создания сущности.
     */
    fun logCreated(
        entityType: String,
        entityId: String,
        userId: UUID,
        username: String,
        entity: Any? = null
    ): Mono<AuditLog> {
        val changes = entity?.let { mapOf("created" to it) }
        return log(entityType, entityId, "created", userId, username, changes)
    }

    /**
     * Записать событие обновления сущности.
     */
    fun logUpdated(
        entityType: String,
        entityId: String,
        userId: UUID,
        username: String,
        oldValues: Map<String, Any?>,
        newValues: Map<String, Any?>
    ): Mono<AuditLog> {
        val changes = mapOf("old" to oldValues, "new" to newValues)
        return log(entityType, entityId, "updated", userId, username, changes)
    }

    /**
     * Записать событие смены роли пользователя (AC3 Story 2.6).
     */
    fun logRoleChanged(
        targetUserId: UUID,
        targetUsername: String,
        oldRole: String,
        newRole: String,
        performedByUserId: UUID,
        performedByUsername: String
    ): Mono<AuditLog> {
        val changes = mapOf(
            "oldRole" to oldRole,
            "newRole" to newRole,
            "targetUsername" to targetUsername
        )
        return log(
            entityType = "user",
            entityId = targetUserId.toString(),
            action = "role_changed",
            userId = performedByUserId,
            username = performedByUsername,
            changes = changes
        )
    }

    /**
     * Записать событие удаления/деактивации сущности.
     */
    fun logDeleted(
        entityType: String,
        entityId: String,
        userId: UUID,
        username: String
    ): Mono<AuditLog> {
        return log(entityType, entityId, "deleted", userId, username)
    }
}
