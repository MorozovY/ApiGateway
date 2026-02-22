package com.company.gateway.admin.service

import com.company.gateway.admin.dto.ServiceStatus
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Result
import io.r2dbc.spi.Statement
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.reactivestreams.Publisher
import org.springframework.data.redis.connection.ReactiveRedisConnection
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.net.ConnectException
import java.time.Duration

/**
 * Unit тесты для HealthService (Story 8.1, 10.5).
 *
 * Покрывает AC1 и AC2:
 * - AC1: Отображение статусов всех сервисов (UP/DOWN)
 * - AC2: Отображение DOWN для недоступных сервисов с деталями ошибки
 *
 * Использует MockWebServer для эмуляции HTTP endpoints (nginx, gateway-core, prometheus, grafana).
 */
class HealthServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var connectionFactory: ConnectionFactory
    private lateinit var redisTemplate: ReactiveStringRedisTemplate
    private lateinit var healthService: HealthService
    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        connectionFactory = mock()
        redisTemplate = mock()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    /**
     * Создаёт HealthService с настроенными моками и MockWebServer.
     */
    private fun createHealthService(
        redisTemplateToUse: ReactiveStringRedisTemplate? = redisTemplate
    ): HealthService {
        val baseUrl = mockWebServer.url("/").toString().dropLast(1) // Убираем trailing slash

        return HealthService(
            webClientBuilder = WebClient.builder(),
            connectionFactory = connectionFactory,
            redisTemplate = redisTemplateToUse,
            gatewayCoreUrl = baseUrl,
            prometheusUrl = baseUrl,
            grafanaUrl = baseUrl,
            nginxUrl = baseUrl,
            checkTimeout = Duration.ofSeconds(5)
        )
    }

    // ============================================
    // AC1: Все сервисы UP
    // ============================================

    @Nested
    inner class AC1_AllServicesUp {

        @Test
        fun `возвращает UP для Nginx когда health endpoint отвечает`() {
            // Given: nginx /nginx-health возвращает 200
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("healthy\n")
            )

            val healthService = createHealthService()

            // When & Then
            StepVerifier.create(healthService.checkNginx())
                .expectNextMatches { service ->
                    service.name == "nginx" &&
                        service.status == ServiceStatus.UP &&
                        service.details == null
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает UP для gateway-core когда actuator отвечает`() {
            // Given: gateway-core actuator возвращает UP
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(objectMapper.writeValueAsString(mapOf("status" to "UP")))
            )

            val healthService = createHealthService()

            // When & Then
            StepVerifier.create(healthService.checkGatewayCore())
                .expectNextMatches { service ->
                    service.name == "gateway-core" &&
                        service.status == ServiceStatus.UP &&
                        service.details == null
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает UP для gateway-admin всегда`() {
            // Given
            val healthService = createHealthService()

            // When & Then
            StepVerifier.create(healthService.checkGatewayAdmin())
                .expectNextMatches { service ->
                    service.name == "gateway-admin" &&
                        service.status == ServiceStatus.UP &&
                        service.details == null
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает UP для PostgreSQL когда SELECT 1 успешен`() {
            // Given
            val healthService = createHealthService()

            val connection = mock<Connection>()
            val statement = mock<Statement>()
            val result = mock<Result>()

            @Suppress("UNCHECKED_CAST")
            whenever(connectionFactory.create()).thenReturn(Mono.just(connection) as Publisher<Connection>)
            whenever(connection.createStatement("SELECT 1")).thenReturn(statement)
            @Suppress("UNCHECKED_CAST")
            whenever(statement.execute()).thenReturn(Mono.just(result) as Publisher<Result>)
            @Suppress("UNCHECKED_CAST")
            whenever(connection.close()).thenReturn(Mono.empty<Void>() as Publisher<Void>)

            // When & Then
            StepVerifier.create(healthService.checkPostgresql())
                .expectNextMatches { service ->
                    service.name == "postgresql" &&
                        service.status == ServiceStatus.UP &&
                        service.details == null
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает UP для Redis когда PING успешен`() {
            // Given
            val redisConnectionFactory = mock<ReactiveRedisConnectionFactory>()
            val redisConnection = mock<ReactiveRedisConnection>()

            whenever(redisTemplate.connectionFactory).thenReturn(redisConnectionFactory)
            whenever(redisConnectionFactory.reactiveConnection).thenReturn(redisConnection)
            whenever(redisConnection.ping()).thenReturn(Mono.just("PONG"))

            val healthService = createHealthService()

            // When & Then
            StepVerifier.create(healthService.checkRedis())
                .expectNextMatches { service ->
                    service.name == "redis" &&
                        service.status == ServiceStatus.UP &&
                        service.details == null
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает UP для Prometheus когда healthy endpoint отвечает`() {
            // Given
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("Prometheus Server is Ready.")
            )

            val healthService = createHealthService()

            // When & Then
            StepVerifier.create(healthService.checkPrometheus())
                .expectNextMatches { service ->
                    service.name == "prometheus" &&
                        service.status == ServiceStatus.UP &&
                        service.details == null
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает UP для Grafana когда health endpoint отвечает ok`() {
            // Given
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(objectMapper.writeValueAsString(mapOf("database" to "ok")))
            )

            val healthService = createHealthService()

            // When & Then
            StepVerifier.create(healthService.checkGrafana())
                .expectNextMatches { service ->
                    service.name == "grafana" &&
                        service.status == ServiceStatus.UP &&
                        service.details == null
                }
                .verifyComplete()
        }
    }

    // ============================================
    // AC2: Сервисы DOWN с error details
    // ============================================

    @Nested
    inner class AC2_ServiceDown {

        @Test
        fun `возвращает DOWN для Nginx когда сервер недоступен`() {
            // Given: MockWebServer закрыт — соединение будет отклонено
            mockWebServer.shutdown()

            val healthService = createHealthService()

            // When & Then
            StepVerifier.create(healthService.checkNginx())
                .expectNextMatches { service ->
                    service.name == "nginx" &&
                        service.status == ServiceStatus.DOWN &&
                        service.details != null
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает DOWN для gateway-core когда actuator возвращает status DOWN`() {
            // Given
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(objectMapper.writeValueAsString(mapOf("status" to "DOWN")))
            )

            val healthService = createHealthService()

            // When & Then
            StepVerifier.create(healthService.checkGatewayCore())
                .expectNextMatches { service ->
                    service.name == "gateway-core" &&
                        service.status == ServiceStatus.DOWN &&
                        service.details?.contains("DOWN") == true
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает DOWN для gateway-core когда сервер недоступен`() {
            // Given: MockWebServer закрыт — соединение будет отклонено
            mockWebServer.shutdown()

            val healthService = createHealthService()

            // When & Then
            StepVerifier.create(healthService.checkGatewayCore())
                .expectNextMatches { service ->
                    service.name == "gateway-core" &&
                        service.status == ServiceStatus.DOWN &&
                        service.details != null
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает DOWN для PostgreSQL когда connection fails`() {
            // Given
            val healthService = createHealthService()

            @Suppress("UNCHECKED_CAST")
            whenever(connectionFactory.create()).thenReturn(
                Mono.error<Connection>(ConnectException("Connection refused"))
                    as Publisher<Connection>
            )

            // When & Then
            StepVerifier.create(healthService.checkPostgresql())
                .expectNextMatches { service ->
                    service.name == "postgresql" &&
                        service.status == ServiceStatus.DOWN &&
                        service.details == "Connection refused"
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает DOWN для Redis когда PING fails`() {
            // Given
            val redisConnectionFactory = mock<ReactiveRedisConnectionFactory>()
            val redisConnection = mock<ReactiveRedisConnection>()

            whenever(redisTemplate.connectionFactory).thenReturn(redisConnectionFactory)
            whenever(redisConnectionFactory.reactiveConnection).thenReturn(redisConnection)
            whenever(redisConnection.ping()).thenReturn(
                Mono.error(ConnectException("Connection refused"))
            )

            val healthService = createHealthService()

            // When & Then
            StepVerifier.create(healthService.checkRedis())
                .expectNextMatches { service ->
                    service.name == "redis" &&
                        service.status == ServiceStatus.DOWN &&
                        service.details == "Connection refused"
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает DOWN для Redis когда template не настроен`() {
            // Given: Redis не настроен (redisTemplate = null)
            val healthService = createHealthService(redisTemplateToUse = null)

            // When & Then
            StepVerifier.create(healthService.checkRedis())
                .expectNextMatches { service ->
                    service.name == "redis" &&
                        service.status == ServiceStatus.DOWN &&
                        service.details == "Redis not configured"
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает DOWN для Prometheus когда сервер недоступен`() {
            // Given: MockWebServer закрыт
            mockWebServer.shutdown()

            val healthService = createHealthService()

            // When & Then
            StepVerifier.create(healthService.checkPrometheus())
                .expectNextMatches { service ->
                    service.name == "prometheus" &&
                        service.status == ServiceStatus.DOWN &&
                        service.details != null
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает DOWN для Grafana когда сервер недоступен`() {
            // Given: MockWebServer закрыт
            mockWebServer.shutdown()

            val healthService = createHealthService()

            // When & Then
            StepVerifier.create(healthService.checkGrafana())
                .expectNextMatches { service ->
                    service.name == "grafana" &&
                        service.status == ServiceStatus.DOWN &&
                        service.details != null
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает DOWN для Grafana когда database не ok`() {
            // Given
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(objectMapper.writeValueAsString(mapOf("database" to "error")))
            )

            val healthService = createHealthService()

            // When & Then
            StepVerifier.create(healthService.checkGrafana())
                .expectNextMatches { service ->
                    service.name == "grafana" &&
                        service.status == ServiceStatus.DOWN &&
                        service.details?.contains("error") == true
                }
                .verifyComplete()
        }
    }

    // ============================================
    // Агрегированный запрос getServicesHealth
    // ============================================

    @Nested
    inner class GetServicesHealth {

        @Test
        fun `возвращает HealthResponse со всеми сервисами`() {
            // Given: gateway-admin = UP, всё остальное = DOWN (MockWebServer закрыт)
            mockWebServer.shutdown()

            val healthService = createHealthService(redisTemplateToUse = null)

            // PostgreSQL: connection refused
            @Suppress("UNCHECKED_CAST")
            whenever(connectionFactory.create()).thenReturn(
                Mono.error<Connection>(ConnectException("Connection refused"))
                    as Publisher<Connection>
            )

            // When & Then: ожидаем 7 сервисов (nginx + 4 из AC + prometheus + grafana)
            StepVerifier.create(healthService.getServicesHealth())
                .expectNextMatches { response ->
                    response.services.size == 7 &&
                        response.services.any { it.name == "nginx" && it.status == ServiceStatus.DOWN } &&
                        response.services.any { it.name == "gateway-core" && it.status == ServiceStatus.DOWN } &&
                        response.services.any { it.name == "gateway-admin" && it.status == ServiceStatus.UP } &&
                        response.services.any { it.name == "postgresql" && it.status == ServiceStatus.DOWN } &&
                        response.services.any { it.name == "redis" && it.status == ServiceStatus.DOWN } &&
                        response.services.any { it.name == "prometheus" && it.status == ServiceStatus.DOWN } &&
                        response.services.any { it.name == "grafana" && it.status == ServiceStatus.DOWN } &&
                        response.timestamp != null
                }
                .verifyComplete()
        }
    }
}
