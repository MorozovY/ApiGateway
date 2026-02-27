package com.company.gateway.admin.integration

import com.company.gateway.admin.repository.AuditLogRepository
import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.admin.security.JwtService
import com.company.gateway.admin.service.PasswordService
import com.company.gateway.common.model.AuditLog
import com.company.gateway.common.model.Role
import com.company.gateway.common.model.User
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import reactor.test.StepVerifier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Integration тесты для AuditController (Story 7.2).
 *
 * Story 7.2: Audit Log API with Filtering
 * - AC1: Базовый список audit logs с пагинацией
 * - AC2: Фильтрация по userId
 * - AC3: Фильтрация по action
 * - AC4: Фильтрация по entityType
 * - AC5: Фильтрация по диапазону дат
 * - AC6: Комбинирование фильтров
 * - AC7: Контроль доступа (developer получает 403)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuditControllerIntegrationTest {

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
                // В CI читаем из env переменных (gateway-admin использует POSTGRES_DB_ADMIN)
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
    private lateinit var auditLogRepository: AuditLogRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordService: PasswordService

    @Autowired
    private lateinit var jwtService: JwtService

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    private lateinit var developerToken: String
    private lateinit var securityToken: String
    private lateinit var adminToken: String
    private lateinit var developerUser: User
    private lateinit var securityUser: User
    private lateinit var adminUser: User

    @BeforeEach
    fun setUp() {
        // Очищаем routes ПЕРЕД users (FK constraint: routes.approved_by -> users.id)
        StepVerifier.create(
            databaseClient.sql("DELETE FROM routes").fetch().rowsUpdated()
        ).expectNextCount(1).verifyComplete()

        // Очищаем audit_logs
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
        adminUser = createTestUser("admin_test", "password", Role.ADMIN)

        developerToken = jwtService.generateToken(developerUser)
        securityToken = jwtService.generateToken(securityUser)
        adminToken = jwtService.generateToken(adminUser)
    }

    // ============================================
    // AC1: Базовый список audit logs с пагинацией
    // ============================================

    @Nested
    inner class AC1_БазовыйСписокСПагинацией {

        @Test
        fun `GET audit возвращает пагинированный список`() {
            // Given — создаём тестовые audit log записи
            createTestAuditLog("route", UUID.randomUUID().toString(), "created", securityUser.id!!, "security")
            createTestAuditLog("route", UUID.randomUUID().toString(), "approved", securityUser.id!!, "security")
            createTestAuditLog("user", UUID.randomUUID().toString(), "role_changed", securityUser.id!!, "security")

            // When & Then
            webTestClient.get()
                .uri("/api/v1/audit")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items").isArray
                .jsonPath("$.items.length()").isEqualTo(3)
                .jsonPath("$.total").isEqualTo(3)
                .jsonPath("$.offset").isEqualTo(0)
                .jsonPath("$.limit").isEqualTo(50)
        }

        @Test
        fun `GET audit с пагинацией offset и limit`() {
            // Given — создаём 5 записей
            repeat(5) { i ->
                createTestAuditLog("route", UUID.randomUUID().toString(), "created", securityUser.id!!, "security")
            }

            // When & Then — запрашиваем с offset=2, limit=2
            webTestClient.get()
                .uri("/api/v1/audit?offset=2&limit=2")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.total").isEqualTo(5)
                .jsonPath("$.offset").isEqualTo(2)
                .jsonPath("$.limit").isEqualTo(2)
        }

        @Test
        fun `GET audit сортирует по timestamp DESC (новые первыми)`() {
            // Given — создаём записи с разными timestamp через прямой SQL
            val olderTimestamp = Instant.parse("2026-01-01T10:00:00Z")
            val newerTimestamp = Instant.parse("2026-02-20T10:00:00Z")

            // Создаём старую запись
            createTestAuditLogWithTimestamp("route", "created", securityUser.id!!, "security", olderTimestamp)

            // Создаём новую запись
            createTestAuditLogWithTimestamp("route", "approved", securityUser.id!!, "security", newerTimestamp)

            // When & Then — новая запись должна быть первой
            webTestClient.get()
                .uri("/api/v1/audit")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items[0].action").isEqualTo("approved")
                .jsonPath("$.items[1].action").isEqualTo("created")
        }

        @Test
        fun `GET audit возвращает правильную структуру элементов`() {
            // Given
            val entityId = UUID.randomUUID().toString()
            val ipAddress = "192.168.1.100"
            val correlationId = "test-corr-id-123"
            val changes = """{"oldStatus":"pending","newStatus":"published"}"""

            val auditLog = AuditLog(
                entityType = "route",
                entityId = entityId,
                action = "approved",
                userId = securityUser.id!!,
                username = "security",
                changes = changes,
                ipAddress = ipAddress,
                correlationId = correlationId,
                createdAt = Instant.now()
            )
            StepVerifier.create(auditLogRepository.save(auditLog)).expectNextCount(1).verifyComplete()

            // When & Then
            webTestClient.get()
                .uri("/api/v1/audit")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items[0].id").isNotEmpty
                .jsonPath("$.items[0].entityType").isEqualTo("route")
                .jsonPath("$.items[0].entityId").isEqualTo(entityId)
                .jsonPath("$.items[0].action").isEqualTo("approved")
                .jsonPath("$.items[0].user.id").isNotEmpty
                .jsonPath("$.items[0].user.username").isEqualTo("security")
                .jsonPath("$.items[0].timestamp").isNotEmpty
                .jsonPath("$.items[0].ipAddress").isEqualTo(ipAddress)
                .jsonPath("$.items[0].correlationId").isEqualTo(correlationId)
                .jsonPath("$.items[0].changes.oldStatus").isEqualTo("pending")
                .jsonPath("$.items[0].changes.newStatus").isEqualTo("published")
        }

        @Test
        fun `GET audit возвращает пустой список когда записей нет`() {
            // Given — нет записей

            // When & Then
            webTestClient.get()
                .uri("/api/v1/audit")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items").isArray
                .jsonPath("$.items.length()").isEqualTo(0)
                .jsonPath("$.total").isEqualTo(0)
        }

        @Test
        fun `GET audit с невалидным offset возвращает 400`() {
            // When & Then
            webTestClient.get()
                .uri("/api/v1/audit?offset=-1")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `GET audit с невалидным limit возвращает 400`() {
            // When & Then — limit = 0
            webTestClient.get()
                .uri("/api/v1/audit?limit=0")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isBadRequest

            // limit > 100
            webTestClient.get()
                .uri("/api/v1/audit?limit=101")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `GET audit с limit равным MAX_LIMIT работает корректно`() {
            // L3: Тест на граничное значение limit=100
            // Given
            createTestAuditLog("route", UUID.randomUUID().toString(), "created", securityUser.id!!, "security")

            // When & Then
            webTestClient.get()
                .uri("/api/v1/audit?limit=100")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.limit").isEqualTo(100)
        }

        @Test
        fun `GET audit с невалидным UUID в userId возвращает 400`() {
            // L2: Тест на невалидный формат UUID
            // When & Then
            webTestClient.get()
                .uri("/api/v1/audit?userId=not-a-valid-uuid")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    // ============================================
    // AC2: Фильтрация по userId
    // ============================================

    @Nested
    inner class AC2_ФильтрацияПоUserId {

        @Test
        fun `GET audit с userId фильтром возвращает только записи этого пользователя`() {
            // Given — записи от разных пользователей
            createTestAuditLog("route", UUID.randomUUID().toString(), "created", securityUser.id!!, "security")
            createTestAuditLog("route", UUID.randomUUID().toString(), "approved", developerUser.id!!, "developer")
            createTestAuditLog("user", UUID.randomUUID().toString(), "role_changed", securityUser.id!!, "security")

            // When & Then — фильтруем по securityUser
            webTestClient.get()
                .uri("/api/v1/audit?userId=${securityUser.id}")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.total").isEqualTo(2)
                .jsonPath("$.items[0].user.id").isEqualTo(securityUser.id.toString())
                .jsonPath("$.items[1].user.id").isEqualTo(securityUser.id.toString())
        }

        @Test
        fun `GET audit с несуществующим userId возвращает пустой список`() {
            // Given
            createTestAuditLog("route", UUID.randomUUID().toString(), "created", securityUser.id!!, "security")

            // When & Then — фильтруем по несуществующему userId
            val nonExistentUserId = UUID.randomUUID()
            webTestClient.get()
                .uri("/api/v1/audit?userId=$nonExistentUserId")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(0)
                .jsonPath("$.total").isEqualTo(0)
        }
    }

    // ============================================
    // AC3: Фильтрация по action
    // ============================================

    @Nested
    inner class AC3_ФильтрацияПоAction {

        @Test
        fun `GET audit с action фильтром возвращает только записи с этим action`() {
            // Given — записи с разными action
            createTestAuditLog("route", UUID.randomUUID().toString(), "created", securityUser.id!!, "security")
            createTestAuditLog("route", UUID.randomUUID().toString(), "approved", securityUser.id!!, "security")
            createTestAuditLog("route", UUID.randomUUID().toString(), "approved", securityUser.id!!, "security")
            createTestAuditLog("route", UUID.randomUUID().toString(), "rejected", securityUser.id!!, "security")

            // When & Then — фильтруем по approved
            webTestClient.get()
                .uri("/api/v1/audit?action=approved")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.total").isEqualTo(2)
                .jsonPath("$.items[0].action").isEqualTo("approved")
                .jsonPath("$.items[1].action").isEqualTo("approved")
        }

        @Test
        fun `GET audit с несуществующим action возвращает пустой список`() {
            // Given
            createTestAuditLog("route", UUID.randomUUID().toString(), "created", securityUser.id!!, "security")

            // When & Then
            webTestClient.get()
                .uri("/api/v1/audit?action=nonexistent")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(0)
                .jsonPath("$.total").isEqualTo(0)
        }

        @Test
        fun `GET audit с SQL injection в action параметре возвращает пустой список`() {
            // M3: Тест на устойчивость к SQL injection
            // Given
            createTestAuditLog("route", UUID.randomUUID().toString(), "created", securityUser.id!!, "security")

            // When & Then — попытка SQL injection должна быть безопасной
            webTestClient.get()
                .uri("/api/v1/audit?action='; DROP TABLE audit_logs; --")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(0)
                .jsonPath("$.total").isEqualTo(0)
        }

        @Test
        fun `GET audit с multi-select action фильтром возвращает записи с любым из указанных action`() {
            // Story 7.7.3: multi-select action — поддержка нескольких значений через запятую
            // Given — записи с разными action
            createTestAuditLog("route", UUID.randomUUID().toString(), "created", securityUser.id!!, "security")
            createTestAuditLog("route", UUID.randomUUID().toString(), "approved", securityUser.id!!, "security")
            createTestAuditLog("route", UUID.randomUUID().toString(), "rejected", securityUser.id!!, "security")
            createTestAuditLog("route", UUID.randomUUID().toString(), "deleted", securityUser.id!!, "security")

            // When & Then — фильтруем по нескольким action через запятую
            webTestClient.get()
                .uri("/api/v1/audit?action=created,approved")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.total").isEqualTo(2)
                // Проверяем что возвращены именно запрошенные action (Story 7.7.3 fix)
                .jsonPath("$.items[?(@.action == 'created')]").exists()
                .jsonPath("$.items[?(@.action == 'approved')]").exists()
                .jsonPath("$.items[?(@.action == 'rejected')]").doesNotExist()
                .jsonPath("$.items[?(@.action == 'deleted')]").doesNotExist()
        }

        @Test
        fun `GET audit с multi-select action и другими фильтрами работает корректно`() {
            // Story 7.7.3: multi-select action в комбинации с другими фильтрами
            // Given
            createTestAuditLog("route", UUID.randomUUID().toString(), "created", securityUser.id!!, "security")
            createTestAuditLog("route", UUID.randomUUID().toString(), "approved", securityUser.id!!, "security")
            createTestAuditLog("user", UUID.randomUUID().toString(), "created", securityUser.id!!, "security")
            createTestAuditLog("route", UUID.randomUUID().toString(), "deleted", securityUser.id!!, "security")

            // When & Then — entityType=route AND action IN (created, approved)
            webTestClient.get()
                .uri("/api/v1/audit?entityType=route&action=created,approved")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.total").isEqualTo(2)
        }

        @Test
        fun `GET audit с пустыми значениями в action строке игнорирует их`() {
            // Story 7.7.3: edge cases — пустые значения между запятыми
            // Given
            createTestAuditLog("route", UUID.randomUUID().toString(), "created", securityUser.id!!, "security")
            createTestAuditLog("route", UUID.randomUUID().toString(), "approved", securityUser.id!!, "security")
            createTestAuditLog("route", UUID.randomUUID().toString(), "rejected", securityUser.id!!, "security")

            // When & Then — action=created,,approved (пустое значение в середине)
            webTestClient.get()
                .uri("/api/v1/audit?action=created,,approved")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.total").isEqualTo(2)
                .jsonPath("$.items[?(@.action == 'created')]").exists()
                .jsonPath("$.items[?(@.action == 'approved')]").exists()
        }

        @Test
        fun `GET audit с trailing comma в action возвращает корректные результаты`() {
            // Story 7.7.3: edge case — trailing comma
            // Given
            createTestAuditLog("route", UUID.randomUUID().toString(), "created", securityUser.id!!, "security")
            createTestAuditLog("route", UUID.randomUUID().toString(), "approved", securityUser.id!!, "security")

            // When & Then — action=created, (trailing comma)
            webTestClient.get()
                .uri("/api/v1/audit?action=created,")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.total").isEqualTo(1)
                .jsonPath("$.items[0].action").isEqualTo("created")
        }
    }

    // ============================================
    // AC4: Фильтрация по entityType
    // ============================================

    @Nested
    inner class AC4_ФильтрацияПоEntityType {

        @Test
        fun `GET audit с entityType фильтром возвращает только записи этого типа`() {
            // Given — записи с разными entityType
            createTestAuditLog("route", UUID.randomUUID().toString(), "created", securityUser.id!!, "security")
            createTestAuditLog("route", UUID.randomUUID().toString(), "approved", securityUser.id!!, "security")
            createTestAuditLog("user", UUID.randomUUID().toString(), "role_changed", securityUser.id!!, "security")
            createTestAuditLog("rate_limit", UUID.randomUUID().toString(), "created", securityUser.id!!, "security")

            // When & Then — фильтруем по route
            webTestClient.get()
                .uri("/api/v1/audit?entityType=route")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.total").isEqualTo(2)
                .jsonPath("$.items[0].entityType").isEqualTo("route")
                .jsonPath("$.items[1].entityType").isEqualTo("route")
        }
    }

    // ============================================
    // AC5: Фильтрация по диапазону дат
    // ============================================

    @Nested
    inner class AC5_ФильтрацияПоДиапазонуДат {

        @Test
        fun `GET audit с dateFrom и dateTo возвращает записи в диапазоне`() {
            // Given — записи с разными датами
            val jan15 = Instant.parse("2026-01-15T12:00:00Z")
            val feb05 = Instant.parse("2026-02-05T12:00:00Z")
            val feb10 = Instant.parse("2026-02-10T12:00:00Z")
            val mar01 = Instant.parse("2026-03-01T12:00:00Z")

            createTestAuditLogWithTimestamp("route", "created", securityUser.id!!, "security", jan15)
            createTestAuditLogWithTimestamp("route", "approved", securityUser.id!!, "security", feb05)
            createTestAuditLogWithTimestamp("route", "rejected", securityUser.id!!, "security", feb10)
            createTestAuditLogWithTimestamp("route", "published", securityUser.id!!, "security", mar01)

            // When & Then — фильтруем по диапазону 2026-02-01 to 2026-02-11
            webTestClient.get()
                .uri("/api/v1/audit?dateFrom=2026-02-01&dateTo=2026-02-11")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.total").isEqualTo(2)
        }

        @Test
        fun `GET audit только с dateFrom возвращает записи начиная с этой даты`() {
            // Given
            val feb01 = Instant.parse("2026-02-01T12:00:00Z")
            val feb15 = Instant.parse("2026-02-15T12:00:00Z")

            createTestAuditLogWithTimestamp("route", "created", securityUser.id!!, "security", feb01)
            createTestAuditLogWithTimestamp("route", "approved", securityUser.id!!, "security", feb15)

            // When & Then — dateFrom=2026-02-10
            webTestClient.get()
                .uri("/api/v1/audit?dateFrom=2026-02-10")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.total").isEqualTo(1)
        }

        @Test
        fun `GET audit только с dateTo возвращает записи до этой даты`() {
            // Given
            val feb01 = Instant.parse("2026-02-01T12:00:00Z")
            val feb15 = Instant.parse("2026-02-15T12:00:00Z")

            createTestAuditLogWithTimestamp("route", "created", securityUser.id!!, "security", feb01)
            createTestAuditLogWithTimestamp("route", "approved", securityUser.id!!, "security", feb15)

            // When & Then — dateTo=2026-02-10
            webTestClient.get()
                .uri("/api/v1/audit?dateTo=2026-02-10")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.total").isEqualTo(1)
        }

        @Test
        fun `GET audit с dateTo включает записи до конца дня`() {
            // Given — запись создана в конце дня
            val endOfDay = Instant.parse("2026-02-10T23:59:59Z")

            createTestAuditLogWithTimestamp("route", "approved", securityUser.id!!, "security", endOfDay)

            // When & Then — dateTo=2026-02-10 должен включить эту запись
            webTestClient.get()
                .uri("/api/v1/audit?dateTo=2026-02-10")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
        }

        @Test
        fun `GET audit с dateFrom позже dateTo возвращает 400`() {
            // M2: Валидация что dateFrom <= dateTo
            // When & Then
            webTestClient.get()
                .uri("/api/v1/audit?dateFrom=2026-03-01&dateTo=2026-02-01")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    // ============================================
    // AC6: Комбинирование фильтров
    // ============================================

    @Nested
    inner class AC6_КомбинированиеФильтров {

        @Test
        fun `GET audit с несколькими фильтрами применяет AND логику`() {
            // Given — разные комбинации данных
            createTestAuditLog("route", UUID.randomUUID().toString(), "rejected", securityUser.id!!, "security")
            createTestAuditLog("route", UUID.randomUUID().toString(), "approved", securityUser.id!!, "security")
            createTestAuditLog("route", UUID.randomUUID().toString(), "rejected", developerUser.id!!, "developer")
            createTestAuditLog("user", UUID.randomUUID().toString(), "rejected", securityUser.id!!, "security")

            // When & Then — фильтруем: entityType=route AND action=rejected AND userId=security
            webTestClient.get()
                .uri("/api/v1/audit?entityType=route&action=rejected&userId=${securityUser.id}")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.total").isEqualTo(1)
                .jsonPath("$.items[0].entityType").isEqualTo("route")
                .jsonPath("$.items[0].action").isEqualTo("rejected")
                .jsonPath("$.items[0].user.id").isEqualTo(securityUser.id.toString())
        }

        @Test
        fun `GET audit с комбинацией всех фильтров включая даты`() {
            // Given
            val feb10 = Instant.parse("2026-02-10T12:00:00Z")
            val jan01 = Instant.parse("2026-01-01T12:00:00Z")

            // Подходящая запись (февраль)
            createTestAuditLogWithTimestamp("route", "approved", securityUser.id!!, "security", feb10)

            // Не подходящая запись (январь — вне диапазона)
            createTestAuditLogWithTimestamp("route", "approved", securityUser.id!!, "security", jan01)

            // When & Then
            webTestClient.get()
                .uri("/api/v1/audit?entityType=route&action=approved&userId=${securityUser.id}&dateFrom=2026-02-01&dateTo=2026-02-28")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.total").isEqualTo(1)
        }
    }

    // ============================================
    // AC7: Контроль доступа
    // ============================================

    @Nested
    inner class AC7_КонтрольДоступа {

        @Test
        fun `developer получает 403 Forbidden при доступе к audit`() {
            // When & Then
            webTestClient.get()
                .uri("/api/v1/audit")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun `security получает доступ к audit`() {
            // When & Then
            webTestClient.get()
                .uri("/api/v1/audit")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `admin получает доступ к audit`() {
            // When & Then
            webTestClient.get()
                .uri("/api/v1/audit")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        fun `неавторизованный пользователь получает 401`() {
            // When & Then
            webTestClient.get()
                .uri("/api/v1/audit")
                .exchange()
                .expectStatus().isUnauthorized
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

    private fun createTestAuditLog(
        entityType: String,
        entityId: String,
        action: String,
        userId: UUID,
        username: String,
        changes: String? = null,
        ipAddress: String? = null,
        correlationId: String? = null
    ) {
        val auditLog = AuditLog(
            entityType = entityType,
            entityId = entityId,
            action = action,
            userId = userId,
            username = username,
            changes = changes,
            ipAddress = ipAddress,
            correlationId = correlationId,
            createdAt = Instant.now()
        )
        StepVerifier.create(auditLogRepository.save(auditLog))
            .expectNextCount(1)
            .verifyComplete()
    }

    /**
     * Создаёт audit log с явно указанным timestamp через прямой SQL.
     *
     * Используется вместо auditLogRepository.save(), потому что @CreatedDate
     * в AuditLog entity автоматически переопределяет createdAt при вставке.
     */
    private fun createTestAuditLogWithTimestamp(
        entityType: String,
        action: String,
        userId: UUID,
        username: String,
        timestamp: Instant
    ) {
        val id = UUID.randomUUID()
        val entityId = UUID.randomUUID().toString()

        StepVerifier.create(
            databaseClient.sql(
                """
                INSERT INTO audit_logs (id, entity_type, entity_id, action, user_id, username, created_at)
                VALUES (:id, :entityType, :entityId, :action, :userId, :username, :createdAt)
                """.trimIndent()
            )
                .bind("id", id)
                .bind("entityType", entityType)
                .bind("entityId", entityId)
                .bind("action", action)
                .bind("userId", userId)
                .bind("username", username)
                .bind("createdAt", timestamp)
                .fetch()
                .rowsUpdated()
        )
            .expectNext(1L)
            .verifyComplete()
    }
}
