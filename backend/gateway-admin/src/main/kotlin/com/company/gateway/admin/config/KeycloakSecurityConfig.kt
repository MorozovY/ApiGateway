package com.company.gateway.admin.config

import com.company.gateway.admin.properties.KeycloakProperties
import com.company.gateway.admin.security.KeycloakAccessDeniedHandler
import com.company.gateway.admin.security.KeycloakAuthenticationEntryPoint
import com.company.gateway.admin.security.KeycloakGrantedAuthoritiesConverter
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter
import org.springframework.security.web.server.SecurityWebFilterChain

/**
 * Конфигурация Spring Security для Keycloak OAuth2 Resource Server.
 *
 * Активируется при keycloak.enabled=true.
 * Настраивает JWT валидацию через Keycloak JWKS endpoint.
 *
 * Особенности:
 * - Использует RS256 алгоритм (асимметричные ключи)
 * - JWKS endpoint кэшируется (по умолчанию 5 минут)
 * - Role mapping: admin-ui:X -> ROLE_X
 * - /api/v1/users/ требует ROLE_ADMIN
 *
 * @see com.company.gateway.admin.config.SecurityConfig для legacy режима
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(KeycloakProperties::class)
@ConditionalOnProperty(name = ["keycloak.enabled"], havingValue = "true")
class KeycloakSecurityConfig(
    private val keycloakProperties: KeycloakProperties,
    private val authenticationEntryPoint: KeycloakAuthenticationEntryPoint,
    private val accessDeniedHandler: KeycloakAccessDeniedHandler
) {
    // Создаём converter локально, чтобы Spring Boot не регистрировал его как web converter
    private val authoritiesConverter = KeycloakGrantedAuthoritiesConverter()
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        log.info("Keycloak security mode ENABLED")
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
    fun keycloakJwtDecoder(): ReactiveJwtDecoder {
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
     */
    private class MultiIssuerValidator(
        private val allowedIssuers: List<String>
    ) : OAuth2TokenValidator<Jwt> {
        override fun validate(token: Jwt): OAuth2TokenValidatorResult {
            val issuer = token.issuer?.toString()
            return if (issuer != null && allowedIssuers.contains(issuer)) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(
                    org.springframework.security.oauth2.core.OAuth2Error(
                        "invalid_issuer",
                        "JWT issuer '$issuer' is not in allowed list: $allowedIssuers",
                        null
                    )
                )
            }
        }
    }

    /**
     * Конвертер JWT -> Authentication с кастомными authorities.
     *
     * Преобразует Keycloak JWT в JwtAuthenticationToken с:
     * - Authorities из realm_access.roles (через KeycloakGrantedAuthoritiesConverter)
     */
    private fun keycloakJwtAuthenticationConverter(): ReactiveJwtAuthenticationConverter {
        val converter = ReactiveJwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter)
        return converter
    }

    /**
     * Security filter chain для Keycloak режима.
     *
     * Настройки:
     * - OAuth2 Resource Server с JWT validation
     * - Role-based authorization для /api/v1/users/
     * - RFC 7807 error responses
     */
    @Bean
    fun keycloakSecurityFilterChain(
        http: ServerHttpSecurity,
        jwtDecoder: ReactiveJwtDecoder
    ): SecurityWebFilterChain {
        log.info("Configuring Keycloak security filter chain")

        return http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtDecoder(jwtDecoder)
                    jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter())
                }
            }
            .authorizeExchange { exchanges ->
                exchanges
                    // Public endpoints
                    .pathMatchers("/actuator/health/**").permitAll()
                    .pathMatchers("/actuator/info").permitAll()
                    .pathMatchers("/actuator/prometheus").permitAll()
                    // Auth endpoints: login, logout, reset-demo-passwords — public
                    .pathMatchers("/api/v1/auth/login").permitAll()
                    .pathMatchers("/api/v1/auth/logout").permitAll()
                    .pathMatchers("/api/v1/auth/reset-demo-passwords").permitAll()
                    // Auth endpoints: change-password, me — требуют аутентификации
                    .pathMatchers("/api/v1/auth/change-password").authenticated()
                    .pathMatchers("/api/v1/auth/me").authenticated()
                    .pathMatchers("/api-docs/**").permitAll()
                    .pathMatchers("/swagger-ui/**").permitAll()
                    .pathMatchers("/swagger-ui.html").permitAll()
                    .pathMatchers("/webjars/**").permitAll()
                    // User management requires ADMIN role (AC7)
                    .pathMatchers("/api/v1/users/**").hasRole("ADMIN")
                    // All other API endpoints require authentication
                    .pathMatchers("/api/v1/**").authenticated()
                    // Everything else is public
                    .anyExchange().permitAll()
            }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
            }
            .build()
    }
}
