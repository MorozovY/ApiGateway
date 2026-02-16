package com.company.gateway.admin.controller

import com.company.gateway.admin.security.RequireRole
import com.company.gateway.common.model.Role
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Контроллер для управления пользователями.
 *
 * Доступен только для ADMIN роли.
 * Placeholder реализация для тестирования RBAC.
 * Полная реализация будет добавлена в Epic 2 Story 2.6.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequireRole(Role.ADMIN)
class UserController {

    /**
     * Получение списка пользователей.
     *
     * Доступно только ADMIN роли.
     */
    @GetMapping
    fun listUsers(): Mono<ResponseEntity<Map<String, Any>>> {
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
     * Создание нового пользователя.
     *
     * Доступно только ADMIN роли.
     */
    @PostMapping
    fun createUser(): Mono<ResponseEntity<Void>> {
        // Placeholder — для тестирования RBAC
        return Mono.just(ResponseEntity.status(HttpStatus.CREATED).build())
    }

    /**
     * Обновление пользователя.
     *
     * Доступно только ADMIN роли.
     */
    @PutMapping("/{id}")
    fun updateUser(@PathVariable id: UUID): Mono<ResponseEntity<Void>> {
        // Placeholder — для тестирования RBAC
        return Mono.just(ResponseEntity.ok().build())
    }
}
