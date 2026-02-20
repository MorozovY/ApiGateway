package com.company.gateway.admin.repository

import com.company.gateway.common.model.AuditLog
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import java.util.UUID

/**
 * Репозиторий для работы с аудит-логами.
 *
 * Предоставляет доступ к записям аудит-лога.
 * Расширен custom методами с фильтрацией в Story 7.2.
 */
@Repository
interface AuditLogRepository : ReactiveCrudRepository<AuditLog, UUID>, AuditLogRepositoryCustom {

    /**
     * Получение записей аудит-лога по типу сущности и её ID.
     */
    fun findByEntityTypeAndEntityId(entityType: String, entityId: String): Flux<AuditLog>

    /**
     * Получение записей аудит-лога по ID пользователя.
     */
    fun findByUserId(userId: UUID): Flux<AuditLog>

    /**
     * Получение записей аудит-лога по действию.
     */
    fun findByAction(action: String): Flux<AuditLog>
}
