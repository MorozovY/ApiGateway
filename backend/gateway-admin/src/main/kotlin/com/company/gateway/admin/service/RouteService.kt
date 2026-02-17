package com.company.gateway.admin.service

import com.company.gateway.admin.dto.CreateRouteRequest
import com.company.gateway.admin.dto.RouteListResponse
import com.company.gateway.admin.dto.RouteResponse
import com.company.gateway.admin.dto.UpdateRouteRequest
import com.company.gateway.admin.exception.ConflictException
import com.company.gateway.admin.exception.NotFoundException
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.common.model.Role
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * Сервис для управления маршрутами.
 *
 * Реализует бизнес-логику CRUD операций с маршрутами:
 * - Создание маршрута с проверкой уникальности path
 * - Обновление маршрута с проверкой ownership и статуса
 * - Удаление маршрута с проверкой ownership и статуса
 * - Получение маршрута по ID
 * - Получение списка маршрутов с пагинацией
 */
@Service
class RouteService(
    private val routeRepository: RouteRepository,
    private val auditService: AuditService
) {
    private val logger = LoggerFactory.getLogger(RouteService::class.java)

    /**
     * Создаёт новый маршрут.
     *
     * Маршрут создаётся со статусом DRAFT и привязывается к текущему пользователю.
     * Проверяет уникальность path перед созданием.
     *
     * @param request данные для создания маршрута
     * @param userId ID пользователя, создающего маршрут
     * @param username имя пользователя для аудит-лога
     * @return Mono<RouteResponse> созданный маршрут
     * @throws ConflictException если маршрут с таким path уже существует
     */
    fun create(
        request: CreateRouteRequest,
        userId: UUID,
        username: String
    ): Mono<RouteResponse> {
        return routeRepository.existsByPath(request.path)
            .flatMap { exists ->
                if (exists) {
                    Mono.error(ConflictException("Route with this path already exists"))
                } else {
                    val route = Route(
                        path = request.path,
                        upstreamUrl = request.upstreamUrl,
                        methods = request.methods,
                        description = request.description,
                        status = RouteStatus.DRAFT,
                        createdBy = userId,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now()
                    )
                    routeRepository.save(route)
                }
            }
            .flatMap { savedRoute ->
                // Логируем создание маршрута
                auditService.logCreated(
                    entityType = "route",
                    entityId = savedRoute.id.toString(),
                    userId = userId,
                    username = username,
                    entity = mapOf(
                        "path" to savedRoute.path,
                        "upstreamUrl" to savedRoute.upstreamUrl,
                        "methods" to savedRoute.methods,
                        "description" to savedRoute.description
                    )
                ).thenReturn(savedRoute)
            }
            .map { RouteResponse.from(it) }
            .doOnSuccess { logger.info("Маршрут создан: path={}, userId={}", request.path, userId) }
    }

    /**
     * Обновляет существующий маршрут.
     *
     * Developer может обновлять только свои draft маршруты.
     * Security и Admin могут обновлять любые draft маршруты.
     * Маршруты не в статусе DRAFT не могут быть обновлены.
     *
     * @param id ID маршрута
     * @param request данные для обновления
     * @param userId ID текущего пользователя
     * @param username имя пользователя для аудит-лога
     * @param userRole роль пользователя
     * @return Mono<RouteResponse> обновлённый маршрут
     * @throws NotFoundException если маршрут не найден
     * @throws ConflictException если маршрут не в статусе DRAFT
     * @throws AccessDeniedException если пользователь не владелец маршрута
     */
    fun update(
        id: UUID,
        request: UpdateRouteRequest,
        userId: UUID,
        username: String,
        userRole: Role
    ): Mono<RouteResponse> {
        return routeRepository.findById(id)
            .switchIfEmpty(Mono.error(NotFoundException("Route not found")))
            .flatMap { route ->
                // Проверяем статус маршрута
                if (route.status != RouteStatus.DRAFT) {
                    return@flatMap Mono.error<Route>(
                        ConflictException("Cannot edit route in current status")
                    )
                }

                // Проверяем ownership для Developer
                if (userRole == Role.DEVELOPER && route.createdBy != userId) {
                    return@flatMap Mono.error<Route>(
                        com.company.gateway.admin.exception.AccessDeniedException(
                            "You can only modify your own routes"
                        )
                    )
                }

                // Проверяем уникальность нового path (если изменяется)
                val pathCheck = if (request.path != null && request.path != route.path) {
                    routeRepository.existsByPath(request.path)
                        .flatMap { exists ->
                            if (exists) {
                                Mono.error<Boolean>(ConflictException("Route with this path already exists"))
                            } else {
                                Mono.just(false)
                            }
                        }
                } else {
                    Mono.just(false)
                }

                pathCheck.flatMap {
                    val oldValues = mapOf(
                        "path" to route.path,
                        "upstreamUrl" to route.upstreamUrl,
                        "methods" to route.methods,
                        "description" to route.description
                    )

                    val updatedRoute = route.copy(
                        path = request.path ?: route.path,
                        upstreamUrl = request.upstreamUrl ?: route.upstreamUrl,
                        methods = request.methods ?: route.methods,
                        description = request.description ?: route.description,
                        updatedAt = Instant.now()
                    )

                    routeRepository.save(updatedRoute)
                        .flatMap { saved ->
                            val newValues = mapOf(
                                "path" to saved.path,
                                "upstreamUrl" to saved.upstreamUrl,
                                "methods" to saved.methods,
                                "description" to saved.description
                            )
                            auditService.logUpdated(
                                entityType = "route",
                                entityId = saved.id.toString(),
                                userId = userId,
                                username = username,
                                oldValues = oldValues,
                                newValues = newValues
                            ).thenReturn(saved)
                        }
                }
            }
            .map { RouteResponse.from(it) }
            .doOnSuccess { logger.info("Маршрут обновлён: id={}, userId={}", id, userId) }
    }

    /**
     * Удаляет маршрут.
     *
     * Developer может удалять только свои draft маршруты.
     * Security и Admin могут удалять любые draft маршруты.
     * Маршруты не в статусе DRAFT не могут быть удалены.
     *
     * @param id ID маршрута
     * @param userId ID текущего пользователя
     * @param username имя пользователя для аудит-лога
     * @param userRole роль пользователя
     * @return Mono<Void>
     * @throws NotFoundException если маршрут не найден
     * @throws ConflictException если маршрут не в статусе DRAFT
     * @throws AccessDeniedException если пользователь не владелец маршрута
     */
    fun delete(
        id: UUID,
        userId: UUID,
        username: String,
        userRole: Role
    ): Mono<Void> {
        return routeRepository.findById(id)
            .switchIfEmpty(Mono.error(NotFoundException("Route not found")))
            .flatMap { route ->
                // Проверяем статус маршрута
                if (route.status != RouteStatus.DRAFT) {
                    return@flatMap Mono.error<Void>(
                        ConflictException("Only draft routes can be deleted")
                    )
                }

                // Проверяем ownership для Developer
                if (userRole == Role.DEVELOPER && route.createdBy != userId) {
                    return@flatMap Mono.error<Void>(
                        com.company.gateway.admin.exception.AccessDeniedException(
                            "You can only modify your own routes"
                        )
                    )
                }

                routeRepository.delete(route)
                    .then(
                        auditService.logDeleted(
                            entityType = "route",
                            entityId = id.toString(),
                            userId = userId,
                            username = username
                        )
                    )
                    .then()
            }
            .doOnSuccess { logger.info("Маршрут удалён: id={}, userId={}", id, userId) }
    }

    /**
     * Получает маршрут по ID.
     *
     * @param id ID маршрута
     * @return Mono<RouteResponse> маршрут
     * @throws NotFoundException если маршрут не найден
     */
    fun findById(id: UUID): Mono<RouteResponse> {
        return routeRepository.findById(id)
            .switchIfEmpty(Mono.error(NotFoundException("Route not found")))
            .map { RouteResponse.from(it) }
    }

    /**
     * Получает список маршрутов с пагинацией.
     *
     * @param offset смещение от начала списка
     * @param limit максимальное количество элементов
     * @return Mono<RouteListResponse> пагинированный список маршрутов
     */
    fun findAll(offset: Int, limit: Int): Mono<RouteListResponse> {
        val routesMono = routeRepository.findAllWithPagination(offset, limit)
            .map { RouteResponse.from(it) }
            .collectList()

        val totalMono = routeRepository.count()

        return Mono.zip(routesMono, totalMono)
            .map { tuple ->
                RouteListResponse(
                    items = tuple.t1,
                    total = tuple.t2,
                    offset = offset,
                    limit = limit
                )
            }
    }
}
