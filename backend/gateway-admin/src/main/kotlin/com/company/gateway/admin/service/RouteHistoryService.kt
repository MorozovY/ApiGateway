package com.company.gateway.admin.service

import com.company.gateway.admin.dto.HistoryEntry
import com.company.gateway.admin.dto.RouteHistoryResponse
import com.company.gateway.admin.dto.RouteHistoryUserInfo
import com.company.gateway.admin.exception.NotFoundException
import com.company.gateway.admin.exception.ValidationException
import com.company.gateway.admin.repository.AuditLogRepository
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.common.model.AuditLog
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.util.UUID

/**
 * Сервис для получения истории изменений маршрутов.
 *
 * Story 7.3: Route Change History API (FR23).
 *
 * Предоставляет хронологический список всех изменений маршрута,
 * включая создание, обновления, отправку на согласование,
 * одобрение/отклонение и публикацию.
 */
@Service
class RouteHistoryService(
    private val routeRepository: RouteRepository,
    private val auditLogRepository: AuditLogRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(RouteHistoryService::class.java)

    companion object {
        /** Тип сущности для маршрутов в audit_logs */
        const val ENTITY_TYPE_ROUTE = "route"
    }

    /**
     * Получает историю изменений маршрута.
     *
     * @param routeId ID маршрута
     * @param from начало периода (опционально)
     * @param to конец периода (опционально)
     * @return история изменений маршрута
     * @throws NotFoundException если маршрут не существует
     */
    fun getRouteHistory(
        routeId: UUID,
        from: LocalDate? = null,
        to: LocalDate? = null
    ): Mono<RouteHistoryResponse> {
        // M2: Валидация диапазона дат — from должен быть <= to
        if (from != null && to != null && from.isAfter(to)) {
            return Mono.error(
                ValidationException("Некорректный диапазон дат: from ($from) не может быть позже to ($to)")
            )
        }

        return routeRepository.findById(routeId)
            .switchIfEmpty(
                Mono.error(NotFoundException("Маршрут не найден: $routeId"))
            )
            .flatMap { route ->
                auditLogRepository.findByEntityIdWithFilters(
                    entityType = ENTITY_TYPE_ROUTE,
                    entityId = routeId.toString(),
                    dateFrom = from,
                    dateTo = to
                )
                    .map { it.toHistoryEntry() }
                    .collectList()
                    .map { history ->
                        RouteHistoryResponse(
                            routeId = routeId,
                            currentPath = route.path,
                            history = history
                        )
                    }
            }
    }

    /**
     * Преобразует AuditLog в HistoryEntry.
     *
     * Маппинг полей:
     * - createdAt → timestamp
     * - action → action
     * - userId/username → user
     * - changes (JSON string) → changes (JsonNode)
     */
    private fun AuditLog.toHistoryEntry(): HistoryEntry {
        val changesNode: JsonNode? = this.changes?.let { changesJson ->
            try {
                objectMapper.readTree(changesJson)
            } catch (e: Exception) {
                // M3: Логируем ошибку парсинга JSON для диагностики проблем с данными
                logger.warn(
                    "Ошибка парсинга JSON changes для audit log: entityId={}, action={}, error={}",
                    this.entityId, this.action, e.message
                )
                null
            }
        }

        return HistoryEntry(
            timestamp = requireNotNull(this.createdAt) {
                "AuditLog.createdAt не может быть null"
            },
            action = this.action,
            user = RouteHistoryUserInfo(
                id = this.userId,
                username = this.username
            ),
            changes = changesNode
        )
    }
}
