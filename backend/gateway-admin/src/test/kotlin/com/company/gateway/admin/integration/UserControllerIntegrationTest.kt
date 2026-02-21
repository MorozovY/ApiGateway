package com.company.gateway.admin.integration

import com.company.gateway.admin.dto.CreateUserRequest
import com.company.gateway.admin.dto.UpdateUserRequest
import com.company.gateway.admin.repository.AuditLogRepository
import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.admin.security.JwtService
import com.company.gateway.admin.service.PasswordService
import com.company.gateway.common.model.Role
import com.company.gateway.common.model.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.test.StepVerifier
import java.util.UUID

/**
 * Integration тесты для UserController (Story 2.6).
 *
 * Тестирование AC1-AC5:
 * - AC1: API — Получение списка пользователей (пагинация, без passwordHash)
 * - AC2: API — Создание нового пользователя (BCrypt хеширование)
 * - AC3: API — Обновление пользователя (смена роли)
 * - AC4: UI — (тестируется на frontend)
 * - AC5: Ограничение доступа для non-admin
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class UserControllerIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16")
            .withDatabaseName("gateway")
            .withUsername("gateway")
            .withPassword("gateway")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") {
                "r2dbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/${postgres.databaseName}"
            }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
            registry.add("spring.flyway.url", postgres::getJdbcUrl)
            registry.add("spring.flyway.user", postgres::getUsername)
            registry.add("spring.flyway.password", postgres::getPassword)
            // Отключаем Redis для тестов
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
            }
            registry.add("management.health.redis.enabled") { false }
            registry.add("management.endpoint.health.group.readiness.include") { "r2dbc" }
            // JWT конфигурация
            registry.add("jwt.secret") { "test-secret-key-minimum-32-characters-long" }
            registry.add("jwt.expiration") { 86400000 }
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var auditLogRepository: AuditLogRepository

    @Autowired
    private lateinit var passwordService: PasswordService

    @Autowired
    private lateinit var jwtService: JwtService

    private lateinit var adminToken: String
    private lateinit var developerToken: String
    private lateinit var securityToken: String
    private lateinit var adminUser: User
    private lateinit var developerUser: User

    @BeforeEach
    fun setUp() {
        // Сначала очищаем аудит-логи (FK RESTRICT не даст удалить пользователей иначе)
        StepVerifier.create(auditLogRepository.deleteAll()).verifyComplete()

        // Очищаем ВСЕХ пользователей включая seed-admin (иначе в тесте "единственный admin"
        // будет 2 активных admin: seed 'admin' + тестовый 'testadmin')
        StepVerifier.create(userRepository.deleteAll()).verifyComplete()

        // Создаём тестовых пользователей
        adminUser = createTestUser("testadmin", "password", Role.ADMIN)
        developerUser = createTestUser("developer", "password", Role.DEVELOPER)
        val securityUser = createTestUser("security", "password", Role.SECURITY)

        adminToken = jwtService.generateToken(adminUser)
        developerToken = jwtService.generateToken(developerUser)
        securityToken = jwtService.generateToken(securityUser)
    }

    // ============================================
    // AC1: API — Получение списка пользователей
    // ============================================

    @Nested
    inner class AC1_GetUsersList {

        @Test
        fun `возвращает список пользователей для admin`() {
            webTestClient.get()
                .uri("/api/v1/users")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items").isArray
                .jsonPath("$.total").isNumber
                .jsonPath("$.offset").isEqualTo(0)
                .jsonPath("$.limit").isEqualTo(20)
        }

        @Test
        fun `возвращает пользователей с полями id, username, email, role, isActive, createdAt`() {
            webTestClient.get()
                .uri("/api/v1/users")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items[0].id").isNotEmpty
                .jsonPath("$.items[0].username").isNotEmpty
                .jsonPath("$.items[0].email").isNotEmpty
                .jsonPath("$.items[0].role").isNotEmpty
                .jsonPath("$.items[0].isActive").isBoolean
                .jsonPath("$.items[0].createdAt").isNotEmpty
        }

        @Test
        fun `не включает passwordHash в response`() {
            webTestClient.get()
                .uri("/api/v1/users")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items[0].passwordHash").doesNotExist()
                .jsonPath("$.items[0].password_hash").doesNotExist()
                .jsonPath("$.items[0].password").doesNotExist()
        }

        @Test
        fun `поддерживает пагинацию с offset и limit`() {
            // Создаём ещё несколько пользователей
            createTestUser("user1", "password", Role.DEVELOPER)
            createTestUser("user2", "password", Role.DEVELOPER)
            createTestUser("user3", "password", Role.DEVELOPER)

            webTestClient.get()
                .uri("/api/v1/users?offset=0&limit=2")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.offset").isEqualTo(0)
                .jsonPath("$.limit").isEqualTo(2)
        }

        @Test
        fun `возвращает total количество всех пользователей`() {
            // Создаём ещё несколько пользователей
            createTestUser("user1", "password", Role.DEVELOPER)
            createTestUser("user2", "password", Role.DEVELOPER)

            webTestClient.get()
                .uri("/api/v1/users?offset=0&limit=2")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                // admin из миграции + testadmin + developer + security + user1 + user2 = 6
                .jsonPath("$.total").value<Int> { total ->
                    assert(total >= 5) { "Должно быть минимум 5 пользователей, получено $total" }
                }
        }

        @Test
        fun `возвращает роли в lowercase`() {
            webTestClient.get()
                .uri("/api/v1/users")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items[?(@.role == 'developer' || @.role == 'security' || @.role == 'admin')]").exists()
        }

        // ============================================
        // Story 8.3: Поиск по username и email
        // ============================================

        @Test
        fun `поиск по username возвращает совпадающих пользователей`() {
            // Создаём пользователя с уникальным username
            createTestUser("searchtest_john", "password", Role.DEVELOPER)

            webTestClient.get()
                .uri("/api/v1/users?search=searchtest_john")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.items[0].username").isEqualTo("searchtest_john")
        }

        @Test
        fun `поиск по email возвращает совпадающих пользователей`() {
            // Создаём пользователя с уникальным email
            createTestUser("emailsearchuser", "password", Role.DEVELOPER)

            webTestClient.get()
                .uri("/api/v1/users?search=emailsearchuser@example")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.items[0].email").isEqualTo("emailsearchuser@example.com")
        }

        @Test
        fun `поиск case-insensitive`() {
            // Создаём пользователя с lowercase username
            createTestUser("casesensitiveuser", "password", Role.DEVELOPER)

            // Ищем с uppercase
            webTestClient.get()
                .uri("/api/v1/users?search=CASESENSITIVEUSER")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.items[0].username").isEqualTo("casesensitiveuser")
        }

        @Test
        fun `поиск без результатов возвращает пустой список`() {
            webTestClient.get()
                .uri("/api/v1/users?search=nonexistentuserxyz123")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(0)
                .jsonPath("$.total").isEqualTo(0)
        }

        @Test
        fun `поиск работает с пагинацией`() {
            // Создаём несколько пользователей с общим префиксом
            createTestUser("paginationsearch1", "password", Role.DEVELOPER)
            createTestUser("paginationsearch2", "password", Role.DEVELOPER)
            createTestUser("paginationsearch3", "password", Role.DEVELOPER)

            webTestClient.get()
                .uri("/api/v1/users?search=paginationsearch&limit=2")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.total").isEqualTo(3)
                .jsonPath("$.limit").isEqualTo(2)
        }

        @Test
        fun `пустой search возвращает всех пользователей`() {
            webTestClient.get()
                .uri("/api/v1/users?search=")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items").isArray
                .jsonPath("$.total").value<Int> { total ->
                    assert(total >= 3) { "Должны быть возвращены все пользователи" }
                }
        }
    }

    // ============================================
    // AC2: API — Создание нового пользователя
    // ============================================

    @Nested
    inner class AC2_CreateUser {

        @Test
        fun `создаёт нового пользователя и возвращает 201`() {
            val request = CreateUserRequest(
                username = "newuser",
                email = "newuser@company.com",
                password = "SecurePassword123",
                role = Role.DEVELOPER
            )

            webTestClient.post()
                .uri("/api/v1/users")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").isNotEmpty
                .jsonPath("$.username").isEqualTo("newuser")
                .jsonPath("$.email").isEqualTo("newuser@company.com")
                .jsonPath("$.role").isEqualTo("developer")
                .jsonPath("$.isActive").isEqualTo(true)
                .jsonPath("$.createdAt").isNotEmpty
        }

        @Test
        fun `не включает passwordHash в response при создании`() {
            val request = CreateUserRequest(
                username = "nopasshash",
                email = "nopasshash@company.com",
                password = "SecurePassword123",
                role = Role.DEVELOPER
            )

            webTestClient.post()
                .uri("/api/v1/users")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.passwordHash").doesNotExist()
                .jsonPath("$.password_hash").doesNotExist()
                .jsonPath("$.password").doesNotExist()
        }

        @Test
        fun `хеширует пароль с BCrypt`() {
            val request = CreateUserRequest(
                username = "bcryptuser",
                email = "bcryptuser@company.com",
                password = "SecurePassword123",
                role = Role.DEVELOPER
            )

            webTestClient.post()
                .uri("/api/v1/users")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated

            // Проверяем что пароль захеширован в БД
            StepVerifier.create(userRepository.findByUsername("bcryptuser"))
                .expectNextMatches { user ->
                    // BCrypt хеши начинаются с $2
                    user.passwordHash.startsWith("\$2") &&
                        passwordService.verify("SecurePassword123", user.passwordHash)
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает 409 при дублирующем username`() {
            val request = CreateUserRequest(
                username = "testadmin", // Уже существует
                email = "newemail@company.com",
                password = "SecurePassword123",
                role = Role.DEVELOPER
            )

            webTestClient.post()
                .uri("/api/v1/users")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/conflict")
                .jsonPath("$.title").isEqualTo("Conflict")
                .jsonPath("$.status").isEqualTo(409)
                .jsonPath("$.detail").value<String> { detail ->
                    assert(detail.contains("testadmin")) { "Сообщение должно содержать username" }
                }
        }

        @Test
        fun `возвращает 409 при дублирующем email`() {
            val request = CreateUserRequest(
                username = "uniqueusername",
                email = "testadmin@example.com", // Уже существует
                password = "SecurePassword123",
                role = Role.DEVELOPER
            )

            webTestClient.post()
                .uri("/api/v1/users")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/conflict")
                .jsonPath("$.detail").value<String> { detail ->
                    assert(detail.contains("testadmin@example.com")) { "Сообщение должно содержать email" }
                }
        }

        @Test
        fun `возвращает 400 при пустом username`() {
            val request = mapOf(
                "username" to "",
                "email" to "valid@company.com",
                "password" to "SecurePassword123",
                "role" to "DEVELOPER"
            )

            webTestClient.post()
                .uri("/api/v1/users")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `возвращает 400 при коротком пароле`() {
            val request = CreateUserRequest(
                username = "shortpassuser",
                email = "shortpass@company.com",
                password = "short", // Меньше 8 символов
                role = Role.DEVELOPER
            )

            webTestClient.post()
                .uri("/api/v1/users")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `возвращает 400 при некорректном email`() {
            val request = mapOf(
                "username" to "invalidemail",
                "email" to "not-an-email",
                "password" to "SecurePassword123",
                "role" to "DEVELOPER"
            )

            webTestClient.post()
                .uri("/api/v1/users")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    // ============================================
    // AC3: API — Обновление пользователя (смена роли)
    // ============================================

    @Nested
    inner class AC3_UpdateUser {

        @Test
        fun `обновляет роль пользователя`() {
            val targetUser = createTestUser("userroleupdate", "password", Role.DEVELOPER)

            val request = UpdateUserRequest(role = Role.SECURITY)

            webTestClient.put()
                .uri("/api/v1/users/${targetUser.id}")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(targetUser.id.toString())
                .jsonPath("$.role").isEqualTo("security")
        }

        @Test
        fun `обновляет email пользователя`() {
            val targetUser = createTestUser("useremailupdate", "password", Role.DEVELOPER)

            val request = UpdateUserRequest(email = "newemail@company.com")

            webTestClient.put()
                .uri("/api/v1/users/${targetUser.id}")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.email").isEqualTo("newemail@company.com")
        }

        @Test
        fun `возвращает 404 для несуществующего пользователя`() {
            val nonExistentId = UUID.randomUUID()

            val request = UpdateUserRequest(role = Role.SECURITY)

            webTestClient.put()
                .uri("/api/v1/users/$nonExistentId")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/not-found")
                .jsonPath("$.status").isEqualTo(404)
        }

        @Test
        fun `возвращает 409 при обновлении на существующий email`() {
            val targetUser = createTestUser("emailconflict", "password", Role.DEVELOPER)

            val request = UpdateUserRequest(email = "testadmin@example.com") // Уже существует

            webTestClient.put()
                .uri("/api/v1/users/${targetUser.id}")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(409)
        }

        @Test
        fun `возвращает 409 при попытке деактивировать единственного admin через PUT isActive=false`() {
            // В системе есть только один активный admin (testadmin) — нельзя деактивировать его через PUT
            val request = UpdateUserRequest(isActive = false)

            webTestClient.put()
                .uri("/api/v1/users/${adminUser.id}")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/conflict")
                .jsonPath("$.detail").value<String> { detail ->
                    assert(detail.contains("администратор") || detail.contains("admin")) {
                        "Сообщение должно объяснять причину запрета"
                    }
                }
        }

        @Test
        fun `создаёт запись в аудит-логе при смене роли`() {
            val targetUser = createTestUser("auditloguser", "password", Role.DEVELOPER)

            val request = UpdateUserRequest(role = Role.ADMIN)

            // Выполняем обновление роли
            webTestClient.put()
                .uri("/api/v1/users/${targetUser.id}")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk

            // Проверяем что запись в аудит-логе создана
            StepVerifier.create(
                auditLogRepository.findByEntityTypeAndEntityId("user", targetUser.id.toString())
                    .filter { it.action == "role_changed" }
            )
                .expectNextMatches { auditLog ->
                    auditLog.entityType == "user" &&
                        auditLog.entityId == targetUser.id.toString() &&
                        auditLog.action == "role_changed" &&
                        auditLog.changes?.contains("developer") == true &&
                        auditLog.changes?.contains("admin") == true
                }
                .verifyComplete()
        }
    }

    // ============================================
    // Деактивация пользователя
    // ============================================

    @Nested
    inner class DeactivateUser {

        /**
         * Root cause 401 был исправлен в RoleAuthorizationAspect:
         * switchIfEmpty стоял ПОСЛЕ flatMap, поэтому Mono<Void> (нет эмиссии элемента)
         * запускал switchIfEmpty → TokenMissing() → 401, даже при успешной деактивации.
         * Исправление: switchIfEmpty перемещён ДО flatMap.
         */
        @Test
        fun `деактивирует пользователя и возвращает 204`() {
            webTestClient.delete()
                .uri("/api/v1/users/${developerUser.id}")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isNoContent

            // Проверяем что пользователь деактивирован
            StepVerifier.create(userRepository.findById(developerUser.id!!))
                .expectNextMatches { it.isActive == false }
                .verifyComplete()
        }

        @Test
        fun `возвращает 404 для несуществующего пользователя`() {
            val nonExistentId = UUID.randomUUID()

            webTestClient.delete()
                .uri("/api/v1/users/$nonExistentId")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isNotFound
        }

        @Test
        fun `возвращает 409 при попытке деактивировать себя`() {
            webTestClient.delete()
                .uri("/api/v1/users/${adminUser.id}")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/conflict")
                .jsonPath("$.detail").value<String> { detail ->
                    assert(detail.contains("деактивировать") || detail.contains("себя"))
                }
        }
    }

    // ============================================
    // AC5: Ограничение доступа для non-admin
    // ============================================

    @Nested
    inner class AC5_NonAdminAccessDenied {

        @Test
        fun `developer получает 403 при попытке получить список пользователей`() {
            webTestClient.get()
                .uri("/api/v1/users")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/forbidden")
                .jsonPath("$.status").isEqualTo(403)
        }

        @Test
        fun `developer получает 403 при попытке создать пользователя`() {
            val request = CreateUserRequest(
                username = "newuser",
                email = "newuser@company.com",
                password = "SecurePassword123",
                role = Role.DEVELOPER
            )

            webTestClient.post()
                .uri("/api/v1/users")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun `developer получает 403 при попытке обновить пользователя`() {
            val request = UpdateUserRequest(role = Role.SECURITY)

            webTestClient.put()
                .uri("/api/v1/users/${UUID.randomUUID()}")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun `developer получает 403 при попытке деактивировать пользователя`() {
            webTestClient.delete()
                .uri("/api/v1/users/${UUID.randomUUID()}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun `security получает 403 при попытке получить список пользователей`() {
            webTestClient.get()
                .uri("/api/v1/users")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun `security получает 403 при попытке создать пользователя`() {
            val request = CreateUserRequest(
                username = "newuser",
                email = "newuser@company.com",
                password = "SecurePassword123",
                role = Role.DEVELOPER
            )

            webTestClient.post()
                .uri("/api/v1/users")
                .cookie("auth_token", securityToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun `403 response включает correlationId`() {
            webTestClient.get()
                .uri("/api/v1/users")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
                .expectHeader().exists("X-Correlation-ID")
                .expectBody()
                .jsonPath("$.correlationId").isNotEmpty
        }
    }

    // ============================================
    // GET User by ID
    // ============================================

    @Nested
    inner class GetUserById {

        @Test
        fun `возвращает пользователя по ID`() {
            webTestClient.get()
                .uri("/api/v1/users/${adminUser.id}")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(adminUser.id.toString())
                .jsonPath("$.username").isEqualTo("testadmin")
        }

        @Test
        fun `возвращает 404 для несуществующего ID`() {
            val nonExistentId = UUID.randomUUID()

            webTestClient.get()
                .uri("/api/v1/users/$nonExistentId")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/not-found")
        }
    }

    // ============================================
    // Вспомогательные методы
    // ============================================

    private fun createTestUser(
        username: String,
        password: String,
        role: Role,
        isActive: Boolean = true
    ): User {
        val hashedPassword = passwordService.hash(password)
        val user = User(
            username = username,
            email = "$username@example.com",
            passwordHash = hashedPassword,
            role = role,
            isActive = isActive
        )
        var savedUser: User? = null
        StepVerifier.create(userRepository.save(user))
            .consumeNextWith { savedUser = it }
            .verifyComplete()
        return savedUser!!
    }
}
