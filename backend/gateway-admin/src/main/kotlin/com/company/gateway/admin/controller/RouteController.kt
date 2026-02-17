package com.company.gateway.admin.controller

import com.company.gateway.admin.dto.CreateRouteRequest
import com.company.gateway.admin.dto.RouteListResponse
import com.company.gateway.admin.dto.RouteResponse
import com.company.gateway.admin.dto.UpdateRouteRequest
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
 * Реализует CRUD операции для маршрутов (Story 3.1):
 * - POST /api/v1/routes — создание маршрута
 * - PUT /api/v1/routes/{id} — обновление маршрута
 * - DELETE /api/v1/routes/{id} — удаление маршрута
 * - GET /api/v1/routes/{id} — получение маршрута по ID
 * - GET /api/v1/routes — получение списка маршрутов с пагинацией
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

    /**
     * Получение списка маршрутов с пагинацией.
     *
     * Доступно всем аутентифицированным пользователям (DEVELOPER и выше).
     */
    @GetMapping
    @RequireRole(Role.DEVELOPER)
    fun listRoutes(
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "20") limit: Int
    ): Mono<ResponseEntity<RouteListResponse>> {
        return routeService.findAll(offset, limit)
            .map { ResponseEntity.ok(it) }
    }

    /**
     * Получение маршрута по ID.
     *
     * Доступно всем аутентифицированным пользователям (DEVELOPER и выше).
     */
    @GetMapping("/{id}")
    @RequireRole(Role.DEVELOPER)
    fun getRoute(@PathVariable id: UUID): Mono<ResponseEntity<RouteResponse>> {
        return routeService.findById(id)
            .map { ResponseEntity.ok(it) }
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
