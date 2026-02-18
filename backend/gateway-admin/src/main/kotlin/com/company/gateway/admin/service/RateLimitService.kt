package com.company.gateway.admin.service

import com.company.gateway.admin.dto.CreateRateLimitRequest
import com.company.gateway.admin.dto.PagedResponse
import com.company.gateway.admin.dto.RateLimitResponse
import com.company.gateway.admin.dto.UpdateRateLimitRequest
import com.company.gateway.admin.exception.ConflictException
import com.company.gateway.admin.exception.NotFoundException
import com.company.gateway.admin.exception.ValidationException
import com.company.gateway.admin.publisher.RouteEventPublisher
import com.company.gateway.admin.repository.RateLimitRepository
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.common.model.RateLimit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * Сервис для управления политиками rate limiting.
 *
 * Реализует бизнес-логику CRUD операций с политиками:
 * - Создание политики с проверкой уникальности name
 * - Обновление политики с cache invalidation
 * - Удаление политики с проверкой использования
 * - Получение политики по ID с usageCount
 * - Получение списка политик с пагинацией
 */
@Service
class RateLimitService(
    private val rateLimitRepository: RateLimitRepository,
    private val routeRepository: RouteRepository,
    private val routeEventPublisher: RouteEventPublisher,
    private val auditService: AuditService
) {
    private val logger = LoggerFactory.getLogger(RateLimitService::class.java)

    /**
     * Создаёт новую политику rate limiting.
     *
     * Проверяет уникальность name и валидирует burstSize >= requestsPerSecond.
     *
     * @param request данные для создания политики
     * @param userId ID пользователя, создающего политику
     * @param username имя пользователя для аудит-лога
     * @return Mono<RateLimitResponse> созданная политика
     * @throws ConflictException если политика с таким именем уже существует
     * @throws ValidationException если burstSize < requestsPerSecond
     */
    fun create(
        request: CreateRateLimitRequest,
        userId: UUID,
        username: String
    ): Mono<RateLimitResponse> {
        // Валидация: burstSize должен быть >= requestsPerSecond
        if (request.burstSize < request.requestsPerSecond) {
            return Mono.error(
                ValidationException("Burst size must be at least equal to requests per second")
            )
        }

        return rateLimitRepository.existsByName(request.name)
            .flatMap { exists ->
                if (exists) {
                    Mono.error(ConflictException("Rate limit policy with this name already exists"))
                } else {
                    val rateLimit = RateLimit(
                        name = request.name,
                        description = request.description,
                        requestsPerSecond = request.requestsPerSecond,
                        burstSize = request.burstSize,
                        createdBy = userId,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now()
                    )
                    rateLimitRepository.save(rateLimit)
                }
            }
            .flatMap { saved ->
                // Логируем создание политики
                auditService.logCreated(
                    entityType = "ratelimit",
                    entityId = saved.id.toString(),
                    userId = userId,
                    username = username,
                    entity = mapOf(
                        "name" to saved.name,
                        "requestsPerSecond" to saved.requestsPerSecond,
                        "burstSize" to saved.burstSize
                    )
                ).thenReturn(saved)
            }
            .map { RateLimitResponse.from(it, usageCount = 0) }
            .doOnSuccess { logger.info("Политика rate limiting создана: name={}, userId={}", request.name, userId) }
    }

    /**
     * Обновляет существующую политику rate limiting.
     *
     * После обновления публикует cache invalidation события
     * для всех маршрутов, использующих эту политику.
     *
     * @param id ID политики
     * @param request данные для обновления
     * @param userId ID текущего пользователя
     * @param username имя пользователя для аудит-лога
     * @return Mono<RateLimitResponse> обновлённая политика
     * @throws NotFoundException если политика не найдена
     * @throws ConflictException если новое имя уже занято
     * @throws ValidationException если burstSize < requestsPerSecond
     */
    fun update(
        id: UUID,
        request: UpdateRateLimitRequest,
        userId: UUID,
        username: String
    ): Mono<RateLimitResponse> {
        return rateLimitRepository.findById(id)
            .switchIfEmpty(Mono.error(NotFoundException("Rate limit policy not found")))
            .flatMap { existing ->
                // Определяем финальные значения
                val newRequestsPerSecond = request.requestsPerSecond ?: existing.requestsPerSecond
                val newBurstSize = request.burstSize ?: existing.burstSize

                // Валидация: burstSize должен быть >= requestsPerSecond
                if (newBurstSize < newRequestsPerSecond) {
                    return@flatMap Mono.error<RateLimit>(
                        ValidationException("Burst size must be at least equal to requests per second")
                    )
                }

                // Проверяем уникальность нового имени (если изменяется)
                val nameCheck = if (request.name != null && request.name != existing.name) {
                    rateLimitRepository.existsByNameAndIdNot(request.name, id)
                        .flatMap { nameExists ->
                            if (nameExists) {
                                Mono.error<Boolean>(ConflictException("Rate limit policy with this name already exists"))
                            } else {
                                Mono.just(false)
                            }
                        }
                } else {
                    Mono.just(false)
                }

                nameCheck.flatMap {
                    val oldValues = mapOf(
                        "name" to existing.name,
                        "requestsPerSecond" to existing.requestsPerSecond,
                        "burstSize" to existing.burstSize,
                        "description" to existing.description
                    )

                    val updated = existing.copy(
                        name = request.name ?: existing.name,
                        description = request.description ?: existing.description,
                        requestsPerSecond = newRequestsPerSecond,
                        burstSize = newBurstSize,
                        updatedAt = Instant.now()
                    )

                    rateLimitRepository.save(updated)
                        .flatMap { saved ->
                            val newValues = mapOf(
                                "name" to saved.name,
                                "requestsPerSecond" to saved.requestsPerSecond,
                                "burstSize" to saved.burstSize,
                                "description" to saved.description
                            )
                            auditService.logUpdated(
                                entityType = "ratelimit",
                                entityId = saved.id.toString(),
                                userId = userId,
                                username = username,
                                oldValues = oldValues,
                                newValues = newValues
                            ).thenReturn(saved)
                        }
                }
            }
            .flatMap { saved ->
                // Инвалидируем кэш для всех маршрутов с этой политикой
                routeRepository.findByRateLimitId(id)
                    .flatMap { route -> routeEventPublisher.publishRouteChanged(route.id!!) }
                    .then()
                    .thenReturn(saved)
            }
            .flatMap { saved ->
                // Получаем актуальный usageCount
                routeRepository.countByRateLimitId(id)
                    .map { count -> RateLimitResponse.from(saved, usageCount = count) }
            }
            .doOnSuccess { logger.info("Политика rate limiting обновлена: id={}, userId={}", id, userId) }
    }

    /**
     * Удаляет политику rate limiting.
     *
     * Запрещает удаление политики, если она используется хотя бы одним маршрутом.
     *
     * @param id ID политики
     * @param userId ID текущего пользователя
     * @param username имя пользователя для аудит-лога
     * @return Mono<Void>
     * @throws NotFoundException если политика не найдена
     * @throws ConflictException если политика используется маршрутами
     */
    fun delete(id: UUID, userId: UUID, username: String): Mono<Void> {
        return rateLimitRepository.findById(id)
            .switchIfEmpty(Mono.error(NotFoundException("Rate limit policy not found")))
            .flatMap { policy ->
                // Проверяем, используется ли политика маршрутами
                routeRepository.countByRateLimitId(id)
                    .flatMap { count ->
                        if (count > 0) {
                            Mono.error<Void>(
                                ConflictException("Policy is in use by $count routes")
                            )
                        } else {
                            rateLimitRepository.delete(policy)
                                .then(
                                    auditService.logDeleted(
                                        entityType = "ratelimit",
                                        entityId = id.toString(),
                                        userId = userId,
                                        username = username
                                    )
                                )
                                .then()
                        }
                    }
            }
            .doOnSuccess { logger.info("Политика rate limiting удалена: id={}, userId={}", id, userId) }
    }

    /**
     * Получает политику по ID с usageCount.
     *
     * @param id ID политики
     * @return Mono<RateLimitResponse> политика
     * @throws NotFoundException если политика не найдена
     */
    fun findById(id: UUID): Mono<RateLimitResponse> {
        return rateLimitRepository.findById(id)
            .switchIfEmpty(Mono.error(NotFoundException("Rate limit policy not found")))
            .flatMap { policy ->
                routeRepository.countByRateLimitId(id)
                    .map { count -> RateLimitResponse.from(policy, usageCount = count) }
            }
    }

    /**
     * Получает список всех политик с пагинацией и usageCount.
     *
     * @param offset смещение от начала списка
     * @param limit максимальное количество элементов (default 100)
     * @return Mono<PagedResponse<RateLimitResponse>> пагинированный список политик
     */
    fun findAll(offset: Int = 0, limit: Int = 100): Mono<PagedResponse<RateLimitResponse>> {
        val policiesMono = rateLimitRepository.findAll()
            .skip(offset.toLong())
            .take(limit.toLong())
            .flatMap { policy ->
                routeRepository.countByRateLimitId(policy.id!!)
                    .map { count -> RateLimitResponse.from(policy, usageCount = count) }
            }
            .collectList()

        val totalMono = rateLimitRepository.count()

        return Mono.zip(policiesMono, totalMono)
            .map { tuple ->
                PagedResponse(
                    items = tuple.t1,
                    total = tuple.t2,
                    offset = offset,
                    limit = limit
                )
            }
    }
}
