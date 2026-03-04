package com.company.gateway.admin.service

import com.company.gateway.admin.dto.ActivityItem
import com.company.gateway.admin.dto.DashboardSummaryDto
import com.company.gateway.admin.dto.RecentActivityDto
import com.company.gateway.admin.dto.ServiceStatus
import com.company.gateway.admin.repository.AuditLogRepository
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.admin.security.AuthenticatedUser
import com.company.gateway.common.model.Role
import com.company.gateway.common.model.RouteStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

/**
 * Сервис для Dashboard данных.
 *
 * Предоставляет сводку системы и последние действия с учётом роли пользователя:
 * - DEVELOPER: видит только свои маршруты
 * - SECURITY: видит все маршруты + pending approvals
 * - ADMIN: видит всё + users count, consumers count, health status
 *
 * Story 16.2: Наполнение Dashboard полезным контентом
 */
@Service
class DashboardService(
    private val routeRepository: RouteRepository,
    private val userRepository: UserRepository,
    private val auditLogRepository: AuditLogRepository,
    private val healthService: HealthService,
    private val databaseClient: DatabaseClient,
    @Autowired(required = false)
    private val consumerService: ConsumerService?
) {
    private val logger = LoggerFactory.getLogger(DashboardService::class.java)

    companion object {
        const val DEFAULT_ACTIVITY_LIMIT = 5
        const val MAX_ACTIVITY_LIMIT = 20
    }

    /**
     * Получает сводку для Dashboard.
     *
     * Возвращает данные в зависимости от роли пользователя:
     * - DEVELOPER: статистика только своих маршрутов
     * - SECURITY: статистика всех маршрутов + pending count
     * - ADMIN: всё + totalUsers, totalConsumers, systemHealth
     *
     * @param user текущий пользователь
     * @return Mono<DashboardSummaryDto>
     */
    fun getSummary(user: AuthenticatedUser): Mono<DashboardSummaryDto> {
        logger.debug("Получение dashboard summary для user={}, role={}", user.username, user.role)

        return when (user.role) {
            Role.DEVELOPER -> getDeveloperSummary(user)
            Role.SECURITY -> getSecuritySummary()
            Role.ADMIN -> getAdminSummary()
        }
    }

    /**
     * Сводка для Developer — только свои маршруты.
     */
    private fun getDeveloperSummary(user: AuthenticatedUser): Mono<DashboardSummaryDto> {
        return countRoutesByStatusForUser(user.userId)
            .map { routesByStatus ->
                DashboardSummaryDto(
                    routesByStatus = routesByStatus,
                    pendingApprovalsCount = routesByStatus["pending"] ?: 0
                )
            }
    }

    /**
     * Сводка для Security — все маршруты + pending.
     */
    private fun getSecuritySummary(): Mono<DashboardSummaryDto> {
        return Mono.zip(
            countAllRoutesByStatus(),
            routeRepository.countPending()
        ).map { tuple ->
            val routesByStatus = tuple.t1
            val pendingCount = tuple.t2
            DashboardSummaryDto(
                routesByStatus = routesByStatus,
                pendingApprovalsCount = pendingCount.toInt()
            )
        }
    }

    /**
     * Сводка для Admin — всё + users, consumers, health.
     */
    private fun getAdminSummary(): Mono<DashboardSummaryDto> {
        return Mono.zip(
            countAllRoutesByStatus(),
            routeRepository.countPending(),
            userRepository.count(),
            getConsumersCount(),
            getSystemHealthStatus()
        ).map { tuple ->
            val routesByStatus = tuple.t1
            val pendingCount = tuple.t2
            val totalUsers = tuple.t3
            val totalConsumersRaw = tuple.t4
            val healthStatus = tuple.t5

            // -1 используется как sentinel value для "Keycloak недоступен"
            val totalConsumers = if (totalConsumersRaw >= 0) totalConsumersRaw else null

            DashboardSummaryDto(
                routesByStatus = routesByStatus,
                pendingApprovalsCount = pendingCount.toInt(),
                totalUsers = totalUsers.toInt(),
                totalConsumers = totalConsumers,
                systemHealth = healthStatus
            )
        }
    }

    /**
     * Подсчёт маршрутов по статусам для конкретного пользователя.
     */
    private fun countRoutesByStatusForUser(userId: java.util.UUID): Mono<Map<String, Int>> {
        val sql = """
            SELECT status, COUNT(*) as count
            FROM routes
            WHERE created_by = :userId
            GROUP BY status
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("userId", userId)
            .map { row, _ ->
                val status = row.get("status", String::class.java)!!
                val count = row.get("count", java.lang.Number::class.java)!!.intValue()
                status to count
            }
            .all()
            .collectList()
            .map { pairs ->
                // Создаём map со всеми статусами, включая 0 для отсутствующих
                val resultMap = mutableMapOf(
                    "draft" to 0,
                    "pending" to 0,
                    "published" to 0,
                    "rejected" to 0
                )
                pairs.forEach { (status, count) ->
                    resultMap[status] = count
                }
                resultMap.toMap()
            }
    }

    /**
     * Подсчёт всех маршрутов по статусам.
     */
    private fun countAllRoutesByStatus(): Mono<Map<String, Int>> {
        val sql = """
            SELECT status, COUNT(*) as count
            FROM routes
            GROUP BY status
        """.trimIndent()

        return databaseClient.sql(sql)
            .map { row, _ ->
                val status = row.get("status", String::class.java)!!
                val count = row.get("count", java.lang.Number::class.java)!!.intValue()
                status to count
            }
            .all()
            .collectList()
            .map { pairs ->
                val resultMap = mutableMapOf(
                    "draft" to 0,
                    "pending" to 0,
                    "published" to 0,
                    "rejected" to 0
                )
                pairs.forEach { (status, count) ->
                    resultMap[status] = count
                }
                resultMap.toMap()
            }
    }

    /**
     * Получает количество consumers из Keycloak.
     * Возвращает -1 если Keycloak отключен или недоступен (преобразуется в null в DTO).
     *
     * ВАЖНО: Используем -1 как sentinel value вместо null чтобы Mono.zip работал корректно.
     */
    private fun getConsumersCount(): Mono<Int> {
        if (consumerService == null) {
            return Mono.just(-1)
        }

        return consumerService.listConsumers(0, 1, null)
            .map { response -> response.total }
            .onErrorResume { error ->
                logger.warn("Не удалось получить количество consumers: {}", error.message)
                Mono.just(-1)
            }
    }

    /**
     * Получает статус здоровья системы.
     * Возвращает "healthy", "degraded" или "down".
     */
    private fun getSystemHealthStatus(): Mono<String> {
        return healthService.getServicesHealth()
            .map { response ->
                val downCount = response.services.count { it.status == ServiceStatus.DOWN }
                val totalCount = response.services.size

                when {
                    downCount == 0 -> "healthy"
                    downCount < totalCount -> "degraded"
                    else -> "down"
                }
            }
            .onErrorReturn("unknown")
    }

    /**
     * Получает последние действия для Dashboard.
     *
     * Фильтрация по роли:
     * - DEVELOPER: только свои действия с маршрутами
     * - SECURITY: только approve/reject действия
     * - ADMIN: все действия
     *
     * @param user текущий пользователь
     * @param limit максимальное количество записей (default: 5)
     * @return Mono<RecentActivityDto>
     */
    fun getRecentActivity(user: AuthenticatedUser, limit: Int = DEFAULT_ACTIVITY_LIMIT): Mono<RecentActivityDto> {
        val validLimit = limit.coerceIn(1, MAX_ACTIVITY_LIMIT)

        logger.debug("Получение recent activity для user={}, role={}, limit={}", user.username, user.role, validLimit)

        return when (user.role) {
            Role.DEVELOPER -> getDeveloperRecentActivity(user, validLimit)
            Role.SECURITY -> getSecurityRecentActivity(validLimit)
            Role.ADMIN -> getAdminRecentActivity(validLimit)
        }
    }

    /**
     * Recent activity для Developer — только свои действия с маршрутами.
     */
    private fun getDeveloperRecentActivity(user: AuthenticatedUser, limit: Int): Mono<RecentActivityDto> {
        val sql = """
            SELECT al.id, al.entity_type, al.entity_id, al.action, al.username, al.created_at,
                   r.path as entity_name
            FROM audit_logs al
            LEFT JOIN routes r ON al.entity_id = r.id::text AND al.entity_type = 'route'
            WHERE al.entity_type = 'route'
              AND al.user_id = :userId
            ORDER BY al.created_at DESC
            LIMIT :limit
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("userId", user.userId)
            .bind("limit", limit)
            .map { row, _ -> mapRowToActivityItem(row) }
            .all()
            .collectList()
            .map { RecentActivityDto(it) }
    }

    /**
     * Recent activity для Security — только approve/reject действия.
     */
    private fun getSecurityRecentActivity(limit: Int): Mono<RecentActivityDto> {
        val sql = """
            SELECT al.id, al.entity_type, al.entity_id, al.action, al.username, al.created_at,
                   r.path as entity_name
            FROM audit_logs al
            LEFT JOIN routes r ON al.entity_id = r.id::text AND al.entity_type = 'route'
            WHERE al.entity_type = 'route'
              AND al.action IN ('approved', 'rejected')
            ORDER BY al.created_at DESC
            LIMIT :limit
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("limit", limit)
            .map { row, _ -> mapRowToActivityItem(row) }
            .all()
            .collectList()
            .map { RecentActivityDto(it) }
    }

    /**
     * Recent activity для Admin — все действия с маршрутами.
     */
    private fun getAdminRecentActivity(limit: Int): Mono<RecentActivityDto> {
        val sql = """
            SELECT al.id, al.entity_type, al.entity_id, al.action, al.username, al.created_at,
                   r.path as entity_name
            FROM audit_logs al
            LEFT JOIN routes r ON al.entity_id = r.id::text AND al.entity_type = 'route'
            WHERE al.entity_type = 'route'
            ORDER BY al.created_at DESC
            LIMIT :limit
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("limit", limit)
            .map { row, _ -> mapRowToActivityItem(row) }
            .all()
            .collectList()
            .map { RecentActivityDto(it) }
    }

    /**
     * Маппинг строки результата в ActivityItem.
     */
    private fun mapRowToActivityItem(row: io.r2dbc.spi.Row): ActivityItem {
        return ActivityItem(
            id = row.get("id", java.util.UUID::class.java)!!.toString(),
            action = row.get("action", String::class.java)!!,
            entityType = row.get("entity_type", String::class.java)!!,
            entityId = row.get("entity_id", String::class.java)!!,
            entityName = row.get("entity_name", String::class.java),
            performedBy = row.get("username", String::class.java)!!,
            performedAt = row.get("created_at", java.time.Instant::class.java)!!
        )
    }
}
