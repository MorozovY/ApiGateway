package com.company.gateway.core.repository

import com.company.gateway.common.model.RateLimit
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import java.util.UUID

/**
 * Репозиторий для чтения политик rate limiting в gateway-core.
 *
 * Используется для загрузки политик вместе с маршрутами при кэшировании.
 * Только операции чтения — CRUD выполняется в gateway-admin.
 */
@Repository
interface RateLimitRepository : R2dbcRepository<RateLimit, UUID> {

    /**
     * Находит все политики по списку идентификаторов.
     * Используется для batch загрузки при refresh кэша.
     */
    fun findAllByIdIn(ids: Collection<UUID>): Flux<RateLimit>
}
