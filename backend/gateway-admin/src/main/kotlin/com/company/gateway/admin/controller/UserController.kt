package com.company.gateway.admin.controller

import com.company.gateway.admin.dto.CreateUserRequest
import com.company.gateway.admin.dto.UpdateUserRequest
import com.company.gateway.admin.dto.UserListResponse
import com.company.gateway.admin.dto.UserResponse
import com.company.gateway.admin.security.RequireRole
import com.company.gateway.admin.security.SecurityContextUtils
import com.company.gateway.admin.service.UserService
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
 * Контроллер для управления пользователями.
 *
 * Предоставляет REST API для CRUD операций с пользователями.
 * Доступен только для пользователей с ролью ADMIN.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequireRole(Role.ADMIN)
class UserController(
    private val userService: UserService
) {

    /**
     * Получение списка пользователей с пагинацией и поиском.
     *
     * GET /api/v1/users?offset=0&limit=20&search=john
     *
     * @param offset смещение от начала списка (по умолчанию 0)
     * @param limit максимальное количество элементов (по умолчанию 20)
     * @param search поиск по username или email (case-insensitive, опционально)
     * @return пагинированный список пользователей
     */
    @GetMapping
    fun listUsers(
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) search: String?
    ): Mono<UserListResponse> {
        return userService.findAll(offset, limit, search)
    }

    /**
     * Получение пользователя по ID.
     *
     * GET /api/v1/users/{id}
     *
     * @param id идентификатор пользователя
     * @return данные пользователя
     */
    @GetMapping("/{id}")
    fun getUser(@PathVariable id: UUID): Mono<UserResponse> {
        return userService.findById(id)
    }

    /**
     * Создание нового пользователя.
     *
     * POST /api/v1/users
     *
     * @param request данные для создания пользователя
     * @return созданный пользователь
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createUser(@Valid @RequestBody request: CreateUserRequest): Mono<UserResponse> {
        return userService.create(request)
    }

    /**
     * Обновление пользователя.
     *
     * PUT /api/v1/users/{id}
     *
     * @param id идентификатор пользователя
     * @param request данные для обновления
     * @return обновлённый пользователь
     */
    @PutMapping("/{id}")
    fun updateUser(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateUserRequest
    ): Mono<UserResponse> {
        return userService.update(id, request)
    }

    /**
     * Деактивация пользователя (soft delete).
     *
     * DELETE /api/v1/users/{id}
     *
     * @param id идентификатор пользователя
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deactivateUser(@PathVariable id: UUID): Mono<Void> {
        // Используем единственную подписку на SecurityContext через flatMap.
        // RoleAuthorizationAspect гарантирует наличие SecurityContext до этой точки.
        return SecurityContextUtils.currentUser()
            .flatMap { user -> userService.deactivate(id, user.userId) }
    }
}
