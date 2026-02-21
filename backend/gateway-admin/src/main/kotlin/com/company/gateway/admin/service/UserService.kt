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
