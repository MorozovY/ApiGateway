package com.company.gateway.admin.controller

import com.company.gateway.admin.dto.DashboardSummaryDto
import com.company.gateway.admin.dto.RecentActivityDto
import com.company.gateway.admin.security.RequireRole
import com.company.gateway.admin.security.SecurityContextUtils
import com.company.gateway.admin.service.DashboardService
import com.company.gateway.common.model.Role
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * REST контроллер для Dashboard данных.
 *
 * Предоставляет API для:
 * - Сводки системы (маршруты по статусам, pending approvals, health)
 * - Последних действий (recent activity)
 *
 * Данные фильтруются в зависимости от роли пользователя:
 * - DEVELOPER: видит только свои данные
 * - SECURITY: видит все маршруты + pending approvals
 * - ADMIN: видит всё + users count, consumers count, health status
 *
 * Story 16.2: Наполнение Dashboard полезным контентом
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Dashboard API для сводки системы и последних действий")
class DashboardController(
    private val dashboardService: DashboardService
) {
    private val logger = LoggerFactory.getLogger(DashboardController::class.java)

    /**
     * Получает сводку для Dashboard.
     *
     * GET /api/v1/dashboard/summary
     *
     * Возвращает статистику в зависимости от роли:
     * - DEVELOPER: количество своих маршрутов по статусам
     * - SECURITY: все маршруты + количество pending approvals
     * - ADMIN: всё + totalUsers, totalConsumers, systemHealth
     *
     * @return DashboardSummaryDto
     */
    @GetMapping("/summary")
    @RequireRole(Role.DEVELOPER)
    @Operation(
        summary = "Сводка Dashboard",
        description = "Возвращает сводку системы. Данные фильтруются по роли пользователя."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Сводка получена"),
        ApiResponse(responseCode = "401", description = "Не аутентифицирован")
    )
    fun getSummary(): Mono<ResponseEntity<DashboardSummaryDto>> {
        logger.debug("GET /api/v1/dashboard/summary")

        return SecurityContextUtils.currentUser()
            .flatMap { user ->
                dashboardService.getSummary(user)
            }
            .map { ResponseEntity.ok(it) }
    }

    /**
     * Получает последние действия для Dashboard.
     *
     * GET /api/v1/dashboard/recent-activity
     * GET /api/v1/dashboard/recent-activity?limit=10
     *
     * Фильтрация по роли:
     * - DEVELOPER: только свои действия с маршрутами
     * - SECURITY: только approve/reject действия
     * - ADMIN: все действия
     *
     * @param limit максимальное количество записей (default: 5, max: 20)
     * @return RecentActivityDto
     */
    @GetMapping("/recent-activity")
    @RequireRole(Role.DEVELOPER)
    @Operation(
        summary = "Последние действия",
        description = "Возвращает последние действия пользователя. Фильтруется по роли."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Список действий получен"),
        ApiResponse(responseCode = "401", description = "Не аутентифицирован")
    )
    fun getRecentActivity(
        @Parameter(description = "Максимальное количество записей (default: 5, max: 20)")
        @RequestParam(defaultValue = "5") limit: Int
    ): Mono<ResponseEntity<RecentActivityDto>> {
        logger.debug("GET /api/v1/dashboard/recent-activity?limit={}", limit)

        return SecurityContextUtils.currentUser()
            .flatMap { user ->
                dashboardService.getRecentActivity(user, limit)
            }
            .map { ResponseEntity.ok(it) }
    }
}
