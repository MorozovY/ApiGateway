package com.company.gateway.core.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Конфигурационные свойства для Keycloak интеграции в Gateway Core.
 *
 * Используется для JWT валидации входящих запросов от API consumers.
 * Feature flag `enabled` позволяет включать/выключать JWT аутентификацию.
 *
 * @see com.company.gateway.core.config.KeycloakJwtConfig
 */
@ConfigurationProperties(prefix = "keycloak")
data class KeycloakProperties(
    /**
     * Включить JWT аутентификацию через Keycloak.
     * По умолчанию ВЫКЛЮЧЕН для обратной совместимости.
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
     * Дополнительные issuer URL для JWT validation.
     * Используется когда токены могут приходить от разных URL Keycloak
     * (например, внешний URL https://keycloak.example.com и внутренний http://keycloak:8080).
     * Формат: список URL через запятую.
     */
    val additionalIssuers: List<String> = emptyList()
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
