package com.company.gateway.admin.security

import com.company.gateway.common.model.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

/**
 * Сервис для работы с JWT токенами.
 *
 * Генерирует и валидирует JWT токены с использованием алгоритма HMAC-SHA256.
 * Токены содержат: sub (user_id), username, role, exp (срок действия).
 */
@Service
class JwtService(
    @Value("\${jwt.secret}")
    private val secret: String,
    @Value("\${jwt.expiration:86400000}")
    private val expiration: Long
) {
    // Ленивая инициализация ключа из секрета
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray())
    }

    /**
     * Генерирует JWT токен для пользователя.
     *
     * @param user пользователь, для которого генерируется токен
     * @return строка JWT токена
     */
    fun generateToken(user: User): String {
        val now = Date()
        val expiryDate = Date(now.time + expiration)

        return Jwts.builder()
            .subject(user.id.toString())
            .claim("username", user.username)
            .claim("role", user.role.name.lowercase())
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(key)
            .compact()
    }

    /**
     * Валидирует токен и возвращает claims.
     *
     * @param token JWT токен для валидации
     * @return Claims если токен валиден, null в противном случае
     */
    fun validateToken(token: String): Claims? {
        return try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: Exception) {
            // Токен невалиден или истёк
            null
        }
    }

    /**
     * Извлекает user ID из токена.
     *
     * @param token JWT токен
     * @return UUID пользователя или null если токен невалиден
     */
    fun extractUserId(token: String): UUID? {
        return validateToken(token)?.subject?.let {
            try {
                UUID.fromString(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    /**
     * Извлекает username из токена.
     *
     * @param token JWT токен
     * @return username или null если токен невалиден
     */
    fun extractUsername(token: String): String? {
        return validateToken(token)?.get("username", String::class.java)
    }

    /**
     * Извлекает роль из токена.
     *
     * @param token JWT токен
     * @return роль в lowercase или null если токен невалиден
     */
    fun extractRole(token: String): String? {
        return validateToken(token)?.get("role", String::class.java)
    }
}
