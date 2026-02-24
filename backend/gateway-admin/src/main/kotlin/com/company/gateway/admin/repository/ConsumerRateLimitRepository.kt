package com.company.gateway.admin.repository

import com.company.gateway.common.model.ConsumerRateLimit
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Репозиторий для работы с per-consumer rate limits.
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

    /**
     * Проверяет существование rate limit для consumer.
     *
     * @param consumerId идентификатор consumer
     * @return true если rate limit существует
     */
    fun existsByConsumerId(consumerId: String): Mono<Boolean>

    /**
     * Удаляет rate limit по consumer ID.
     *
     * @param consumerId идентификатор consumer
     * @return Mono<Void>
     */
    fun deleteByConsumerId(consumerId: String): Mono<Void>

    /**
     * Получает все rate limits с пагинацией (сортировка по created_at DESC).
     *
     * @param offset смещение
     * @param limit количество записей
     * @return поток rate limits
     */
    @Query("SELECT * FROM consumer_rate_limits ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    fun findAllWithPagination(offset: Int, limit: Int): Flux<ConsumerRateLimit>

    /**
     * Получает rate limits с фильтрацией по consumer ID prefix (LIKE 'prefix%').
     *
     * @param consumerIdPrefix префикс consumer ID для фильтрации
     * @param offset смещение
     * @param limit количество записей
     * @return поток rate limits
     */
    @Query(
        """
        SELECT * FROM consumer_rate_limits
        WHERE consumer_id ILIKE :consumerIdPrefix || '%'
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun findAllByConsumerIdStartingWith(consumerIdPrefix: String, offset: Int, limit: Int): Flux<ConsumerRateLimit>

    /**
     * Подсчитывает общее количество rate limits.
     *
     * @return количество записей
     */
    @Query("SELECT COUNT(*) FROM consumer_rate_limits")
    fun countAll(): Mono<Long>

    /**
     * Подсчитывает количество rate limits с фильтрацией по consumer ID prefix.
     *
     * @param consumerIdPrefix префикс consumer ID для фильтрации
     * @return количество записей
     */
    @Query("SELECT COUNT(*) FROM consumer_rate_limits WHERE consumer_id ILIKE :consumerIdPrefix || '%'")
    fun countByConsumerIdStartingWith(consumerIdPrefix: String): Mono<Long>
}
