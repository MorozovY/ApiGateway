package com.company.gateway.admin.controller

import com.company.gateway.admin.dto.HealthResponse
import com.company.gateway.admin.service.HealthService
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * REST контроллер для проверки здоровья сервисов.
 *
 * Предоставляет endpoint для получения статуса всех компонентов системы:
 * - gateway-core
 * - gateway-admin
 * - PostgreSQL
 * - Redis
 *
 * Story 8.1: Health Check на странице Metrics
 */
@RestController
@RequestMapping("/api/v1/health")
class HealthController(
    private val healthService: HealthService
) {
    private val logger = LoggerFactory.getLogger(HealthController::class.java)

    /**
     * Получает статус здоровья всех сервисов.
     *
     * GET /api/v1/health/services
     *
     * Возвращает статус каждого сервиса (UP/DOWN) с timestamp последней проверки.
     * При ошибке подключения к сервису возвращается DOWN с описанием ошибки.
     *
     * @return Mono<HealthResponse> со списком статусов сервисов
     */
    @GetMapping("/services")
    fun getServicesHealth(): Mono<HealthResponse> {
        logger.debug("GET /api/v1/health/services")
        return healthService.getServicesHealth()
    }
}
