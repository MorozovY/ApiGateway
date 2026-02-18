package com.company.gateway.admin.service

import com.company.gateway.admin.dto.RouteResponse
import com.company.gateway.admin.exception.AccessDeniedException
import com.company.gateway.admin.exception.ConflictException
import com.company.gateway.admin.exception.NotFoundException
import com.company.gateway.admin.exception.ValidationException
import com.company.gateway.admin.publisher.RouteEventPublisher
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * Сервис для управления workflow согласования маршрутов.
 *
 * Реализует бизнес-логику:
 * - Отправка маршрута на согласование (DRAFT → PENDING)
 * - Одобрение маршрута (PENDING → PUBLISHED) — Story 4.2
 * - Отклонение маршрута (PENDING → REJECTED) — Story 4.2
 *
 * Story 4.1: Submit for Approval API
 * Story 4.2: Approval & Rejection API
 */
@Service
class ApprovalService(
    private val routeRepository: RouteRepository,
    private val auditService: AuditService,
    private val routeEventPublisher: RouteEventPublisher
) {
    private val logger = LoggerFactory.getLogger(ApprovalService::class.java)

    /**
     * Отправляет маршрут на согласование.
     *
     * Поддерживает два сценария (Story 4.4, AC4):
     * - DRAFT → PENDING: первичная подача, action = "route.submitted"
     * - REJECTED → PENDING: повторная подача, очищает rejection-поля, action = "route.resubmitted"
     *
     * Проверяет:
     * - Маршрут существует
     * - Маршрут в статусе DRAFT или REJECTED
     * - Текущий пользователь является владельцем маршрута
     * - Маршрут проходит валидацию перед отправкой
     *
     * @param routeId ID маршрута
     * @param userId ID пользователя, отправляющего маршрут
     * @param username имя пользователя для аудит-лога
     * @return Mono<RouteResponse> обновлённый маршрут
     * @throws NotFoundException если маршрут не найден
     * @throws ConflictException если маршрут не в статусе DRAFT или REJECTED
     * @throws AccessDeniedException если пользователь не владелец маршрута
     * @throws ValidationException если маршрут не проходит валидацию
     */
    fun submitForApproval(
        routeId: UUID,
        userId: UUID,
        username: String
    ): Mono<RouteResponse> {
        logger.debug("Отправка маршрута на согласование: routeId={}, userId={}", routeId, userId)

        return routeRepository.findById(routeId)
            .switchIfEmpty(Mono.error(NotFoundException("Route not found")))
            .flatMap { route ->
                // Проверяем ownership — только владелец может отправить на согласование
                if (route.createdBy != userId) {
                    logger.warn(
                        "Попытка отправки чужого маршрута: routeId={}, ownerId={}, userId={}",
                        routeId, route.createdBy, userId
                    )
                    return@flatMap Mono.error<Route>(
                        AccessDeniedException("You can only submit your own routes")
                    )
                }

                // Определяем audit action по статусу маршрута (DRAFT или REJECTED)
                val auditAction = when (route.status) {
                    RouteStatus.DRAFT     -> "route.submitted"
                    RouteStatus.REJECTED  -> "route.resubmitted"
                    else -> {
                        logger.warn(
                            "Попытка отправки маршрута в неподходящем статусе: routeId={}, status={}",
                            routeId, route.status
                        )
                        return@flatMap Mono.error<Route>(
                            ConflictException("Only draft or rejected routes can be submitted for approval")
                        )
                    }
                }

                // Валидируем маршрут перед отправкой
                validateRouteForSubmission(route)
                    .flatMap {
                        // Для повторной подачи очищаем rejection-поля
                        val updatedRoute = route.copy(
                            status = RouteStatus.PENDING,
                            submittedAt = Instant.now(),
                            updatedAt = Instant.now(),
                            // При resubmission сбрасываем rejection-данные
                            rejectionReason = null,
                            rejectedBy = null,
                            rejectedAt = null
                        )
                        routeRepository.save(updatedRoute)
                    }
                    .flatMap { savedRoute ->
                        // Записываем audit log с соответствующим action
                        auditService.log(
                            entityType = "route",
                            entityId = savedRoute.id.toString(),
                            action = auditAction,
                            userId = userId,
                            username = username,
                            changes = mapOf(
                                "newStatus" to RouteStatus.PENDING.name.lowercase(),
                                "submittedAt" to savedRoute.submittedAt.toString()
                            )
                        ).thenReturn(savedRoute)
                    }
            }
            .map { RouteResponse.from(it) }
            .doOnSuccess {
                logger.info(
                    "Маршрут отправлен на согласование: routeId={}, userId={}",
                    routeId, userId
                )
            }
    }

    /**
     * Валидирует маршрут перед отправкой на согласование.
     *
     * Проверяет:
     * - path не пустой
     * - upstreamUrl валидный URL формат
     * - methods не пустой список
     *
     * @param route маршрут для валидации
     * @return Mono<Unit> или Mono.error с ValidationException
     */
    private fun validateRouteForSubmission(route: Route): Mono<Unit> {
        val errors = mutableListOf<String>()

        // Проверяем path
        if (route.path.isBlank()) {
            errors.add("Path cannot be empty")
        }

        // Проверяем upstreamUrl
        if (route.upstreamUrl.isBlank()) {
            errors.add("Upstream URL cannot be empty")
        } else if (!isValidUrl(route.upstreamUrl)) {
            errors.add("Upstream URL must be a valid HTTP/HTTPS URL")
        }

        // Проверяем methods
        if (route.methods.isEmpty()) {
            errors.add("At least one HTTP method must be specified")
        }

        return if (errors.isEmpty()) {
            Mono.just(Unit)
        } else {
            Mono.error(ValidationException(errors.joinToString("; ")))
        }
    }

    /**
     * Проверяет, что строка является валидным HTTP/HTTPS URL.
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            val parsed = java.net.URI(url)
            parsed.scheme?.lowercase() in listOf("http", "https") && parsed.host != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Одобряет маршрут (PENDING → PUBLISHED).
     *
     * Доступно только для пользователей с ролью SECURITY или ADMIN.
     * После одобрения публикуется событие cache invalidation в Redis,
     * что позволяет gateway-core подхватить маршрут в течение 5 секунд (NFR3).
     *
     * @param routeId ID маршрута
     * @param userId ID пользователя, одобряющего маршрут
     * @param username имя пользователя для аудит-лога
     * @return Mono<RouteResponse> обновлённый маршрут со статусом PUBLISHED
     * @throws NotFoundException если маршрут не найден
     * @throws ConflictException если маршрут не в статусе PENDING
     *
     * Story 4.2, AC1: Успешное одобрение маршрута
     * Story 4.2, AC2: Автоматическая публикация после одобрения
     */
    fun approve(
        routeId: UUID,
        userId: UUID,
        username: String
    ): Mono<RouteResponse> {
        logger.debug("Одобрение маршрута: routeId={}, userId={}", routeId, userId)

        return routeRepository.findById(routeId)
            .switchIfEmpty(Mono.error(NotFoundException("Route not found")))
            .flatMap { route ->
                // Проверяем статус — только PENDING маршруты можно одобрить
                if (route.status != RouteStatus.PENDING) {
                    logger.warn(
                        "Попытка одобрения не-pending маршрута: routeId={}, status={}",
                        routeId, route.status
                    )
                    return@flatMap Mono.error<Route>(
                        ConflictException("Only pending routes can be approved/rejected")
                    )
                }

                // Обновляем статус и approval fields
                val approvedAt = Instant.now()
                val updatedRoute = route.copy(
                    status = RouteStatus.PUBLISHED,
                    approvedBy = userId,
                    approvedAt = approvedAt,
                    updatedAt = Instant.now()
                )
                routeRepository.save(updatedRoute)
            }
            .flatMap { savedRoute ->
                // Публикуем cache invalidation в Redis для gateway-core
                routeEventPublisher.publishRouteChanged(savedRoute.id!!)
                    .thenReturn(savedRoute)
            }
            .flatMap { savedRoute ->
                // Записываем audit log
                auditService.log(
                    entityType = "route",
                    entityId = savedRoute.id.toString(),
                    action = "approved",
                    userId = userId,
                    username = username,
                    changes = mapOf(
                        "oldStatus" to RouteStatus.PENDING.name.lowercase(),
                        "newStatus" to RouteStatus.PUBLISHED.name.lowercase(),
                        "approvedAt" to savedRoute.approvedAt.toString()
                    )
                ).thenReturn(savedRoute)
            }
            .map { RouteResponse.from(it) }
            .doOnSuccess {
                logger.info(
                    "Маршрут одобрен: routeId={}, approvedBy={}",
                    routeId, userId
                )
            }
    }

    /**
     * Отклоняет маршрут (PENDING → REJECTED).
     *
     * Доступно только для пользователей с ролью SECURITY или ADMIN.
     * Требует указания причины отклонения.
     *
     * @param routeId ID маршрута
     * @param userId ID пользователя, отклоняющего маршрут
     * @param username имя пользователя для аудит-лога
     * @param reason причина отклонения (обязательна, не может быть пустой)
     * @return Mono<RouteResponse> обновлённый маршрут со статусом REJECTED
     * @throws NotFoundException если маршрут не найден
     * @throws ConflictException если маршрут не в статусе PENDING
     * @throws ValidationException если причина отклонения не указана
     *
     * Story 4.2, AC3: Успешное отклонение маршрута
     * Story 4.2, AC4: Отклонение без причины
     */
    fun reject(
        routeId: UUID,
        userId: UUID,
        username: String,
        reason: String
    ): Mono<RouteResponse> {
        logger.debug("Отклонение маршрута: routeId={}, userId={}", routeId, userId)

        // Валидация причины отклонения
        if (reason.isBlank()) {
            return Mono.error(ValidationException("Rejection reason is required"))
        }

        return routeRepository.findById(routeId)
            .switchIfEmpty(Mono.error(NotFoundException("Route not found")))
            .flatMap { route ->
                // Проверяем статус — только PENDING маршруты можно отклонить
                if (route.status != RouteStatus.PENDING) {
                    logger.warn(
                        "Попытка отклонения не-pending маршрута: routeId={}, status={}",
                        routeId, route.status
                    )
                    return@flatMap Mono.error<Route>(
                        ConflictException("Only pending routes can be approved/rejected")
                    )
                }

                // Обновляем статус и rejection fields
                val rejectedAt = Instant.now()
                val updatedRoute = route.copy(
                    status = RouteStatus.REJECTED,
                    rejectedBy = userId,
                    rejectedAt = rejectedAt,
                    rejectionReason = reason,
                    updatedAt = Instant.now()
                )
                routeRepository.save(updatedRoute)
            }
            .flatMap { savedRoute ->
                // Записываем audit log
                auditService.log(
                    entityType = "route",
                    entityId = savedRoute.id.toString(),
                    action = "rejected",
                    userId = userId,
                    username = username,
                    changes = mapOf(
                        "oldStatus" to RouteStatus.PENDING.name.lowercase(),
                        "newStatus" to RouteStatus.REJECTED.name.lowercase(),
                        "rejectedAt" to savedRoute.rejectedAt.toString(),
                        "rejectionReason" to reason
                    )
                ).thenReturn(savedRoute)
            }
            .map { RouteResponse.from(it) }
            .doOnSuccess {
                logger.info(
                    "Маршрут отклонён: routeId={}, rejectedBy={}, reason={}",
                    routeId, userId, reason
                )
            }
    }
}
