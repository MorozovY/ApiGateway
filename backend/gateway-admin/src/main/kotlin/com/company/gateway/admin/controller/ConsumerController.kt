package com.company.gateway.admin.controller

import com.company.gateway.admin.dto.ConsumerListResponse
import com.company.gateway.admin.dto.ConsumerResponse
import com.company.gateway.admin.dto.CreateConsumerRequest
import com.company.gateway.admin.dto.CreateConsumerResponse
import com.company.gateway.admin.dto.RotateSecretResponse
import com.company.gateway.admin.security.RequireRole
import com.company.gateway.admin.service.ConsumerService
import com.company.gateway.common.model.Role
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

/**
 * Контроллер для управления API consumers (Keycloak service account clients).
 *
 * Consumer — это внешний клиент API (партнёр, сервис), который аутентифицируется
 * через Client Credentials flow и получает JWT токен.
 *
 * Все операции доступны только для ADMIN роли.
 *
 * Story 12.9: Consumer Management UI (FR54-FR59)
 */
@RestController
@RequestMapping("/api/v1/consumers")
@ConditionalOnProperty(name = ["keycloak.enabled"], havingValue = "true")
@Tag(name = "Consumers", description = "Управление API consumers (Keycloak clients)")
class ConsumerController(
    private val consumerService: ConsumerService
) {

    /**
     * Получение списка consumers с пагинацией и поиском.
     *
     * GET /api/v1/consumers
     *
     * @param offset смещение (default: 0)
     * @param limit лимит записей (default: 100)
     * @param search поиск по client ID (prefix match, case-insensitive)
     * @return пагинированный список consumers
     */
    @GetMapping
    @RequireRole(Role.ADMIN)
    @Operation(
        summary = "Список consumers",
        description = "Возвращает пагинированный список API consumers (Keycloak service account clients)"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Список consumers"),
        ApiResponse(responseCode = "401", description = "Не аутентифицирован"),
        ApiResponse(responseCode = "403", description = "Недостаточно прав (требуется ADMIN)")
    )
    fun listConsumers(
        @Parameter(description = "Смещение для пагинации")
        @RequestParam(defaultValue = "0") offset: Int,
        @Parameter(description = "Лимит записей")
        @RequestParam(defaultValue = "100") limit: Int,
        @Parameter(description = "Поиск по client ID (prefix)")
        @RequestParam(required = false) search: String?
    ): Mono<ConsumerListResponse> {
        return consumerService.listConsumers(offset, limit, search)
    }

    /**
     * Получение данных одного consumer.
     *
     * GET /api/v1/consumers/{clientId}
     *
     * @param clientId client ID в Keycloak
     * @return consumer данные
     */
    @GetMapping("/{clientId}")
    @RequireRole(Role.ADMIN)
    @Operation(
        summary = "Получить consumer",
        description = "Возвращает данные одного consumer по client ID"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Consumer найден"),
        ApiResponse(responseCode = "404", description = "Consumer не найден"),
        ApiResponse(responseCode = "401", description = "Не аутентифицирован"),
        ApiResponse(responseCode = "403", description = "Недостаточно прав")
    )
    fun getConsumer(
        @Parameter(description = "Client ID (Keycloak client_id)")
        @PathVariable clientId: String
    ): Mono<ConsumerResponse> {
        return consumerService.getConsumer(clientId)
    }

    /**
     * Создание нового consumer.
     *
     * POST /api/v1/consumers
     *
     * @param request данные для создания
     * @return response с client secret (показывается только один раз!)
     */
    @PostMapping
    @RequireRole(Role.ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Создать consumer",
        description = "Создаёт новый API consumer в Keycloak. ВАЖНО: Secret показывается только один раз!"
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Consumer создан"),
        ApiResponse(responseCode = "400", description = "Невалидные данные"),
        ApiResponse(responseCode = "409", description = "Consumer с таким client ID уже существует"),
        ApiResponse(responseCode = "401", description = "Не аутентифицирован"),
        ApiResponse(responseCode = "403", description = "Недостаточно прав")
    )
    fun createConsumer(
        @Valid @RequestBody request: CreateConsumerRequest
    ): Mono<CreateConsumerResponse> {
        return consumerService.createConsumer(request.clientId, request.description)
    }

    /**
     * Ротация client secret.
     *
     * POST /api/v1/consumers/{clientId}/rotate-secret
     *
     * @param clientId client ID
     * @return новый secret (показывается только один раз!)
     */
    @PostMapping("/{clientId}/rotate-secret")
    @RequireRole(Role.ADMIN)
    @Operation(
        summary = "Ротировать secret",
        description = "Генерирует новый client secret, старый становится невалидным. ВАЖНО: Secret показывается только один раз!"
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Secret ротирован"),
        ApiResponse(responseCode = "404", description = "Consumer не найден"),
        ApiResponse(responseCode = "401", description = "Не аутентифицирован"),
        ApiResponse(responseCode = "403", description = "Недостаточно прав")
    )
    fun rotateSecret(
        @Parameter(description = "Client ID")
        @PathVariable clientId: String
    ): Mono<RotateSecretResponse> {
        return consumerService.rotateSecret(clientId)
    }

    /**
     * Деактивация consumer.
     *
     * POST /api/v1/consumers/{clientId}/disable
     *
     * @param clientId client ID
     */
    @PostMapping("/{clientId}/disable")
    @RequireRole(Role.ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Деактивировать consumer",
        description = "Устанавливает enabled=false, consumer не сможет аутентифицироваться"
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Consumer деактивирован"),
        ApiResponse(responseCode = "404", description = "Consumer не найден"),
        ApiResponse(responseCode = "401", description = "Не аутентифицирован"),
        ApiResponse(responseCode = "403", description = "Недостаточно прав")
    )
    fun disableConsumer(
        @Parameter(description = "Client ID")
        @PathVariable clientId: String
    ): Mono<Void> {
        return consumerService.disableConsumer(clientId)
    }

    /**
     * Активация consumer.
     *
     * POST /api/v1/consumers/{clientId}/enable
     *
     * @param clientId client ID
     */
    @PostMapping("/{clientId}/enable")
    @RequireRole(Role.ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Активировать consumer",
        description = "Устанавливает enabled=true, consumer может аутентифицироваться"
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Consumer активирован"),
        ApiResponse(responseCode = "404", description = "Consumer не найден"),
        ApiResponse(responseCode = "401", description = "Не аутентифицирован"),
        ApiResponse(responseCode = "403", description = "Недостаточно прав")
    )
    fun enableConsumer(
        @Parameter(description = "Client ID")
        @PathVariable clientId: String
    ): Mono<Void> {
        return consumerService.enableConsumer(clientId)
    }
}
