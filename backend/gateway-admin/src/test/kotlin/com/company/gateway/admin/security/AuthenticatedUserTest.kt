package com.company.gateway.admin.security

import com.company.gateway.common.model.Role
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Date
import java.util.UUID

class AuthenticatedUserTest {

    private val testSecret = "test-secret-key-minimum-32-characters"
    private val key = Keys.hmacShaKeyFor(testSecret.toByteArray())

    @Test
    fun `fromClaims создаёт AuthenticatedUser из JWT claims`() {
        // Given: JWT claims с данными пользователя
        val userId = UUID.randomUUID()
        val token = Jwts.builder()
            .subject(userId.toString())
            .claim("username", "testuser")
            .claim("role", "developer")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 86400000))
            .signWith(key)
            .compact()

        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload

        // When: создаём AuthenticatedUser
        val user = AuthenticatedUser.fromClaims(claims)

        // Then: данные корректно извлечены
        assertThat(user.userId).isEqualTo(userId)
        assertThat(user.username).isEqualTo("testuser")
        assertThat(user.role).isEqualTo(Role.DEVELOPER)
    }

    @Test
    fun `fromClaims корректно парсит роль ADMIN в uppercase`() {
        // Given: JWT claims с ролью в lowercase
        val userId = UUID.randomUUID()
        val token = Jwts.builder()
            .subject(userId.toString())
            .claim("username", "admin")
            .claim("role", "admin")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 86400000))
            .signWith(key)
            .compact()

        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload

        // When: создаём AuthenticatedUser
        val user = AuthenticatedUser.fromClaims(claims)

        // Then: роль корректно преобразована в enum
        assertThat(user.role).isEqualTo(Role.ADMIN)
    }

    @Test
    fun `fromClaims корректно парсит роль SECURITY`() {
        // Given: JWT claims с ролью security
        val userId = UUID.randomUUID()
        val token = Jwts.builder()
            .subject(userId.toString())
            .claim("username", "security_user")
            .claim("role", "security")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 86400000))
            .signWith(key)
            .compact()

        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload

        // When: создаём AuthenticatedUser
        val user = AuthenticatedUser.fromClaims(claims)

        // Then: роль корректно преобразована
        assertThat(user.role).isEqualTo(Role.SECURITY)
    }

    @Test
    fun `getName возвращает username`() {
        // Given: AuthenticatedUser
        val user = AuthenticatedUser(
            userId = UUID.randomUUID(),
            username = "testuser",
            role = Role.DEVELOPER
        )

        // When & Then: getName() возвращает username
        assertThat(user.name).isEqualTo("testuser")
    }

    @Test
    fun `authorities содержат роль с префиксом ROLE_`() {
        // Given: AuthenticatedUser с ролью DEVELOPER
        val user = AuthenticatedUser(
            userId = UUID.randomUUID(),
            username = "testuser",
            role = Role.DEVELOPER
        )

        // When & Then: authorities содержат ROLE_DEVELOPER
        assertThat(user.authorities)
            .hasSize(1)
            .extracting("authority")
            .containsExactly("ROLE_DEVELOPER")
    }

    @Test
    fun `authorities для роли ADMIN`() {
        // Given: AuthenticatedUser с ролью ADMIN
        val user = AuthenticatedUser(
            userId = UUID.randomUUID(),
            username = "admin",
            role = Role.ADMIN
        )

        // When & Then: authorities содержат ROLE_ADMIN
        assertThat(user.authorities)
            .hasSize(1)
            .extracting("authority")
            .containsExactly("ROLE_ADMIN")
    }

    @Test
    fun `data class equals работает корректно`() {
        // Given: два AuthenticatedUser с одинаковыми данными
        val userId = UUID.randomUUID()
        val user1 = AuthenticatedUser(userId, "testuser", Role.DEVELOPER)
        val user2 = AuthenticatedUser(userId, "testuser", Role.DEVELOPER)

        // When & Then: они равны
        assertThat(user1).isEqualTo(user2)
    }

    @Test
    fun `data class equals возвращает false для разных пользователей`() {
        // Given: два AuthenticatedUser с разными данными
        val user1 = AuthenticatedUser(UUID.randomUUID(), "user1", Role.DEVELOPER)
        val user2 = AuthenticatedUser(UUID.randomUUID(), "user2", Role.ADMIN)

        // When & Then: они не равны
        assertThat(user1).isNotEqualTo(user2)
    }

    @Test
    fun `fromClaims выбрасывает TokenInvalid при некорректном userId`() {
        // Given: JWT claims с невалидным UUID в subject
        val token = Jwts.builder()
            .subject("not-a-uuid")
            .claim("username", "testuser")
            .claim("role", "developer")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 86400000))
            .signWith(key)
            .compact()

        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload

        // When & Then: выбрасывается JwtAuthenticationException.TokenInvalid
        org.junit.jupiter.api.assertThrows<JwtAuthenticationException.TokenInvalid> {
            AuthenticatedUser.fromClaims(claims)
        }
    }

    @Test
    fun `fromClaims выбрасывает TokenInvalid при отсутствующем username`() {
        // Given: JWT claims без username
        val userId = UUID.randomUUID()
        val token = Jwts.builder()
            .subject(userId.toString())
            .claim("role", "developer")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 86400000))
            .signWith(key)
            .compact()

        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload

        // When & Then: выбрасывается JwtAuthenticationException.TokenInvalid
        org.junit.jupiter.api.assertThrows<JwtAuthenticationException.TokenInvalid> {
            AuthenticatedUser.fromClaims(claims)
        }
    }

    @Test
    fun `fromClaims выбрасывает TokenInvalid при неизвестной роли`() {
        // Given: JWT claims с неизвестной ролью
        val userId = UUID.randomUUID()
        val token = Jwts.builder()
            .subject(userId.toString())
            .claim("username", "testuser")
            .claim("role", "unknown_role")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 86400000))
            .signWith(key)
            .compact()

        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload

        // When & Then: выбрасывается JwtAuthenticationException.TokenInvalid
        org.junit.jupiter.api.assertThrows<JwtAuthenticationException.TokenInvalid> {
            AuthenticatedUser.fromClaims(claims)
        }
    }
}
