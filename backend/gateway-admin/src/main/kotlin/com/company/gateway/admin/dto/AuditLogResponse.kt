package com.company.gateway.admin.dto

import com.company.gateway.common.model.AuditLog
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

/**
 * DTO для ответа с данными аудит-лога.
 *
 * Преобразует внутреннюю модель AuditLog в формат API response.
 * Story 7.1, Task 6: AuditLogResponse DTO.
 */
data class AuditLogResponse(
    val id: UUID,
    val entityType: String,
    val entityId: String,
    val action: String,
    val user: UserInfo,
    val timestamp: Instant,
    val changes: Map<String, Any?>?,
    val ipAddress: String?,
    val correlationId: String?
) {
    /**
     * Информация о пользователе, выполнившем действие.
     */
    data class UserInfo(
        val id: UUID,
        val username: String
    )

    companion object {
        /**
         * Создаёт AuditLogResponse из AuditLog entity.
         *
         * @param auditLog исходная запись аудит-лога
         * @param objectMapper ObjectMapper для десериализации changes JSON
         * @return AuditLogResponse DTO
         */
        fun from(auditLog: AuditLog, objectMapper: ObjectMapper): AuditLogResponse {
            val changesMap = auditLog.changes?.let { changesJson ->
                try {
                    objectMapper.readValue(
                        changesJson,
                        object : TypeReference<Map<String, Any?>>() {}
                    )
                } catch (e: Exception) {
                    // Если не удалось распарсить JSON, возвращаем null
                    null
                }
            }

            return AuditLogResponse(
                id = requireNotNull(auditLog.id) { "AuditLog.id не может быть null при преобразовании в DTO" },
                entityType = auditLog.entityType,
                entityId = auditLog.entityId,
                action = auditLog.action,
                user = UserInfo(
                    id = auditLog.userId,
                    username = auditLog.username
                ),
                timestamp = requireNotNull(auditLog.createdAt) { "AuditLog.createdAt не может быть null при преобразовании в DTO" },
                changes = changesMap,
                ipAddress = auditLog.ipAddress,
                correlationId = auditLog.correlationId
            )
        }
    }
}
