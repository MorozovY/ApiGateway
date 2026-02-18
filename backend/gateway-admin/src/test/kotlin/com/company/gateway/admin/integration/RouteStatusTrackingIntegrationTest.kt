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
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * Интеграционные тесты для Route Status Tracking (Story 4.4).
 *
 * AC2: GET /api/v1/routes/{id} для rejected маршрута содержит rejectorUsername
 * AC3: GET /api/v1/routes/{id} для published маршрута содержит approverUsername
 * AC4: Повторная подача rejected маршрута (resubmission)
 * AC5: GET /api/v1/routes?createdBy=me возвращает статус
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class RouteStatusTrackingIntegrationTest {

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
    private lateinit var anotherDeveloperToken: String
    private lateinit var securityToken: String
    private lateinit var developerUser: User
    private lateinit var anotherDeveloperUser: User
    private lateinit var securityUser: User

    @BeforeEach
    fun setUp() {
        // Очищаем маршруты и audit log
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
        developerUser = createTestUser("developer44", "password", Role.DEVELOPER)
        anotherDeveloperUser = createTestUser("anotherdev44", "password", Role.DEVELOPER)
        securityUser = createTestUser("security44", "password", Role.SECURITY)

        developerToken = jwtService.generateToken(developerUser)
        anotherDeveloperToken = jwtService.generateToken(anotherDeveloperUser)
        securityToken = jwtService.generateToken(securityUser)
    }

    // ============================================
    // AC2: GET /api/v1/routes/{id} — rejected маршрут содержит rejectorUsername
    // ============================================

    @Nested
    inner class AC2_ОтклонённыйМаршрутСодержитRejectorUsername {

        @Test
        fun `GET routes по id возвращает rejectorUsername для rejected маршрута`() {
            // Given — маршрут в статусе rejected с заполненными rejection полями
            val route = createTestRouteWithRejection(
                createdBy = developerUser.id!!,
                rejectedBy = securityUser.id!!,
                rejectionReason = "Security issue found"
            )

            // When & Then
            webTestClient.get()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(route.id.toString())
                .jsonPath("$.status").isEqualTo("rejected")
                .jsonPath("$.rejectionReason").isEqualTo("Security issue found")
                .jsonPath("$.rejectorUsername").isEqualTo(securityUser.username)
                .jsonPath("$.rejectedAt").isNotEmpty
        }

        @Test
        fun `GET routes по id возвращает null rejectorUsername для draft маршрута`() {
            // Given — draft маршрут без rejection полей
            val route = createTestRoute("/api/orders44-draft", developerUser.id!!, RouteStatus.DRAFT)

            // When & Then
            webTestClient.get()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.status").isEqualTo("draft")
                .jsonPath("$.rejectorUsername").doesNotExist()
        }
    }

    // ============================================
    // AC3: GET /api/v1/routes/{id} — published маршрут содержит approverUsername
    // ============================================

    @Nested
    inner class AC3_ОдобренныйМаршрутСодержитApproverUsername {

        @Test
        fun `GET routes по id возвращает approverUsername для published маршрута`() {
            // Given — маршрут в статусе published с заполненными approval полями
            val route = createTestRouteWithApproval(
                createdBy = developerUser.id!!,
                approvedBy = securityUser.id!!
            )

            // When & Then
            webTestClient.get()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(route.id.toString())
                .jsonPath("$.status").isEqualTo("published")
                .jsonPath("$.approverUsername").isEqualTo(securityUser.username)
                .jsonPath("$.approvedAt").isNotEmpty
        }

        @Test
        fun `GET routes по id возвращает null approverUsername для draft маршрута`() {
            // Given — draft маршрут без approval полей
            val route = createTestRoute("/api/orders44-appr", developerUser.id!!, RouteStatus.DRAFT)

            // When & Then
            webTestClient.get()
                .uri("/api/v1/routes/${route.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.status").isEqualTo("draft")
                .jsonPath("$.approverUsername").doesNotExist()
        }
    }

    // ============================================
    // AC4: Повторная подача отклонённого маршрута
    // ============================================

    @Nested
    inner class AC4_ПовторнаяПодачаОтклонённогоМаршрута {

        @Test
        fun `POST submit для rejected маршрута меняет статус на pending`() {
            // Given — маршрут в статусе rejected
            val route = createTestRouteWithRejection(
                createdBy = developerUser.id!!,
                rejectedBy = securityUser.id!!,
                rejectionReason = "Security issue"
            )

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
        fun `POST submit для rejected маршрута очищает rejection поля`() {
            // Given — маршрут в статусе rejected с данными об отклонении
            val route = createTestRouteWithRejection(
                createdBy = developerUser.id!!,
                rejectedBy = securityUser.id!!,
                rejectionReason = "Security issue"
            )

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/submit")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk

            // Then — проверяем в базе, что rejection поля очищены и submittedAt обновлён
            StepVerifier.create(routeRepository.findById(route.id!!))
                .expectNextMatches { savedRoute ->
                    savedRoute.status == RouteStatus.PENDING &&
                    savedRoute.rejectionReason == null &&
                    savedRoute.rejectedBy == null &&
                    savedRoute.rejectedAt == null &&
                    savedRoute.submittedAt != null &&
                    // submittedAt должен быть позже исходного значения (1 час назад)
                    savedRoute.submittedAt!! > route.submittedAt!!
                }
                .verifyComplete()
        }

        @Test
        fun `POST submit для rejected маршрута создаёт audit log с action route_resubmitted`() {
            // Given — маршрут в статусе rejected
            val route = createTestRouteWithRejection(
                createdBy = developerUser.id!!,
                rejectedBy = securityUser.id!!,
                rejectionReason = "Security issue"
            )

            // When
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/submit")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk

            // Then — проверяем audit log
            StepVerifier.create(
                auditLogRepository.findAll()
                    .filter { it.entityId == route.id.toString() && it.action == "route.resubmitted" }
            )
                .expectNextMatches { auditLog ->
                    auditLog.entityType == "route" &&
                    auditLog.userId == developerUser.id
                }
                .verifyComplete()
        }

        @Test
        fun `POST submit для rejected маршрута другим пользователем возвращает 403`() {
            // Given — маршрут принадлежит первому developer, пытается подать второй developer
            val route = createTestRouteWithRejection(
                createdBy = developerUser.id!!,
                rejectedBy = securityUser.id!!,
                rejectionReason = "Security issue"
            )

            // When & Then — другой developer не может повторно подать чужой маршрут
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/submit")
                .cookie("auth_token", anotherDeveloperToken)
                .exchange()
                .expectStatus().isForbidden
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/forbidden")
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.detail").isEqualTo("You can only submit your own routes")
        }
    }

    // ============================================
    // AC5: GET /api/v1/routes?createdBy=me возвращает статус
    // ============================================

    @Nested
    inner class AC5_СписокМаршрутовСоСтатусами {

        @Test
        fun `GET routes с параметром createdBy=me возвращает только маршруты текущего пользователя`() {
            // Given — маршруты разных разработчиков
            createTestRoute("/api/orders44-me", developerUser.id!!, RouteStatus.DRAFT)
            createTestRoute("/api/orders44-other", anotherDeveloperUser.id!!, RouteStatus.PENDING)

            // When & Then — developer видит только свои маршруты
            webTestClient.get()
                .uri("/api/v1/routes?createdBy=me")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(1)
                .jsonPath("$.items[0].status").isEqualTo("draft")
        }

        @Test
        fun `GET routes с параметром createdBy=me возвращает поле status в каждом маршруте`() {
            // Given — маршруты в разных статусах
            createTestRoute("/api/orders44-st1", developerUser.id!!, RouteStatus.DRAFT)
            createTestRoute("/api/orders44-st2", developerUser.id!!, RouteStatus.PENDING)

            // When & Then — каждый маршрут содержит поле status
            webTestClient.get()
                .uri("/api/v1/routes?createdBy=me")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items[0].status").isNotEmpty
                .jsonPath("$.items[1].status").isNotEmpty
        }

        @Test
        fun `GET routes с параметром createdBy=me поддерживает пагинацию`() {
            // Given — несколько маршрутов
            createTestRoute("/api/orders44-p1", developerUser.id!!, RouteStatus.DRAFT)
            createTestRoute("/api/orders44-p2", developerUser.id!!, RouteStatus.DRAFT)
            createTestRoute("/api/orders44-p3", developerUser.id!!, RouteStatus.DRAFT)

            // When & Then — пагинация работает корректно (API использует offset/limit)
            webTestClient.get()
                .uri("/api/v1/routes?createdBy=me&offset=0&limit=2")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items.length()").isEqualTo(2)
                .jsonPath("$.total").isEqualTo(3)
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
            description = "Test route for status tracking",
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

    private fun createTestRouteWithRejection(
        createdBy: UUID,
        rejectedBy: UUID,
        rejectionReason: String
    ): Route {
        // Создаём маршрут напрямую в БД с rejection полями
        val rejectedAt = Instant.now()
        val route = Route(
            path = "/api/rejected-${UUID.randomUUID()}",
            upstreamUrl = "http://test-service:8080",
            methods = listOf("GET"),
            description = "Rejected test route",
            status = RouteStatus.REJECTED,
            createdBy = createdBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            submittedAt = rejectedAt.minusSeconds(3600),
            rejectedBy = rejectedBy,
            rejectedAt = rejectedAt,
            rejectionReason = rejectionReason
        )
        var savedRoute: Route? = null
        StepVerifier.create(routeRepository.save(route))
            .consumeNextWith { savedRoute = it }
            .verifyComplete()
        return savedRoute!!
    }

    private fun createTestRouteWithApproval(
        createdBy: UUID,
        approvedBy: UUID
    ): Route {
        // Создаём маршрут напрямую в БД с approval полями
        val approvedAt = Instant.now()
        val route = Route(
            path = "/api/approved-${UUID.randomUUID()}",
            upstreamUrl = "http://test-service:8080",
            methods = listOf("GET"),
            description = "Approved test route",
            status = RouteStatus.PUBLISHED,
            createdBy = createdBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            submittedAt = approvedAt.minusSeconds(3600),
            approvedBy = approvedBy,
            approvedAt = approvedAt
        )
        var savedRoute: Route? = null
        StepVerifier.create(routeRepository.save(route))
            .consumeNextWith { savedRoute = it }
            .verifyComplete()
        return savedRoute!!
    }
}
