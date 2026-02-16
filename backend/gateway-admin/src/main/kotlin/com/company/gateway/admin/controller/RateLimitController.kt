package com.company.gateway.admin.controller

import com.company.gateway.admin.security.RequireRole
import com.company.gateway.common.model.Role
import org.springframework.http.HttpStatus
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
 * Контроллер для управления политиками rate limiting.
 *
 * Чтение доступно всем аутентифицированным пользователям.
 * Создание, обновление, удаление — только для ADMIN роли.
 * Placeholder реализация для тестирования RBAC.
 * Полная реализация будет добавлена в Epic 5.
 */
@RestController
@RequestMapping("/api/v1/rate-limits")
class RateLimitController {

    /**
     * Получение списка политик rate limiting.
     *
     * Доступно всем аутентифицированным пользователям.
     */
    @GetMapping
    @RequireRole(Role.DEVELOPER)
    fun listPolicies(): Mono<ResponseEntity<Map<String, Any>>> {
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
     * Создание новой политики rate limiting.
     *
     * Доступно только ADMIN роли.
     */
    @PostMapping
    @RequireRole(Role.ADMIN)
    fun createPolicy(): Mono<ResponseEntity<Void>> {
        // Placeholder — для тестирования RBAC
        return Mono.just(ResponseEntity.status(HttpStatus.CREATED).build())
    }

    /**
     * Обновление политики rate limiting.
     *
     * Доступно только ADMIN роли.
     */
    @PutMapping("/{id}")
    @RequireRole(Role.ADMIN)
    fun updatePolicy(@PathVariable id: UUID): Mono<ResponseEntity<Void>> {
        // Placeholder — для тестирования RBAC
        return Mono.just(ResponseEntity.ok().build())
    }

    /**
     * Удаление политики rate limiting.
     *
     * Доступно только ADMIN роли.
     */
    @DeleteMapping("/{id}")
    @RequireRole(Role.ADMIN)
    fun deletePolicy(@PathVariable id: UUID): Mono<ResponseEntity<Void>> {
        // Placeholder — для тестирования RBAC
        return Mono.just(ResponseEntity.noContent().build())
    }
}
