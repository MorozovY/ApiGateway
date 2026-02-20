package com.company.gateway.admin.service

import com.company.gateway.admin.repository.AuditLogRepository
import com.company.gateway.admin.security.AuditContextFilter.Companion.AUDIT_CORRELATION_ID_KEY
import com.company.gateway.admin.security.AuditContextFilter.Companion.AUDIT_IP_ADDRESS_KEY
import com.company.gateway.common.model.AuditLog
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
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
     * @param action действие (created, updated, deleted, role_changed, approved, rejected, published)
     * @param userId ID пользователя, выполнившего действие
     * @param username имя пользователя
     * @param changes карта изменений (oldValue, newValue)
     * @param ipAddress IP адрес клиента (Story 7.1, AC3)
     * @param correlationId Correlation ID запроса (Story 7.1, AC3)
     * @return Mono<AuditLog> сохранённая запись
     */
    fun log(
        entityType: String,
        entityId: String,
        action: String,
        userId: UUID,
        username: String,
        changes: Map<String, Any?>? = null,
        ipAddress: String? = null,
        correlationId: String? = null
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
            changes = changesJson,
            ipAddress = ipAddress,
            correlationId = correlationId
        )

        return auditLogRepository.save(auditLog)
            .doOnSuccess {
                logger.info(
                    "Аудит-лог: action={}, entityType={}, entityId={}, userId={}, correlationId={}",
                    action, entityType, entityId, userId, correlationId
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
     * Асинхронная запись аудит-лога с graceful degradation.
     *
     * Используется для fire-and-forget записи, которая не блокирует
     * основную операцию и не пропагирует ошибки к вызывающему коду.
     * (Story 7.1, AC5, AC6)
     *
     * @param entityType тип сущности
     * @param entityId ID сущности
     * @param action действие
     * @param userId ID пользователя
     * @param username имя пользователя
     * @param changes карта изменений
     * @param ipAddress IP адрес клиента
     * @param correlationId Correlation ID запроса
     */
    fun logAsync(
        entityType: String,
        entityId: String,
        action: String,
        userId: UUID,
        username: String,
        changes: Map<String, Any?>? = null,
        ipAddress: String? = null,
        correlationId: String? = null
    ) {
        log(entityType, entityId, action, userId, username, changes, ipAddress, correlationId)
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume { e ->
                logger.warn(
                    "Ошибка асинхронной записи аудит-лога: action={}, entityId={}, error={}",
                    action, entityId, e.message
                )
                Mono.empty()
            }
            .subscribe()
    }

    /**
     * Записать событие создания сущности.
     */
    fun logCreated(
        entityType: String,
        entityId: String,
        userId: UUID,
        username: String,
        entity: Any? = null,
        ipAddress: String? = null,
        correlationId: String? = null
    ): Mono<AuditLog> {
        val changes = entity?.let { mapOf("created" to it) }
        return log(entityType, entityId, "created", userId, username, changes, ipAddress, correlationId)
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
        newValues: Map<String, Any?>,
        ipAddress: String? = null,
        correlationId: String? = null
    ): Mono<AuditLog> {
        val changes = mapOf("old" to oldValues, "new" to newValues)
        return log(entityType, entityId, "updated", userId, username, changes, ipAddress, correlationId)
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
        performedByUsername: String,
        ipAddress: String? = null,
        correlationId: String? = null
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
            changes = changes,
            ipAddress = ipAddress,
            correlationId = correlationId
        )
    }

    /**
     * Записать событие удаления/деактивации сущности.
     */
    fun logDeleted(
        entityType: String,
        entityId: String,
        userId: UUID,
        username: String,
        ipAddress: String? = null,
        correlationId: String? = null
    ): Mono<AuditLog> {
        return log(entityType, entityId, "deleted", userId, username, null, ipAddress, correlationId)
    }

    /**
     * Записать событие одобрения маршрута (Story 7.1, AC2).
     */
    fun logApproved(
        entityType: String,
        entityId: String,
        userId: UUID,
        username: String,
        changes: Map<String, Any?>? = null,
        ipAddress: String? = null,
        correlationId: String? = null
    ): Mono<AuditLog> {
        return log(entityType, entityId, "approved", userId, username, changes, ipAddress, correlationId)
    }

    /**
     * Записать событие отклонения маршрута (Story 7.1, AC2).
     */
    fun logRejected(
        entityType: String,
        entityId: String,
        userId: UUID,
        username: String,
        changes: Map<String, Any?>? = null,
        ipAddress: String? = null,
        correlationId: String? = null
    ): Mono<AuditLog> {
        return log(entityType, entityId, "rejected", userId, username, changes, ipAddress, correlationId)
    }

    /**
     * Записать событие публикации маршрута (Story 7.1, AC4).
     */
    fun logPublished(
        entityType: String,
        entityId: String,
        userId: UUID,
        username: String,
        changes: Map<String, Any?>? = null,
        ipAddress: String? = null,
        correlationId: String? = null
    ): Mono<AuditLog> {
        return log(entityType, entityId, "published", userId, username, changes, ipAddress, correlationId)
    }

    /**
     * Записать событие аудит-лога с автоматическим извлечением IP и correlationId из Reactor Context.
     *
     * Использует данные, сохранённые в Context через AuditContextFilter.
     * (Story 7.1, AC3)
     *
     * @param entityType тип сущности (user, route, rate_limit)
     * @param entityId ID сущности
     * @param action действие (created, updated, deleted, approved, rejected, published)
     * @param userId ID пользователя, выполнившего действие
     * @param username имя пользователя
     * @param changes карта изменений
     * @return Mono<AuditLog> сохранённая запись
     */
    fun logWithContext(
        entityType: String,
        entityId: String,
        action: String,
        userId: UUID,
        username: String,
        changes: Map<String, Any?>? = null
    ): Mono<AuditLog> {
        return Mono.deferContextual { ctx ->
            val ipAddress = ctx.getOrDefault<String>(AUDIT_IP_ADDRESS_KEY, null)
            val correlationId = ctx.getOrDefault<String>(AUDIT_CORRELATION_ID_KEY, null)
            log(entityType, entityId, action, userId, username, changes, ipAddress, correlationId)
        }
    }

    /**
     * Асинхронная запись аудит-лога с извлечением IP и correlationId из Reactor Context.
     *
     * Версия logWithContext() с graceful degradation для fire-and-forget записи.
     * (Story 7.1, AC3, AC5, AC6)
     *
     * @param entityType тип сущности
     * @param entityId ID сущности
     * @param action действие
     * @param userId ID пользователя
     * @param username имя пользователя
     * @param changes карта изменений
     * @return Mono<Void> — можно цеплять через flatMap для сохранения context
     */
    fun logWithContextAsync(
        entityType: String,
        entityId: String,
        action: String,
        userId: UUID,
        username: String,
        changes: Map<String, Any?>? = null
    ): Mono<Void> {
        return Mono.deferContextual { ctx ->
            val ipAddress = ctx.getOrDefault<String>(AUDIT_IP_ADDRESS_KEY, null)
            val correlationId = ctx.getOrDefault<String>(AUDIT_CORRELATION_ID_KEY, null)

            log(entityType, entityId, action, userId, username, changes, ipAddress, correlationId)
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume { e ->
                    logger.warn(
                        "Ошибка асинхронной записи аудит-лога: action={}, entityId={}, error={}",
                        action, entityId, e.message
                    )
                    Mono.empty()
                }
                .then()
        }
    }
}
