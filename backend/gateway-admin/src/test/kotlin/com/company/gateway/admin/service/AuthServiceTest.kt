package com.company.gateway.admin.service

import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.common.model.Role
import com.company.gateway.common.model.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AuthServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var passwordService: PasswordService

    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        authService = AuthService(userRepository, passwordService)
    }

    @Test
    fun `успешная аутентификация возвращает пользователя`() {
        val user = createActiveUser()
        whenever(userRepository.findByUsername("testuser")).thenReturn(Mono.just(user))
        whenever(passwordService.verify("correctpassword", user.passwordHash)).thenReturn(true)

        StepVerifier.create(authService.authenticate("testuser", "correctpassword"))
            .expectNext(user)
            .verifyComplete()
    }

    @Test
    fun `неверный пароль возвращает ошибку Invalid credentials`() {
        val user = createActiveUser()
        whenever(userRepository.findByUsername("testuser")).thenReturn(Mono.just(user))
        whenever(passwordService.verify("wrongpassword", user.passwordHash)).thenReturn(false)

        StepVerifier.create(authService.authenticate("testuser", "wrongpassword"))
            .expectErrorMatches { ex ->
                ex is AuthenticationException && ex.message == "Invalid credentials"
            }
            .verify()
    }

    @Test
    fun `несуществующий пользователь возвращает ошибку Invalid credentials`() {
        whenever(userRepository.findByUsername("nonexistent")).thenReturn(Mono.empty())

        StepVerifier.create(authService.authenticate("nonexistent", "anypassword"))
            .expectErrorMatches { ex ->
                ex is AuthenticationException && ex.message == "Invalid credentials"
            }
            .verify()
    }

    @Test
    fun `неактивный пользователь возвращает ошибку Account is disabled`() {
        val inactiveUser = createInactiveUser()
        whenever(userRepository.findByUsername("inactive")).thenReturn(Mono.just(inactiveUser))

        StepVerifier.create(authService.authenticate("inactive", "anypassword"))
            .expectErrorMatches { ex ->
                ex is AuthenticationException && ex.message == "Account is disabled"
            }
            .verify()
    }

    @Test
    fun `проверка isActive происходит до проверки пароля`() {
        val inactiveUser = createInactiveUser()
        whenever(userRepository.findByUsername("inactive")).thenReturn(Mono.just(inactiveUser))
        // passwordService.verify НЕ должен вызываться для неактивного пользователя

        StepVerifier.create(authService.authenticate("inactive", "anypassword"))
            .expectError(AuthenticationException::class.java)
            .verify()
    }

    @Test
    fun `аутентификация с пустым username возвращает ошибку`() {
        whenever(userRepository.findByUsername("")).thenReturn(Mono.empty())

        StepVerifier.create(authService.authenticate("", "password"))
            .expectError(AuthenticationException::class.java)
            .verify()
    }

    @Test
    fun `аутентификация сохраняет все поля пользователя`() {
        val user = createActiveUser()
        whenever(userRepository.findByUsername("testuser")).thenReturn(Mono.just(user))
        whenever(passwordService.verify(any(), any())).thenReturn(true)

        StepVerifier.create(authService.authenticate("testuser", "password"))
            .expectNextMatches { authenticatedUser ->
                authenticatedUser.id == user.id &&
                authenticatedUser.username == user.username &&
                authenticatedUser.email == user.email &&
                authenticatedUser.role == user.role &&
                authenticatedUser.isActive == user.isActive
            }
            .verifyComplete()
    }

    @Test
    fun `аутентификация пользователя с ролью ADMIN`() {
        val adminUser = User(
            id = UUID.randomUUID(),
            username = "admin",
            email = "admin@example.com",
            passwordHash = "\$2a\$10\$hash",
            role = Role.ADMIN,
            isActive = true
        )
        whenever(userRepository.findByUsername("admin")).thenReturn(Mono.just(adminUser))
        whenever(passwordService.verify("adminpassword", adminUser.passwordHash)).thenReturn(true)

        StepVerifier.create(authService.authenticate("admin", "adminpassword"))
            .expectNextMatches { user -> user.role == Role.ADMIN }
            .verifyComplete()
    }

    @Test
    fun `аутентификация пользователя с ролью SECURITY`() {
        val securityUser = User(
            id = UUID.randomUUID(),
            username = "security",
            email = "security@example.com",
            passwordHash = "\$2a\$10\$hash",
            role = Role.SECURITY,
            isActive = true
        )
        whenever(userRepository.findByUsername("security")).thenReturn(Mono.just(securityUser))
        whenever(passwordService.verify("securitypassword", securityUser.passwordHash)).thenReturn(true)

        StepVerifier.create(authService.authenticate("security", "securitypassword"))
            .expectNextMatches { user -> user.role == Role.SECURITY }
            .verifyComplete()
    }

    private fun createActiveUser() = User(
        id = UUID.randomUUID(),
        username = "testuser",
        email = "test@example.com",
        passwordHash = "\$2a\$10\$hashedpassword",
        role = Role.DEVELOPER,
        isActive = true
    )

    private fun createInactiveUser() = User(
        id = UUID.randomUUID(),
        username = "inactive",
        email = "inactive@example.com",
        passwordHash = "\$2a\$10\$hashedpassword",
        role = Role.DEVELOPER,
        isActive = false
    )
}
