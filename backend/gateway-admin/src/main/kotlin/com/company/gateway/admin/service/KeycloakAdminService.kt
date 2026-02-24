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

    /**
     * Получение admin токена через password grant.
     *
     * @return access_token для Admin API
     */
    fun getAdminToken(): Mono<String> {
        val tokenUrl = "${keycloakProperties.url}/realms/master/protocol/openid-connect/token"

        logger.debug("Получение admin токена от Keycloak: {}", tokenUrl)

        return webClient.post()
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
            .doOnSuccess { logger.debug("Admin токен получен") }
            .doOnError { e -> logger.error("Ошибка получения admin токена: {}", e.message) }
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
}
