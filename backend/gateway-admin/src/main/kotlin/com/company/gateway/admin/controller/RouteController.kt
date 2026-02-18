package com.company.gateway.admin.controller

import com.company.gateway.admin.dto.CreateRouteRequest
import com.company.gateway.admin.dto.RouteDetailResponse
import com.company.gateway.admin.dto.RouteFilterRequest
import com.company.gateway.admin.dto.RouteListResponse
import com.company.gateway.admin.dto.RouteResponse
import com.company.gateway.admin.dto.UpdateRouteRequest
import com.company.gateway.admin.exception.ValidationException
import com.company.gateway.common.model.RouteStatus
import com.company.gateway.admin.security.RequireRole
import com.company.gateway.admin.security.SecurityContextUtils
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
    private val routeService: RouteService
) {
    companion object {
        /** Максимальное количество элементов на странице */
        const val MAX_LIMIT = 100
        /** Максимальная длина поискового запроса */
        const val MAX_SEARCH_LENGTH = 100
    }

    /**
     * Получение списка маршрутов с фильтрацией и пагинацией.
     *
     * Поддерживает фильтрацию по:
     * - status: статус маршрута (draft, pending, published, rejected)
     * - createdBy: "me" для своих маршрутов или UUID пользователя
     * - search: текстовый поиск по path и description (case-insensitive)
     *
     * Пагинация:
     * - offset: смещение от начала списка (default 0)
     * - limit: количество элементов на странице (default 20, max 100)
     *
     * Доступно всем аутентифицированным пользователям (DEVELOPER и выше).
     */
    @GetMapping
    @RequireRole(Role.DEVELOPER)
    fun listRoutes(
        @RequestParam(required = false) status: RouteStatus?,
        @RequestParam(required = false) createdBy: String?,
        @RequestParam(required = false) search: String?,
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

        val filter = RouteFilterRequest(
            status = status,
            createdBy = createdBy,
            search = search,
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
     * Одобрение маршрута.
     *
     * Доступно только SECURITY и ADMIN ролям.
     * Будет реализовано в Epic 4 (Approval Workflow).
     */
    @PostMapping("/{id}/approve")
    @RequireRole(Role.SECURITY)
    fun approveRoute(@PathVariable id: UUID): Mono<ResponseEntity<Void>> {
        // TODO(Epic 4, Story 4.1): Реализовать approve workflow — переход PENDING → PUBLISHED
        return Mono.just(ResponseEntity.ok().build())
    }

    /**
     * Отклонение маршрута.
     *
     * Доступно только SECURITY и ADMIN ролям.
     * Будет реализовано в Epic 4 (Approval Workflow).
     */
    @PostMapping("/{id}/reject")
    @RequireRole(Role.SECURITY)
    fun rejectRoute(@PathVariable id: UUID): Mono<ResponseEntity<Void>> {
        // TODO(Epic 4, Story 4.2): Реализовать reject workflow — переход PENDING → REJECTED с reason
        return Mono.just(ResponseEntity.ok().build())
    }
}
