package com.company.gateway.admin.repository

import com.company.gateway.common.model.RateLimit
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Репозиторий для работы с политиками rate limiting.
 */
@Repository
interface RateLimitRepository : R2dbcRepository<RateLimit, UUID> {

    /**
     * Находит политику по уникальному имени.
     */
    fun findByName(name: String): Mono<RateLimit>

    /**
     * Проверяет существование политики с указанным именем.
     */
    fun existsByName(name: String): Mono<Boolean>

    /**
     * Проверяет существование политики с указанным именем, исключая указанный ID.
     * Используется при обновлении для проверки уникальности нового имени.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM rate_limits WHERE name = :name AND id != :excludeId)")
    fun existsByNameAndIdNot(name: String, excludeId: UUID): Mono<Boolean>
}
