package com.company.gateway.admin.repository

import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@Repository
interface RouteRepository : R2dbcRepository<Route, UUID>, RouteRepositoryCustom {

    /**
     * Находит все маршруты с указанным статусом.
     */
    fun findByStatus(status: RouteStatus): Flux<Route>

    /**
     * Находит маршрут по URL path.
     */
    fun findByPath(path: String): Mono<Route>

    /**
     * Проверяет существование маршрута с указанным path.
     */
    fun existsByPath(path: String): Mono<Boolean>

    /**
     * Получает маршруты с пагинацией (сортировка по createdAt DESC).
     */
    @Query("SELECT * FROM routes ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    fun findAllWithPagination(offset: Int, limit: Int): Flux<Route>

    /**
     * Подсчитывает количество маршрутов по статусу.
     */
    fun countByStatus(status: RouteStatus): Mono<Long>

    /**
     * Подсчитывает количество маршрутов, использующих указанную политику rate limiting.
     * Используется для usageCount и проверки перед удалением политики.
     */
    @Query("SELECT COUNT(*) FROM routes WHERE rate_limit_id = :rateLimitId")
    fun countByRateLimitId(rateLimitId: UUID): Mono<Long>

    /**
     * Находит все маршруты, использующие указанную политику rate limiting.
     * Используется для cache invalidation при обновлении политики.
     */
    @Query("SELECT * FROM routes WHERE rate_limit_id = :rateLimitId")
    fun findByRateLimitId(rateLimitId: UUID): Flux<Route>
}