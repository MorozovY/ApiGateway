package com.company.gateway.admin.security

import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import reactor.core.publisher.Flux

/**
 * Конвертер для преобразования Keycloak ролей в Spring Security authorities.
 *
 * Keycloak хранит роли в claim `realm_access.roles` в формате:
 * ```json
 * {
 *   "realm_access": {
 *     "roles": ["admin-ui:admin", "admin-ui:developer", "default-roles-api-gateway"]
 *   }
 * }
 * ```
 *
 * Маппинг ролей:
 * - `admin-ui:developer` -> `ROLE_DEVELOPER`
 * - `admin-ui:security` -> `ROLE_SECURITY`
 * - `admin-ui:admin` -> `ROLE_ADMIN`
 *
 * ВАЖНО: Не помечаем @Component, чтобы Spring Boot не пытался зарегистрировать
 * его как web converter. Создаётся вручную в KeycloakSecurityConfig.
 *
 * @see com.company.gateway.admin.config.KeycloakSecurityConfig
 */
class KeycloakGrantedAuthoritiesConverter : Converter<Jwt, Flux<GrantedAuthority>> {

    companion object {
        /**
         * Маппинг Keycloak ролей на Spring Security authorities.
         * Только роли с префиксом `admin-ui:` обрабатываются.
         */
        private val ROLE_MAPPING = mapOf(
            "admin-ui:developer" to "ROLE_DEVELOPER",
            "admin-ui:security" to "ROLE_SECURITY",
            "admin-ui:admin" to "ROLE_ADMIN"
        )
    }

    /**
     * Преобразует JWT claims в Flux Spring Security authorities.
     *
     * @param jwt JWT токен с Keycloak claims
     * @return Flux GrantedAuthority на основе realm_access.roles
     */
    override fun convert(jwt: Jwt): Flux<GrantedAuthority> {
        val authorities = extractAuthorities(jwt)
        return Flux.fromIterable(authorities)
    }

    /**
     * Извлекает authorities из JWT (синхронный метод для unit-тестов).
     */
    fun extractAuthorities(jwt: Jwt): Collection<GrantedAuthority> {
        val realmAccess = jwt.getClaimAsMap("realm_access") ?: return emptyList()

        @Suppress("UNCHECKED_CAST")
        val roles = realmAccess["roles"] as? List<String> ?: return emptyList()

        return roles
            .mapNotNull { ROLE_MAPPING[it] }
            .map { SimpleGrantedAuthority(it) }
    }
}
