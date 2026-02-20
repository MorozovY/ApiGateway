package com.company.gateway.admin.repository

import com.company.gateway.admin.dto.AuditFilterRequest
import com.company.gateway.common.model.AuditLog
import io.r2dbc.spi.Row
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Реализация custom методов репозитория для аудит-логов.
 *
 * Использует DatabaseClient с raw SQL для динамических запросов.
 * Story 7.2: Audit Log API with Filtering.
 * Story 7.3: Route Change History API — findByEntityIdWithFilters.
 */
@Repository
class AuditLogRepositoryCustomImpl(
    private val databaseClient: DatabaseClient
) : AuditLogRepositoryCustom {

    override fun findAllWithFilters(filter: AuditFilterRequest): Flux<AuditLog> {
        val (whereClause, params) = buildWhereClause(filter)

        val sql = """
            SELECT * FROM audit_logs
            WHERE 1=1 $whereClause
            ORDER BY created_at DESC
            LIMIT :limit OFFSET :offset
        """.trimIndent()

        params["limit"] = filter.limit
        params["offset"] = filter.offset

        var spec = databaseClient.sql(sql)
        params.forEach { (key, value) -> spec = spec.bind(key, value) }

        return spec.map { row, _ -> mapRowToAuditLog(row) }.all()
    }

    override fun countWithFilters(filter: AuditFilterRequest): Mono<Long> {
        val (whereClause, params) = buildWhereClause(filter)

        val sql = "SELECT COUNT(*) FROM audit_logs WHERE 1=1 $whereClause"

        var spec = databaseClient.sql(sql)
        params.forEach { (key, value) -> spec = spec.bind(key, value) }

        return spec.map { row, _ ->
            // PostgreSQL возвращает bigint, который маппится в Number
            val count = row.get(0, Number::class.java)
            count?.toLong() ?: 0L
        }.one().defaultIfEmpty(0L)
    }

    /**
     * Строит WHERE clause и параметры для динамического запроса.
     *
     * Вынесено в отдельный метод для соблюдения DRY-принципа.
     *
     * @param filter параметры фильтрации
     * @return пара (WHERE clause строка, параметры)
     */
    private fun buildWhereClause(filter: AuditFilterRequest): Pair<String, MutableMap<String, Any>> {
        val clauses = StringBuilder()
        val params = mutableMapOf<String, Any>()

        filter.userId?.let {
            clauses.append(" AND user_id = :userId")
            params["userId"] = it
        }

        filter.action?.let {
            clauses.append(" AND action = :action")
            params["action"] = it
        }

        filter.entityType?.let {
            clauses.append(" AND entity_type = :entityType")
            params["entityType"] = it
        }

        // AC5: dateFrom — начало дня (00:00:00 UTC)
        filter.dateFrom?.let {
            clauses.append(" AND created_at >= :dateFrom")
            params["dateFrom"] = it.atStartOfDay().toInstant(ZoneOffset.UTC)
        }

        // AC5: dateTo — конец дня (23:59:59.999999999 UTC)
        filter.dateTo?.let {
            clauses.append(" AND created_at <= :dateTo")
            params["dateTo"] = it.plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC)
        }

        return Pair(clauses.toString(), params)
    }

    /**
     * Маппинг строки результата в AuditLog entity.
     */
    private fun mapRowToAuditLog(row: Row): AuditLog {
        return AuditLog(
            id = row.get("id", UUID::class.java),
            entityType = row.get("entity_type", String::class.java)!!,
            entityId = row.get("entity_id", String::class.java)!!,
            action = row.get("action", String::class.java)!!,
            userId = row.get("user_id", UUID::class.java)!!,
            username = row.get("username", String::class.java)!!,
            changes = row.get("changes", String::class.java),
            ipAddress = row.get("ip_address", String::class.java),
            correlationId = row.get("correlation_id", String::class.java),
            createdAt = row.get("created_at", Instant::class.java)
        )
    }

    /**
     * Получение истории изменений для конкретной сущности.
     *
     * Story 7.3: Route Change History API.
     * Сортировка по created_at ASC (хронологический порядок — старые первыми).
     *
     * @param entityType тип сущности (например, "route")
     * @param entityId ID сущности
     * @param dateFrom начало периода (опционально)
     * @param dateTo конец периода (опционально)
     * @return Flux записей аудит-лога в хронологическом порядке
     */
    override fun findByEntityIdWithFilters(
        entityType: String,
        entityId: String,
        dateFrom: LocalDate?,
        dateTo: LocalDate?
    ): Flux<AuditLog> {
        val clauses = StringBuilder()
        val params = mutableMapOf<String, Any>(
            "entityType" to entityType,
            "entityId" to entityId
        )

        // Фильтрация по диапазону дат (AC4)
        dateFrom?.let {
            clauses.append(" AND created_at >= :dateFrom")
            params["dateFrom"] = it.atStartOfDay().toInstant(ZoneOffset.UTC)
        }

        dateTo?.let {
            clauses.append(" AND created_at <= :dateTo")
            // Конец дня (23:59:59.999999999 UTC)
            params["dateTo"] = it.plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC)
        }

        // Сортировка ASC для хронологического порядка (AC5)
        val sql = """
            SELECT * FROM audit_logs
            WHERE entity_type = :entityType AND entity_id = :entityId $clauses
            ORDER BY created_at ASC
        """.trimIndent()

        var spec = databaseClient.sql(sql)
        params.forEach { (key, value) -> spec = spec.bind(key, value) }

        return spec.map { row, _ -> mapRowToAuditLog(row) }.all()
    }
}
