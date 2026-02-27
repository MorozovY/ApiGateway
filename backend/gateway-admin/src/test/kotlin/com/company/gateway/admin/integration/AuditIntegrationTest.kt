package com.company.gateway.admin.integration

import com.company.gateway.admin.repository.AuditLogRepository
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.admin.security.JwtService
import com.company.gateway.admin.service.PasswordService
import com.company.gateway.common.Constants.CORRELATION_ID_HEADER
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
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * Integration тесты для Audit Log (Story 7.1).
 *
 * Story 7.1: Audit Log Entity & Event Recording
 * - AC2: Approve/Reject events записываются
 * - AC3: IP Address и Correlation ID записываются
 * - AC4: Published event записывается
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuditIntegrationTest {

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
    private lateinit var securityToken: String
    private lateinit var developerUser: User
    private lateinit var securityUser: User

    @BeforeEach
    fun setUp() {
        // Очищаем маршруты и audit_logs
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
        securityUser = createTestUser("security", "password", Role.SECURITY)

        developerToken = jwtService.generateToken(developerUser)
        securityToken = jwtService.generateToken(securityUser)
    }

    // ============================================
    // AC2: Approve/Reject events записываются
    // ============================================

    @Nested
    inner class AC2_ApproveRejectEventsЗаписываются {

        @Test
        fun `approve route создаёт audit log с action approved`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/approve")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk

            // Then — проверяем audit log с action "approved"
            StepVerifier.create(
                auditLogRepository.findAll()
                    .filter { it.entityId == route.id.toString() && it.action == "approved" }
            )
                .expectNextMatches { auditLog ->
                    auditLog.entityType == "route" &&
                    auditLog.userId == securityUser.id &&
                    auditLog.username == "security"
                }
                .verifyComplete()
        }

        @Test
        fun `approve route создаёт audit log с правильными changes`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/approve")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk

            // Then — проверяем changes в audit log
            StepVerifier.create(
                auditLogRepository.findAll()
                    .filter { it.entityId == route.id.toString() && it.action == "approved" }
            )
                .expectNextMatches { auditLog ->
                    auditLog.changes != null &&
                    auditLog.changes!!.contains("previousStatus") &&
                    auditLog.changes!!.contains("pending") &&
                    auditLog.changes!!.contains("newStatus") &&
                    auditLog.changes!!.contains("published")
                }
                .verifyComplete()
        }

        @Test
        fun `reject route создаёт audit log с action rejected`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)
            val reason = "Security vulnerability found"

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/reject")
                .cookie("auth_token", securityToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"reason": "$reason"}""")
                .exchange()
                .expectStatus().isOk

            // Then — проверяем audit log с action "rejected"
            StepVerifier.create(
                auditLogRepository.findAll()
                    .filter { it.entityId == route.id.toString() && it.action == "rejected" }
            )
                .expectNextMatches { auditLog ->
                    auditLog.entityType == "route" &&
                    auditLog.userId == securityUser.id &&
                    auditLog.username == "security"
                }
                .verifyComplete()
        }

        @Test
        fun `reject route записывает rejectionReason в changes`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)
            val reason = "Security vulnerability found"

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/reject")
                .cookie("auth_token", securityToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"reason": "$reason"}""")
                .exchange()
                .expectStatus().isOk

            // Then — проверяем rejectionReason в changes
            StepVerifier.create(
                auditLogRepository.findAll()
                    .filter { it.entityId == route.id.toString() && it.action == "rejected" }
            )
                .expectNextMatches { auditLog ->
                    auditLog.changes != null &&
                    auditLog.changes!!.contains(reason)
                }
                .verifyComplete()
        }
    }

    // ============================================
    // AC3: IP Address и Correlation ID записываются
    // ============================================

    @Nested
    inner class AC3_IPAddressИCorrelationIDЗаписываются {

        @Test
        fun `audit log записывает correlation ID из header`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)
            val correlationId = "test-correlation-id-12345"

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/approve")
                .cookie("auth_token", securityToken)
                .header(CORRELATION_ID_HEADER, correlationId)
                .exchange()
                .expectStatus().isOk

            // Then — проверяем correlation ID в audit log
            StepVerifier.create(
                auditLogRepository.findAll()
                    .filter { it.entityId == route.id.toString() && it.action == "approved" }
            )
                .expectNextMatches { auditLog ->
                    auditLog.correlationId == correlationId
                }
                .verifyComplete()
        }

        @Test
        fun `audit log генерирует correlation ID если header отсутствует`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/approve")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk

            // Then — correlation ID должен быть сгенерирован (UUID формат)
            StepVerifier.create(
                auditLogRepository.findAll()
                    .filter { it.entityId == route.id.toString() && it.action == "approved" }
            )
                .expectNextMatches { auditLog ->
                    auditLog.correlationId != null &&
                    auditLog.correlationId!!.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))
                }
                .verifyComplete()
        }

        @Test
        fun `reject также записывает correlation ID`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)
            val correlationId = "reject-correlation-id-67890"

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/reject")
                .cookie("auth_token", securityToken)
                .header(CORRELATION_ID_HEADER, correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"reason": "Security issue"}""")
                .exchange()
                .expectStatus().isOk

            // Then
            StepVerifier.create(
                auditLogRepository.findAll()
                    .filter { it.entityId == route.id.toString() && it.action == "rejected" }
            )
                .expectNextMatches { auditLog ->
                    auditLog.correlationId == correlationId
                }
                .verifyComplete()
        }

        @Test
        fun `audit log записывает IP address из X-Forwarded-For`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)
            val clientIp = "192.168.1.100"

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/approve")
                .cookie("auth_token", securityToken)
                .header("X-Forwarded-For", clientIp)
                .exchange()
                .expectStatus().isOk

            // Then — проверяем IP address в audit log
            // Примечание: из-за асинхронной записи аудита нужно подождать
            Thread.sleep(100)

            StepVerifier.create(
                auditLogRepository.findAll()
                    .filter { it.entityId == route.id.toString() && it.action == "approved" }
            )
                .expectNextMatches { auditLog ->
                    auditLog.ipAddress == clientIp
                }
                .verifyComplete()
        }

        @Test
        fun `audit log записывает IP address при reject`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)
            val clientIp = "10.0.0.50"

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/reject")
                .cookie("auth_token", securityToken)
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"reason": "Security issue"}""")
                .exchange()
                .expectStatus().isOk

            // Then
            Thread.sleep(100)

            StepVerifier.create(
                auditLogRepository.findAll()
                    .filter { it.entityId == route.id.toString() && it.action == "rejected" }
            )
                .expectNextMatches { auditLog ->
                    auditLog.ipAddress == clientIp
                }
                .verifyComplete()
        }
    }

    // ============================================
    // AC4: Published event записывается
    // ============================================

    @Nested
    inner class AC4_PublishedEventЗаписывается {

        @Test
        fun `approve route создаёт дополнительный audit log с action published`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/approve")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk

            // Then — должен быть audit log с action "published"
            StepVerifier.create(
                auditLogRepository.findAll()
                    .filter { it.entityId == route.id.toString() && it.action == "published" }
            )
                .expectNextMatches { auditLog ->
                    auditLog.entityType == "route" &&
                    auditLog.userId == securityUser.id
                }
                .verifyComplete()
        }

        @Test
        fun `published event включает approvedBy в changes`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/approve")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk

            // Then — проверяем approvedBy в changes
            StepVerifier.create(
                auditLogRepository.findAll()
                    .filter { it.entityId == route.id.toString() && it.action == "published" }
            )
                .expectNextMatches { auditLog ->
                    auditLog.changes != null &&
                    auditLog.changes!!.contains("approvedBy") &&
                    auditLog.changes!!.contains("security")
                }
                .verifyComplete()
        }

        @Test
        fun `approve создаёт оба audit log записи (approved и published)`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/approve")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk

            // Then — проверяем оба audit log записи
            StepVerifier.create(
                auditLogRepository.findAll()
                    .filter { it.entityId == route.id.toString() }
                    .collectList()
            )
                .expectNextMatches { auditLogs ->
                    val actions = auditLogs.map { it.action }
                    actions.contains("approved") && actions.contains("published")
                }
                .verifyComplete()
        }
    }

    // ============================================
    // Дополнительные тесты: submit записывает correlation ID
    // ============================================

    @Nested
    inner class SubmitЗаписываетCorrelationID {

        @Test
        fun `submit for approval записывает correlation ID`() {
            // Given
            val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.DRAFT)
            val correlationId = "submit-correlation-id-11111"

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/submit")
                .cookie("auth_token", developerToken)
                .header(CORRELATION_ID_HEADER, correlationId)
                .exchange()
                .expectStatus().isOk

            // Then
            StepVerifier.create(
                auditLogRepository.findAll()
                    .filter { it.entityId == route.id.toString() && it.action == "route.submitted" }
            )
                .expectNextMatches { auditLog ->
                    auditLog.correlationId == correlationId
                }
                .verifyComplete()
        }
    }

    // ============================================
    // Вспомогательные методы
    // ============================================

    private fun createTestUser(
        username: String,
        password: String,
        role: Role
    ): User {
        val hashedPassword = passwordService.hash(password)
        val user = User(
            username = username,
            email = "$username@example.com",
            passwordHash = hashedPassword,
            role = role,
            isActive = true
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
        status: RouteStatus
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
}
