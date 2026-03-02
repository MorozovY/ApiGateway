package com.company.gateway.admin.controller

import com.company.gateway.admin.dto.CreateRateLimitRequest
import com.company.gateway.admin.dto.PagedResponse
import com.company.gateway.admin.dto.RateLimitResponse
import com.company.gateway.admin.dto.UpdateRateLimitRequest
import com.company.gateway.admin.security.RequireRole
import com.company.gateway.admin.security.SecurityContextUtils
import com.company.gateway.admin.service.RateLimitService
import com.company.gateway.common.model.Role
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Контроллер для управления политиками rate limiting.
 *
 * Чтение (GET) доступно всем аутентифицированным пользователям.
 * Создание, обновление, удаление (POST/PUT/DELETE) — только для ADMIN роли.
 */
@RestController
@RequestMapping("/api/v1/rate-limits")
class RateLimitController(
    private val rateLimitService: RateLimitService
) {

    /**
     * Получение списка политик rate limiting с пагинацией.
     *
     * Доступно всем аутентифицированным пользователям.
     */
    @GetMapping
    @RequireRole(Role.DEVELOPER)
    fun listPolicies(
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "100") limit: Int
    ): Mono<PagedResponse<RateLimitResponse>> {
        return rateLimitService.findAll(offset, limit)
    }

    /**
     * Получение политики rate limiting по ID.
     *
     * Доступно всем аутентифицированным пользователям.
     */
    @GetMapping("/{id}")
    @RequireRole(Role.DEVELOPER)
    fun getPolicy(@PathVariable id: UUID): Mono<RateLimitResponse> {
        return rateLimitService.findById(id)
    }

    /**
     * Создание новой политики rate limiting.
     *
     * Доступно только ADMIN роли.
     */
    @PostMapping
    @RequireRole(Role.ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    fun createPolicy(
        @Valid @RequestBody request: CreateRateLimitRequest
    ): Mono<RateLimitResponse> {
        return SecurityContextUtils.currentUser()
            .flatMap { user ->
                rateLimitService.create(request, user.userId, user.username)
            }
    }

    /**
     * Обновление политики rate limiting.
     *
     * Доступно только ADMIN роли.
     */
    @PutMapping("/{id}")
    @RequireRole(Role.ADMIN)
    fun updatePolicy(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateRateLimitRequest
    ): Mono<RateLimitResponse> {
        return SecurityContextUtils.currentUser()
            .flatMap { user ->
                rateLimitService.update(id, request, user.userId, user.username)
            }
    }

    /**
     * Удаление политики rate limiting.
     *
     * Доступно только ADMIN роли.
     * Запрещено если политика используется маршрутами.
     */
    @DeleteMapping("/{id}")
    @RequireRole(Role.ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePolicy(@PathVariable id: UUID): Mono<Void> {
        return SecurityContextUtils.currentUser()
            .flatMap { user ->
                rateLimitService.delete(id, user.userId, user.username)
            }
    }
}
