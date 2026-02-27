package com.company.gateway.admin.controller

import com.company.gateway.admin.dto.ConsumerListResponse
import com.company.gateway.admin.dto.ConsumerResponse
import com.company.gateway.admin.dto.RotateSecretResponse
import com.company.gateway.admin.service.ConsumerRateLimitService
import com.company.gateway.admin.service.ConsumerService
import com.company.gateway.admin.service.KeycloakAdminService
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.redis.testcontainers.RedisContainer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import reactor.core.publisher.Mono
import java.util.Date
import java.util.UUID

/**
 * Security тесты для ConsumerController — проверка @RequireRole(Role.ADMIN).
 *
 * Story 12.9, Task 3.8, Issue H1 (Code Review).
 * Story 13.2: Исправлен порядок инициализации MockWebServer.
 *
 * Проверяем что:
 * - ADMIN получает 200 OK
 * - DEVELOPER получает 403 Forbidden
 * - SECURITY получает 403 Forbidden
 * - Нет токена — 401 Unauthorized
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["keycloak.enabled=true"]
)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
class ConsumerControllerSecurityTest {

    companion object {
        private val isTestcontainersDisabled = System.getenv("TESTCONTAINERS_DISABLED") == "true"

        // Генерируем RSA ключ для подписи JWT (до инициализации MockWebServer)
        private val rsaKey: RSAKey = RSAKeyGenerator(2048)
            .keyID("test-key-id")
            .generate()

        // Контейнеры — запускаются только локально
        private var postgres: PostgreSQLContainer<*>? = null
        private var redis: RedisContainer? = null

        init {
            if (!isTestcontainersDisabled) {
                postgres = PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("testdb")
                    .withUsername("gateway")
                    .withPassword("gateway")
                    .apply { start() }

                redis = RedisContainer("redis:7-alpine")
                    .apply { start() }
            }
        }

        // MockWebServer инициализируется СРАЗУ в companion object с Dispatcher
        // ВАЖНО: это СТАТИЧЕСКАЯ инициализация которая происходит ДО DynamicPropertySource
        private val mockWebServer: MockWebServer = run {
            val server = MockWebServer()
            val jwkSet = JWKSet(rsaKey.toPublicJWK())
            val jwksJson = jwkSet.toString()

            // Dispatcher возвращает JWKS для всех запросов
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(jwksJson)
                        .setResponseCode(200)
                }
            }
            server.start(0)
            server
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            if (isTestcontainersDisabled) {
                // CI режим — используем GitLab Services
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
                // Локальный режим — Testcontainers
                postgres?.let { pg ->
                    registry.add("spring.r2dbc.url") {
                        "r2dbc:postgresql://${pg.host}:${pg.getMappedPort(5432)}/${pg.databaseName}"
                    }
                    registry.add("spring.r2dbc.username") { pg.username }
                    registry.add("spring.r2dbc.password") { pg.password }
                    registry.add("spring.flyway.url") { pg.jdbcUrl }
                    registry.add("spring.flyway.user") { pg.username }
                    registry.add("spring.flyway.password") { pg.password }
                }
                redis?.let { rd ->
                    registry.add("spring.data.redis.host") { rd.host }
                    registry.add("spring.data.redis.port") { rd.getMappedPort(6379) }
                }
            }

            // Keycloak - MockWebServer уже запущен
            registry.add("keycloak.url") { mockWebServer.url("/").toString().removeSuffix("/") }
            registry.add("keycloak.realm") { "api-gateway" }
            registry.add("keycloak.client-id") { "admin-ui" }
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var consumerService: ConsumerService

    @MockBean
    private lateinit var keycloakAdminService: KeycloakAdminService

    @MockBean
    private lateinit var consumerRateLimitService: ConsumerRateLimitService

    private lateinit var adminToken: String
    private lateinit var developerToken: String
    private lateinit var securityToken: String

    @BeforeEach
    fun setUp() {
        // Генерируем JWT токены с разными ролями
        adminToken = createSignedJwt(
            username = "admin-test",
            email = "admin@test.com",
            roles = listOf("admin-ui:admin")
        )

        developerToken = createSignedJwt(
            username = "dev-test",
            email = "dev@test.com",
            roles = listOf("admin-ui:developer")
        )

        securityToken = createSignedJwt(
            username = "security-test",
            email = "security@test.com",
            roles = listOf("admin-ui:security")
        )

        // Mock ConsumerService
        whenever(consumerService.listConsumers(any(), any(), anyOrNull()))
            .thenReturn(Mono.just(ConsumerListResponse(items = emptyList(), total = 0)))

        whenever(consumerService.getConsumer(any()))
            .thenReturn(Mono.just(
                ConsumerResponse(
                    clientId = "test-consumer",
                    description = "Test",
                    enabled = true,
                    createdTimestamp = 1000L,
                    rateLimit = null
                )
            ))

        whenever(consumerService.disableConsumer(any())).thenReturn(Mono.empty())
        whenever(consumerService.enableConsumer(any())).thenReturn(Mono.empty())
        whenever(consumerService.rotateSecret(any())).thenReturn(
            Mono.just(RotateSecretResponse(clientId = "test-consumer", secret = "new-secret"))
        )
    }

    /**
     * Создаёт подписанный JWT токен с Keycloak claims для тестирования.
     */
    private fun createSignedJwt(
        subject: String = UUID.randomUUID().toString(),
        username: String = "testuser",
        email: String = "test@example.com",
        roles: List<String> = listOf("admin-ui:developer"),
        expirationTime: Date = Date(System.currentTimeMillis() + 3600000)
    ): String {
        val issuer = mockWebServer.url("/realms/api-gateway").toString()

        val claims = JWTClaimsSet.Builder()
            .subject(subject)
            .claim("preferred_username", username)
            .claim("email", email)
            .claim("realm_access", mapOf("roles" to roles))
            .issuer(issuer)
            .issueTime(Date())
            .expirationTime(expirationTime)
            .build()

        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(rsaKey.keyID)
            .build()

        val signedJwt = SignedJWT(header, claims)
        signedJwt.sign(RSASSASigner(rsaKey))

        return signedJwt.serialize()
    }

    // ============ GET /api/v1/consumers ============

    @Test
    fun `GET consumers без токена возвращает 401 Unauthorized`() {
        webTestClient.get()
            .uri("/api/v1/consumers")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `GET consumers с ADMIN токеном возвращает 200 OK`() {
        webTestClient.get()
            .uri("/api/v1/consumers")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `GET consumers с DEVELOPER токеном возвращает 403 Forbidden`() {
        webTestClient.get()
            .uri("/api/v1/consumers")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $developerToken")
            .exchange()
            .expectStatus().isForbidden
    }

    @Test
    fun `GET consumers с SECURITY токеном возвращает 403 Forbidden`() {
        webTestClient.get()
            .uri("/api/v1/consumers")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $securityToken")
            .exchange()
            .expectStatus().isForbidden
    }

    // ============ GET /api/v1/consumers/{clientId} ============

    @Test
    fun `GET consumer по ID с ADMIN токеном возвращает 200 OK`() {
        webTestClient.get()
            .uri("/api/v1/consumers/test-consumer")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `GET consumer по ID с DEVELOPER токеном возвращает 403 Forbidden`() {
        webTestClient.get()
            .uri("/api/v1/consumers/test-consumer")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $developerToken")
            .exchange()
            .expectStatus().isForbidden
    }

    // ============ POST /api/v1/consumers/{clientId}/disable ============

    @Test
    fun `POST disable consumer с DEVELOPER токеном возвращает 403 Forbidden`() {
        webTestClient.post()
            .uri("/api/v1/consumers/test-consumer/disable")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $developerToken")
            .exchange()
            .expectStatus().isForbidden
    }

    // ============ POST /api/v1/consumers/{clientId}/enable ============

    @Test
    fun `POST enable consumer с SECURITY токеном возвращает 403 Forbidden`() {
        webTestClient.post()
            .uri("/api/v1/consumers/test-consumer/enable")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $securityToken")
            .exchange()
            .expectStatus().isForbidden
    }

    // ============ POST /api/v1/consumers/{clientId}/rotate-secret ============

    @Test
    fun `POST rotate-secret с SECURITY токеном возвращает 403 Forbidden`() {
        webTestClient.post()
            .uri("/api/v1/consumers/test-consumer/rotate-secret")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $securityToken")
            .exchange()
            .expectStatus().isForbidden
    }
}
