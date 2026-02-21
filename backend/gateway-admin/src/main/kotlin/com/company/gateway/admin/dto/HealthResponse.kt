package com.company.gateway.admin.dto

import java.time.Instant

/**
 * Статус здоровья отдельного сервиса.
 *
 * @property name имя сервиса (gateway-core, gateway-admin, postgresql, redis, prometheus, grafana)
 * @property status статус сервиса (UP или DOWN)
 * @property lastCheck время последней проверки
 * @property details дополнительная информация или сообщение об ошибке (nullable)
 */
data class ServiceHealthDto(
    val name: String,
    val status: ServiceStatus,
    val lastCheck: Instant,
    val details: String? = null
)

/**
 * Статус сервиса: UP или DOWN.
 */
enum class ServiceStatus {
    UP, DOWN
}

/**
 * Ответ API со статусами всех сервисов.
 *
 * Используется для GET /api/v1/health/services
 *
 * @property services список статусов всех проверяемых сервисов
 * @property timestamp время формирования ответа
 */
data class HealthResponse(
    val services: List<ServiceHealthDto>,
    val timestamp: Instant
) {
    companion object {
        /**
         * Создаёт HealthResponse из списка статусов сервисов.
         *
         * @param services список статусов сервисов
         */
        fun from(services: List<ServiceHealthDto>): HealthResponse {
            return HealthResponse(
                services = services,
                timestamp = Instant.now()
            )
        }
    }
}
