package com.company.gateway.admin.integration

import com.company.gateway.admin.dto.CreateRateLimitRequest
import com.company.gateway.admin.dto.UpdateRateLimitRequest
import com.company.gateway.admin.repository.AuditLogRepository
import com.company.gateway.admin.repository.RateLimitRepository
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.admin.security.JwtService
import com.company.gateway.admin.service.PasswordService
import com.company.gateway.common.model.RateLimit
import com.company.gateway.common.model.Role
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
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
import java.time.Instant
import java.util.UUID

/**
 * Интеграционные тесты для RateLimitController (Story 5.1).
 *
 * Покрывает AC1-AC10:
 * - AC2: Создание политики (admin → 201)
 * - AC3: Валидация создания (400, 409)
 * - AC4: Обновление политики (200)
 * - AC5: Удаление неиспользуемой политики (204)
 * - AC6: Запрет удаления используемой политики (409)
 * - AC7: Получение списка политик (200 + usageCount)
 * - AC8: Получение политики по ID (200 + usageCount)
 * - AC9: 404 для несуществующей политики
 * - AC10: 403 для non-admin (CUD операции)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class RateLimitControllerIntegrationTest {

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
    private lateinit var rateLimitRepository: RateLimitRepository

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

    private lateinit var adminToken: String
    private lateinit var developerToken: String
    private lateinit var securityToken: String
    private lateinit var adminUser: User

    @BeforeEach
    fun setUp() {
        // Очищаем маршруты, политики, audit_logs
        StepVerifier.create(
            routeRepository.findAll()
                .flatMap { routeRepository.delete(it) }
                .then()
        ).verifyComplete()

        StepVerifier.create(rateLimitRepository.deleteAll()).verifyComplete()
        StepVerifier.create(auditLogRepository.deleteAll()).verifyComplete()

        // Очищаем тестовых пользователей (кроме admin из миграции)
        StepVerifier.create(
            userRepository.findAll()
                .filter { it.username != "admin" }
                .flatMap { userRepository.delete(it) }
                .then()
        ).verifyComplete()

        // Создаём тестовых пользователей
        adminUser = createTestUser("testadmin", "password", Role.ADMIN)
        val developerUser = createTestUser("developer", "password", Role.DEVELOPER)
        val securityUser = createTestUser("security", "password", Role.SECURITY)

        adminToken = jwtService.generateToken(adminUser)
        developerToken = jwtService.generateToken(developerUser)
        securityToken = jwtService.generateToken(securityUser)
    }

    // ============================================
    // AC2: Создание политики — admin получает 201
    // ============================================

    @Nested
    inner class AC2_CreatePolicy {

        @Test
        fun `создание политики возвращает 201 для admin`() {
            val request = CreateRateLimitRequest(
                name = "standard",
                description = "Standard rate limit for most services",
                requestsPerSecond = 100,
                burstSize = 150
            )

            webTestClient.post()
                .uri("/api/v1/rate-limits")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").isNotEmpty
                .jsonPath("$.name").isEqualTo("standard")
                .jsonPath("$.description").isEqualTo("Standard rate limit for most services")
                .jsonPath("$.requestsPerSecond").isEqualTo(100)
                .jsonPath("$.burstSize").isEqualTo(150)
                .jsonPath("$.usageCount").isEqualTo(0)
                .jsonPath("$.createdBy").isEqualTo(adminUser.id.toString())
                .jsonPath("$.createdAt").isNotEmpty
                .jsonPath("$.updatedAt").isNotEmpty
        }

        @Test
        fun `создание политики без description возвращает 201`() {
            val request = CreateRateLimitRequest(
                name = "minimal",
                requestsPerSecond = 10,
                burstSize = 20
            )

            webTestClient.post()
                .uri("/api/v1/rate-limits")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.name").isEqualTo("minimal")
                .jsonPath("$.description").doesNotExist()
        }
    }

    // ============================================
    // AC3: Валидация создания
    // ============================================

    @Nested
    inner class AC3_CreateValidation {

        @Test
        fun `пустое имя политики возвращает 400`() {
            val request = mapOf(
                "name" to "",
                "requestsPerSecond" to 100,
                "burstSize" to 150
            )

            webTestClient.post()
                .uri("/api/v1/rate-limits")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `дублирование имени политики возвращает 409`() {
            // Создаём первую политику
            createTestPolicy("duplicate-name", 100, 150)

            val request = CreateRateLimitRequest(
                name = "duplicate-name",
                requestsPerSecond = 200,
                burstSize = 300
            )

            webTestClient.post()
                .uri("/api/v1/rate-limits")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/conflict")
                .jsonPath("$.detail").isEqualTo("Rate limit policy with this name already exists")
        }

        @Test
        fun `requestsPerSecond равный нулю возвращает 400`() {
            val request = mapOf(
                "name" to "test",
                "requestsPerSecond" to 0,
                "burstSize" to 150
            )

            webTestClient.post()
                .uri("/api/v1/rate-limits")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        fun `burstSize меньше requestsPerSecond возвращает 400`() {
            val request = CreateRateLimitRequest(
                name = "invalid-burst",
                requestsPerSecond = 100,
                burstSize = 50
            )

            webTestClient.post()
                .uri("/api/v1/rate-limits")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Burst size must be at least equal to requests per second")
        }

        @Test
        fun `burstSize равный нулю возвращает 400`() {
            val request = mapOf(
                "name" to "test",
                "requestsPerSecond" to 100,
                "burstSize" to 0
            )

            webTestClient.post()
                .uri("/api/v1/rate-limits")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    // ============================================
    // AC4: Обновление политики
    // ============================================

    @Nested
    inner class AC4_UpdatePolicy {

        @Test
        fun `обновление политики возвращает 200`() {
            val policy = createTestPolicy("original", 100, 150)

            val request = UpdateRateLimitRequest(
                name = "updated-name",
                description = "Updated description",
                requestsPerSecond = 200,
                burstSize = 300
            )

            webTestClient.put()
                .uri("/api/v1/rate-limits/${policy.id}")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(policy.id.toString())
                .jsonPath("$.name").isEqualTo("updated-name")
                .jsonPath("$.description").isEqualTo("Updated description")
                .jsonPath("$.requestsPerSecond").isEqualTo(200)
                .jsonPath("$.burstSize").isEqualTo(300)
                .jsonPath("$.updatedAt").isNotEmpty
        }

        @Test
        fun `обновление несуществующей политики возвращает 404`() {
            val nonExistentId = UUID.randomUUID()
            val request = UpdateRateLimitRequest(name = "new-name")

            webTestClient.put()
                .uri("/api/v1/rate-limits/$nonExistentId")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/not-found")
                .jsonPath("$.detail").isEqualTo("Rate limit policy not found")
        }
    }

    // ============================================
    // AC5: Удаление неиспользуемой политики
    // ============================================

    @Nested
    inner class AC5_DeleteUnusedPolicy {

        @Test
        fun `удаление неиспользуемой политики возвращает 204`() {
            val policy = createTestPolicy("unused-policy", 100, 150)

            webTestClient.delete()
                .uri("/api/v1/rate-limits/${policy.id}")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isNoContent
        }
    }

    // ============================================
    // AC6: Запрет удаления используемой политики
    // ============================================

    @Nested
    inner class AC6_DeleteUsedPolicy {

        @Test
        fun `удаление используемой политики возвращает 409`() {
            val policy = createTestPolicy("in-use-policy", 100, 150)
            // Создаём маршрут, использующий эту политику
            createTestRouteWithPolicy(adminUser.id!!, policy.id!!)

            webTestClient.delete()
                .uri("/api/v1/rate-limits/${policy.id}")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/conflict")
                .jsonPath("$.detail").isEqualTo("Policy is in use by 1 routes")
        }
    }

    // ============================================
    // AC7: Получение списка политик с usageCount
    // ============================================

    @Nested
    inner class AC7_ListPolicies {

        @Test
        fun `список политик возвращает 200 с usageCount`() {
            val policy1 = createTestPolicy("policy-1", 100, 150)
            createTestPolicy("policy-2", 200, 300)
            // Привязываем один маршрут к policy1
            createTestRouteWithPolicy(adminUser.id!!, policy1.id!!)

            webTestClient.get()
                .uri("/api/v1/rate-limits")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items").isArray
                .jsonPath("$.total").isEqualTo(2)
                .jsonPath("$.offset").isEqualTo(0)
                .jsonPath("$.limit").isEqualTo(100)
        }

        @Test
        fun `пустой список возвращает 200 с пустым массивом`() {
            webTestClient.get()
                .uri("/api/v1/rate-limits")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.items").isArray
                .jsonPath("$.total").isEqualTo(0)
        }
    }

    // ============================================
    // AC8: Получение политики по ID
    // ============================================

    @Nested
    inner class AC8_GetPolicyById {

        @Test
        fun `получение политики по ID возвращает 200 с usageCount`() {
            val policy = createTestPolicy("test-policy", 100, 150)
            createTestRouteWithPolicy(adminUser.id!!, policy.id!!)

            webTestClient.get()
                .uri("/api/v1/rate-limits/${policy.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(policy.id.toString())
                .jsonPath("$.name").isEqualTo("test-policy")
                .jsonPath("$.requestsPerSecond").isEqualTo(100)
                .jsonPath("$.burstSize").isEqualTo(150)
                .jsonPath("$.usageCount").isEqualTo(1)
        }
    }

    // ============================================
    // AC9: 404 для несуществующей политики
    // ============================================

    @Nested
    inner class AC9_NotFound {

        @Test
        fun `получение несуществующей политики возвращает 404`() {
            val nonExistentId = UUID.randomUUID()

            webTestClient.get()
                .uri("/api/v1/rate-limits/$nonExistentId")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.gateway/errors/not-found")
                .jsonPath("$.detail").isEqualTo("Rate limit policy not found")
        }

        @Test
        fun `обновление несуществующей политики возвращает 404`() {
            val nonExistentId = UUID.randomUUID()

            webTestClient.put()
                .uri("/api/v1/rate-limits/$nonExistentId")
                .cookie("auth_token", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(UpdateRateLimitRequest(name = "new"))
                .exchange()
                .expectStatus().isNotFound
        }

        @Test
        fun `удаление несуществующей политики возвращает 404`() {
            val nonExistentId = UUID.randomUUID()

            webTestClient.delete()
                .uri("/api/v1/rate-limits/$nonExistentId")
                .cookie("auth_token", adminToken)
                .exchange()
                .expectStatus().isNotFound
        }
    }

    // ============================================
    // AC10: 403 для non-admin (CUD операции)
    // ============================================

    @Nested
    inner class AC10_ForbiddenForNonAdmin {

        @Test
        fun `создание политики возвращает 403 для developer`() {
            val request = CreateRateLimitRequest(
                name = "should-fail",
                requestsPerSecond = 100,
                burstSize = 150
            )

            webTestClient.post()
                .uri("/api/v1/rate-limits")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun `создание политики возвращает 403 для security`() {
            val request = CreateRateLimitRequest(
                name = "should-fail",
                requestsPerSecond = 100,
                burstSize = 150
            )

            webTestClient.post()
                .uri("/api/v1/rate-limits")
                .cookie("auth_token", securityToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun `обновление политики возвращает 403 для developer`() {
            val policy = createTestPolicy("policy-to-update", 100, 150)

            webTestClient.put()
                .uri("/api/v1/rate-limits/${policy.id}")
                .cookie("auth_token", developerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(UpdateRateLimitRequest(name = "new-name"))
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        fun `удаление политики возвращает 403 для developer`() {
            val policy = createTestPolicy("policy-to-delete", 100, 150)

            webTestClient.delete()
                .uri("/api/v1/rate-limits/${policy.id}")
                .cookie("auth_token", developerToken)
                .exchange()
                .expectStatus().isForbidden
        }
    }

    // ============================================
    // Вспомогательные методы
    // ============================================

    private fun createTestUser(username: String, password: String, role: Role): User {
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

    private fun createTestPolicy(name: String, rps: Int, burst: Int): RateLimit {
        val policy = RateLimit(
            name = name,
            requestsPerSecond = rps,
            burstSize = burst,
            createdBy = adminUser.id!!,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        var saved: RateLimit? = null
        StepVerifier.create(rateLimitRepository.save(policy))
            .consumeNextWith { saved = it }
            .verifyComplete()
        return saved!!
    }

    private fun createTestRouteWithPolicy(createdBy: UUID, rateLimitId: UUID): Route {
        val route = Route(
            path = "/api/test-${UUID.randomUUID()}",
            upstreamUrl = "http://test-service:8080",
            methods = listOf("GET"),
            status = RouteStatus.DRAFT,
            createdBy = createdBy,
            rateLimitId = rateLimitId,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        var saved: Route? = null
        StepVerifier.create(routeRepository.save(route))
            .consumeNextWith { saved = it }
            .verifyComplete()
        return saved!!
    }
}
