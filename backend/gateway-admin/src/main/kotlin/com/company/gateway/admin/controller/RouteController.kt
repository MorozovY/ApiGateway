package com.company.gateway.admin.controller

import com.company.gateway.admin.dto.CreateRouteRequest
import com.company.gateway.admin.dto.PagedResponse
import com.company.gateway.admin.dto.RejectRouteRequest
import com.company.gateway.admin.dto.RouteDetailResponse
import com.company.gateway.admin.dto.RouteFilterRequest
import com.company.gateway.admin.dto.RouteListResponse
import com.company.gateway.admin.dto.RouteResponse
import com.company.gateway.admin.dto.UpdateRouteRequest
import com.company.gateway.admin.dto.UpstreamsListResponse
import com.company.gateway.admin.exception.ValidationException
import com.company.gateway.common.model.RouteStatus
import com.company.gateway.admin.security.RequireRole
import com.company.gateway.admin.security.SecurityContextUtils
import com.company.gateway.admin.service.ApprovalService
import com.company.gateway.admin.service.RouteService
import com.company.gateway.common.model.Role
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Контроллер для управления маршрутами.
 *
 * Реализует CRUD операции для маршрутов:
 * - POST /api/v1/routes — создание маршрута (Story 3.1)
 * - PUT /api/v1/routes/{id} — обновление маршрута (Story 3.1)
 * - DELETE /api/v1/routes/{id} — удаление маршрута (Story 3.1)
 * - GET /api/v1/routes/{id} — получение маршрута по ID (Story 3.3)
 * - GET /api/v1/routes — список с фильтрацией и поиском (Story 3.2)
 * - POST /api/v1/routes/{id}/clone — клонирование маршрута (Story 3.3)
 *
 * Фильтрация и поиск (Story 3.2):
 * - status: фильтр по статусу (draft, pending, published, rejected)
 * - createdBy: "me" для своих маршрутов или UUID пользователя
 * - search: текстовый поиск по path и description (case-insensitive, ILIKE)
 * - offset/limit: пагинация
 *
 * Разграничение доступа:
 * - Developer может создавать маршруты и управлять только своими draft маршрутами
 * - Security/Admin могут управлять любыми draft маршрутами
 */
