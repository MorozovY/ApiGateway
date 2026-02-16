package com.company.gateway.admin.service

import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.common.model.User
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

/**
 * Сервис аутентификации пользователей.
 *
 * Проверяет учётные данные пользователя:
 * - Существование пользователя
 * - Активность аккаунта (isActive)
 * - Корректность пароля через BCrypt
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordService: PasswordService
) {
    /**
     * Аутентифицирует пользователя по username и password.
     *
     * @param username имя пользователя
     * @param password пароль в открытом виде
     * @return Mono<User> при успешной аутентификации
     * @throws AuthenticationException если аутентификация не удалась
     */
    fun authenticate(username: String, password: String): Mono<User> {
        return userRepository.findByUsername(username)
            .switchIfEmpty(Mono.error(AuthenticationException("Invalid credentials")))
            .flatMap { user ->
                // Сначала проверяем, активен ли аккаунт
                if (!user.isActive) {
                    return@flatMap Mono.error<User>(AuthenticationException("Account is disabled"))
                }
                // Затем проверяем пароль
                if (!passwordService.verify(password, user.passwordHash)) {
                    return@flatMap Mono.error<User>(AuthenticationException("Invalid credentials"))
                }
                Mono.just(user)
            }
    }
}

/**
 * Исключение аутентификации.
 *
 * Бросается при ошибках аутентификации:
 * - Неверные учётные данные
 * - Неактивный аккаунт
 */
class AuthenticationException(message: String) : RuntimeException(message)
