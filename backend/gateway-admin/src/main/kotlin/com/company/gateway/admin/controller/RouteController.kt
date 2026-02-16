package com.company.gateway.admin.controller

import com.company.gateway.admin.exception.AccessDeniedException
import com.company.gateway.admin.exception.ConflictException
import com.company.gateway.admin.security.RequireRole
import com.company.gateway.admin.security.SecurityContextUtils
import com.company.gateway.admin.service.DeleteCheckResult
import com.company.gateway.admin.service.OwnershipService
import com.company.gateway.common.model.Role
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Контроллер для управления маршрутами.
 *
 * Placeholder реализация для тестирования RBAC.
 * Полная реализация CRUD будет добавлена в Epic 3.
 */
@RestController
@RequestMapping("/api/v1/routes")
class RouteController(
    private val ownershipService: OwnershipService
) {

    /**
     * Получение списка маршрутов.
     *
     * Доступно всем аутентифицированным пользователям (DEVELOPER и выше).
     */
    @GetMapping
    @RequireRole(Role.DEVELOPER)
    fun listRoutes(): Mono<ResponseEntity<Map<String, Any>>> {
        // Placeholder — вернуть пустой список
        return Mono.just(
            ResponseEntity.ok(
                mapOf(
                    "items" to emptyList<Any>(),
                    "total" to 0
                )
            )
        )
    }

    /**
     * Обновление маршрута.
     *
     * Developer может обновлять только свои маршруты (AC4).
     * Security и Admin могут обновлять любые маршруты.
     */
    @PutMapping("/{id}")
    @RequireRole(Role.DEVELOPER)
    fun updateRoute(@PathVariable id: UUID): Mono<ResponseEntity<Void>> {
        return SecurityContextUtils.currentUser()
            .flatMap { user ->
                // Admin и Security могут обновлять любые маршруты
                if (user.role == Role.ADMIN || user.role == Role.SECURITY) {
                    // Placeholder — полная реализация в Epic 3
                    Mono.just(ResponseEntity.ok().build())
                } else {
                    // Developer может обновлять только свои маршруты
                    ownershipService.canModifyRoute(id, user.userId)
                        .flatMap { canModify ->
                            if (canModify) {
                                // Placeholder — полная реализация в Epic 3
                                Mono.just(ResponseEntity.ok().build())
                            } else {
                                Mono.error(AccessDeniedException("You can only modify your own routes"))
                            }
                        }
                }
            }
    }

    /**
     * Удаление маршрута.
     *
     * Developer может удалять только свои маршруты в статусе DRAFT (AC4, AC5).
     * Security и Admin могут удалять любые маршруты.
     */
    @DeleteMapping("/{id}")
    @RequireRole(Role.DEVELOPER)
    fun deleteRoute(@PathVariable id: UUID): Mono<ResponseEntity<Void>> {
        return SecurityContextUtils.currentUser()
            .flatMap { user ->
                // Admin и Security могут удалять любые маршруты
                if (user.role == Role.ADMIN || user.role == Role.SECURITY) {
                    // Placeholder — полная реализация в Epic 3
                    Mono.just(ResponseEntity.noContent().build())
                } else {
                    // Developer может удалять только свои draft маршруты
                    ownershipService.canDeleteRoute(id, user.userId)
                        .flatMap { result ->
                            when (result) {
                                is DeleteCheckResult.Allowed -> {
                                    // Placeholder — полная реализация в Epic 3
                                    Mono.just(ResponseEntity.noContent().build())
                                }
                                is DeleteCheckResult.NotOwner -> {
                                    Mono.error(AccessDeniedException("You can only modify your own routes"))
                                }
                                is DeleteCheckResult.NotDraft -> {
                                    Mono.error(ConflictException("Only draft routes can be deleted"))
                                }
                                is DeleteCheckResult.NotFound -> {
                                    // Возвращаем 204 для идемпотентности DELETE
                                    Mono.just(ResponseEntity.noContent().build())
                                }
                            }
                        }
                }
            }
    }

    /**
     * Одобрение маршрута.
     *
     * Доступно только SECURITY и ADMIN ролям (AC6).
     */
    @PostMapping("/{id}/approve")
    @RequireRole(Role.SECURITY)
    fun approveRoute(@PathVariable id: UUID): Mono<ResponseEntity<Void>> {
        // Placeholder — для тестирования RBAC
        return Mono.just(ResponseEntity.ok().build())
    }

    /**
     * Отклонение маршрута.
     *
     * Доступно только SECURITY и ADMIN ролям (AC6).
     */
    @PostMapping("/{id}/reject")
    @RequireRole(Role.SECURITY)
    fun rejectRoute(@PathVariable id: UUID): Mono<ResponseEntity<Void>> {
        // Placeholder — для тестирования RBAC
        return Mono.just(ResponseEntity.ok().build())
    }
}
