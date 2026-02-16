package com.company.gateway.admin.security

import com.company.gateway.common.model.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import reactor.test.StepVerifier
import java.util.UUID

class SecurityContextUtilsTest {

    @Test
    fun `currentUser возвращает AuthenticatedUser из SecurityContext`() {
        // Given: SecurityContext с AuthenticatedUser
        val userId = UUID.randomUUID()
        val user = AuthenticatedUser(userId, "testuser", Role.DEVELOPER)
        val authentication = UsernamePasswordAuthenticationToken(
            user, null, user.authorities
        )

        // When: вызываем currentUser() внутри контекста
        val result = SecurityContextUtils.currentUser()
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))

        // Then: возвращается AuthenticatedUser
        StepVerifier.create(result)
            .assertNext { authenticatedUser ->
                assertThat(authenticatedUser.userId).isEqualTo(userId)
                assertThat(authenticatedUser.username).isEqualTo("testuser")
                assertThat(authenticatedUser.role).isEqualTo(Role.DEVELOPER)
            }
            .verifyComplete()
    }

    @Test
    fun `currentUser возвращает empty когда пользователь не аутентифицирован`() {
        // When: вызываем currentUser() без контекста
        val result = SecurityContextUtils.currentUser()

        // Then: возвращается empty Mono
        StepVerifier.create(result)
            .verifyComplete()
    }

    @Test
    fun `currentUserId возвращает UUID пользователя`() {
        // Given: SecurityContext с AuthenticatedUser
        val userId = UUID.randomUUID()
        val user = AuthenticatedUser(userId, "testuser", Role.DEVELOPER)
        val authentication = UsernamePasswordAuthenticationToken(
            user, null, user.authorities
        )

        // When: вызываем currentUserId()
        val result = SecurityContextUtils.currentUserId()
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))

        // Then: возвращается userId
        StepVerifier.create(result)
            .assertNext { id ->
                assertThat(id).isEqualTo(userId)
            }
            .verifyComplete()
    }

    @Test
    fun `currentUserId возвращает empty когда пользователь не аутентифицирован`() {
        // When: вызываем currentUserId() без контекста
        val result = SecurityContextUtils.currentUserId()

        // Then: возвращается empty Mono
        StepVerifier.create(result)
            .verifyComplete()
    }

    @Test
    fun `currentUsername возвращает имя пользователя`() {
        // Given: SecurityContext с AuthenticatedUser
        val user = AuthenticatedUser(UUID.randomUUID(), "testuser", Role.ADMIN)
        val authentication = UsernamePasswordAuthenticationToken(
            user, null, user.authorities
        )

        // When: вызываем currentUsername()
        val result = SecurityContextUtils.currentUsername()
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))

        // Then: возвращается username
        StepVerifier.create(result)
            .assertNext { username ->
                assertThat(username).isEqualTo("testuser")
            }
            .verifyComplete()
    }

    @Test
    fun `currentUsername возвращает empty когда пользователь не аутентифицирован`() {
        // When: вызываем currentUsername() без контекста
        val result = SecurityContextUtils.currentUsername()

        // Then: возвращается empty Mono
        StepVerifier.create(result)
            .verifyComplete()
    }

    @Test
    fun `currentUser возвращает empty когда principal не AuthenticatedUser`() {
        // Given: SecurityContext с другим типом principal
        val authentication = UsernamePasswordAuthenticationToken(
            "string-principal", null, emptyList()
        )

        // When: вызываем currentUser()
        val result = SecurityContextUtils.currentUser()
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))

        // Then: возвращается empty Mono (principal не AuthenticatedUser)
        StepVerifier.create(result)
            .verifyComplete()
    }

    @Test
    fun `currentUser возвращает empty когда principal равен null`() {
        // Given: SecurityContext с null principal
        val authentication = UsernamePasswordAuthenticationToken(
            null, null, emptyList()
        )

        // When: вызываем currentUser()
        val result = SecurityContextUtils.currentUser()
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))

        // Then: возвращается empty Mono (principal равен null)
        StepVerifier.create(result)
            .verifyComplete()
    }
}
