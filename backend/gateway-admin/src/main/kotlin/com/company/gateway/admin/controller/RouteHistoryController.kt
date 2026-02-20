package com.company.gateway.admin.controller

import com.company.gateway.admin.dto.RouteHistoryResponse
import com.company.gateway.admin.security.RequireRole
import com.company.gateway.admin.service.RouteHistoryService
import com.company.gateway.common.model.Role
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.util.UUID

/**
 * Контроллер для получения истории изменений маршрутов.
 *
 * Story 7.3: Route Change History API (FR23).
 *
 * Предоставляет API для просмотра хронологии изменений маршрута,
 * включая создание, обновления, согласование и публикацию.
 *
 * Доступ: только SECURITY и ADMIN роли.
 */
@RestController
@RequestMapping("/api/v1/routes")
class RouteHistoryController(
    private val routeHistoryService: RouteHistoryService
) {
    /**
     * Получение истории изменений маршрута.
     *
     * AC1: Возвращает хронологический список всех изменений маршрута.
     * AC3: Возвращает 404 для несуществующего маршрута.
     * AC4: Поддерживает фильтрацию по диапазону дат (from/to).
     * AC5: Записи отсортированы по времени (oldest first).
     *
     * @param routeId UUID маршрута
     * @param from начало периода (опционально, формат: yyyy-MM-dd)
     * @param to конец периода (опционально, формат: yyyy-MM-dd)
     * @return история изменений маршрута
     */
    @GetMapping("/{routeId}/history")
    @RequireRole(Role.SECURITY)
    fun getRouteHistory(
        @PathVariable routeId: UUID,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?
    ): Mono<ResponseEntity<RouteHistoryResponse>> {
        return routeHistoryService.getRouteHistory(routeId, from, to)
            .map { ResponseEntity.ok(it) }
    }
}
