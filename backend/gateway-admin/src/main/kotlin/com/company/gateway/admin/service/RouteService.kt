package com.company.gateway.admin.service

import com.company.gateway.admin.dto.CreateRouteRequest
import com.company.gateway.admin.dto.PagedResponse
import com.company.gateway.admin.dto.RateLimitInfo
import com.company.gateway.admin.dto.RouteDetailResponse
import com.company.gateway.admin.dto.RouteFilterRequest
import com.company.gateway.admin.dto.RouteListResponse
import com.company.gateway.admin.dto.RouteResponse
import com.company.gateway.admin.dto.UpdateRouteRequest
import com.company.gateway.admin.dto.UpstreamsListResponse
import com.company.gateway.admin.exception.ConflictException
import com.company.gateway.admin.exception.NotFoundException
import com.company.gateway.admin.exception.ValidationException
import com.company.gateway.admin.repository.RateLimitRepository
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.common.model.Role
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
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
    private val rateLimitRepository: RateLimitRepository,
    private val userRepository: UserRepository,
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
                    // Валидация rateLimitId если указан (Story 5.5)
                    val rateLimitValidation: Mono<Boolean> = if (request.rateLimitId != null) {
                        rateLimitRepository.existsById(request.rateLimitId)
                            .flatMap { rateLimitExists ->
                                if (!rateLimitExists) {
                                    Mono.error(ValidationException("Rate limit policy not found"))
                                } else {
                                    Mono.just(true)
                                }
                            }
                    } else {
                        Mono.just(true) // Нет rateLimitId — валидация пройдена
                    }

                    rateLimitValidation.flatMap {
                        val route = Route(
                            path = request.path,
                            upstreamUrl = request.upstreamUrl,
                            methods = request.methods,
                            description = request.description,
                            status = RouteStatus.DRAFT,
                            createdBy = userId,
                            createdAt = Instant.now(),
                            updatedAt = Instant.now(),
                            rateLimitId = request.rateLimitId,
                            authRequired = request.authRequired,
                            allowedConsumers = request.allowedConsumers
                        )
                        routeRepository.save(route)
                    }
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
                        "description" to savedRoute.description,
                        "rateLimitId" to savedRoute.rateLimitId,
                        "authRequired" to savedRoute.authRequired,
                        "allowedConsumers" to savedRoute.allowedConsumers
                    )
                ).thenReturn(savedRoute)
            }
            .flatMap { savedRoute ->
                // Загружаем информацию о rate limit для response (Story 5.5)
                // Передаём username создателя напрямую (это текущий пользователь) — Story 8.4
                loadRateLimitInfo(savedRoute.rateLimitId)
                    .map { rateLimitInfo -> RouteResponse.from(savedRoute, rateLimitInfo, username) }
                    .switchIfEmpty(Mono.defer { Mono.just(RouteResponse.from(savedRoute, null, username)) })
            }
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

                // Сохраняем значения из mutable request в локальные val для smart cast
                val requestPath = request.path
                val requestRateLimitId = request.rateLimitId
                val rateLimitIdProvided = request.rateLimitIdProvided

                // Проверяем уникальность нового path (если изменяется)
                val pathCheck = if (requestPath != null && requestPath != route.path) {
                    routeRepository.existsByPath(requestPath)
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

                // Валидация rateLimitId (только если явно передан в запросе)
                val rateLimitCheck = if (rateLimitIdProvided && requestRateLimitId != null) {
                    // rateLimitId явно указан — проверяем существование политики
                    rateLimitRepository.existsById(requestRateLimitId)
                        .flatMap { exists ->
                            if (!exists) {
                                Mono.error<Boolean>(ValidationException("Rate limit policy not found"))
                            } else {
                                Mono.just(true)
                            }
                        }
                } else {
                    // rateLimitId не передан (partial update) или передан как null (удаление) — валидно
                    Mono.just(true)
                }

                // Сохраняем значения auth полей из request
                val requestAuthRequired = request.authRequired
                val authRequiredProvided = request.authRequiredProvided
                val requestAllowedConsumers = request.allowedConsumers
                val allowedConsumersProvided = request.allowedConsumersProvided

                // Объединяем проверку path и rateLimitId
                pathCheck.then(rateLimitCheck).flatMap {
                    val oldValues = mapOf(
                        "path" to route.path,
                        "upstreamUrl" to route.upstreamUrl,
                        "methods" to route.methods,
                        "description" to route.description,
                        "rateLimitId" to route.rateLimitId,
                        "authRequired" to route.authRequired,
                        "allowedConsumers" to route.allowedConsumers
                    )

                    // Определяем новое значение rateLimitId:
                    // - Если rateLimitIdProvided = false → сохраняем текущее (partial update)
                    // - Если rateLimitIdProvided = true → используем requestRateLimitId (назначить или удалить)
                    val newRateLimitId = if (rateLimitIdProvided) {
                        requestRateLimitId // null = удалить, UUID = назначить
                    } else {
                        route.rateLimitId // partial update — сохранить текущее
                    }

                    // Определяем новое значение authRequired (Story 12.7)
                    val newAuthRequired = if (authRequiredProvided && requestAuthRequired != null) {
                        requestAuthRequired
                    } else {
                        route.authRequired // partial update — сохранить текущее
                    }

                    // Определяем новое значение allowedConsumers (Story 12.7)
                    val newAllowedConsumers = if (allowedConsumersProvided) {
                        requestAllowedConsumers // null = очистить whitelist, List = установить
                    } else {
                        route.allowedConsumers // partial update — сохранить текущее
                    }

                    val updatedRoute = route.copy(
                        path = request.path ?: route.path,
                        upstreamUrl = request.upstreamUrl ?: route.upstreamUrl,
                        methods = request.methods ?: route.methods,
                        description = request.description ?: route.description,
                        rateLimitId = newRateLimitId,
                        authRequired = newAuthRequired,
                        allowedConsumers = newAllowedConsumers,
                        updatedAt = Instant.now()
                    )

                    routeRepository.save(updatedRoute)
                        .flatMap { saved ->
                            val newValues = mapOf(
                                "path" to saved.path,
                                "upstreamUrl" to saved.upstreamUrl,
                                "methods" to saved.methods,
                                "description" to saved.description,
                                "rateLimitId" to saved.rateLimitId,
                                "authRequired" to saved.authRequired,
                                "allowedConsumers" to saved.allowedConsumers
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
            .flatMap { saved ->
                // Загружаем информацию о rate limit и username создателя для response — Story 8.4
                loadRateLimitInfo(saved.rateLimitId)
                    .flatMap { rateLimit ->
                        loadCreatorUsername(saved.createdBy)
                            .map { creatorUsername -> RouteResponse.from(saved, rateLimit, creatorUsername) }
                            .defaultIfEmpty(RouteResponse.from(saved, rateLimit, null))
                    }
                    .switchIfEmpty(Mono.defer {
                        loadCreatorUsername(saved.createdBy)
                            .map { creatorUsername -> RouteResponse.from(saved, null, creatorUsername) }
                            .defaultIfEmpty(RouteResponse.from(saved, null, null))
                    })
            }
            .doOnSuccess { logger.info("Маршрут обновлён: id={}, userId={}", id, userId) }
    }

    /**
     * Загружает информацию о политике rate limit по ID.
     *
     * Возвращает Mono с RateLimitInfo если политика найдена, или пустой Mono если не найдена/не указана.
     *
     * @param rateLimitId ID политики (может быть null)
     * @return Mono<RateLimitInfo> информация о политике (может быть пустым)
     */
    private fun loadRateLimitInfo(rateLimitId: UUID?): Mono<RateLimitInfo> {
        return if (rateLimitId != null) {
            rateLimitRepository.findById(rateLimitId)
                .map { RateLimitInfo.from(it) }
        } else {
            Mono.empty()
        }
    }

    /**
     * Загружает username создателя маршрута по ID.
     *
     * Возвращает Mono с username если пользователь найден, или пустой Mono если не найден/не указан.
     * Story 8.4.
     *
     * @param createdBy ID создателя (может быть null)
     * @return Mono<String> username создателя (может быть пустым)
     */
    private fun loadCreatorUsername(createdBy: UUID?): Mono<String> {
        return if (createdBy != null) {
            userRepository.findById(createdBy)
                .map { it.username }
        } else {
            Mono.empty()
        }
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
     * Получает маршрут по ID с информацией о rate limit.
     *
     * @param id ID маршрута
     * @return Mono<RouteResponse> маршрут с данными rate limit
     * @throws NotFoundException если маршрут не найден
     */
    fun findById(id: UUID): Mono<RouteResponse> {
        return routeRepository.findById(id)
            .switchIfEmpty(Mono.error(NotFoundException("Route not found")))
            .flatMap { route ->
                // Загружаем информацию о rate limit и username создателя — Story 8.4
                loadRateLimitInfo(route.rateLimitId)
                    .flatMap { rateLimit ->
                        loadCreatorUsername(route.createdBy)
                            .map { creatorUsername -> RouteResponse.from(route, rateLimit, creatorUsername) }
                            .defaultIfEmpty(RouteResponse.from(route, rateLimit, null))
                    }
                    .switchIfEmpty(Mono.defer {
                        loadCreatorUsername(route.createdBy)
                            .map { creatorUsername -> RouteResponse.from(route, null, creatorUsername) }
                            .defaultIfEmpty(RouteResponse.from(route, null, null))
                    })
            }
    }

    /**
     * Проверяет существование маршрута с указанным path.
     *
     * Используется для inline валидации уникальности path в форме.
     * Story 3.5, AC2.
     *
     * @param path путь маршрута для проверки
     * @return Mono<Boolean> true если маршрут с таким path существует
     */
    fun existsByPath(path: String): Mono<Boolean> {
        return routeRepository.existsByPath(path)
    }

    /**
     * Получает детальную информацию о маршруте по ID с username создателя.
     *
     * Используется для GET /api/v1/routes/{id} (Story 3.3, AC1).
     * Выполняет JOIN с таблицей users для получения creatorUsername.
     *
     * @param id ID маршрута
     * @return Mono<RouteDetailResponse> детали маршрута с информацией о создателе
     * @throws NotFoundException если маршрут не найден
     */
    fun findByIdWithCreator(id: UUID): Mono<RouteDetailResponse> {
        return routeRepository.findByIdWithCreator(id)
            .switchIfEmpty(Mono.error(NotFoundException("Route not found")))
            .map { it.toResponse() }
            .doOnSuccess { logger.debug("Получены детали маршрута: id={}", id) }
    }

    /**
     * Клонирует существующий маршрут.
     *
     * Создаёт копию маршрута со статусом DRAFT и автоматически генерирует
     * уникальный path с суффиксом -copy или -copy-N (Story 3.3, AC3, AC4).
     *
     * @param routeId ID маршрута для клонирования
     * @param currentUserId ID текущего пользователя (станет владельцем клона)
     * @param currentUsername username текущего пользователя для аудит-лога
     * @return Mono<RouteDetailResponse> клонированный маршрут
     * @throws NotFoundException если маршрут не найден
     */
    fun cloneRoute(
        routeId: UUID,
        currentUserId: UUID,
        currentUsername: String
    ): Mono<RouteDetailResponse> {
        return routeRepository.findById(routeId)
            .switchIfEmpty(Mono.error(NotFoundException("Route not found")))
            .flatMap { original ->
                // Оборачиваем генерацию path + save в retry для обработки race condition
                // При параллельном клонировании может возникнуть конфликт path
                generateUniquePath(original.path)
                    .flatMap { newPath ->
                        val cloned = Route(
                            path = newPath,
                            upstreamUrl = original.upstreamUrl,
                            methods = original.methods,
                            description = original.description,
                            status = RouteStatus.DRAFT,
                            createdBy = currentUserId,
                            createdAt = Instant.now(),
                            updatedAt = Instant.now()
                        )
                        routeRepository.save(cloned)
                    }
                    .retryWhen(
                        Retry.max(3)
                            .filter { it is DataIntegrityViolationException }
                            .doBeforeRetry { logger.debug("Retry клонирования из-за конфликта path, попытка {}", it.totalRetries() + 1) }
                    )
                    .flatMap { savedRoute ->
                        // Логируем клонирование
                        auditService.logCreated(
                            entityType = "route",
                            entityId = savedRoute.id.toString(),
                            userId = currentUserId,
                            username = currentUsername,
                            entity = mapOf(
                                "path" to savedRoute.path,
                                "upstreamUrl" to savedRoute.upstreamUrl,
                                "methods" to savedRoute.methods,
                                "description" to savedRoute.description,
                                "clonedFrom" to routeId.toString()
                            )
                        ).thenReturn(savedRoute)
                    }
            }
            .flatMap { savedRoute ->
                // Возвращаем детали с username создателя
                routeRepository.findByIdWithCreator(savedRoute.id!!)
                    .map { it.toResponse() }
            }
            .doOnSuccess { logger.info("Маршрут клонирован: sourceId={}, newPath={}", routeId, it.path) }
    }

    /**
     * Генерирует уникальный path для клонированного маршрута.
     *
     * Алгоритм:
     * 1. Пробует originalPath-copy
     * 2. Если занят — пробует originalPath-copy-2, -copy-3 и т.д.
     *
     * @param originalPath оригинальный path маршрута
     * @return Mono<String> уникальный path для клона
     */
    private fun generateUniquePath(originalPath: String): Mono<String> {
        val baseCopyPath = "$originalPath-copy"

        return routeRepository.existsByPath(baseCopyPath)
            .flatMap { exists ->
                if (!exists) {
                    // -copy свободен
                    Mono.just(baseCopyPath)
                } else {
                    // Ищем следующий свободный суффикс
                    findNextAvailablePath(originalPath)
                }
            }
    }

    /**
     * Находит следующий свободный номер для суффикса -copy-N.
     *
     * Загружает все существующие пути с паттерном originalPath-copy%,
     * извлекает максимальный номер и возвращает следующий.
     */
    private fun findNextAvailablePath(originalPath: String): Mono<String> {
        val pattern = "$originalPath-copy%"

        return routeRepository.findByPathLike(pattern)
            .map { it.path }
            .collectList()
            .map { existingPaths ->
                // Находим максимальный суффикс среди путей вида:
                // /api/orders-copy (считается как 1)
                // /api/orders-copy-2, /api/orders-copy-3, etc.
                val regex = Regex("${Regex.escape(originalPath)}-copy(?:-(\\d+))?$")

                val maxSuffix = existingPaths
                    .mapNotNull { path ->
                        regex.find(path)?.let { match ->
                            // Если группа (\\d+) не захвачена — это -copy без номера, считаем как 1
                            match.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
                        }
                    }
                    .maxOrNull() ?: 1

                "$originalPath-copy-${maxSuffix + 1}"
            }
    }

    /**
     * Получает список маршрутов со статусом pending с сортировкой и пагинацией.
     *
     * Default сортировка: submittedAt ascending (FIFO очередь).
     * Поддерживает sort parameter в формате "field:direction".
     *
     * Story 4.3, AC1, AC2, AC3, AC5.
     *
     * @param sort параметр сортировки в формате "submittedAt:asc" или "submittedAt:desc"
     * @param offset смещение от начала списка
     * @param limit максимальное количество элементов
     * @return Mono<PagedResponse<RouteDetailResponse>> пагинированный список pending маршрутов
     */
    fun findPendingRoutes(sort: String?, offset: Int, limit: Int): Mono<PagedResponse<RouteDetailResponse>> {
        val (sortField, sortDirection) = parseSort(sort)

        logger.debug("Получение pending маршрутов: sort={}, offset={}, limit={}", sort, offset, limit)

        val routesMono = routeRepository.findPendingWithCreator(sortField, sortDirection, offset, limit)
            .map { it.toResponse() }
            .collectList()

        val totalMono = routeRepository.countPending()

        return Mono.zip(routesMono, totalMono)
            .map { tuple ->
                PagedResponse(
                    items = tuple.t1,
                    total = tuple.t2,
                    offset = offset,
                    limit = limit
                )
            }
    }

    /**
     * Парсит sort parameter в пару (поле, направление).
     *
     * Формат: "field:direction", например "submittedAt:asc" или "submittedAt:desc".
     * Default: ("submitted_at", "ASC") — FIFO очередь.
     *
     * @param sort строка сортировки или null
     * @return Pair<String, String> — (имя колонки БД, направление ASC/DESC)
     */
    internal fun parseSort(sort: String?): Pair<String, String> {
        if (sort.isNullOrBlank()) {
            return "submitted_at" to "ASC"
        }
        val parts = sort.split(":")
        val field = when (parts[0].trim()) {
            "submittedAt" -> "submitted_at"
            else -> "submitted_at"
        }
        val direction = if (parts.getOrNull(1)?.trim()?.lowercase() == "desc") "DESC" else "ASC"
        return field to direction
    }

    /**
     * Получает список маршрутов с пагинацией.
     *
     * @param offset смещение от начала списка
     * @param limit максимальное количество элементов
     * @return Mono<RouteListResponse> пагинированный список маршрутов
     */
    fun findAll(offset: Int, limit: Int): Mono<RouteListResponse> {
        return findAllWithFilters(RouteFilterRequest(offset = offset, limit = limit), null)
    }

    /**
     * Получает список маршрутов с фильтрацией и пагинацией.
     *
     * Поддерживает фильтрацию по:
     * - status: статус маршрута (draft, pending, published, rejected)
     * - createdBy: "me" преобразуется в userId текущего пользователя
     * - search: текстовый поиск по path и description (case-insensitive)
     * - upstream: поиск по части upstream URL (ILIKE, case-insensitive) — Story 7.4, AC1
     * - upstreamExact: точное совпадение upstream URL (case-sensitive) — Story 7.4, AC2
     *
     * Фильтры применяются с AND логикой.
     *
     * @param filter параметры фильтрации и пагинации
     * @param currentUserId ID текущего пользователя (для преобразования "me")
     * @return Mono<RouteListResponse> пагинированный список маршрутов
     */
    fun findAllWithFilters(filter: RouteFilterRequest, currentUserId: UUID?): Mono<RouteListResponse> {
        logger.debug(
            "Поиск маршрутов: status={}, createdBy={}, search={}, upstream={}, upstreamExact={}, offset={}, limit={}",
            filter.status, filter.createdBy, filter.search, filter.upstream, filter.upstreamExact, filter.offset, filter.limit
        )

        // Преобразуем createdBy="me" в UUID текущего пользователя
        // Если createdBy указан, но не является "me" и не валидный UUID — возвращаем пустой результат
        val createdByResult = parseCreatedBy(filter.createdBy, currentUserId)
        if (createdByResult.isInvalid) {
            logger.debug("Невалидный createdBy={}, возвращаем пустой результат", filter.createdBy)
            return Mono.just(
                RouteListResponse(
                    items = emptyList(),
                    total = 0,
                    offset = filter.offset,
                    limit = filter.limit
                )
            )
        }

        // Загружаем маршруты
        val routesMono = routeRepository.findWithFilters(
            status = filter.status,
            createdBy = createdByResult.uuid,
            search = filter.search,
            upstream = filter.upstream,
            upstreamExact = filter.upstreamExact,
            offset = filter.offset,
            limit = filter.limit
        ).collectList()

        val totalMono = routeRepository.countWithFilters(
            status = filter.status,
            createdBy = createdByResult.uuid,
            search = filter.search,
            upstream = filter.upstream,
            upstreamExact = filter.upstreamExact
        )

        return Mono.zip(routesMono, totalMono)
            .flatMap { tuple ->
                val routes = tuple.t1
                val total = tuple.t2

                // Собираем уникальные user IDs (исключая null) — Story 8.4
                val userIds = routes.mapNotNull { it.createdBy }.distinct()

                // Собираем уникальные rateLimitId (исключая null)
                val rateLimitIds = routes.mapNotNull { it.rateLimitId }.distinct()

                // Batch-загрузка пользователей (Story 8.4)
                val usersMono = if (userIds.isEmpty()) {
                    Mono.just(emptyMap<UUID, String>())
                } else {
                    userRepository.findAllById(userIds)
                        .collectMap({ it.id!! }, { it.username })
                }

                // Batch-загрузка rate limits
                val rateLimitsMono = if (rateLimitIds.isEmpty()) {
                    Mono.just(emptyMap<UUID, RateLimitInfo>())
                } else {
                    rateLimitRepository.findAllById(rateLimitIds)
                        .collectMap({ it.id!! }, { RateLimitInfo.from(it) })
                }

                // Объединяем lookup maps и строим response
                Mono.zip(usersMono, rateLimitsMono)
                    .map { lookups ->
                        val usersMap = lookups.t1
                        val rateLimitsMap = lookups.t2

                        RouteListResponse(
                            items = routes.map { route ->
                                RouteResponse.from(
                                    route,
                                    rateLimitsMap[route.rateLimitId],
                                    usersMap[route.createdBy]
                                )
                            },
                            total = total,
                            offset = filter.offset,
                            limit = filter.limit
                        )
                    }
            }
    }

    /**
     * Возвращает список уникальных upstream хостов с количеством маршрутов.
     *
     * Извлекает hostname:port из upstream_url (удаляет схему http:// или https://).
     * Результат отсортирован по routeCount DESC.
     *
     * Story 7.4, AC3.
     *
     * @return Mono<UpstreamsListResponse> список уникальных хостов с количеством маршрутов
     */
    fun getUpstreams(): Mono<UpstreamsListResponse> {
        logger.debug("Получение списка уникальных upstream хостов")

        return routeRepository.findUniqueUpstreams()
            .collectList()
            .map { upstreams ->
                UpstreamsListResponse(upstreams = upstreams)
            }
            .doOnSuccess { logger.debug("Найдено {} уникальных upstream хостов", it.upstreams.size) }
    }

    /**
     * Результат парсинга createdBy параметра.
     *
     * @property uuid распарсенный UUID (null если не указан или "me" без currentUserId)
     * @property isInvalid true если createdBy указан, но невалиден
     */
    private data class CreatedByParseResult(val uuid: UUID?, val isInvalid: Boolean)

    /**
     * Парсит createdBy параметр в UUID.
     *
     * - "me" → currentUserId
     * - валидный UUID → UUID
     * - невалидная строка → isInvalid=true
     * - null → uuid=null, isInvalid=false
     */
    private fun parseCreatedBy(createdBy: String?, currentUserId: UUID?): CreatedByParseResult {
        return when {
            createdBy == null -> CreatedByParseResult(null, false)
            createdBy == "me" -> CreatedByParseResult(currentUserId, false)
            else -> {
                try {
                    CreatedByParseResult(UUID.fromString(createdBy), false)
                } catch (e: IllegalArgumentException) {
                    CreatedByParseResult(null, true)
                }
            }
        }
    }
}
