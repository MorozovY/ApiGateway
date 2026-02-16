package com.company.gateway.admin.security

import com.company.gateway.common.model.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
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

    /**
     * Валидирует токен и возвращает результат с различением типа ошибки.
     *
     * @param token JWT токен для валидации
     * @return TokenValidationResult с claims при успехе или типом ошибки при неудаче
     */
    fun validateTokenWithResult(token: String): TokenValidationResult {
        return try {
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
            TokenValidationResult.Valid(claims)
        } catch (e: ExpiredJwtException) {
            // Токен истёк
            TokenValidationResult.Expired
        } catch (e: SignatureException) {
            // Неверная подпись
            TokenValidationResult.Invalid
        } catch (e: Exception) {
            // Любая другая ошибка (неверный формат, malformed и т.д.)
            TokenValidationResult.Invalid
        }
    }
}

/**
 * Результат валидации JWT токена.
 */
sealed class TokenValidationResult {
    /**
     * Токен валиден, содержит claims.
     */
    data class Valid(val claims: Claims) : TokenValidationResult()

    /**
     * Токен истёк.
     */
    data object Expired : TokenValidationResult()

    /**
     * Токен невалиден (неверная подпись, формат и т.д.).
     */
    data object Invalid : TokenValidationResult()
}
