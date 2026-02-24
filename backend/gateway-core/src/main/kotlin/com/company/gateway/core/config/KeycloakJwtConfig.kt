package com.company.gateway.core.config

import com.company.gateway.core.properties.KeycloakProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder

/**
 * Конфигурация ReactiveJwtDecoder для JWT валидации в Gateway Core.
 *
 * Активируется при keycloak.enabled=true.
 * Настраивает JWKS-based JWT валидацию с multi-issuer support.
 *
 * Особенности:
 * - Использует RS256 алгоритм (асимметричные ключи)
 * - JWKS endpoint кэшируется (по умолчанию 5 минут)
 * - Поддержка нескольких issuer для Docker/localhost совместимости
 *
 * @see com.company.gateway.core.filter.JwtAuthenticationFilter
 */
@Configuration
@EnableConfigurationProperties(KeycloakProperties::class)
@ConditionalOnProperty(name = ["keycloak.enabled"], havingValue = "true")
class KeycloakJwtConfig(
    private val keycloakProperties: KeycloakProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        log.info("Keycloak JWT validation ENABLED in Gateway Core")
        log.info("Keycloak issuer URI: ${keycloakProperties.issuerUri}")
        log.info("Keycloak JWKS URI: ${keycloakProperties.jwksUri}")
    }

    /**
     * ReactiveJwtDecoder для валидации Keycloak JWT токенов.
     *
     * Использует NimbusReactiveJwtDecoder с JWKS endpoint для получения публичных ключей.
     * JWKS кэшируется автоматически (по умолчанию 5 минут).
     *
     * Поддерживает несколько issuer для совместимости Docker/localhost:
     * - http://localhost:8180/realms/api-gateway (внешние запросы)
     * - http://host.docker.internal:8180/realms/api-gateway (Docker)
     * - http://keycloak:8080/realms/api-gateway (Docker internal)
     */
    @Bean
    fun reactiveJwtDecoder(): ReactiveJwtDecoder {
        log.info("Configuring Keycloak JWT decoder with JWKS URI: ${keycloakProperties.jwksUri}")

        val decoder = NimbusReactiveJwtDecoder
            .withJwkSetUri(keycloakProperties.jwksUri)
            .build()

        // Разрешаем несколько issuer для Docker/localhost совместимости
        val allowedIssuers = listOf(
            "http://localhost:8180/realms/${keycloakProperties.realm}",
            "http://host.docker.internal:8180/realms/${keycloakProperties.realm}",
            "http://keycloak:8080/realms/${keycloakProperties.realm}"
        )
        log.info("Allowed JWT issuers: $allowedIssuers")

        val issuerValidator = MultiIssuerValidator(allowedIssuers)
        val timestampValidator = JwtTimestampValidator()
        val validator = DelegatingOAuth2TokenValidator(issuerValidator, timestampValidator)

        decoder.setJwtValidator(validator)
        return decoder
    }

    /**
     * OAuth2TokenValidator для проверки issuer из списка разрешённых.
     *
     * Необходим для поддержки разных URL Keycloak в разных средах:
     * - localhost:8180 при локальной разработке
     * - host.docker.internal:8180 при обращении из Docker (gateway-core контейнер)
     * - keycloak:8080 для внутренней Docker сети
     */
    private class MultiIssuerValidator(
        private val allowedIssuers: List<String>
    ) : OAuth2TokenValidator<Jwt> {
        private val log = LoggerFactory.getLogger(javaClass)

        override fun validate(token: Jwt): OAuth2TokenValidatorResult {
            val issuer = token.issuer?.toString()
            return if (issuer != null && allowedIssuers.contains(issuer)) {
                OAuth2TokenValidatorResult.success()
            } else {
                log.warn("JWT issuer '$issuer' is not in allowed list: $allowedIssuers")
                OAuth2TokenValidatorResult.failure(
                    OAuth2Error(
                        "invalid_issuer",
                        "JWT issuer '$issuer' is not in allowed list",
                        null
                    )
                )
            }
        }
    }
}
