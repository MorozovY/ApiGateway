package com.company.gateway.core.repository

import com.company.gateway.common.model.ConsumerRateLimit
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Репозиторий для чтения per-consumer rate limits в gateway-core.
 *
 * Используется для проверки consumer rate limit в RateLimitFilter.
 * Только операции чтения — CRUD выполняется в gateway-admin.
 */
@Repository
interface ConsumerRateLimitRepository : R2dbcRepository<ConsumerRateLimit, UUID> {

    /**
     * Находит rate limit по consumer ID.
     *
     * @param consumerId идентификатор consumer (Keycloak client_id)
     * @return rate limit или empty Mono
     */
    fun findByConsumerId(consumerId: String): Mono<ConsumerRateLimit>
}
