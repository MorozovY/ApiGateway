package com.company.gateway.admin.controller

import com.company.gateway.admin.dto.HealthResponse
import com.company.gateway.admin.dto.ServiceHealthDto
import com.company.gateway.admin.dto.ServiceStatus
import com.company.gateway.admin.service.HealthService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

/**
 * Unit тесты для HealthController (Story 8.1).
 *
 * Проверяет, что контроллер правильно делегирует вызовы HealthService
 * и возвращает корректную структуру ответа.
 */
class HealthControllerTest {

    private lateinit var healthService: HealthService
    private lateinit var healthController: HealthController

    @BeforeEach
    fun setUp() {
        healthService = mock()
        healthController = HealthController(healthService)
    }

    @Test
    fun `возвращает статусы всех сервисов из HealthService`() {
        // Given
        val timestamp = Instant.now()
        val services = listOf(
            ServiceHealthDto("gateway-core", ServiceStatus.UP, timestamp, null),
            ServiceHealthDto("gateway-admin", ServiceStatus.UP, timestamp, null),
            ServiceHealthDto("postgresql", ServiceStatus.UP, timestamp, null),
            ServiceHealthDto("redis", ServiceStatus.UP, timestamp, null)
        )
        val healthResponse = HealthResponse(services, timestamp)

        whenever(healthService.getServicesHealth()).thenReturn(Mono.just(healthResponse))

        // When & Then
        StepVerifier.create(healthController.getServicesHealth())
            .expectNextMatches { response ->
                response.services.size == 4 &&
                    response.services.all { it.status == ServiceStatus.UP } &&
                    response.timestamp == timestamp
            }
            .verifyComplete()
    }

    @Test
    fun `возвращает DOWN статусы когда сервисы недоступны`() {
        // Given
        val timestamp = Instant.now()
        val services = listOf(
            ServiceHealthDto("gateway-core", ServiceStatus.DOWN, timestamp, "Connection refused"),
            ServiceHealthDto("gateway-admin", ServiceStatus.UP, timestamp, null),
            ServiceHealthDto("postgresql", ServiceStatus.DOWN, timestamp, "Connection timeout"),
            ServiceHealthDto("redis", ServiceStatus.UP, timestamp, null)
        )
        val healthResponse = HealthResponse(services, timestamp)

        whenever(healthService.getServicesHealth()).thenReturn(Mono.just(healthResponse))

        // When & Then
        StepVerifier.create(healthController.getServicesHealth())
            .expectNextMatches { response ->
                response.services.size == 4 &&
                    response.services.count { it.status == ServiceStatus.DOWN } == 2 &&
                    response.services.find { it.name == "gateway-core" }?.details == "Connection refused" &&
                    response.services.find { it.name == "postgresql" }?.details == "Connection timeout"
            }
            .verifyComplete()
    }

    @Test
    fun `возвращает правильную JSON структуру для API response`() {
        // Given
        val timestamp = Instant.parse("2026-02-21T10:30:00Z")
        val services = listOf(
            ServiceHealthDto("gateway-core", ServiceStatus.UP, timestamp, null),
            ServiceHealthDto("gateway-admin", ServiceStatus.UP, timestamp, null),
            ServiceHealthDto("postgresql", ServiceStatus.DOWN, timestamp, "Connection refused"),
            ServiceHealthDto("redis", ServiceStatus.UP, timestamp, null)
        )
        val healthResponse = HealthResponse(services, timestamp)

        whenever(healthService.getServicesHealth()).thenReturn(Mono.just(healthResponse))

        // When & Then
        StepVerifier.create(healthController.getServicesHealth())
            .expectNextMatches { response ->
                // Проверяем структуру как в Dev Notes Story 8.1
                response.services.any { it.name == "postgresql" && it.status == ServiceStatus.DOWN && it.details == "Connection refused" } &&
                    response.services.all { it.lastCheck == timestamp } &&
                    response.timestamp == timestamp
            }
            .verifyComplete()
    }
}
