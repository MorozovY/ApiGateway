package com.company.gateway.admin.controller

import com.company.gateway.admin.dto.ConsumerRateLimitRequest
import com.company.gateway.admin.dto.ConsumerRateLimitResponse
import com.company.gateway.admin.dto.PagedResponse
import com.company.gateway.admin.security.RequireRole
import com.company.gateway.admin.security.SecurityContextUtils
import com.company.gateway.admin.service.ConsumerRateLimitService
import com.company.gateway.common.model.Role
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Контроллер для управления per-consumer rate limits.
 *
 * Все операции доступны только для ADMIN роли.
 * Consumer rate limits применяются глобально для всех маршрутов consumer'а.
 *
 * Story 12.8: Per-consumer Rate Limits (FR50-FR53)
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Consumer Rate Limits", description = "Управление per-consumer rate limits")
class ConsumerRateLimitController(
    private val consumerRateLimitService: ConsumerRateLimitService
) {

    /**
     * Создаёт или обновляет rate limit для consumer (upsert).
     *
     * PUT /api/v1/consumers/{consumerId}/rate-limit
     *
     * @param consumerId идентификатор consumer (Keycloak client_id)
     * @param request данные rate limit
     * @return созданный/обновлённый rate limit
     */
    @PutMapping("/consumers/{consumerId}/rate-limit")
    @RequireRole(Role.ADMIN)
    @Operation(
        summary = "Установить rate limit для consumer",
        description = "Создаёт или обновляет per-consumer rate limit (upsert)"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Rate limit успешно установлен"),
        ApiResponse(responseCode = "400", description = "Невалидные данные"),
        ApiResponse(responseCode = "401", description = "Не аутентифицирован"),
        ApiResponse(responseCode = "403", description = "Недостаточно прав")
    )
    fun setRateLimit(
        @Parameter(description = "Consumer ID (Keycloak client_id)")
        @PathVariable consumerId: String,
        @Valid @RequestBody request: ConsumerRateLimitRequest
    ): Mono<ConsumerRateLimitResponse> {
        return SecurityContextUtils.currentUser()
            .flatMap { user ->
                consumerRateLimitService.setRateLimit(consumerId, request, user.userId, user.username)
            }
    }

    /**
     * Получает rate limit для consumer.
     *
     * GET /api/v1/consumers/{consumerId}/rate-limit
     *
     * @param consumerId идентификатор consumer
     * @return rate limit или 404 если не найден
     */
    @GetMapping("/consumers/{consumerId}/rate-limit")
    @RequireRole(Role.ADMIN)
    @Operation(
        summary = "Получить rate limit для consumer",
        description = "Возвращает per-consumer rate limit или 404 если не установлен"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Rate limit найден"),
        ApiResponse(responseCode = "404", description = "Rate limit не найден"),
        ApiResponse(responseCode = "401", description = "Не аутентифицирован"),
        ApiResponse(responseCode = "403", description = "Недостаточно прав")
    )
    fun getRateLimit(
        @Parameter(description = "Consumer ID (Keycloak client_id)")
        @PathVariable consumerId: String
    ): Mono<ConsumerRateLimitResponse> {
        return consumerRateLimitService.getRateLimit(consumerId)
    }

    /**
     * Удаляет rate limit для consumer.
     *
     * DELETE /api/v1/consumers/{consumerId}/rate-limit
     *
     * После удаления consumer будет ограничен только per-route лимитами.
     *
     * @param consumerId идентификатор consumer
     * @return 204 No Content
     */
    @DeleteMapping("/consumers/{consumerId}/rate-limit")
    @RequireRole(Role.ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Удалить rate limit для consumer",
        description = "Удаляет per-consumer rate limit, consumer возвращается к per-route лимитам"
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Rate limit удалён"),
        ApiResponse(responseCode = "404", description = "Rate limit не найден"),
        ApiResponse(responseCode = "401", description = "Не аутентифицирован"),
        ApiResponse(responseCode = "403", description = "Недостаточно прав")
    )
    fun deleteRateLimit(
        @Parameter(description = "Consumer ID (Keycloak client_id)")
        @PathVariable consumerId: String
    ): Mono<Void> {
        return SecurityContextUtils.currentUser()
            .flatMap { user ->
                consumerRateLimitService.deleteRateLimit(consumerId, user.userId, user.username)
            }
    }

    /**
     * Получает список всех consumer rate limits с пагинацией.
     *
     * GET /api/v1/consumer-rate-limits
     *
     * @param offset смещение от начала списка
     * @param limit максимальное количество элементов
     * @param filter фильтр по prefixу consumer ID (опционально)
     * @return пагинированный список rate limits
     */
    @GetMapping("/consumer-rate-limits")
    @RequireRole(Role.ADMIN)
    @Operation(
        summary = "Получить список consumer rate limits",
        description = "Возвращает пагинированный список всех per-consumer rate limits"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Список rate limits"),
        ApiResponse(responseCode = "401", description = "Не аутентифицирован"),
        ApiResponse(responseCode = "403", description = "Недостаточно прав")
    )
    fun listRateLimits(
        @Parameter(description = "Смещение от начала списка")
        @RequestParam(defaultValue = "0") offset: Int,
        @Parameter(description = "Максимальное количество элементов")
        @RequestParam(defaultValue = "20") limit: Int,
        @Parameter(description = "Фильтр по prefixу consumer ID")
        @RequestParam(required = false) filter: String?
    ): Mono<PagedResponse<ConsumerRateLimitResponse>> {
        return consumerRateLimitService.listRateLimits(offset, limit, filter)
    }
}
