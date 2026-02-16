package com.company.gateway.admin.security

import com.company.gateway.common.model.Role
import com.company.gateway.common.model.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class JwtServiceTest {

    private lateinit var jwtService: JwtService

    // Секрет минимум 32 символа для HS256
    private val testSecret = "test-secret-key-minimum-32-characters"
    private val testExpiration = 86400000L // 24 часа

    @BeforeEach
    fun setUp() {
        jwtService = JwtService(testSecret, testExpiration)
    }

    @Test
    fun `генерирует валидный JWT токен`() {
        val user = createTestUser()

        val token = jwtService.generateToken(user)

        assertThat(token).isNotBlank()
        // JWT формат: header.payload.signature
        assertThat(token.split(".")).hasSize(3)
    }

    @Test
    fun `токен содержит три части разделённые точками`() {
        val user = createTestUser()

        val token = jwtService.generateToken(user)
        val parts = token.split(".")

        assertThat(parts).hasSize(3)
        // Каждая часть не должна быть пустой
        parts.forEach { part ->
            assertThat(part).isNotBlank()
        }
    }

    @Test
    fun `validateToken возвращает claims для валидного токена`() {
        val user = createTestUser()
        val token = jwtService.generateToken(user)

        val claims = jwtService.validateToken(token)

        assertThat(claims).isNotNull
        assertThat(claims!!.subject).isEqualTo(user.id.toString())
        assertThat(claims["username"]).isEqualTo(user.username)
        assertThat(claims["role"]).isEqualTo(user.role.name.lowercase())
    }

    @Test
    fun `validateToken возвращает null для невалидного токена`() {
        val claims = jwtService.validateToken("invalid.token.here")

        assertThat(claims).isNull()
    }

    @Test
    fun `validateToken возвращает null для пустой строки`() {
        val claims = jwtService.validateToken("")

        assertThat(claims).isNull()
    }

    @Test
    fun `validateToken возвращает null для изменённого токена`() {
        val user = createTestUser()
        val token = jwtService.generateToken(user)
        // Изменяем последний символ токена (подпись)
        val tamperedToken = token.dropLast(1) + "X"

        val claims = jwtService.validateToken(tamperedToken)

        assertThat(claims).isNull()
    }

    @Test
    fun `extractUserId возвращает корректный UUID`() {
        val userId = UUID.randomUUID()
        val user = createTestUser(id = userId)
        val token = jwtService.generateToken(user)

        val extractedId = jwtService.extractUserId(token)

        assertThat(extractedId).isEqualTo(userId)
    }

    @Test
    fun `extractUserId возвращает null для невалидного токена`() {
        val extractedId = jwtService.extractUserId("invalid.token.here")

        assertThat(extractedId).isNull()
    }

    @Test
    fun `extractUsername возвращает корректное имя пользователя`() {
        val user = createTestUser(username = "testuser")
        val token = jwtService.generateToken(user)

        val extractedUsername = jwtService.extractUsername(token)

        assertThat(extractedUsername).isEqualTo("testuser")
    }

    @Test
    fun `extractUsername возвращает null для невалидного токена`() {
        val extractedUsername = jwtService.extractUsername("invalid.token")

        assertThat(extractedUsername).isNull()
    }

    @Test
    fun `extractRole возвращает роль в lowercase`() {
        val user = createTestUser(role = Role.ADMIN)
        val token = jwtService.generateToken(user)

        val extractedRole = jwtService.extractRole(token)

        assertThat(extractedRole).isEqualTo("admin")
    }

    @Test
    fun `extractRole возвращает null для невалидного токена`() {
        val extractedRole = jwtService.extractRole("invalid.token")

        assertThat(extractedRole).isNull()
    }

    @Test
    fun `разные пользователи получают разные токены`() {
        val user1 = createTestUser(username = "user1")
        val user2 = createTestUser(username = "user2")

        val token1 = jwtService.generateToken(user1)
        val token2 = jwtService.generateToken(user2)

        assertThat(token1).isNotEqualTo(token2)
    }

    @Test
    fun `токены для одного пользователя содержат одинаковые данные`() {
        val user = createTestUser()

        val token1 = jwtService.generateToken(user)
        val token2 = jwtService.generateToken(user)

        // Оба токена должны содержать одинаковые данные пользователя
        assertThat(jwtService.extractUserId(token1)).isEqualTo(jwtService.extractUserId(token2))
        assertThat(jwtService.extractUsername(token1)).isEqualTo(jwtService.extractUsername(token2))
        assertThat(jwtService.extractRole(token1)).isEqualTo(jwtService.extractRole(token2))
    }

    @Test
    fun `validateToken возвращает null для истёкшего токена`() {
        // Создаём JwtService с очень коротким временем жизни токена (1 мс)
        val shortLivedJwtService = JwtService(testSecret, 1L)
        val user = createTestUser()

        val token = shortLivedJwtService.generateToken(user)

        // Токен уже истёк к моменту проверки (1 мс)
        val claims = shortLivedJwtService.validateToken(token)

        assertThat(claims).isNull()
    }

    @Test
    fun `claims содержат expiration`() {
        val user = createTestUser()
        val token = jwtService.generateToken(user)

        val claims = jwtService.validateToken(token)

        assertThat(claims).isNotNull
        assertThat(claims!!.expiration).isNotNull()
        assertThat(claims.expiration.time).isGreaterThan(System.currentTimeMillis())
    }

    @Test
    fun `claims содержат issuedAt`() {
        val user = createTestUser()
        val token = jwtService.generateToken(user)

        val claims = jwtService.validateToken(token)

        assertThat(claims).isNotNull
        assertThat(claims!!.issuedAt).isNotNull()
        assertThat(claims.issuedAt.time).isLessThanOrEqualTo(System.currentTimeMillis())
    }

    private fun createTestUser(
        id: UUID = UUID.randomUUID(),
        username: String = "testuser",
        role: Role = Role.DEVELOPER
    ) = User(
        id = id,
        username = username,
        email = "test@example.com",
        passwordHash = "\$2a\$10\$hashedpassword",
        role = role,
        isActive = true
    )
}
