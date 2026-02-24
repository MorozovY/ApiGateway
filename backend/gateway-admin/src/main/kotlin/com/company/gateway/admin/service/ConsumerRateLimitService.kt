package com.company.gateway.admin.service

import com.company.gateway.admin.dto.ConsumerRateLimitRequest
import com.company.gateway.admin.dto.ConsumerRateLimitResponse
import com.company.gateway.admin.dto.PagedResponse
import com.company.gateway.admin.exception.NotFoundException
import com.company.gateway.admin.exception.ValidationException
import com.company.gateway.admin.publisher.ConsumerRateLimitEventPublisher
import com.company.gateway.admin.repository.ConsumerRateLimitRepository
import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.common.model.ConsumerRateLimit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * Сервис для управления per-consumer rate limits.
 *
 * Реализует бизнес-логику CRUD операций:
 * - Создание/обновление rate limit для consumer (upsert)
 * - Получение rate limit по consumer ID
 * - Удаление rate limit
 * - Получение списка с пагинацией и фильтрацией
 *
 * При создании/обновлении/удалении публикуется событие
 * для синхронизации кэша gateway-core.
 */
@Service
class ConsumerRateLimitService(
    private val consumerRateLimitRepository: ConsumerRateLimitRepository,
    private val userRepository: UserRepository,
    private val auditService: AuditService,
    private val eventPublisher: ConsumerRateLimitEventPublisher
) {
    private val logger = LoggerFactory.getLogger(ConsumerRateLimitService::class.java)

    /**
     * Создаёт или обновляет rate limit для consumer (upsert).
     *
     * @param consumerId идентификатор consumer (Keycloak client_id)
     * @param request данные rate limit
     * @param userId ID пользователя (Admin), выполняющего операцию
     * @param username имя пользователя для аудит-лога
     * @return Mono<ConsumerRateLimitResponse> созданный/обновлённый rate limit
     * @throws ValidationException если burstSize < requestsPerSecond
     */
    fun setRateLimit(
        consumerId: String,
        request: ConsumerRateLimitRequest,
        userId: UUID,
        username: String
    ): Mono<ConsumerRateLimitResponse> {
        // Валидация: burstSize должен быть >= requestsPerSecond
        if (request.burstSize < request.requestsPerSecond) {
            return Mono.error(
                ValidationException("Burst size must be at least equal to requests per second")
            )
        }

        return consumerRateLimitRepository.findByConsumerId(consumerId)
            .flatMap { existing ->
                // UPDATE: обновляем существующий rate limit
                val oldValues = mapOf(
                    "requestsPerSecond" to existing.requestsPerSecond,
                    "burstSize" to existing.burstSize
                )

                val updated = existing.copy(
                    requestsPerSecond = request.requestsPerSecond,
                    burstSize = request.burstSize,
                    updatedAt = Instant.now()
                )

                consumerRateLimitRepository.save(updated)
                    .flatMap { saved ->
                        val newValues = mapOf(
                            "requestsPerSecond" to saved.requestsPerSecond,
                            "burstSize" to saved.burstSize
                        )
                        auditService.logUpdated(
                            entityType = "consumer_rate_limit",
                            entityId = saved.id.toString(),
                            userId = userId,
                            username = username,
                            oldValues = oldValues,
                            newValues = newValues
                        ).thenReturn(saved)
                    }
                    .doOnSuccess {
                        logger.info(
                            "Per-consumer rate limit обновлён: consumerId={}, rps={}, burst={}",
                            consumerId, request.requestsPerSecond, request.burstSize
                        )
                    }
            }
            .switchIfEmpty(
                // CREATE: создаём новый rate limit
                Mono.defer {
                    val entity = ConsumerRateLimit(
                        consumerId = consumerId,
                        requestsPerSecond = request.requestsPerSecond,
                        burstSize = request.burstSize,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now(),
                        createdBy = userId
                    )

                    consumerRateLimitRepository.save(entity)
                        .flatMap { saved ->
                            auditService.logCreated(
                                entityType = "consumer_rate_limit",
                                entityId = saved.id.toString(),
                                userId = userId,
                                username = username,
                                entity = mapOf(
                                    "consumerId" to saved.consumerId,
                                    "requestsPerSecond" to saved.requestsPerSecond,
                                    "burstSize" to saved.burstSize
                                )
                            ).thenReturn(saved)
                        }
                        .doOnSuccess {
                            logger.info(
                                "Per-consumer rate limit создан: consumerId={}, rps={}, burst={}",
                                consumerId, request.requestsPerSecond, request.burstSize
                            )
                        }
                }
            )
            .flatMap { saved ->
                // Публикуем событие для синхронизации кэша gateway-core
                eventPublisher.publishConsumerRateLimitChanged(consumerId)
                    .thenReturn(saved)
            }
            .flatMap { saved -> enrichWithCreatorUsername(saved) }
    }

    /**
     * Получает rate limit по consumer ID.
     *
     * @param consumerId идентификатор consumer
     * @return Mono<ConsumerRateLimitResponse> rate limit
     * @throws NotFoundException если rate limit не найден
     */
    fun getRateLimit(consumerId: String): Mono<ConsumerRateLimitResponse> {
        return consumerRateLimitRepository.findByConsumerId(consumerId)
            .switchIfEmpty(Mono.error(NotFoundException("Consumer rate limit not found")))
            .flatMap { entity -> enrichWithCreatorUsername(entity) }
    }

    /**
     * Удаляет rate limit для consumer.
     *
     * @param consumerId идентификатор consumer
     * @param userId ID пользователя, выполняющего операцию
     * @param username имя пользователя для аудит-лога
     * @return Mono<Void>
     * @throws NotFoundException если rate limit не найден
     */
    fun deleteRateLimit(consumerId: String, userId: UUID, username: String): Mono<Void> {
        return consumerRateLimitRepository.findByConsumerId(consumerId)
            .switchIfEmpty(Mono.error(NotFoundException("Consumer rate limit not found")))
            .flatMap { entity ->
                consumerRateLimitRepository.delete(entity)
                    .then(
                        auditService.logDeleted(
                            entityType = "consumer_rate_limit",
                            entityId = entity.id.toString(),
                            userId = userId,
                            username = username
                        )
                    )
                    .then(
                        // Публикуем событие для синхронизации кэша gateway-core
                        eventPublisher.publishConsumerRateLimitChanged(consumerId)
                    )
            }
            .doOnSuccess {
                logger.info("Per-consumer rate limit удалён: consumerId={}", consumerId)
            }
            .then()
    }

    /**
     * Получает список всех rate limits с пагинацией и опциональной фильтрацией.
     *
     * @param offset смещение от начала списка
     * @param limit максимальное количество элементов
     * @param filter фильтр по prefixу consumer ID (опционально)
     * @return Mono<PagedResponse<ConsumerRateLimitResponse>> пагинированный список
     */
    fun listRateLimits(
        offset: Int = 0,
        limit: Int = 20,
        filter: String? = null
    ): Mono<PagedResponse<ConsumerRateLimitResponse>> {
        // Загружаем rate limits с пагинацией
        val entitiesMono = if (filter.isNullOrBlank()) {
            consumerRateLimitRepository.findAllWithPagination(offset, limit).collectList()
        } else {
            consumerRateLimitRepository.findAllByConsumerIdStartingWith(filter, offset, limit).collectList()
        }

        // Подсчитываем общее количество
        val totalMono = if (filter.isNullOrBlank()) {
            consumerRateLimitRepository.countAll()
        } else {
            consumerRateLimitRepository.countByConsumerIdStartingWith(filter)
        }

        return Mono.zip(entitiesMono, totalMono)
            .flatMap { tuple ->
                val entities = tuple.t1
                val total = tuple.t2

                // Загружаем usernames для всех createdBy за один запрос
                val creatorIds = entities.mapNotNull { it.createdBy }.distinct()

                if (creatorIds.isEmpty()) {
                    // Нет создателей — возвращаем без username
                    val items = entities.map { entity ->
                        ConsumerRateLimitResponse.from(entity, creatorUsername = null)
                    }
                    Mono.just(PagedResponse(items, total, offset, limit))
                } else {
                    // Загружаем usernames batch-запросом
                    userRepository.findAllById(creatorIds).collectList()
                        .map { users ->
                            val usernameMap = users.associate { it.id!! to it.username }
                            val items = entities.map { entity ->
                                val username = entity.createdBy?.let { usernameMap[it] }
                                ConsumerRateLimitResponse.from(entity, creatorUsername = username)
                            }
                            PagedResponse(items, total, offset, limit)
                        }
                }
            }
    }

    /**
     * Обогащает entity username'ом создателя.
     */
    private fun enrichWithCreatorUsername(entity: ConsumerRateLimit): Mono<ConsumerRateLimitResponse> {
        val createdById = entity.createdBy
        return if (createdById != null) {
            userRepository.findById(createdById)
                .map { user -> ConsumerRateLimitResponse.from(entity, user.username) }
                .defaultIfEmpty(ConsumerRateLimitResponse.from(entity, null))
        } else {
            Mono.just(ConsumerRateLimitResponse.from(entity, null))
        }
    }
}
