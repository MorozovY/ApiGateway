package com.company.gateway.admin.controller

import com.company.gateway.admin.dto.MetricsPeriod
import com.company.gateway.admin.dto.MetricsSortBy
import com.company.gateway.admin.dto.MetricsSummaryDto
import com.company.gateway.admin.dto.RouteMetricsDto
import com.company.gateway.admin.dto.TopRouteDto
import com.company.gateway.admin.exception.ValidationException
import com.company.gateway.admin.security.SecurityContextUtils
import com.company.gateway.admin.service.MetricsService
import com.company.gateway.common.model.Role
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * REST контроллер для доступа к метрикам gateway.
 *
 * Предоставляет API для:
 * - Общей сводки метрик (AC1, AC2)
 * - Метрик по конкретному маршруту (AC3)
 * - Топ маршрутов по различным критериям (AC4)
 *
 * Все endpoints требуют аутентификации (AC5).
 */
@RestController
@RequestMapping("/api/v1/metrics")
class MetricsController(
    private val metricsService: MetricsService
) {
    private val logger = LoggerFactory.getLogger(MetricsController::class.java)

    /**
     * Получает общую сводку метрик gateway.
     *
     * GET /api/v1/metrics/summary
     * GET /api/v1/metrics/summary?period=1h
     *
     * @param period период для расчёта (5m, 15m, 1h, 6h, 24h). По умолчанию: 5m
     * @return агрегированные метрики
     */
    @GetMapping("/summary")
    fun getSummary(
        @RequestParam(defaultValue = "5m") period: String
    ): Mono<MetricsSummaryDto> {
        logger.debug("GET /metrics/summary?period={}", period)

        val metricsPeriod = validatePeriod(period)
        return metricsService.getSummary(metricsPeriod)
    }

    /**
     * Получает метрики для конкретного маршрута.
     *
     * GET /api/v1/metrics/routes/{routeId}
     * GET /api/v1/metrics/routes/{routeId}?period=1h
     *
     * @param routeId UUID маршрута
     * @param period период для расчёта. По умолчанию: 5m
     * @return метрики маршрута или 404 если маршрут не найден
     */
    @GetMapping("/routes/{routeId}")
    fun getRouteMetrics(
        @PathVariable routeId: UUID,
        @RequestParam(defaultValue = "5m") period: String
    ): Mono<RouteMetricsDto> {
        logger.debug("GET /metrics/routes/{}?period={}", routeId, period)

        val metricsPeriod = validatePeriod(period)
        return metricsService.getRouteMetrics(routeId, metricsPeriod)
    }

    /**
     * Получает топ маршрутов по указанному критерию.
     *
     * GET /api/v1/metrics/top-routes
     * GET /api/v1/metrics/top-routes?by=latency&limit=5
     *
     * Для роли DEVELOPER — автоматическая фильтрация по createdBy = currentUser.id (AC1 Story 6.5.1).
     * Для ролей ADMIN и SECURITY — без фильтрации, возвращаются все маршруты (AC2 Story 6.5.1).
     *
     * @param by критерий сортировки: requests, latency, errors. По умолчанию: requests
     * @param limit максимальное количество маршрутов (1-100). По умолчанию: 10
     * @return список топ-маршрутов
     */
    @GetMapping("/top-routes")
    fun getTopRoutes(
        @RequestParam(defaultValue = "requests") by: String,
        @RequestParam(defaultValue = "10") limit: Int
    ): Mono<List<TopRouteDto>> {
        logger.debug("GET /metrics/top-routes?by={}&limit={}", by, limit)

        val sortBy = validateSortBy(by)
        val validLimit = validateLimit(limit)

        // Получаем текущего пользователя для определения фильтрации
        return SecurityContextUtils.currentUser()
            .switchIfEmpty(Mono.error(
                IllegalStateException("Пользователь не аутентифицирован — SecurityContext пуст")
            ))
            .flatMap { user ->
                // Для developer фильтруем по владельцу, для admin/security — без фильтра
                val ownerId = if (user.role == Role.DEVELOPER) {
                    user.userId
                } else {
                    null
                }
                logger.debug("Фильтрация top-routes: role={}, ownerId={}", user.role, ownerId)
                metricsService.getTopRoutes(sortBy, validLimit, ownerId)
            }
    }

    /**
     * Валидирует параметр period.
     *
     * @throws ValidationException если period невалиден
     */
    private fun validatePeriod(period: String): MetricsPeriod {
        return MetricsPeriod.fromString(period)
            ?: throw ValidationException(
                "Invalid period: $period",
                "Невалидное значение period: $period. Допустимые значения: ${MetricsPeriod.VALID_VALUES.joinToString()}"
            )
    }

    /**
     * Валидирует параметр sortBy (by).
     *
     * @throws ValidationException если sortBy невалиден
     */
    private fun validateSortBy(by: String): MetricsSortBy {
        return MetricsSortBy.fromString(by)
            ?: throw ValidationException(
                "Invalid sort by: $by",
                "Невалидное значение by: $by. Допустимые значения: ${MetricsSortBy.VALID_VALUES.joinToString()}"
            )
    }

    /**
     * Валидирует параметр limit.
     *
     * @return limit в диапазоне 1-100
     */
    private fun validateLimit(limit: Int): Int {
        return when {
            limit < 1 -> 1
            limit > 100 -> 100
            else -> limit
        }
    }
}
