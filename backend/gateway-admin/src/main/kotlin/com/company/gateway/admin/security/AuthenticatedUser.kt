package com.company.gateway.admin.security

import com.company.gateway.common.model.Role
import io.jsonwebtoken.Claims
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.security.Principal
import java.util.UUID

/**
 * Аутентифицированный пользователь из JWT токена.
 *
 * Реализует Principal interface и хранит данные пользователя,
 * извлечённые из JWT claims: userId, username, role.
 */
data class AuthenticatedUser(
    val userId: UUID,
    val username: String,
    val role: Role
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
         * Создаёт AuthenticatedUser из JWT claims.
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
    }
}
