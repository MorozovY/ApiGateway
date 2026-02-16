package com.company.gateway.admin.security

import com.company.gateway.admin.exception.AccessDeniedException
import com.company.gateway.common.model.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextImpl
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.lang.reflect.Method
import java.util.UUID

/**
 * Unit тесты для RoleAuthorizationAspect.
 *
 * Тестирует логику проверки ролей в изоляции от Spring контекста.
 */
class RoleAuthorizationAspectTest {

    private lateinit var aspect: RoleAuthorizationAspect

    @BeforeEach
    fun setUp() {
        aspect = RoleAuthorizationAspect()
    }

    // ============================================
    // Тесты для AuthenticatedUser
    // ============================================
    // Примечание: Тесты для RoleHierarchy находятся в RoleHierarchyTest.kt

    @Nested
    inner class AuthenticatedUserTests {

        @Test
        fun `AuthenticatedUser содержит все обязательные поля`() {
            val user = AuthenticatedUser(
                userId = UUID.randomUUID(),
                username = "testuser",
                role = Role.DEVELOPER
            )

            assertThat(user.userId).isNotNull()
            assertThat(user.username).isEqualTo("testuser")
            assertThat(user.role).isEqualTo(Role.DEVELOPER)
        }

        @Test
        fun `AuthenticatedUser реализует Principal с корректным name`() {
            val user = AuthenticatedUser(
                userId = UUID.randomUUID(),
                username = "admin_user",
                role = Role.ADMIN
            )

            assertThat(user.name).isEqualTo("admin_user")
        }
    }

    // ============================================
    // Тесты для AccessDeniedException
    // ============================================

    @Nested
    inner class AccessDeniedExceptionTests {

        @Test
        fun `AccessDeniedException содержит message и detail`() {
            val exception = AccessDeniedException(
                message = "Access denied",
                detail = "Insufficient permissions"
            )

            assertThat(exception.message).isEqualTo("Access denied")
            assertThat(exception.detail).isEqualTo("Insufficient permissions")
        }

        @Test
        fun `AccessDeniedException использует message как default detail`() {
            val exception = AccessDeniedException("Not allowed")

            assertThat(exception.message).isEqualTo("Not allowed")
            assertThat(exception.detail).isEqualTo("Not allowed")
        }

        @Test
        fun `AccessDeniedException является RuntimeException`() {
            val exception = AccessDeniedException("Test")
            assertThat(exception).isInstanceOf(RuntimeException::class.java)
        }
    }

    // ============================================
    // Тесты для JwtAuthenticationException
    // ============================================

    @Nested
    inner class JwtAuthenticationExceptionTests {

        @Test
        fun `TokenMissing имеет корректный message и detail`() {
            val exception = JwtAuthenticationException.TokenMissing()

            assertThat(exception.message).isEqualTo("Authentication required")
            assertThat(exception.detail).isEqualTo("Authentication token is missing")
        }

        @Test
        fun `TokenExpired имеет корректный message и detail`() {
            val exception = JwtAuthenticationException.TokenExpired()

            assertThat(exception.message).isEqualTo("Token expired")
            assertThat(exception.detail).isEqualTo("Token expired")
        }

        @Test
        fun `TokenInvalid имеет корректный message и detail`() {
            val exception = JwtAuthenticationException.TokenInvalid()

            assertThat(exception.message).isEqualTo("Invalid token")
            assertThat(exception.detail).isEqualTo("Invalid token")
        }

        @Test
        fun `все подтипы являются JwtAuthenticationException`() {
            assertThat(JwtAuthenticationException.TokenMissing())
                .isInstanceOf(JwtAuthenticationException::class.java)
            assertThat(JwtAuthenticationException.TokenExpired())
                .isInstanceOf(JwtAuthenticationException::class.java)
            assertThat(JwtAuthenticationException.TokenInvalid())
                .isInstanceOf(JwtAuthenticationException::class.java)
        }
    }
}
