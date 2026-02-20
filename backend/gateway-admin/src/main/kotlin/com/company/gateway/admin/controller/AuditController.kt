package com.company.gateway.admin.controller

import com.company.gateway.admin.dto.AuditFilterRequest
import com.company.gateway.admin.dto.AuditLogResponse
import com.company.gateway.admin.dto.PagedResponse
import com.company.gateway.admin.exception.ValidationException
import com.company.gateway.admin.security.RequireRole
import com.company.gateway.admin.service.AuditService
import com.company.gateway.common.model.Role
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.util.UUID

/**
 * Контроллер для работы с audit логами.
 *
 * Доступен только для SECURITY и ADMIN ролей (AC7).
 * Story 7.2: Audit Log API with Filtering.
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequireRole(Role.SECURITY)
class AuditController(
    private val auditService: AuditService
) {

    /**
     * Получение списка audit log записей с фильтрацией и пагинацией.
     *
     * Поддерживаемые фильтры:
     * - userId: фильтр по ID пользователя (AC2)
     * - action: фильтр по действию (AC3)
     * - entityType: фильтр по типу сущности (AC4)
     * - dateFrom/dateTo: фильтр по диапазону дат (AC5)
     *
     * Фильтры комбинируются с AND логикой (AC6).
     * Сортировка: timestamp DESC (новые записи первыми) (AC1).
     *
     * Доступно только SECURITY и ADMIN ролям (AC7).
     */
    @GetMapping
    fun listAuditLogs(
        @RequestParam(required = false) userId: UUID?,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) entityType: String?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        dateFrom: LocalDate?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        dateTo: LocalDate?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int
    ): Mono<ResponseEntity<PagedResponse<AuditLogResponse>>> {
        // Валидация параметров пагинации
        if (offset < 0) {
            return Mono.error(ValidationException("Параметр offset должен быть >= 0"))
        }
        if (limit < 1 || limit > AuditFilterRequest.MAX_LIMIT) {
            return Mono.error(
                ValidationException("Параметр limit должен быть от 1 до ${AuditFilterRequest.MAX_LIMIT}")
            )
        }

        // Валидация диапазона дат (M2: dateFrom должен быть <= dateTo)
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            return Mono.error(ValidationException("Параметр dateFrom не может быть позже dateTo"))
        }

        val filter = AuditFilterRequest(
            userId = userId,
            action = action,
            entityType = entityType,
            dateFrom = dateFrom,
            dateTo = dateTo,
            offset = offset,
            limit = limit
        )

        return auditService.findAll(filter)
            .map { ResponseEntity.ok(it) }
    }
}