@RestController
@RequestMapping("/api/v1/routes")
class RouteController(
    private val routeService: RouteService,
    private val approvalService: ApprovalService
) {
    companion object {
        /** Максимальное количество элементов на странице */
        const val MAX_LIMIT = 100
        /** Максимальная длина поискового запроса */
        const val MAX_SEARCH_LENGTH = 100
    }

    /**
     * Получение списка маршрутов, ожидающих согласования.
     *
     * Возвращает все маршруты со статусом pending.
     * Default сортировка: submittedAt ascending (FIFO очередь).
     *
     * Доступно только SECURITY и ADMIN ролям (AC4).
     * Story 4.3, AC1-AC5.
     */
    @GetMapping("/pending")
    @RequireRole(Role.SECURITY)
    fun listPendingRoutes(
        @RequestParam(required = false) sort: String?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "20") limit: Int
    ): Mono<ResponseEntity<PagedResponse<RouteDetailResponse>>> {
        // Валидация параметров пагинации
        if (offset < 0) {
            return Mono.error(ValidationException("Offset must be greater than or equal to 0"))
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            return Mono.error(ValidationException("Limit must be between 1 and $MAX_LIMIT"))
        }

        return routeService.findPendingRoutes(sort, offset, limit)
            .map { ResponseEntity.ok(it) }
    }

    /**
     * Получение списка уникальных upstream хостов.
     *
     * Возвращает все уникальные upstream хосты (hostname:port) с количеством маршрутов.
     * Схема (http:// или https://) удаляется из URL.
     * Результат отсортирован по routeCount DESC.
     *
     * Доступно всем аутентифицированным пользователям (DEVELOPER и выше).
     * Story 7.4, AC3.
     */
    @GetMapping("/upstreams")
    @RequireRole(Role.DEVELOPER)
    fun listUpstreams(): Mono<ResponseEntity<UpstreamsListResponse>> {
        return routeService.getUpstreams()
            .map { ResponseEntity.ok(it) }
    }

    /**
     * Получение списка маршрутов с фильтрацией и пагинацией.
     *
     * Поддерживает фильтрацию по:
     * - status: статус маршрута (draft, pending, published, rejected)
     * - createdBy: "me" для своих маршрутов или UUID пользователя
     * - search: текстовый поиск по path и description (case-insensitive)
     * - upstream: поиск по части upstream URL (ILIKE, case-insensitive) — Story 7.4, AC1
     * - upstreamExact: точное совпадение upstream URL (case-sensitive) — Story 7.4, AC2
     *
     * Пагинация:
     * - offset: смещение от начала списка (default 0)
     * - limit: количество элементов на странице (default 20, max 100)
     *
     * ВАЖНО: upstream и upstreamExact нельзя использовать одновременно (400 Bad Request).
     *
     * Доступно всем аутентифицированным пользователям (DEVELOPER и выше).
     */
    @GetMapping
    @RequireRole(Role.DEVELOPER)
    fun listRoutes(
        @RequestParam(required = false) status: RouteStatus?,
        @RequestParam(required = false) createdBy: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) upstream: String?,
        @RequestParam(required = false) upstreamExact: String?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "20") limit: Int
    ): Mono<ResponseEntity<RouteListResponse>> {
        // Валидация параметров с RFC 7807 ошибками
        if (offset < 0) {
            return Mono.error(ValidationException("Offset must be greater than or equal to 0"))
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            return Mono.error(ValidationException("Limit must be between 1 and $MAX_LIMIT"))
        }
        if (search != null && (search.isEmpty() || search.length > MAX_SEARCH_LENGTH)) {
            return Mono.error(ValidationException("Search query must be between 1 and $MAX_SEARCH_LENGTH characters"))
        }

        // Story 7.4: upstream и upstreamExact нельзя использовать одновременно
        if (upstream != null && upstreamExact != null) {
            return Mono.error(ValidationException("Cannot specify both 'upstream' and 'upstreamExact' parameters"))
        }

        // Story 7.4: валидация длины upstream параметров
        if (upstream != null && upstream.length > MAX_SEARCH_LENGTH) {
            return Mono.error(ValidationException("Upstream filter must be between 1 and $MAX_SEARCH_LENGTH characters"))
        }
        if (upstreamExact != null && upstreamExact.length > 2000) {
            return Mono.error(ValidationException("Upstream exact filter must be less than 2000 characters"))
        }

        val filter = RouteFilterRequest(
            status = status,
            createdBy = createdBy,
            search = search,
            upstream = upstream,
            upstreamExact = upstreamExact,
            offset = offset,
            limit = limit
        )

        return SecurityContextUtils.currentUser()
            .flatMap { user ->
                routeService.findAllWithFilters(filter, user.userId)
            }
            .map { ResponseEntity.ok(it) }
    }

    /**
     * Получение деталей маршрута по ID.
     *
     * Возвращает полную информацию о маршруте включая username создателя.
     * Доступно всем аутентифицированным пользователям (DEVELOPER и выше).
     * Story 3.3, AC1, AC2.
     */
    @GetMapping("/{id}")
    @RequireRole(Role.DEVELOPER)
    fun getRoute(@PathVariable id: UUID): Mono<ResponseEntity<RouteDetailResponse>> {
        return routeService.findByIdWithCreator(id)
            .map { ResponseEntity.ok(it) }
    }

    /**
     * Клонирование маршрута.
     *
     * Создаёт копию существующего маршрута со статусом DRAFT.
     * Path автоматически получает суффикс -copy (или -copy-N при конфликте).
     * Владельцем клона становится текущий пользователь.
     *
     * Доступно всем аутентифицированным пользователям (DEVELOPER и выше).
     * Story 3.3, AC3, AC4, AC5.
     */
    @PostMapping("/{id}/clone")
    @RequireRole(Role.DEVELOPER)
    fun cloneRoute(@PathVariable id: UUID): Mono<ResponseEntity<RouteDetailResponse>> {
        return SecurityContextUtils.currentUser()
            .flatMap { user ->
                routeService.cloneRoute(id, user.userId, user.username)
            }
            .map { ResponseEntity.status(HttpStatus.CREATED).body(it) }
    }

    /**
     * Создание нового маршрута.
     *
     * Маршрут создаётся со статусом DRAFT и привязывается к текущему пользователю.
     * Доступно всем аутентифицированным пользователям (DEVELOPER и выше).
     */
    @PostMapping
    @RequireRole(Role.DEVELOPER)
    fun createRoute(
        @Valid @RequestBody request: CreateRouteRequest
    ): Mono<ResponseEntity<RouteResponse>> {
        return SecurityContextUtils.currentUser()
            .flatMap { user ->
                routeService.create(request, user.userId, user.username)
            }
            .map { ResponseEntity.status(HttpStatus.CREATED).body(it) }
    }

    /**
     * Обновление маршрута.
     *
     * Developer может обновлять только свои draft маршруты.
     * Security и Admin могут обновлять любые draft маршруты.
     */
    @PutMapping("/{id}")
    @RequireRole(Role.DEVELOPER)
    fun updateRoute(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateRouteRequest
    ): Mono<ResponseEntity<RouteResponse>> {
        return SecurityContextUtils.currentUser()
            .flatMap { user ->
                routeService.update(id, request, user.userId, user.username, user.role)
            }
            .map { ResponseEntity.ok(it) }
    }

    /**
     * Удаление маршрута.
     *
     * Developer может удалять только свои draft маршруты.
     * Security и Admin могут удалять любые draft маршруты.
     */
    @DeleteMapping("/{id}")
    @RequireRole(Role.DEVELOPER)
    fun deleteRoute(@PathVariable id: UUID): Mono<ResponseEntity<Void>> {
        return SecurityContextUtils.currentUser()
            .flatMap { user ->
                routeService.delete(id, user.userId, user.username, user.role)
            }
            .then(Mono.just(ResponseEntity.noContent().build()))
    }

    /**
     * Проверка существования маршрута с указанным path.
     *
     * Используется для inline валидации уникальности path в форме создания/редактирования.
     * Возвращает { "exists": true/false }.
     *
     * Story 3.5, AC2 — валидация уникальности path.
     */
    @GetMapping("/check-path")
    @RequireRole(Role.DEVELOPER)
    fun checkPathExists(@RequestParam path: String): Mono<ResponseEntity<Map<String, Boolean>>> {
        return routeService.existsByPath(path)
            .map { exists ->
                ResponseEntity.ok(mapOf("exists" to exists))
            }
    }

    /**
     * Отправка маршрута на согласование.
     *
     * Доступно только владельцу маршрута (DEVELOPER и выше).
     * Маршрут должен быть в статусе DRAFT.
     * Story 4.1, AC1-AC5.
     */
    @PostMapping("/{id}/submit")
    @RequireRole(Role.DEVELOPER)
    fun submitRoute(@PathVariable id: UUID): Mono<ResponseEntity<RouteResponse>> {
        return SecurityContextUtils.currentUser()
            .flatMap { user ->
                approvalService.submitForApproval(id, user.userId, user.username)
            }
            .map { ResponseEntity.ok(it) }
    }

    /**
     * Одобрение маршрута.
     *
     * Доступно только SECURITY и ADMIN ролям.
     * Переводит маршрут из PENDING в PUBLISHED и публикует
     * событие cache invalidation в Redis.
     *
     * Story 4.2, AC1, AC2, AC5, AC6, AC7.
     */
    @PostMapping("/{id}/approve")
    @RequireRole(Role.SECURITY)
    fun approveRoute(@PathVariable id: UUID): Mono<ResponseEntity<RouteResponse>> {
        return SecurityContextUtils.currentUser()
            .flatMap { user ->
                approvalService.approve(id, user.userId, user.username)
            }
            .map { ResponseEntity.ok(it) }
    }

    /**
     * Отклонение маршрута.
     *
     * Доступно только SECURITY и ADMIN ролям.
     * Требует указания причины отклонения.
     * Переводит маршрут из PENDING в REJECTED.
     *
     * Story 4.2, AC3, AC4, AC5, AC6, AC7.
     */
    @PostMapping("/{id}/reject")
    @RequireRole(Role.SECURITY)
    fun rejectRoute(
        @PathVariable id: UUID,
        @Valid @RequestBody request: RejectRouteRequest
    ): Mono<ResponseEntity<RouteResponse>> {
        return SecurityContextUtils.currentUser()
            .flatMap { user ->
                approvalService.reject(id, user.userId, user.username, request.reason)
            }
            .map { ResponseEntity.ok(it) }
    }
}
