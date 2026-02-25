package com.company.gateway.admin.service

import com.company.gateway.admin.properties.KeycloakProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

/**
 * Сервис для работы с Keycloak Admin API.
 *
 * Используется для управления пользователями в Keycloak:
 * - Получение списка пользователей
 * - Сброс паролей
 *
 * Активируется только при keycloak.enabled=true.
 */
@Service
@ConditionalOnProperty(name = ["keycloak.enabled"], havingValue = "true")
class KeycloakAdminService(
    private val keycloakProperties: KeycloakProperties
) {
    private val logger = LoggerFactory.getLogger(KeycloakAdminService::class.java)
    private val webClient = WebClient.builder().build()

    // Кеш для admin токена (TTL = 50 секунд, expires_in обычно 60 секунд)
    @Volatile
    private var cachedTokenMono: Mono<String>? = null

    /**
     * Получение admin токена через password grant с кешированием.
     *
     * Токен кешируется на 50 секунд для уменьшения нагрузки на Keycloak.
     * Keycloak access_token обычно имеет expires_in = 60 секунд.
     *
     * @return access_token для Admin API
     */
    fun getAdminToken(): Mono<String> {
        // Если токен уже закеширован, возвращаем его
        val cached = cachedTokenMono
        if (cached != null) {
            return cached
        }

        // Иначе запрашиваем новый токен
        val tokenUrl = "${keycloakProperties.url}/realms/master/protocol/openid-connect/token"

        logger.debug("Получение нового admin токена от Keycloak: {}", tokenUrl)

        val tokenMono = webClient.post()
            .uri(tokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("client_id", "admin-cli")
                    .with("username", keycloakProperties.adminUsername)
                    .with("password", keycloakProperties.adminPassword)
                    .with("grant_type", "password")
            )
            .retrieve()
            .bodyToMono(TokenResponse::class.java)
            .map { it.access_token }
            .doOnSuccess { logger.debug("Admin токен получен и закеширован") }
            .doOnError { e ->
                logger.error("Ошибка получения admin токена: {}", e.message)
                cachedTokenMono = null // Очистить кеш при ошибке
            }
            .cache(java.time.Duration.ofSeconds(50)) // Кеш на 50 секунд

        cachedTokenMono = tokenMono
        return tokenMono
    }

    /**
     * Поиск пользователя по username.
     *
     * @param username имя пользователя
     * @return ID пользователя или empty если не найден
     */
    fun findUserByUsername(username: String): Mono<String> {
        val usersUrl = "${keycloakProperties.url}/admin/realms/${keycloakProperties.realm}/users"

        return getAdminToken()
            .flatMap { token ->
                webClient.get()
                    .uri("$usersUrl?username=$username&exact=true")
                    .header("Authorization", "Bearer $token")
                    .retrieve()
                    .bodyToFlux(KeycloakUser::class.java)
                    .next()
                    .map { it.id }
            }
            .doOnSuccess { userId ->
                if (userId != null) {
                    logger.debug("Найден пользователь в Keycloak: username={}, id={}", username, userId)
                } else {
                    logger.debug("Пользователь не найден в Keycloak: username={}", username)
                }
            }
    }

    /**
     * Сброс пароля пользователя.
     *
     * @param userId ID пользователя в Keycloak
     * @param newPassword новый пароль
     * @return Mono<Void> при успехе
     */
    fun resetPassword(userId: String, newPassword: String): Mono<Void> {
        val resetUrl = "${keycloakProperties.url}/admin/realms/${keycloakProperties.realm}/users/$userId/reset-password"

        return getAdminToken()
            .flatMap { token ->
                val credential = PasswordCredential(
                    type = "password",
                    value = newPassword,
                    temporary = false
                )

                webClient.put()
                    .uri(resetUrl)
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(credential)
                    .retrieve()
                    .bodyToMono(Void::class.java)
            }
            .doOnSuccess {
                logger.info("Пароль сброшен в Keycloak для userId={}", userId)
            }
            .doOnError { e ->
                logger.error("Ошибка сброса пароля в Keycloak для userId={}: {}", userId, e.message)
            }
    }

    /**
     * Сброс пароля пользователя по username.
     *
     * @param username имя пользователя
     * @param newPassword новый пароль
     * @return username при успехе, empty если пользователь не найден
     */
    fun resetPasswordByUsername(username: String, newPassword: String): Mono<String> {
        return findUserByUsername(username)
            .flatMap { userId ->
                resetPassword(userId, newPassword)
                    .thenReturn(username)
            }
            .doOnSuccess { result ->
                if (result != null) {
                    logger.info("Пароль сброшен в Keycloak для пользователя: {}", username)
                }
            }
    }

    /**
     * Проверяет пароль пользователя через Keycloak token endpoint.
     *
     * Пытается получить токен с указанными credentials.
     * Если успешно - пароль верный, если ошибка - неверный.
     *
     * Использует gateway-admin-ui client (public client с Direct Access Grants).
     *
     * @param username имя пользователя
     * @param password пароль для проверки
     * @return true если пароль верный, false если неверный
     */
    fun verifyPassword(username: String, password: String): Mono<Boolean> {
        val tokenUrl = "${keycloakProperties.url}/realms/${keycloakProperties.realm}/protocol/openid-connect/token"

        logger.debug("Проверка пароля для пользователя: {}", username)

        // Используем clientId из конфигурации (gateway-admin-ui - public client с Direct Access Grants)
        return webClient.post()
            .uri(tokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("client_id", keycloakProperties.clientId)
                    .with("username", username)
                    .with("password", password)
                    .with("grant_type", "password")
            )
            .retrieve()
            .bodyToMono(TokenResponse::class.java)
            .map { true }
            .doOnSuccess { logger.debug("Пароль верный для пользователя: {}", username) }
            .onErrorResume { e ->
                logger.debug("Пароль неверный для пользователя {}: {}", username, e.message)
                Mono.just(false)
            }
    }

    // DTO классы для Keycloak API responses
    private data class TokenResponse(
        val access_token: String,
        val expires_in: Int = 0,
        val token_type: String = ""
    )

    private data class KeycloakUser(
        val id: String,
        val username: String,
        val email: String? = null,
        val enabled: Boolean = true
    )

    private data class PasswordCredential(
        val type: String,
        val value: String,
        val temporary: Boolean
    )

    // ============ Consumer Management (Story 12.9) ============

    /**
     * Получение списка API consumers (clients с serviceAccountsEnabled=true).
     *
     * @param first смещение для пагинации (опционально)
     * @param max лимит записей (опционально)
     * @return список Keycloak clients
     */
    fun listConsumers(first: Int? = null, max: Int? = null): Mono<List<KeycloakClient>> {
        val clientsUrl = "${keycloakProperties.url}/admin/realms/${keycloakProperties.realm}/clients"

        return getAdminToken()
            .flatMapMany { token ->
                // Построение URI с опциональными параметрами пагинации
                val uriBuilder = StringBuilder(clientsUrl)
                if (first != null || max != null) {
                    uriBuilder.append("?")
                    val params = mutableListOf<String>()
                    if (first != null) params.add("first=$first")
                    if (max != null) params.add("max=$max")
                    uriBuilder.append(params.joinToString("&"))
                }

                webClient.get()
                    .uri(uriBuilder.toString())
                    .header("Authorization", "Bearer $token")
                    .retrieve()
                    .bodyToFlux(KeycloakClient::class.java)
            }
            .filter { it.serviceAccountsEnabled == true }
            .collectList()
            .onErrorResume { error ->
                // Retry при 401 Unauthorized (token expired) — очистить кеш и повторить
                if (error.message?.contains("401") == true) {
                    logger.warn("401 Unauthorized при listConsumers, очищаем token cache и повторяем")
                    cachedTokenMono = null
                    listConsumers(first, max)
                } else {
                    Mono.error(error)
                }
            }
            .doOnSuccess { clients ->
                logger.debug("Найдено {} API consumers в Keycloak (first={}, max={})", clients.size, first, max)
            }
    }

    /**
     * Получение данных одного consumer по client ID.
     *
     * @param clientId client ID в Keycloak
     * @return client данные или empty если не найден
     */
    fun getConsumer(clientId: String): Mono<KeycloakClient> {
        val clientsUrl = "${keycloakProperties.url}/admin/realms/${keycloakProperties.realm}/clients"

        return getAdminToken()
            .flatMapMany { token ->
                webClient.get()
                    .uri("$clientsUrl?clientId=$clientId")
                    .header("Authorization", "Bearer $token")
                    .retrieve()
                    .bodyToFlux(KeycloakClient::class.java)
            }
            .next()
            .doOnSuccess { client ->
                if (client != null) {
                    logger.debug("Найден consumer в Keycloak: clientId={}, id={}", clientId, client.id)
                } else {
                    logger.debug("Consumer не найден в Keycloak: clientId={}", clientId)
                }
            }
    }

    /**
     * Создание нового consumer (service account client).
     *
     * @param clientId client ID
     * @param description описание consumer
     * @return созданный client с secret
     */
    fun createConsumer(clientId: String, description: String?): Mono<KeycloakClientWithSecret> {
        val clientsUrl = "${keycloakProperties.url}/admin/realms/${keycloakProperties.realm}/clients"

        val clientRepresentation = mapOf(
            "clientId" to clientId,
            "description" to (description ?: ""),
            "enabled" to true,
            "serviceAccountsEnabled" to true,
            "standardFlowEnabled" to false,
            "implicitFlowEnabled" to false,
            "directAccessGrantsEnabled" to false,
            "publicClient" to false,
            "protocol" to "openid-connect"
        )

        return getAdminToken()
            .flatMap { token ->
                // Создать client в Keycloak
                webClient.post()
                    .uri(clientsUrl)
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(clientRepresentation)
                    .retrieve()
                    .toBodilessEntity()
                    .flatMap {
                        // Получить созданный client для извлечения ID
                        getConsumer(clientId)
                            .flatMap { client ->
                                // Получить client secret
                                getClientSecret(client.id, token)
                                    .map { secret ->
                                        KeycloakClientWithSecret(
                                            id = client.id,
                                            clientId = client.clientId,
                                            description = client.description,
                                            enabled = client.enabled,
                                            serviceAccountsEnabled = client.serviceAccountsEnabled,
                                            createdTimestamp = client.createdTimestamp,
                                            secret = secret
                                        )
                                    }
                            }
                    }
            }
            .doOnSuccess { client ->
                logger.info("Consumer создан в Keycloak: clientId={}", client.clientId)
            }
            .doOnError { e ->
                logger.error("Ошибка создания consumer в Keycloak clientId={}: {}", clientId, e.message)
            }
    }

    /**
     * Ротация client secret.
     *
     * @param clientId client ID
     * @return новый secret
     */
    fun rotateSecret(clientId: String): Mono<String> {
        return getConsumer(clientId)
            .flatMap { client ->
                getAdminToken()
                    .flatMap { token ->
                        val secretUrl =
                            "${keycloakProperties.url}/admin/realms/${keycloakProperties.realm}/clients/${client.id}/client-secret"

                        // Regenerate secret
                        webClient.post()
                            .uri(secretUrl)
                            .header("Authorization", "Bearer $token")
                            .retrieve()
                            .bodyToMono(KeycloakCredential::class.java)
                            .map { it.value }
                    }
            }
            .doOnSuccess {
                logger.info("Secret ротирован для consumer: clientId={}", clientId)
            }
    }

    /**
     * Деактивация consumer (enabled=false).
     *
     * @param clientId client ID
     * @return Mono<Void>
     */
    fun disableConsumer(clientId: String): Mono<Void> {
        return updateConsumerStatus(clientId, false)
            .doOnSuccess {
                logger.info("Consumer деактивирован: clientId={}", clientId)
            }
    }

    /**
     * Активация consumer (enabled=true).
     *
     * @param clientId client ID
     * @return Mono<Void>
     */
    fun enableConsumer(clientId: String): Mono<Void> {
        return updateConsumerStatus(clientId, true)
            .doOnSuccess {
                logger.info("Consumer активирован: clientId={}", clientId)
            }
    }

    /**
     * Обновление статуса consumer (enabled field).
     *
     * @param clientId client ID
     * @param enabled true = activate, false = disable
     * @return Mono<Void>
     */
    private fun updateConsumerStatus(clientId: String, enabled: Boolean): Mono<Void> {
        return getConsumer(clientId)
            .flatMap { client ->
                getAdminToken()
                    .flatMap { token ->
                        val clientUrl =
                            "${keycloakProperties.url}/admin/realms/${keycloakProperties.realm}/clients/${client.id}"

                        val update = mapOf("enabled" to enabled)

                        webClient.put()
                            .uri(clientUrl)
                            .header("Authorization", "Bearer $token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(update)
                            .retrieve()
                            .bodyToMono(Void::class.java)
                    }
            }
    }

    /**
     * Получение client secret по internal ID.
     *
     * @param internalClientId Keycloak internal client ID (UUID)
     * @param token admin access token
     * @return client secret
     */
    private fun getClientSecret(internalClientId: String, token: String): Mono<String> {
        val secretUrl =
            "${keycloakProperties.url}/admin/realms/${keycloakProperties.realm}/clients/$internalClientId/client-secret"

        return webClient.get()
            .uri(secretUrl)
            .header("Authorization", "Bearer $token")
            .retrieve()
            .bodyToMono(KeycloakCredential::class.java)
            .map { it.value }
    }

    /**
     * Keycloak client representation.
     */
    data class KeycloakClient(
        val id: String,                         // Internal UUID
        val clientId: String,                   // Publicly visible client_id
        val description: String? = null,
        val enabled: Boolean = true,
        val serviceAccountsEnabled: Boolean? = false,
        val createdTimestamp: Long = 0
    )

    /**
     * Keycloak client с secret (после создания или ротации).
     */
    data class KeycloakClientWithSecret(
        val id: String,
        val clientId: String,
        val description: String?,
        val enabled: Boolean,
        val serviceAccountsEnabled: Boolean?,
        val createdTimestamp: Long,
        val secret: String
    )

    /**
     * Keycloak credential response.
     */
    private data class KeycloakCredential(
        val type: String = "secret",
        val value: String
    )
}
