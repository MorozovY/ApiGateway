package com.company.gateway.admin.integration

import com.company.gateway.admin.repository.AuditLogRepository
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.admin.security.JwtService
import com.company.gateway.admin.service.PasswordService
import com.company.gateway.common.model.Role
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import com.company.gateway.common.model.User
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * Integration тесты для Approval Workflow API (Story 4.1, Story 4.2).
 *
 * Story 4.1 - POST /api/v1/routes/{id}/submit:
 * - AC1: Успешная отправка на согласование
 * - AC2: Нельзя отправить не-draft маршрут
 * - AC3: Нельзя отправить чужой маршрут
 * - AC4: Валидация перед отправкой
 * - AC5: Маршрут не найден
 *
 * Story 4.2 - POST /api/v1/routes/{id}/approve и /reject:
 * - AC1: Успешное одобрение маршрута
 * - AC3: Успешное отклонение маршрута
 * - AC4: Отклонение без причины
 * - AC5: Недостаточно прав (Developer)
 * - AC6: Маршрут не в статусе pending
 * - AC7: Маршрут не найден
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApprovalIntegrationTest {

    companion object {
        // Проверяем запущены ли мы в CI
        private val isTestcontainersDisabled = System.getenv("TESTCONTAINERS_DISABLED") == "true"

        private var postgres: PostgreSQLContainer<*>? = null

        @BeforeAll
        @JvmStatic
        fun startContainers() {
            if (isTestcontainersDisabled) {
                return
            }
            postgres = PostgreSQLContainer("postgres:16")
                .withDatabaseName("gateway")
                .withUsername("gateway")
                .withPassword("gateway")
            postgres!!.start()
        }

        @AfterAll
        @JvmStatic
        fun stopContainers() {
            postgres?.stop()
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            if (isTestcontainersDisabled) {
                // В CI читаем из env переменных
                val pgHost = System.getenv("POSTGRES_HOST") ?: "localhost"
                val pgPort = System.getenv("POSTGRES_PORT") ?: "5432"
                val pgDb = System.getenv("POSTGRES_DB_ADMIN") ?: System.getenv("POSTGRES_DB") ?: "gateway_admin_test"
                val pgUser = System.getenv("POSTGRES_USER") ?: "gateway"
                val pgPass = System.getenv("POSTGRES_PASSWORD") ?: "gateway"

                registry.add("spring.r2dbc.url") { "r2dbc:postgresql://$pgHost:$pgPort/$pgDb" }
                registry.add("spring.r2dbc.username") { pgUser }
                registry.add("spring.r2dbc.password") { pgPass }
                registry.add("spring.flyway.url") { "jdbc:postgresql://$pgHost:$pgPort/$pgDb" }
                registry.add("spring.flyway.user") { pgUser }
                registry.add("spring.flyway.password") { pgPass }
            } else {
                registry.add("spring.r2dbc.url") {
                    "r2dbc:postgresql://${postgres!!.host}:${postgres!!.firstMappedPort}/${postgres!!.databaseName}"
                }
                registry.add("spring.r2dbc.username", postgres!!::getUsername)
                registry.add("spring.r2dbc.password", postgres!!::getPassword)
                registry.add("spring.flyway.url", postgres!!::getJdbcUrl)
                registry.add("spring.flyway.user", postgres!!::getUsername)
                registry.add("spring.flyway.password", postgres!!::getPassword)
            }
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
    private lateinit var routeRepository: RouteRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var auditLogRepository: AuditLogRepository

    @Autowired
    private lateinit var passwordService: PasswordService

    @Autowired
    private lateinit var jwtService: JwtService

    private lateinit var developerToken: String
    private lateinit var otherDeveloperToken: String
    private lateinit var securityToken: String
    private lateinit var adminToken: String
    private lateinit var developerUser: User
    private lateinit var otherDeveloperUser: User
    private lateinit var securityUser: User
    private lateinit var adminUser: User

    @BeforeEach
    fun setUp() {
        // Очищаем маршруты и audit_logs (FK: users → audit_logs с RESTRICT, V5_1 миграция)
        StepVerifier.create(routeRepository.deleteAll()).verifyComplete()
        StepVerifier.create(auditLogRepository.deleteAll()).verifyComplete()

        // Очищаем тестовых пользователей (кроме admin из миграции)
        StepVerifier.create(
            userRepository.findAll()
                .filter { it.username != "admin" }
                .flatMap { userRepository.delete(it) }
                .then()
        ).verifyComplete()

        // Создаём тестовых пользователей
        developerUser = createTestUser("developer", "password", Role.DEVELOPER)
        otherDeveloperUser = createTestUser("otherdev", "password", Role.DEVELOPER)
        securityUser = createTestUser("security", "password", Role.SECURITY)
        adminUser = createTestUser("admintest", "password", Role.ADMIN)

        developerToken = jwtService.generateToken(developerUser)
        otherDeveloperToken = jwtService.generateToken(otherDeveloperUser)
        securityToken = jwtService.generateToken(securityUser)
        adminToken = jwtService.generateToken(adminUser)
    }

    // ============================================
    // AC1: Успешная отправка на согласование
    // ============================================

    @Nested
    inner class AC1_УспешнаяОтправка {

        @Test
        fun `POST submit возвращает 200 и обновляет статус на pending`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.DRAFT)

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/submit")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(route.id.toString())
                .jsonPath("$.status").isEqualTo("pending")
                .jsonPath("$.submittedAt").isNotEmpty
        }

        @Test
        fun `POST submit устанавливает submittedAt timestamp`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.DRAFT)

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/submit")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk

            // Then - проверяем в базе
            StepVerifier.create(routeRepository.findById(route.id!!))
                .expectNextMatches { savedRoute ->
                    savedRoute.status == RouteStatus.PENDING &&
                    savedRoute.submittedAt != null
                }
                .verifyComplete()
        }

        @Test
        fun `POST submit создаёт audit log entry`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.DRAFT)

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/submit")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk

            // Then - проверяем audit log
            StepVerifier.create(
                auditLogRepository.findAll()
                    .filter { it.entityId == route.id.toString() && it.action == "route.submitted" }
            )
                .expectNextMatches { auditLog ->
                    auditLog.entityType == "route" &&
                    auditLog.userId == developerUser.id
                }
                .verifyComplete()
        }
    }

    // ============================================
    // AC2: Нельзя отправить не-draft маршрут
    // ============================================

    @Nested
    inner class AC2_НельзяОтправитьНеDraft {

        @Test
        fun `POST submit возвращает 409 для pending маршрута`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/submit")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/conflict")
                .jsonPath("$.status").isEqualTo(409)
                .jsonPath("$.detail").isEqualTo("Only draft or rejected routes can be submitted for approval")
        }

        @Test
        fun `POST submit возвращает 409 для published маршрута`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PUBLISHED)

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/submit")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Only draft or rejected routes can be submitted for approval")
        }

        @Test
        fun `POST submit возвращает 200 для rejected маршрута (resubmission разрешён в Story 4_4)`() {
            // Given — rejected маршрут теперь можно повторно подать (Story 4.4, AC4)
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.REJECTED)

            // When & Then — 200 OK, статус меняется на pending
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/submit")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.status").isEqualTo("pending")
        }
    }

    // ============================================
    // AC3: Нельзя отправить чужой маршрут
    // ============================================

    @Nested
    inner class AC3_НельзяОтправитьЧужой {

        @Test
        fun `POST submit возвращает 403 для чужого маршрута`() {
            // Given - маршрут принадлежит другому разработчику
            val route = createTestRoute("/api/orders", otherDeveloperUser.id!!, RouteStatus.DRAFT)

            // When & Then - пытаемся отправить не-владельцем
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/submit")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/forbidden")
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.detail").isEqualTo("You can only submit your own routes")
        }
    }

    // ============================================
    // AC4: Валидация перед отправкой
    // ============================================

    @Nested
    inner class AC4_ВалидацияПередОтправкой {

        @Test
        fun `POST submit возвращает 400 для маршрута с пустым methods`() {
            // Given
            val route = createTestRouteWithMethods("/api/orders", developerUser.id!!, emptyList())

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/submit")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/validation")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.detail").value<String> { detail ->
                    assert(detail.contains("At least one HTTP method must be specified"))
                }
        }

        @Test
        fun `POST submit возвращает 400 для маршрута с пустым path`() {
            // Given
            val route = createTestRouteWithCustomFields(
                path = "   ",
                upstreamUrl = "http://test-service:8080",
                methods = listOf("GET"),
                createdBy = developerUser.id!!
            )

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/submit")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/validation")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.detail").value<String> { detail ->
                    assert(detail.contains("Path cannot be empty"))
                }
        }

        @Test
        fun `POST submit возвращает 400 для маршрута с невалидным URL`() {
            // Given
            val route = createTestRouteWithCustomFields(
                path = "/api/orders",
                upstreamUrl = "not-a-valid-url",
                methods = listOf("GET"),
                createdBy = developerUser.id!!
            )

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/submit")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/validation")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.detail").value<String> { detail ->
                    assert(detail.contains("Upstream URL must be a valid HTTP/HTTPS URL"))
                }
        }

        @Test
        fun `POST submit возвращает 400 для маршрута с пустым upstream URL`() {
            // Given
            val route = createTestRouteWithCustomFields(
                path = "/api/orders",
                upstreamUrl = "",
                methods = listOf("GET"),
                createdBy = developerUser.id!!
            )

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/submit")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/validation")
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.detail").value<String> { detail ->
                    assert(detail.contains("Upstream URL cannot be empty"))
                }
        }
    }

    // ============================================
    // AC5: Маршрут не найден
    // ============================================

    @Nested
    inner class AC5_МаршрутНеНайден {

        @Test
        fun `POST submit возвращает 404 для несуществующего маршрута`() {
            // Given
            val nonExistentId = UUID.randomUUID()

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/$nonExistentId/submit")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/not-found")
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.detail").isEqualTo("Route not found")
        }
    }

    // ============================================
    // Дополнительные тесты для Submit
    // ============================================

    @Nested
    inner class ДополнительныеПроверкиSubmit {

        @Test
        fun `POST submit возвращает 401 без авторизации`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.DRAFT)

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/submit")
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        fun `POST submit содержит correlationId в ответе ошибки`() {
            // Given
            val nonExistentId = UUID.randomUUID()

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/$nonExistentId/submit")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.correlationId").isNotEmpty
        }
    }

    // ============================================
    // Story 4.2: AC1 - Успешное одобрение
    // ============================================

    @Nested
    inner class Story42_AC1_УспешноеОдобрение {

        @Test
        fun `POST approve возвращает 200 и обновляет статус на published`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/approve")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(route.id.toString())
                .jsonPath("$.status").isEqualTo("published")
                .jsonPath("$.approvedAt").isNotEmpty
                .jsonPath("$.approvedBy").isEqualTo(securityUser.id.toString())
        }

        @Test
        fun `POST approve устанавливает approvedBy и approvedAt`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/approve")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk

            // Then — проверяем в базе
            StepVerifier.create(routeRepository.findById(route.id!!))
                .expectNextMatches { savedRoute ->
                    savedRoute.status == RouteStatus.PUBLISHED &&
                    savedRoute.approvedBy == securityUser.id &&
                    savedRoute.approvedAt != null
                }
                .verifyComplete()
        }

        @Test
        fun `POST approve создаёт audit log entry`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/approve")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk

            // Then — проверяем audit log
            StepVerifier.create(
                auditLogRepository.findAll()
                    .filter { it.entityId == route.id.toString() && it.action == "approved" }
            )
                .expectNextMatches { auditLog ->
                    auditLog.entityType == "route" &&
                    auditLog.userId == securityUser.id
                }
                .verifyComplete()
        }

        @Test
        fun `POST approve работает для admin роли`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/approve")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.status").isEqualTo("published")
        }
    }

    // ============================================
    // Story 4.2: AC3 - Успешное отклонение
    // ============================================

    @Nested
    inner class Story42_AC3_УспешноеОтклонение {

        @Test
        fun `POST reject возвращает 200 и обновляет статус на rejected`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)
            val reason = "Upstream URL points to internal service not allowed"

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/reject")
                .cookie("auth_token", securityToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""{"reason": "$reason"}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(route.id.toString())
                .jsonPath("$.status").isEqualTo("rejected")
                .jsonPath("$.rejectedAt").isNotEmpty
                .jsonPath("$.rejectedBy").isEqualTo(securityUser.id.toString())
                .jsonPath("$.rejectionReason").isEqualTo(reason)
        }

        @Test
        fun `POST reject сохраняет rejectionReason`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)
            val reason = "Security issue detected"

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/reject")
                .cookie("auth_token", securityToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""{"reason": "$reason"}""")
                .exchange()
                .expectStatus().isOk

            // Then — проверяем в базе
            StepVerifier.create(routeRepository.findById(route.id!!))
                .expectNextMatches { savedRoute ->
                    savedRoute.status == RouteStatus.REJECTED &&
                    savedRoute.rejectedBy == securityUser.id &&
                    savedRoute.rejectedAt != null &&
                    savedRoute.rejectionReason == reason
                }
                .verifyComplete()
        }

        @Test
        fun `POST reject создаёт audit log entry`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)
            val reason = "Security issue"

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/reject")
                .cookie("auth_token", securityToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""{"reason": "$reason"}""")
                .exchange()
                .expectStatus().isOk

            // Then — проверяем audit log
            StepVerifier.create(
                auditLogRepository.findAll()
                    .filter { it.entityId == route.id.toString() && it.action == "rejected" }
            )
                .expectNextMatches { auditLog ->
                    auditLog.entityType == "route" &&
                    auditLog.userId == securityUser.id
                }
                .verifyComplete()
        }
    }

    // ============================================
    // Story 4.2: AC4 - Отклонение без причины
    // ============================================

    @Nested
    inner class Story42_AC4_ОтклонениеБезПричины {

        @Test
        fun `POST reject возвращает 400 без reason`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/reject")
                .cookie("auth_token", securityToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `POST reject возвращает 400 с пустым reason`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/reject")
                .cookie("auth_token", securityToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""{"reason": ""}""")
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `POST reject возвращает 400 с пробелами в reason`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/reject")
                .cookie("auth_token", securityToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""{"reason": "   "}""")
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    // ============================================
    // Story 4.2: AC5 - Недостаточно прав
    // ============================================

    @Nested
    inner class Story42_AC5_НедостаточноПрав {

        @Test
        fun `POST approve возвращает 403 для developer роли`() {
            // Given
            val route = createTestRoute("/api/orders", otherDeveloperUser.id!!, RouteStatus.PENDING)

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/approve")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/forbidden")
                .jsonPath("$.status").isEqualTo(403)
        }

        @Test
        fun `POST reject возвращает 403 для developer роли`() {
            // Given
            val route = createTestRoute("/api/orders", otherDeveloperUser.id!!, RouteStatus.PENDING)

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/reject")
                .cookie("auth_token", developerToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""{"reason": "test"}""")
                .exchange()
                .expectStatus().isForbidden
        }
    }

    // ============================================
    // Story 4.2: AC6 - Маршрут не в статусе pending
    // ============================================

    @Nested
    inner class Story42_AC6_МаршрутНеPending {

        @Test
        fun `POST approve возвращает 409 для draft маршрута`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.DRAFT)

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/approve")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/conflict")
                .jsonPath("$.status").isEqualTo(409)
                .jsonPath("$.detail").isEqualTo("Only pending routes can be approved/rejected")
        }

        @Test
        fun `POST approve возвращает 409 для published маршрута`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PUBLISHED)

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/approve")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Only pending routes can be approved/rejected")
        }

        @Test
        fun `POST reject возвращает 409 для draft маршрута`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.DRAFT)

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/reject")
                .cookie("auth_token", securityToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""{"reason": "test"}""")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Only pending routes can be approved/rejected")
        }

        @Test
        fun `POST reject возвращает 409 для rejected маршрута`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.REJECTED)

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/reject")
                .cookie("auth_token", securityToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""{"reason": "test"}""")
                .exchange()
                .expectStatus().isEqualTo(409)
        }
    }

    // ============================================
    // Story 4.2: AC7 - Маршрут не найден
    // ============================================

    @Nested
    inner class Story42_AC7_МаршрутНеНайден {

        @Test
        fun `POST approve возвращает 404 для несуществующего маршрута`() {
            // Given
            val nonExistentId = UUID.randomUUID()

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/$nonExistentId/approve")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/not-found")
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.detail").isEqualTo("Route not found")
        }

        @Test
        fun `POST reject возвращает 404 для несуществующего маршрута`() {
            // Given
            val nonExistentId = UUID.randomUUID()

            // When & Then
            webTestClient.post()
                .uri("/api/v1/routes/$nonExistentId/reject")
                .cookie("auth_token", securityToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""{"reason": "test"}""")
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/not-found")
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.detail").isEqualTo("Route not found")
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

    private fun createTestRoute(
        path: String,
        createdBy: UUID,
        status: RouteStatus = RouteStatus.DRAFT
    ): Route {
        val route = Route(
            path = path,
            upstreamUrl = "http://test-service:8080",
            methods = listOf("GET", "POST"),
            description = "Test route",
            status = status,
            createdBy = createdBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        var savedRoute: Route? = null
        StepVerifier.create(routeRepository.save(route))
            .consumeNextWith { savedRoute = it }
            .verifyComplete()
        return savedRoute!!
    }

    private fun createTestRouteWithMethods(
        path: String,
        createdBy: UUID,
        methods: List<String>
    ): Route {
        val route = Route(
            path = path,
            upstreamUrl = "http://test-service:8080",
            methods = methods,
            description = "Test route",
            status = RouteStatus.DRAFT,
            createdBy = createdBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        var savedRoute: Route? = null
        StepVerifier.create(routeRepository.save(route))
            .consumeNextWith { savedRoute = it }
            .verifyComplete()
        return savedRoute!!
    }

    private fun createTestRouteWithCustomFields(
        path: String,
        upstreamUrl: String,
        methods: List<String>,
        createdBy: UUID
    ): Route {
        val route = Route(
            path = path,
            upstreamUrl = upstreamUrl,
            methods = methods,
            description = "Test route",
            status = RouteStatus.DRAFT,
            createdBy = createdBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        var savedRoute: Route? = null
        StepVerifier.create(routeRepository.save(route))
            .consumeNextWith { savedRoute = it }
            .verifyComplete()
        return savedRoute!!
    }
}
