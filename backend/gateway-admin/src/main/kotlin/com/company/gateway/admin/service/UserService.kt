package com.company.gateway.admin.service

import com.company.gateway.admin.dto.CreateUserRequest
import com.company.gateway.admin.dto.UpdateUserRequest
import com.company.gateway.admin.dto.UserListResponse
import com.company.gateway.admin.dto.UserOption
import com.company.gateway.admin.dto.UserOptionsResponse
import com.company.gateway.admin.dto.UserResponse
import com.company.gateway.admin.exception.ConflictException
import com.company.gateway.admin.exception.NotFoundException
import com.company.gateway.admin.exception.ValidationException
import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.admin.security.SecurityContextUtils
import com.company.gateway.common.model.Role
import com.company.gateway.common.model.User
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * Сервис для управления пользователями.
 *
 * Предоставляет бизнес-логику для CRUD операций с пользователями.
 * Используется UserController для обработки HTTP запросов.
 */
@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordService: PasswordService,
    private val auditService: AuditService
) {
    private val logger = LoggerFactory.getLogger(UserService::class.java)

    /**
     * Получение списка пользователей с пагинацией и опциональным поиском.
     *
     * @param offset смещение от начала списка
     * @param limit максимальное количество элементов
     * @param search поиск по username или email (case-insensitive, опционально)
     * @return пагинированный список пользователей
     */
    fun findAll(offset: Int, limit: Int, search: String? = null): Mono<UserListResponse> {
        logger.debug("Получение списка пользователей: offset={}, limit={}, search={}", offset, limit, search)

        // Валидация параметров пагинации
        if (limit < 1 || limit > 100) {
            return Mono.error(ValidationException("Некорректный limit", "limit должен быть от 1 до 100"))
        }
        if (offset < 0) {
            return Mono.error(ValidationException("Некорректный offset", "offset должен быть >= 0"))
        }

        // Если search задан — используем поиск по username/email
        // Иначе — обычная пагинация
        val trimmedSearch = search?.trim()?.takeIf { it.isNotEmpty() }

        return if (trimmedSearch != null) {
            // Поиск по username или email (ILIKE)
            val searchPattern = "%$trimmedSearch%"
            userRepository.countBySearch(searchPattern)
                .flatMap { total ->
                    userRepository.searchUsers(searchPattern, limit, offset)
                        .map { it.toResponse() }
                        .collectList()
                        .map { items ->
                            UserListResponse(
                                items = items,
                                total = total,
                                offset = offset,
                                limit = limit
                            )
                        }
                }
        } else {
            // Обычная пагинация без фильтрации
            userRepository.count()
                .flatMap { total ->
                    userRepository.findAllPaginated(limit, offset)
                        .map { it.toResponse() }
                        .collectList()
                        .map { items ->
                            UserListResponse(
                                items = items,
                                total = total,
                                offset = offset,
                                limit = limit
                            )
                        }
                }
        }
    }

    /**
     * Получение списка всех активных пользователей для dropdowns.
     *
     * Возвращает только id и username, отсортированные по алфавиту.
     * Используется для фильтров в audit logs (Story 8.6).
     *
     * @return список пользователей для dropdown
     */
    fun getAllOptions(): Mono<UserOptionsResponse> {
        logger.debug("Получение списка пользователей для dropdown")

        return userRepository.findAllActiveOrderByUsername()
            .map { user -> UserOption(id = user.id!!, username = user.username) }
            .collectList()
            .map { items -> UserOptionsResponse(items = items) }
    }

    /**
     * Получение пользователя по ID.
     *
     * @param id идентификатор пользователя
     * @return данные пользователя
     * @throws NotFoundException если пользователь не найден
     */
    fun findById(id: UUID): Mono<UserResponse> {
        logger.debug("Получение пользователя по ID: {}", id)

        return userRepository.findById(id)
            .map { it.toResponse() }
            .switchIfEmpty(
                Mono.error(NotFoundException("Пользователь не найден", "Пользователь с ID $id не найден"))
            )
    }

    /**
     * Создание нового пользователя.
     *
     * @param request данные для создания пользователя
     * @return созданный пользователь
     * @throws ConflictException если username или email уже существует
     */
    fun create(request: CreateUserRequest): Mono<UserResponse> {
        logger.info("Создание нового пользователя: username={}", request.username)

        return validateUniqueness(request.username, request.email)
            .then(
                Mono.defer {
                    val user = User(
                        username = request.username,
                        email = request.email,
                        passwordHash = passwordService.hash(request.password),
                        role = request.role,
                        isActive = true
                    )
                    userRepository.save(user)
                        .map { it.toResponse() }
                        .doOnSuccess { logger.info("Пользователь создан: id={}", it.id) }
                        // Страховка от race condition: если два запроса прошли проверку одновременно,
                        // unique constraint БД выбросит DataIntegrityViolationException → 409
                        .onErrorMap(DataIntegrityViolationException::class.java) {
                            ConflictException(
                                "Конфликт при создании пользователя",
                                "Пользователь с таким username или email уже существует"
                            )
                        }
                }
            )
    }

    /**
     * Обновление пользователя.
     *
     * При смене роли создаётся запись в аудит-логе (AC3 Story 2.6).
     *
     * @param id идентификатор пользователя
     * @param request данные для обновления
     * @return обновлённый пользователь
     * @throws NotFoundException если пользователь не найден
     * @throws ConflictException если email уже используется другим пользователем
     */
    fun update(id: UUID, request: UpdateUserRequest): Mono<UserResponse> {
        logger.info("Обновление пользователя: id={}", id)

        return userRepository.findById(id)
            .switchIfEmpty(
                Mono.error(NotFoundException("Пользователь не найден", "Пользователь с ID $id не найден"))
            )
            .flatMap { existingUser ->
                // Проверяем уникальность email если он изменяется
                val emailValidation: Mono<Void> = if (request.email != null && request.email != existingUser.email) {
                    validateEmailUniqueness(request.email)
                } else {
                    Mono.empty()
                }

                // Сохраняем старую роль для аудит-лога
                val oldRole = existingUser.role
                val roleChanged = request.role != null && request.role != oldRole

                // Проверяем, не деактивируется ли единственный активный admin через PUT
                val lastAdminDeactivation = request.isActive == false &&
                    existingUser.isActive &&
                    existingUser.role == Role.ADMIN

                val lastAdminCheck: Mono<Void> = if (lastAdminDeactivation) {
                    countActiveAdmins().flatMap { count ->
                        if (count <= 1) {
                            Mono.error<Void>(
                                ConflictException(
                                    "Нельзя деактивировать единственного admin",
                                    "В системе должен оставаться хотя бы один активный администратор"
                                )
                            )
                        } else {
                            Mono.empty<Void>()
                        }
                    }
                } else {
                    Mono.empty<Void>()
                }

                emailValidation.then(lastAdminCheck).then(
                    Mono.defer {
                        val updatedUser = existingUser.copy(
                            email = request.email ?: existingUser.email,
                            role = request.role ?: existingUser.role,
                            isActive = request.isActive ?: existingUser.isActive
                        )
                        userRepository.save(updatedUser)
                            .flatMap { savedUser ->
                                // Записываем аудит-лог при смене роли (AC3)
                                if (roleChanged) {
                                    SecurityContextUtils.currentUser()
                                        .flatMap { currentUser ->
                                            auditService.logRoleChanged(
                                                targetUserId = savedUser.id!!,
                                                targetUsername = savedUser.username,
                                                oldRole = oldRole.toDbValue(),
                                                newRole = savedUser.role.toDbValue(),
                                                performedByUserId = currentUser.userId,
                                                performedByUsername = currentUser.username
                                            )
                                        }
                                        .thenReturn(savedUser)
                                        .onErrorResume { e ->
                                            // Если аудит-лог не записался — логируем ошибку, но не отменяем операцию
                                            logger.error("Ошибка записи аудит-лога при смене роли: {}", e.message)
                                            Mono.just(savedUser)
                                        }
                                } else {
                                    Mono.just(savedUser)
                                }
                            }
                            .map { it.toResponse() }
                            .doOnSuccess {
                                logger.info(
                                    "Пользователь обновлён: id={}, role={}",
                                    it.id,
                                    it.role
                                )
                            }
                    }
                )
            }
    }

    /**
     * Деактивация пользователя (soft delete).
     *
     * @param id идентификатор пользователя
     * @throws NotFoundException если пользователь не найден
     * @throws ConflictException если пользователь пытается деактивировать себя
     *                           или это единственный admin
     */
    fun deactivate(id: UUID, currentUserId: UUID): Mono<Void> {
        logger.info("Деактивация пользователя: id={}", id)

        // Проверяем, что admin не деактивирует себя
        if (id == currentUserId) {
            return Mono.error(
                ConflictException(
                    "Нельзя деактивировать себя",
                    "Администратор не может деактивировать собственную учётную запись"
                )
            )
        }

        return userRepository.findById(id)
            .switchIfEmpty(
                Mono.error(NotFoundException("Пользователь не найден", "Пользователь с ID $id не найден"))
            )
            .flatMap { user ->
                // Проверяем, что это не единственный активный admin
                if (user.role == Role.ADMIN) {
                    countActiveAdmins()
                        .flatMap { count ->
                            if (count <= 1) {
                                Mono.error(
                                    ConflictException(
                                        "Нельзя деактивировать единственного admin",
                                        "В системе должен оставаться хотя бы один активный администратор"
                                    )
                                )
                            } else {
                                performDeactivation(user)
                            }
                        }
                } else {
                    performDeactivation(user)
                }
            }
    }

    /**
     * Проверка уникальности username и email.
     */
    private fun validateUniqueness(username: String, email: String): Mono<Void> {
        return userRepository.existsByUsername(username)
            .flatMap { usernameExists ->
                if (usernameExists) {
                    Mono.error(
                        ConflictException(
                            "Username уже существует",
                            "Пользователь с именем '$username' уже зарегистрирован"
                        )
                    )
                } else {
                    userRepository.existsByEmail(email)
                        .flatMap { emailExists ->
                            if (emailExists) {
                                Mono.error(
                                    ConflictException(
                                        "Email уже существует",
                                        "Пользователь с email '$email' уже зарегистрирован"
                                    )
                                )
                            } else {
                                Mono.empty()
                            }
                        }
                }
            }
    }

    /**
     * Проверка уникальности email.
     */
    private fun validateEmailUniqueness(email: String): Mono<Void> {
        return userRepository.existsByEmail(email)
            .flatMap { emailExists ->
                if (emailExists) {
                    Mono.error(
                        ConflictException(
                            "Email уже существует",
                            "Пользователь с email '$email' уже зарегистрирован"
                        )
                    )
                } else {
                    Mono.empty()
                }
            }
    }

    /**
     * Подсчёт активных администраторов (запрос на уровне БД).
     */
    private fun countActiveAdmins(): Mono<Long> {
        return userRepository.countActiveByRole(Role.ADMIN.toDbValue())
    }

    /**
     * Выполнение деактивации пользователя.
     */
    private fun performDeactivation(user: User): Mono<Void> {
        val deactivatedUser = user.copy(isActive = false)
        return userRepository.save(deactivatedUser)
            .doOnSuccess { logger.info("Пользователь деактивирован: id={}", user.id) }
            .then()
    }

    /**
     * Смена пароля пользователя.
     *
     * Проверяет текущий пароль и обновляет на новый.
     * Записывает audit log при успешной смене (Subtask 1.5).
     *
     * @param userId ID пользователя
     * @param currentPassword текущий пароль для проверки
     * @param newPassword новый пароль
     * @param username имя пользователя для аудит-лога
     * @return Mono<Void> при успехе
     * @throws AuthenticationException если текущий пароль неверный
     * @throws NotFoundException если пользователь не найден
     */
    fun changePassword(
        userId: UUID,
        currentPassword: String,
        newPassword: String,
        username: String
    ): Mono<Void> {
        logger.info("Смена пароля для пользователя: userId={}", userId)

        return userRepository.findById(userId)
            .switchIfEmpty(
                Mono.error(NotFoundException("Пользователь не найден", "Пользователь с ID $userId не найден"))
            )
            .flatMap { user ->
                // Проверяем текущий пароль (Subtask 1.3)
                if (!passwordService.verify(currentPassword, user.passwordHash)) {
                    logger.warn("Неверный текущий пароль при смене: userId={}", userId)
                    return@flatMap Mono.error<User>(
                        AuthenticationException("Current password is incorrect")
                    )
                }

                // Хэшируем и сохраняем новый пароль (Subtask 1.4)
                val updatedUser = user.copy(
                    passwordHash = passwordService.hash(newPassword),
                    updatedAt = Instant.now()
                )
                userRepository.save(updatedUser)
            }
            .flatMap { savedUser ->
                // Записываем audit log (Subtask 1.5)
                auditService.logWithContext(
                    entityType = "user",
                    entityId = savedUser.id.toString(),
                    action = "password_changed",
                    userId = savedUser.id!!,
                    username = username
                ).then()
            }
            .doOnSuccess {
                logger.info("Пароль успешно изменён: userId={}", userId)
            }
    }

    /**
     * Сброс паролей демо-пользователей на дефолтные (Story 9.5).
     *
     * Сбрасывает пароли для существующих пользователей или создаёт новых:
     * - developer → developer123 (Role.DEVELOPER)
     * - security → security123 (Role.SECURITY)
     * - admin → admin123 (Role.ADMIN)
     *
     * @return список имён пользователей, у которых сброшены/созданы пароли
     */
    fun resetDemoPasswords(): Mono<List<String>> {
        // Данные демо-пользователей: username -> (password, role, email)
        data class DemoUser(val password: String, val role: Role, val email: String)
        val demoUsers = mapOf(
            "developer" to DemoUser("developer123", Role.DEVELOPER, "developer@example.com"),
            "security" to DemoUser("security123", Role.SECURITY, "security@example.com"),
            "admin" to DemoUser("admin123", Role.ADMIN, "admin@example.com")
        )

        logger.info("Сброс/создание демо-пользователей: {}", demoUsers.keys)

        return reactor.core.publisher.Flux.fromIterable(demoUsers.entries)
            .flatMap { (username, demoData) ->
                userRepository.findByUsername(username)
                    .flatMap { existingUser ->
                        // Пользователь существует — обновляем пароль
                        val newHash = passwordService.hash(demoData.password)
                        val updatedUser = existingUser.copy(
                            passwordHash = newHash,
                            isActive = true,
                            updatedAt = Instant.now()
                        )
                        userRepository.save(updatedUser)
                            .doOnSuccess {
                                logger.info("Пароль сброшен для пользователя: {}", username)
                            }
                            .thenReturn(username)
                    }
                    .switchIfEmpty(
                        // Пользователь не существует — создаём нового
                        Mono.defer {
                            val newHash = passwordService.hash(demoData.password)
                            val newUser = User(
                                username = username,
                                email = demoData.email,
                                passwordHash = newHash,
                                role = demoData.role,
                                isActive = true
                            )
                            userRepository.save(newUser)
                                .doOnSuccess {
                                    logger.info("Создан демо-пользователь: {}", username)
                                }
                                .thenReturn(username)
                        }
                    )
            }
            .collectList()
            .doOnSuccess { users ->
                logger.info("Сброс/создание демо-пользователей завершено: {} пользователей", users.size)
            }
    }

    /**
     * Преобразование User в UserResponse.
     */
    private fun User.toResponse(): UserResponse {
        return UserResponse(
            id = this.id!!,
            username = this.username,
            email = this.email,
            role = this.role.toDbValue(),
            isActive = this.isActive,
            createdAt = this.createdAt!!
        )
    }
}
