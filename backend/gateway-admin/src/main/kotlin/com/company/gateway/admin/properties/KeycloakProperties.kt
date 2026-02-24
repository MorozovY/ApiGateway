package com.company.gateway.admin.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Конфигурационные свойства для Keycloak интеграции.
 *
 * Используется для OAuth2 Resource Server аутентификации с Keycloak.
 * Feature flag `enabled` позволяет переключаться между legacy auth и Keycloak.
 *
 * @see com.company.gateway.admin.config.KeycloakSecurityConfig
 */
@ConfigurationProperties(prefix = "keycloak")
data class KeycloakProperties(
    /**
     * Включить Keycloak аутентификацию вместо legacy JWT.
     * По умолчанию ВЫКЛЮЧЕН для безопасного rollout.
     */
    val enabled: Boolean = false,

    /**
     * URL Keycloak сервера (без /realms/...).
     * Например: http://localhost:8180
     */
    val url: String = "http://localhost:8180",

    /**
     * Имя realm в Keycloak.
     */
    val realm: String = "api-gateway",

    /**
     * Client ID приложения в Keycloak.
     */
    val clientId: String = "admin-ui",

    /**
     * Admin username для Keycloak Admin API.
     * Используется для сброса паролей демо-пользователей.
     */
    val adminUsername: String = "admin",

    /**
     * Admin password для Keycloak Admin API.
     */
    val adminPassword: String = "admin"
) {
    /**
     * Полный URL issuer для JWT validation.
     * Формат: {url}/realms/{realm}
     */
    val issuerUri: String
        get() = "$url/realms/$realm"

    /**
     * URL JWKS endpoint для получения публичных ключей.
     * Формат: {issuerUri}/protocol/openid-connect/certs
     */
    val jwksUri: String
        get() = "$issuerUri/protocol/openid-connect/certs"
}
