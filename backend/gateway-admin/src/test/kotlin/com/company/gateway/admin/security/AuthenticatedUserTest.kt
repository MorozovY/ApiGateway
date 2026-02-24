package com.company.gateway.admin.security

import com.company.gateway.common.model.Role
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
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

    /**
     * Тесты для fromKeycloakJwt — создание AuthenticatedUser из Keycloak JWT.
     */
    @Nested
    inner class FromKeycloakJwtTests {

        /**
         * Создаёт mock Keycloak JWT с заданными claims.
         */
        private fun createKeycloakJwt(
            subject: String,
            preferredUsername: String?,
            email: String? = null,
            realmAccessRoles: List<String>? = null
        ): Jwt {
            val claims = mutableMapOf<String, Any>()

            if (preferredUsername != null) {
                claims["preferred_username"] = preferredUsername
            }
            if (email != null) {
                claims["email"] = email
            }
            if (realmAccessRoles != null) {
                claims["realm_access"] = mapOf("roles" to realmAccessRoles)
            }

            return Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims { it.putAll(claims) }
                .build()
        }

        @Test
        fun `fromKeycloakJwt создаёт AuthenticatedUser из Keycloak JWT с ролью DEVELOPER`() {
            // Given: Keycloak JWT с ролью admin-ui:developer
            val userId = UUID.randomUUID()
            val jwt = createKeycloakJwt(
                subject = userId.toString(),
                preferredUsername = "dev_user",
                email = "dev@example.com",
                realmAccessRoles = listOf("admin-ui:developer", "default-roles-api-gateway")
            )

            // When: создаём AuthenticatedUser
            val user = AuthenticatedUser.fromKeycloakJwt(jwt)

            // Then: данные корректно извлечены
            assertThat(user.userId).isEqualTo(userId)
            assertThat(user.username).isEqualTo("dev_user")
            assertThat(user.email).isEqualTo("dev@example.com")
            assertThat(user.role).isEqualTo(Role.DEVELOPER)
        }

        @Test
        fun `fromKeycloakJwt создаёт AuthenticatedUser с ролью SECURITY`() {
            // Given: Keycloak JWT с ролью admin-ui:security
            val userId = UUID.randomUUID()
            val jwt = createKeycloakJwt(
                subject = userId.toString(),
                preferredUsername = "security_user",
                email = "security@example.com",
                realmAccessRoles = listOf("admin-ui:security")
            )

            // When: создаём AuthenticatedUser
            val user = AuthenticatedUser.fromKeycloakJwt(jwt)

            // Then: роль корректно извлечена
            assertThat(user.role).isEqualTo(Role.SECURITY)
        }

        @Test
        fun `fromKeycloakJwt создаёт AuthenticatedUser с ролью ADMIN`() {
            // Given: Keycloak JWT с ролью admin-ui:admin
            val userId = UUID.randomUUID()
            val jwt = createKeycloakJwt(
                subject = userId.toString(),
                preferredUsername = "admin_user",
                email = "admin@example.com",
                realmAccessRoles = listOf("admin-ui:admin")
            )

            // When: создаём AuthenticatedUser
            val user = AuthenticatedUser.fromKeycloakJwt(jwt)

            // Then: роль корректно извлечена
            assertThat(user.role).isEqualTo(Role.ADMIN)
        }

        @Test
        fun `fromKeycloakJwt выбирает роль с наивысшим приоритетом при нескольких ролях`() {
            // Given: Keycloak JWT с несколькими ролями (ADMIN имеет наивысший приоритет)
            val userId = UUID.randomUUID()
            val jwt = createKeycloakJwt(
                subject = userId.toString(),
                preferredUsername = "multi_role_user",
                realmAccessRoles = listOf("admin-ui:developer", "admin-ui:security", "admin-ui:admin")
            )

            // When: создаём AuthenticatedUser
            val user = AuthenticatedUser.fromKeycloakJwt(jwt)

            // Then: выбрана роль с наивысшим приоритетом (ADMIN)
            assertThat(user.role).isEqualTo(Role.ADMIN)
        }

        @Test
        fun `fromKeycloakJwt работает без email claim`() {
            // Given: Keycloak JWT без email
            val userId = UUID.randomUUID()
            val jwt = createKeycloakJwt(
                subject = userId.toString(),
                preferredUsername = "user_no_email",
                email = null,
                realmAccessRoles = listOf("admin-ui:developer")
            )

            // When: создаём AuthenticatedUser
            val user = AuthenticatedUser.fromKeycloakJwt(jwt)

            // Then: email равен null
            assertThat(user.email).isNull()
        }

        @Test
        fun `fromKeycloakJwt выбрасывает TokenInvalid при отсутствии preferred_username`() {
            // Given: Keycloak JWT без preferred_username
            val userId = UUID.randomUUID()
            val jwt = createKeycloakJwt(
                subject = userId.toString(),
                preferredUsername = null,
                realmAccessRoles = listOf("admin-ui:developer")
            )

            // When & Then: выбрасывается JwtAuthenticationException.TokenInvalid
            org.junit.jupiter.api.assertThrows<JwtAuthenticationException.TokenInvalid> {
                AuthenticatedUser.fromKeycloakJwt(jwt)
            }
        }

        @Test
        fun `fromKeycloakJwt выбрасывает TokenInvalid при отсутствии admin-ui ролей`() {
            // Given: Keycloak JWT без admin-ui ролей
            val userId = UUID.randomUUID()
            val jwt = createKeycloakJwt(
                subject = userId.toString(),
                preferredUsername = "user_no_role",
                realmAccessRoles = listOf("default-roles-api-gateway", "other-app:user")
            )

            // When & Then: выбрасывается JwtAuthenticationException.TokenInvalid
            org.junit.jupiter.api.assertThrows<JwtAuthenticationException.TokenInvalid> {
                AuthenticatedUser.fromKeycloakJwt(jwt)
            }
        }

        @Test
        fun `fromKeycloakJwt выбрасывает TokenInvalid при невалидном UUID в subject`() {
            // Given: Keycloak JWT с невалидным UUID
            val jwt = createKeycloakJwt(
                subject = "not-a-valid-uuid",
                preferredUsername = "user",
                realmAccessRoles = listOf("admin-ui:developer")
            )

            // When & Then: выбрасывается JwtAuthenticationException.TokenInvalid
            org.junit.jupiter.api.assertThrows<JwtAuthenticationException.TokenInvalid> {
                AuthenticatedUser.fromKeycloakJwt(jwt)
            }
        }

        @Test
        fun `fromKeycloakJwt выбрасывает TokenInvalid при отсутствии realm_access`() {
            // Given: Keycloak JWT без realm_access claim
            val userId = UUID.randomUUID()
            val jwt = createKeycloakJwt(
                subject = userId.toString(),
                preferredUsername = "user_no_realm_access",
                realmAccessRoles = null
            )

            // When & Then: выбрасывается JwtAuthenticationException.TokenInvalid
            org.junit.jupiter.api.assertThrows<JwtAuthenticationException.TokenInvalid> {
                AuthenticatedUser.fromKeycloakJwt(jwt)
            }
        }
    }
}
