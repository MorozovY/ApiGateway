package com.company.gateway.admin.repository

import com.company.gateway.admin.dto.RouteWithCreator
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import io.r2dbc.spi.Row
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * Реализация кастомного репозитория для маршрутов с динамической фильтрацией.
 *
 * Использует DatabaseClient для построения SQL запросов с переменным
 * набором фильтров (status, createdBy, search).
 */
@Repository
class RouteRepositoryCustomImpl(
    private val databaseClient: DatabaseClient
) : RouteRepositoryCustom {

    companion object {
        /**
         * Экранирует специальные символы для PostgreSQL ILIKE.
         * Символы %, _ и \ должны быть экранированы для точного поиска.
         */
        fun escapeForIlike(input: String): String {
            return input
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
        }
    }

    override fun findWithFilters(
        status: RouteStatus?,
        createdBy: UUID?,
        search: String?,
        offset: Int,
        limit: Int
    ): Flux<Route> {
        val (whereClause, params) = buildWhereClause(status, createdBy, search)
        val sql = "SELECT * FROM routes$whereClause ORDER BY created_at DESC LIMIT :limit OFFSET :offset"

        params["offset"] = offset
        params["limit"] = limit

        var spec = databaseClient.sql(sql)
        params.forEach { (key, value) ->
            spec = spec.bind(key, value)
        }

        return spec.map { row, _ -> mapRowToRoute(row) }.all()
    }

    override fun countWithFilters(
        status: RouteStatus?,
        createdBy: UUID?,
        search: String?
    ): Mono<Long> {
        val (whereClause, params) = buildWhereClause(status, createdBy, search)
        val sql = "SELECT COUNT(*) FROM routes$whereClause"

        var spec = databaseClient.sql(sql)
        params.forEach { (key, value) ->
            spec = spec.bind(key, value)
        }

        return spec.map { row, _ ->
            // PostgreSQL COUNT(*) возвращает BIGINT (OID 20), используем java.lang.Number для универсального маппинга
            val count = row.get(0, java.lang.Number::class.java)
            count?.longValue() ?: 0L
        }.one().defaultIfEmpty(0L)
    }

    /**
     * Строит WHERE clause с динамическими фильтрами.
     *
     * Возвращает пару: (SQL WHERE clause, параметры для bind).
     * Фильтры применяются с AND логикой.
     *
     * @param status фильтр по статусу (опционально)
     * @param createdBy фильтр по автору (опционально)
     * @param search строка поиска (опционально)
     * @return Pair<String, MutableMap<String, Any>> — WHERE clause и параметры
     */
    private fun buildWhereClause(
        status: RouteStatus?,
        createdBy: UUID?,
        search: String?
    ): Pair<String, MutableMap<String, Any>> {
        val sql = StringBuilder(" WHERE 1=1")
        val params = mutableMapOf<String, Any>()

        // Добавляем фильтр по статусу
        status?.let {
            sql.append(" AND status = :status")
            params["status"] = it.name.lowercase()
        }

        // Добавляем фильтр по автору
        createdBy?.let {
            sql.append(" AND created_by = :createdBy")
            params["createdBy"] = it
        }

        // Добавляем текстовый поиск (case-insensitive) с явным ESCAPE clause
        search?.let {
            val escapedSearch = escapeForIlike(it)
            sql.append(" AND (path ILIKE :search ESCAPE '\\' OR description ILIKE :search ESCAPE '\\')")
            params["search"] = "%$escapedSearch%"
        }

        return Pair(sql.toString(), params)
    }

    /**
     * Маппинг строки результата в объект Route.
     * Использует PostgreSQL native array для methods.
     */
    private fun mapRowToRoute(row: Row): Route {
        // PostgreSQL массив может возвращаться как Array<Any> или Array<String>
        // Используем mapNotNull для фильтрации null элементов и безопасного преобразования
        val methodsRaw = row.get("methods", Array::class.java)
        val methods = methodsRaw?.mapNotNull { it?.toString() } ?: emptyList()

        return Route(
            id = row.get("id", UUID::class.java),
            path = row.get("path", String::class.java)!!,
            upstreamUrl = row.get("upstream_url", String::class.java)!!,
            methods = methods,
            description = row.get("description", String::class.java),
            status = RouteStatus.valueOf(row.get("status", String::class.java)!!.uppercase()),
            createdBy = row.get("created_by", UUID::class.java),
            createdAt = row.get("created_at", Instant::class.java),
            updatedAt = row.get("updated_at", Instant::class.java),
            submittedAt = row.get("submitted_at", Instant::class.java),
            approvedBy = row.get("approved_by", UUID::class.java),
            approvedAt = row.get("approved_at", Instant::class.java),
            rejectedBy = row.get("rejected_by", UUID::class.java),
            rejectedAt = row.get("rejected_at", Instant::class.java),
            rejectionReason = row.get("rejection_reason", String::class.java)
        )
    }

    override fun findByIdWithCreator(id: UUID): Mono<RouteWithCreator> {
        val sql = """
            SELECT r.id, r.path, r.upstream_url, r.methods, r.description,
                   r.status, r.created_by, r.created_at, r.updated_at,
                   r.submitted_at, r.approved_by, r.approved_at,
                   r.rejected_by, r.rejected_at, r.rejection_reason,
                   u.username as creator_username
            FROM routes r
            LEFT JOIN users u ON r.created_by = u.id
            WHERE r.id = :id
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind("id", id)
            .map { row, _ -> mapRowToRouteWithCreator(row) }
            .one()
    }

    override fun findByPathLike(pattern: String): Flux<Route> {
        val sql = "SELECT * FROM routes WHERE path LIKE :pattern ORDER BY path"

        return databaseClient.sql(sql)
            .bind("pattern", pattern)
            .map { row, _ -> mapRowToRoute(row) }
            .all()
    }

    /**
     * Маппинг строки результата JOIN запроса в RouteWithCreator.
     */
    private fun mapRowToRouteWithCreator(row: Row): RouteWithCreator {
        // Используем mapNotNull для фильтрации null элементов
        val methodsRaw = row.get("methods", Array::class.java)
        val methods = methodsRaw?.mapNotNull { it?.toString() } ?: emptyList()

        return RouteWithCreator(
            id = row.get("id", UUID::class.java)!!,
            path = row.get("path", String::class.java)!!,
            upstreamUrl = row.get("upstream_url", String::class.java)!!,
            methods = methods,
            description = row.get("description", String::class.java),
            status = row.get("status", String::class.java)!!,
            createdBy = row.get("created_by", UUID::class.java),
            creatorUsername = row.get("creator_username", String::class.java),
            createdAt = row.get("created_at", Instant::class.java),
            updatedAt = row.get("updated_at", Instant::class.java),
            submittedAt = row.get("submitted_at", Instant::class.java),
            approvedBy = row.get("approved_by", UUID::class.java),
            approvedAt = row.get("approved_at", Instant::class.java),
            rejectedBy = row.get("rejected_by", UUID::class.java),
            rejectedAt = row.get("rejected_at", Instant::class.java),
            rejectionReason = row.get("rejection_reason", String::class.java),
            rateLimitId = null // rate_limit_id будет добавлен в Epic 5
        )
    }
}
