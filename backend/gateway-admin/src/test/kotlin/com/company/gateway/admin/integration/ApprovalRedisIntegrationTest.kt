package com.company.gateway.admin.integration

import com.company.gateway.admin.publisher.RouteEventPublisher
import com.company.gateway.admin.repository.AuditLogRepository
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.admin.security.JwtService
import com.company.gateway.admin.service.PasswordService
import com.company.gateway.common.model.Role
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import com.company.gateway.common.model.User
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import reactor.test.StepVerifier
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Integration тесты для проверки Redis pub/sub при approve маршрута.
 *
 * Story 4.2, AC2: Автоматическая публикация после одобрения
 * - Gateway-core получает cache invalidation event
 * - Маршрут становится активным в течение 5 секунд
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApprovalRedisIntegrationTest {

    companion object {
        // Проверяем запущены ли мы в CI
        private val isTestcontainersDisabled = System.getenv("TESTCONTAINERS_DISABLED") == "true"

        private var postgres: PostgreSQLContainer<*>? = null
        private var redis: RedisContainer? = null

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

            redis = RedisContainer("redis:7")
            redis!!.start()
        }

        @AfterAll
        @JvmStatic
        fun stopContainers() {
            postgres?.stop()
            redis?.stop()
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            if (isTestcontainersDisabled) {
                // В CI читаем из env переменных
                val pgHost = System.getenv("POSTGRES_HOST") ?: "localhost"
                val pgPort = System.getenv("POSTGRES_PORT") ?: "5432"
                val pgDb = System.getenv("POSTGRES_DB") ?: "gateway_test"
                val pgUser = System.getenv("POSTGRES_USER") ?: "gateway"
                val pgPass = System.getenv("POSTGRES_PASSWORD") ?: "gateway"
                val redisHost = System.getenv("REDIS_HOST") ?: "localhost"
                val redisPort = System.getenv("REDIS_PORT") ?: "6379"

                registry.add("spring.r2dbc.url") { "r2dbc:postgresql://$pgHost:$pgPort/$pgDb" }
                registry.add("spring.r2dbc.username") { pgUser }
                registry.add("spring.r2dbc.password") { pgPass }
                registry.add("spring.flyway.url") { "jdbc:postgresql://$pgHost:$pgPort/$pgDb" }
                registry.add("spring.flyway.user") { pgUser }
                registry.add("spring.flyway.password") { pgPass }
                registry.add("spring.data.redis.host") { redisHost }
                registry.add("spring.data.redis.port") { redisPort.toInt() }
            } else {
                registry.add("spring.r2dbc.url") {
                    "r2dbc:postgresql://${postgres!!.host}:${postgres!!.firstMappedPort}/${postgres!!.databaseName}"
                }
                registry.add("spring.r2dbc.username", postgres!!::getUsername)
                registry.add("spring.r2dbc.password", postgres!!::getPassword)
                registry.add("spring.flyway.url", postgres!!::getJdbcUrl)
                registry.add("spring.flyway.user", postgres!!::getUsername)
                registry.add("spring.flyway.password", postgres!!::getPassword)
                // Redis configuration
                registry.add("spring.data.redis.host", redis!!::getHost)
                registry.add("spring.data.redis.port") { redis!!.firstMappedPort }
            }
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

    @Autowired
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    private lateinit var securityToken: String
    private lateinit var securityUser: User
    private lateinit var developerUser: User

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
        securityUser = createTestUser("security", "password", Role.SECURITY)
        securityToken = jwtService.generateToken(securityUser)
    }

    @Test
    fun `AC2 - POST approve публикует cache invalidation event в Redis`() {
        // Given — создаём pending маршрут
        val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

        // Подписываемся на канал Redis для получения сообщения
        val receivedMessage = AtomicReference<String>()
        val subscription = redisTemplate.listenToChannel(RouteEventPublisher.ROUTE_CACHE_CHANNEL)
            .doOnNext { message ->
                receivedMessage.set(message.message)
            }
            .subscribe()

        try {
            // Небольшая задержка для установки подписки
            Thread.sleep(500)

            // When — одобряем маршрут
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/approve")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.status").isEqualTo("published")

            // Then — проверяем, что сообщение получено в Redis
            // Ждём до 2 секунд для получения сообщения
            val startTime = System.currentTimeMillis()
            while (receivedMessage.get() == null && System.currentTimeMillis() - startTime < 2000) {
                Thread.sleep(100)
            }

            assert(receivedMessage.get() == route.id.toString()) {
                "Redis должен получить route ID: ${route.id}, но получил: ${receivedMessage.get()}"
            }
        } finally {
            subscription.dispose()
        }
    }

    @Test
    fun `AC2 - cache invalidation event содержит правильный route ID`() {
        // Given
        val route1 = createTestRoute("/api/route1", developerUser.id!!, RouteStatus.PENDING)
        val route2 = createTestRoute("/api/route2", developerUser.id!!, RouteStatus.PENDING)

        val receivedMessages = mutableListOf<String>()
        val subscription = redisTemplate.listenToChannel(RouteEventPublisher.ROUTE_CACHE_CHANNEL)
            .doOnNext { message ->
                receivedMessages.add(message.message)
            }
            .subscribe()

        try {
            Thread.sleep(500)

            // When — одобряем оба маршрута
            webTestClient.post()
                .uri("/api/v1/routes/${route1.id}/approve")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk

            webTestClient.post()
                .uri("/api/v1/routes/${route2.id}/approve")
                .cookie("auth_token", securityToken)
                .exchange()
                .expectStatus().isOk

            // Then — ждём получения обоих сообщений
            val startTime = System.currentTimeMillis()
            while (receivedMessages.size < 2 && System.currentTimeMillis() - startTime < 3000) {
                Thread.sleep(100)
            }

            assert(receivedMessages.contains(route1.id.toString())) {
                "Redis должен получить route1 ID"
            }
            assert(receivedMessages.contains(route2.id.toString())) {
                "Redis должен получить route2 ID"
            }
        } finally {
            subscription.dispose()
        }
    }

    @Test
    fun `AC2 - reject НЕ публикует cache invalidation event`() {
        // Given — rejected маршрут не нужно инвалидировать, так как он не в кэше
        val route = createTestRoute("/api/orders", developerUser.id!!, RouteStatus.PENDING)

        val receivedMessages = mutableListOf<String>()
        val subscription = redisTemplate.listenToChannel(RouteEventPublisher.ROUTE_CACHE_CHANNEL)
            .doOnNext { message ->
                receivedMessages.add(message.message)
            }
            .subscribe()

        try {
            Thread.sleep(500)

            // When — отклоняем маршрут
            webTestClient.post()
                .uri("/api/v1/routes/${route.id}/reject")
                .cookie("auth_token", securityToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("""{"reason": "Security issue"}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.status").isEqualTo("rejected")

            // Then — ждём короткое время и проверяем, что сообщений нет
            Thread.sleep(500)

            assert(receivedMessages.isEmpty()) {
                "Redis НЕ должен получить сообщение при reject, но получил: $receivedMessages"
            }
        } finally {
            subscription.dispose()
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
}
