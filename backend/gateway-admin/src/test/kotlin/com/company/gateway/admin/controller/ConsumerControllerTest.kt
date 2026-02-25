package com.company.gateway.admin.controller

import com.company.gateway.admin.dto.ConsumerListResponse
import com.company.gateway.admin.dto.ConsumerResponse
import com.company.gateway.admin.dto.CreateConsumerResponse
import com.company.gateway.admin.dto.RotateSecretResponse
import com.company.gateway.admin.service.ConsumerService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/**
 * Unit тесты для ConsumerController (Story 12.9).
 *
 * Тестируем логику контроллера без реального Keycloak.
 */
class ConsumerControllerTest {

    private lateinit var consumerService: ConsumerService
    private lateinit var consumerController: ConsumerController

    @BeforeEach
    fun setUp() {
        consumerService = mock()
        consumerController = ConsumerController(consumerService)
    }

    // ============ AC1: listConsumers ============

    @Test
    fun `listConsumers возвращает пагинированный список`() {
        // Arrange
        val consumerA = ConsumerResponse("consumer-a", "Consumer A", true, 1000L, null)
        val consumerB = ConsumerResponse("consumer-b", "Consumer B", false, 2000L, null)
        val response = ConsumerListResponse(items = listOf(consumerA, consumerB), total = 2)

        whenever(consumerService.listConsumers(0, 100, null)).thenReturn(Mono.just(response))

        // Act
        val result = consumerController.listConsumers(offset = 0, limit = 100, search = null)

        // Assert
        StepVerifier.create(result)
            .expectNextMatches { it.total == 2 && it.items.size == 2 }
            .verifyComplete()

        verify(consumerService).listConsumers(0, 100, null)
    }

    @Test
    fun `listConsumers с search параметром`() {
        // Arrange
        val consumerA = ConsumerResponse("consumer-alpha", null, true, 1000L, null)
        val response = ConsumerListResponse(items = listOf(consumerA), total = 1)

        whenever(consumerService.listConsumers(0, 100, "consumer")).thenReturn(Mono.just(response))

        // Act
        val result = consumerController.listConsumers(0, 100, "consumer")

        // Assert
        StepVerifier.create(result)
            .expectNextMatches { it.total == 1 && it.items[0].clientId == "consumer-alpha" }
            .verifyComplete()

        verify(consumerService).listConsumers(0, 100, "consumer")
    }

    // ============ AC2: getConsumer ============

    @Test
    fun `getConsumer возвращает данные consumer`() {
        // Arrange
        val consumer = ConsumerResponse("test-consumer", "Test", true, 1000L, null)
        whenever(consumerService.getConsumer("test-consumer")).thenReturn(Mono.just(consumer))

        // Act
        val result = consumerController.getConsumer("test-consumer")

        // Assert
        StepVerifier.create(result)
            .expectNextMatches { it.clientId == "test-consumer" && it.description == "Test" }
            .verifyComplete()

        verify(consumerService).getConsumer("test-consumer")
    }

    // ============ AC2: createConsumer ============

    @Test
    fun `createConsumer создаёт consumer и возвращает secret`() {
        // Arrange
        val createRequest = com.company.gateway.admin.dto.CreateConsumerRequest("new-consumer", "New Consumer")
        val response = CreateConsumerResponse("new-consumer", "secret-abc123")
        whenever(consumerService.createConsumer(eq("new-consumer"), eq("New Consumer")))
            .thenReturn(Mono.just(response))

        // Act
        val result = consumerController.createConsumer(createRequest)

        // Assert
        StepVerifier.create(result)
            .expectNextMatches { it.clientId == "new-consumer" && it.secret == "secret-abc123" }
            .verifyComplete()

        verify(consumerService).createConsumer("new-consumer", "New Consumer")
    }

    // ============ AC3: rotateSecret ============

    @Test
    fun `rotateSecret генерирует новый secret`() {
        // Arrange
        val response = RotateSecretResponse("test-consumer", "new-secret-xyz789")
        whenever(consumerService.rotateSecret("test-consumer")).thenReturn(Mono.just(response))

        // Act
        val result = consumerController.rotateSecret("test-consumer")

        // Assert
        StepVerifier.create(result)
            .expectNextMatches { it.clientId == "test-consumer" && it.secret == "new-secret-xyz789" }
            .verifyComplete()

        verify(consumerService).rotateSecret("test-consumer")
    }

    // ============ AC4, AC5: disable/enable ============

    @Test
    fun `disableConsumer деактивирует consumer`() {
        // Arrange
        whenever(consumerService.disableConsumer("test-consumer")).thenReturn(Mono.empty())

        // Act
        val result = consumerController.disableConsumer("test-consumer")

        // Assert
        StepVerifier.create(result)
            .verifyComplete()

        verify(consumerService).disableConsumer("test-consumer")
    }

    @Test
    fun `enableConsumer активирует consumer`() {
        // Arrange
        whenever(consumerService.enableConsumer("test-consumer")).thenReturn(Mono.empty())

        // Act
        val result = consumerController.enableConsumer("test-consumer")

        // Assert
        StepVerifier.create(result)
            .verifyComplete()

        verify(consumerService).enableConsumer("test-consumer")
    }
}
