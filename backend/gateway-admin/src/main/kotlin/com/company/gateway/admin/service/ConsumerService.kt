package com.company.gateway.admin.service

import com.company.gateway.admin.dto.ConsumerListResponse
import com.company.gateway.admin.dto.ConsumerResponse
import com.company.gateway.admin.dto.CreateConsumerResponse
import com.company.gateway.admin.dto.RotateSecretResponse
import com.company.gateway.admin.exception.NotFoundException
import com.company.gateway.admin.security.SecurityContextUtils
import com.company.gateway.admin.service.KeycloakAdminService.KeycloakClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

/**
 * Сервис для управления API consumers (Keycloak service account clients).
 *
 * Consumer — это внешний клиент API (партнёр, сервис), который аутентифицируется
 * через Client Credentials flow и получает JWT токен для доступа к protected routes.
 *
 * Story 12.9: Consumer Management UI
 */
@Service
@ConditionalOnProperty(name = ["keycloak.enabled"], havingValue = "true")
class ConsumerService(
    private val keycloakAdminService: KeycloakAdminService,
    private val consumerRateLimitService: ConsumerRateLimitService,
    private val auditService: AuditService
) {
    private val logger = LoggerFactory.getLogger(ConsumerService::class.java)

    /**
     * Получение списка всех consumers с пагинацией и поиском.
     *
     * @param offset смещение для пагинации
     * @param limit лимит записей
     * @param search поиск по client ID (prefix match, case-insensitive)
     * @return пагинированный список consumers
     */
    fun listConsumers(offset: Int, limit: Int, search: String?): Mono<ConsumerListResponse> {
        return keycloakAdminService.listConsumers()
            .flatMap { clients ->
                // Фильтрация по search (игнорируем пустые строки и whitespace)
                val filteredClients = if (!search.isNullOrBlank() && search.trim().isNotEmpty()) {
                    clients.filter { it.clientId.startsWith(search.trim(), ignoreCase = true) }
                } else {
                    clients
                }

                val total = filteredClients.size

                // Пагинация
                val paginatedClients = filteredClients
                    .drop(offset)
                    .take(limit)

                // Обогащение данными о rate limits
                enrichWithRateLimits(paginatedClients)
                    .map { consumers ->
                        ConsumerListResponse(items = consumers, total = total)
                    }
            }
            .doOnSuccess { response ->
                logger.debug("Список consumers получен: total={}, returned={}", response.total, response.items.size)
            }
    }

    /**
     * Получение данных одного consumer.
     *
     * @param clientId client ID в Keycloak
     * @return consumer данные
     * @throws NotFoundException если consumer не найден
     */
    fun getConsumer(clientId: String): Mono<ConsumerResponse> {
        return keycloakAdminService.getConsumer(clientId)
            .switchIfEmpty(Mono.error(NotFoundException("Consumer '$clientId' not found")))
            .flatMap { client ->
                enrichWithRateLimit(client)
            }
            .doOnSuccess { consumer ->
                logger.debug("Consumer получен: clientId={}", consumer.clientId)
            }
    }

    /**
     * Создание нового consumer.
     *
     * @param clientId client ID
     * @param description описание
     * @return response с secret
     */
    fun createConsumer(clientId: String, description: String?): Mono<CreateConsumerResponse> {
        return SecurityContextUtils.currentUser()
            .flatMap { user ->
                keycloakAdminService.createConsumer(clientId, description)
                    .map { clientWithSecret ->
                        CreateConsumerResponse(
                            clientId = clientWithSecret.clientId,
                            secret = clientWithSecret.secret
                        )
                    }
                    .doOnSuccess { response ->
                        // Audit logging для security compliance (FR21-FR24)
                        auditService.logAsync(
                            entityType = "consumer",
                            entityId = clientId,
                            action = "secret_generated",
                            userId = user.userId,
                            username = user.username,
                            changes = mapOf(
                                "description" to description,
                                "action" to "Consumer created with new secret"
                            )
                        )
                        logger.info("Consumer создан: clientId={}", response.clientId)
                    }
            }
    }

    /**
     * Ротация client secret.
     *
     * @param clientId client ID
     * @return новый secret
     */
    fun rotateSecret(clientId: String): Mono<RotateSecretResponse> {
        return SecurityContextUtils.currentUser()
            .flatMap { user ->
                keycloakAdminService.rotateSecret(clientId)
                    .map { secret ->
                        RotateSecretResponse(
                            clientId = clientId,
                            secret = secret
                        )
                    }
                    .doOnSuccess { response ->
                        // Audit logging для security compliance (FR21-FR24)
                        auditService.logAsync(
                            entityType = "consumer",
                            entityId = clientId,
                            action = "secret_rotated",
                            userId = user.userId,
                            username = user.username,
                            changes = mapOf(
                                "action" to "Client secret rotated (old secret invalidated)"
                            )
                        )
                        logger.info("Secret ротирован для consumer: clientId={}", clientId)
                    }
            }
    }

    /**
     * Деактивация consumer.
     *
     * @param clientId client ID
     */
    fun disableConsumer(clientId: String): Mono<Void> {
        return keycloakAdminService.disableConsumer(clientId)
            .doOnSuccess {
                logger.info("Consumer деактивирован: clientId={}", clientId)
            }
    }

    /**
     * Активация consumer.
     *
     * @param clientId client ID
     */
    fun enableConsumer(clientId: String): Mono<Void> {
        return keycloakAdminService.enableConsumer(clientId)
            .doOnSuccess {
                logger.info("Consumer активирован: clientId={}", clientId)
            }
    }

    /**
     * Обогащение списка consumers данными о rate limits.
     *
     * @param clients список Keycloak clients
     * @return список ConsumerResponse с rate limits
     */
    private fun enrichWithRateLimits(clients: List<KeycloakClient>): Mono<List<ConsumerResponse>> {
        if (clients.isEmpty()) {
            return Mono.just(emptyList())
        }

        // Получаем rate limits для всех consumers параллельно
        val rateLimitsMono = consumerRateLimitService.listRateLimits(offset = 0, limit = 10000)
            .map { pagedResponse ->
                // Создаём map consumerId -> RateLimit
                pagedResponse.items.associateBy { it.consumerId }
            }
            .onErrorReturn(emptyMap())

        return rateLimitsMono.map { rateLimitsMap ->
            clients.map { client ->
                ConsumerResponse(
                    clientId = client.clientId,
                    description = client.description,
                    enabled = client.enabled,
                    createdTimestamp = client.createdTimestamp,
                    rateLimit = rateLimitsMap[client.clientId]
                )
            }
        }
    }

    /**
     * Обогащение одного consumer данными о rate limit.
     *
     * @param client Keycloak client
     * @return ConsumerResponse с rate limit
     */
    private fun enrichWithRateLimit(client: KeycloakClient): Mono<ConsumerResponse> {
        return consumerRateLimitService.getRateLimit(client.clientId)
            .map { rateLimit ->
                ConsumerResponse(
                    clientId = client.clientId,
                    description = client.description,
                    enabled = client.enabled,
                    createdTimestamp = client.createdTimestamp,
                    rateLimit = rateLimit
                )
            }
            .onErrorResume { error ->
                // Если rate limit не найден (404) — возвращаем consumer без rate limit
                logger.debug("Rate limit не найден для consumer {}: {}", client.clientId, error.message)
                Mono.just(
                    ConsumerResponse(
                        clientId = client.clientId,
                        description = client.description,
                        enabled = client.enabled,
                        createdTimestamp = client.createdTimestamp,
                        rateLimit = null
                    )
                )
            }
    }
}
