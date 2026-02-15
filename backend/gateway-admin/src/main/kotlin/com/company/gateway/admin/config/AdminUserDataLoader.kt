package com.company.gateway.admin.config

import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.admin.service.PasswordService
import com.company.gateway.common.model.Role
import com.company.gateway.common.model.User
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class AdminUserDataLoader(
    private val userRepository: UserRepository,
    private val passwordService: PasswordService,
    // Пароль администратора берётся из env var ADMIN_PASSWORD (по умолчанию только для dev!)
    @Value("\${app.admin.password:admin123}")
    private val adminPassword: String,
    // Email администратора (настраивается через env var)
    @Value("\${app.admin.email:admin@gateway.local}")
    private val adminEmail: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ВАЖНО: НЕ использовать @PostConstruct в reactive контексте!
    @EventListener(ApplicationReadyEvent::class)
    fun initAdminUser() {
        userRepository.findByUsername("admin")
            .switchIfEmpty(
                userRepository.save(
                    User(
                        username = "admin",
                        email = adminEmail,
                        passwordHash = passwordService.hash(adminPassword),
                        role = Role.ADMIN,
                        isActive = true
                    )
                ).doOnSuccess {
                    log.info("Создан пользователь admin с ролью ADMIN")
                }
            )
            .doOnNext { user ->
                // Если admin уже существует — обновляем хеш пароля из env var
                // Используем невалидный BCrypt префикс как маркер placeholder
                if (!user.passwordHash.startsWith("\$2a\$") && !user.passwordHash.startsWith("\$2b\$")) {
                    log.info("Обновляем placeholder пароль для пользователя admin")
                    userRepository.save(user.copy(passwordHash = passwordService.hash(adminPassword)))
                        .subscribe()
                }
            }
            .subscribe()
    }
}
