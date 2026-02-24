package com.company.gateway.admin.security

import com.company.gateway.common.model.Role
import io.jsonwebtoken.Claims
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import java.security.Principal
import java.util.UUID

/**
 * Аутентифицированный пользователь из JWT токена.
 *
 * Реализует Principal interface и хранит данные пользователя,
 * извлечённые из JWT claims: userId, username, role.
 *
 * Поддерживает два источника токенов:
 * - Legacy HMAC JWT (через fromClaims)
 * - Keycloak RS256 JWT (через fromKeycloakJwt)
 */
data class AuthenticatedUser(
    val userId: UUID,
    val username: String,
    val role: Role,
    val email: String? = null
) : Principal {

    /**
     * Возвращает имя пользователя (для Principal interface).
     */
    override fun getName(): String = username

    /**
     * Возвращает Spring Security authorities на основе роли.
     */
    val authorities: Collection<GrantedAuthority>
        get() = listOf(SimpleGrantedAuthority("ROLE_${role.name}"))

    companion object {
        /**
         * Маппинг Keycloak ролей на Role enum.
         */
        private val KEYCLOAK_ROLE_MAPPING = mapOf(
            "admin-ui:developer" to Role.DEVELOPER,
            "admin-ui:security" to Role.SECURITY,
            "admin-ui:admin" to Role.ADMIN
        )

        /**
         * Создаёт AuthenticatedUser из legacy JWT claims (HMAC-SHA256).
         *
         * @param claims JWT claims с sub (userId), username, role
         * @return AuthenticatedUser с данными из claims
         * @throws JwtAuthenticationException.TokenInvalid если claims содержат некорректные данные
         */
        fun fromClaims(claims: Claims): AuthenticatedUser {
            return try {
                AuthenticatedUser(
                    userId = UUID.fromString(claims.subject),
                    username = claims["username"] as String,
                    role = Role.valueOf((claims["role"] as String).uppercase())
                )
            } catch (e: Exception) {
                // Некорректные claims (неверный UUID, отсутствует username, неизвестная role)
                throw JwtAuthenticationException.TokenInvalid()
            }
        }

        /**
         * Создаёт AuthenticatedUser из Keycloak JWT (RS256).
         *
         * Маппинг claims:
         * - sub → userId (UUID из Keycloak)
         * - preferred_username → username
         * - email → email
         * - realm_access.roles → role (первая найденная admin-ui:* роль)
         *
         * @param jwt Spring Security Jwt объект с Keycloak claims
         * @return AuthenticatedUser с данными из Keycloak JWT
         * @throws JwtAuthenticationException.TokenInvalid если обязательные claims отсутствуют
         */
        fun fromKeycloakJwt(jwt: Jwt): AuthenticatedUser {
            return try {
                val userId = UUID.fromString(jwt.subject)
                val username = jwt.getClaimAsString("preferred_username")
                    ?: throw IllegalArgumentException("preferred_username claim is missing")
                val email = jwt.getClaimAsString("email")
                val role = extractKeycloakRole(jwt)
                    ?: throw IllegalArgumentException("No valid admin-ui role found in realm_access.roles")

                AuthenticatedUser(
                    userId = userId,
                    username = username,
                    role = role,
                    email = email
                )
            } catch (e: Exception) {
                throw JwtAuthenticationException.TokenInvalid()
            }
        }

        /**
         * Извлекает роль из Keycloak JWT claims.
         *
         * Ищет первую роль с префиксом admin-ui: в realm_access.roles
         * и преобразует в Role enum.
         *
         * Приоритет (если несколько ролей): ADMIN > SECURITY > DEVELOPER
         */
        private fun extractKeycloakRole(jwt: Jwt): Role? {
            val realmAccess = jwt.getClaimAsMap("realm_access") ?: return null

            @Suppress("UNCHECKED_CAST")
            val roles = realmAccess["roles"] as? List<String> ?: return null

            // Выбираем роль с наивысшим приоритетом
            return roles
                .mapNotNull { KEYCLOAK_ROLE_MAPPING[it] }
                .maxByOrNull { it.ordinal }
        }
    }
}
