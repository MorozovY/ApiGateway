package com.company.gateway.admin.service

import com.company.gateway.admin.dto.ConsumerRateLimitResponse
import com.company.gateway.admin.dto.PagedResponse
import com.company.gateway.admin.exception.NotFoundException
import com.company.gateway.admin.security.AuthenticatedUser
import com.company.gateway.admin.service.KeycloakAdminService.KeycloakClient
import com.company.gateway.admin.service.KeycloakAdminService.KeycloakClientWithSecret
import com.company.gateway.common.model.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * Unit тесты для ConsumerService (Story 12.9).
 *
 * Тестируем методы управления consumers:
 * - listConsumers() — список с пагинацией и search
 * - getConsumer() — получение одного
 * - createConsumer() — создание нового
 * - rotateSecret() — ротация secret
 * - disableConsumer() / enableConsumer() — управление статусом
 */
@ExtendWith(MockitoExtension::class)
class ConsumerServiceTest {

    @Mock
    private lateinit var keycloakAdminService: KeycloakAdminService

    @Mock
    private lateinit var consumerRateLimitService: ConsumerRateLimitService

    @Mock
    private lateinit var auditService: AuditService

    private lateinit var service: ConsumerService

    @BeforeEach
    fun setUp() {
        service = ConsumerService(keycloakAdminService, consumerRateLimitService, auditService)
    }

    // ============ AC1: listConsumers ============

    @Test
    fun `listConsumers возвращает список consumers с rate limits`() {
        // Arrange
        val clients = listOf(
            KeycloakClient(
                id = "uuid-1",
                clientId = "consumer-a",
                description = "Consumer A",
                enabled = true,
                serviceAccountsEnabled = true,
                createdTimestamp = 1000L
            ),
            KeycloakClient(
                id = "uuid-2",
                clientId = "consumer-b",
                description = "Consumer B",
                enabled = false,
                serviceAccountsEnabled = true,
                createdTimestamp = 2000L
            )
        )

        val rateLimit = ConsumerRateLimitResponse(
            id = UUID.randomUUID(),
            consumerId = "consumer-a",
            requestsPerSecond = 100,
            burstSize = 150,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            createdBy = null
        )

        val rateLimitsPage = PagedResponse(items = listOf(rateLimit), total = 1, offset = 0, limit = 10000)

        whenever(keycloakAdminService.listConsumers()).thenReturn(Mono.just(clients))
        whenever(consumerRateLimitService.listRateLimits(0, 10000)).thenReturn(Mono.just(rateLimitsPage))

        // Act
        val result = service.listConsumers(offset = 0, limit = 10, search = null)

        // Assert
        StepVerifier.create(result)
            .assertNext { response ->
                assertThat(response.total).isEqualTo(2)
                assertThat(response.items).hasSize(2)

                val consumerA = response.items.find { it.clientId == "consumer-a" }
                assertThat(consumerA).isNotNull
                assertThat(consumerA?.rateLimit).isNotNull
                assertThat(consumerA?.rateLimit?.consumerId).isEqualTo("consumer-a")

                val consumerB = response.items.find { it.clientId == "consumer-b" }
                assertThat(consumerB).isNotNull
                assertThat(consumerB?.rateLimit).isNull()
            }
            .verifyComplete()

        verify(keycloakAdminService).listConsumers()
        verify(consumerRateLimitService).listRateLimits(0, 10000)
    }

    @Test
    fun `listConsumers фильтрует по search параметру (case-insensitive)`() {
        // Arrange
        val clients = listOf(
            KeycloakClient("uuid-1", "consumer-alpha", null, true, true, 1000L),
            KeycloakClient("uuid-2", "consumer-beta", null, true, true, 2000L),
            KeycloakClient("uuid-3", "partner-gamma", null, true, true, 3000L)
        )

        val emptyRateLimits = PagedResponse<ConsumerRateLimitResponse>(items = emptyList(), total = 0, offset = 0, limit = 10000)

        whenever(keycloakAdminService.listConsumers()).thenReturn(Mono.just(clients))
        whenever(consumerRateLimitService.listRateLimits(0, 10000)).thenReturn(Mono.just(emptyRateLimits))

        // Act
        val result = service.listConsumers(offset = 0, limit = 10, search = "consumer")

        // Assert
        StepVerifier.create(result)
            .assertNext { response ->
                assertThat(response.total).isEqualTo(2)
                assertThat(response.items).hasSize(2)
                assertThat(response.items.map { it.clientId }).containsExactlyInAnyOrder("consumer-alpha", "consumer-beta")
            }
            .verifyComplete()
    }

    @Test
    fun `listConsumers игнорирует пустую search строку`() {
        // Arrange
        val clients = listOf(
            KeycloakClient("uuid-1", "consumer-alpha", null, true, true, 1000L),
            KeycloakClient("uuid-2", "consumer-beta", null, true, true, 2000L)
        )

        val emptyRateLimits = PagedResponse<ConsumerRateLimitResponse>(items = emptyList(), total = 0, offset = 0, limit = 10000)

        whenever(keycloakAdminService.listConsumers()).thenReturn(Mono.just(clients))
        whenever(consumerRateLimitService.listRateLimits(0, 10000)).thenReturn(Mono.just(emptyRateLimits))

        // Act — пустая search строка и whitespace-only
        val result1 = service.listConsumers(offset = 0, limit = 10, search = "")
        val result2 = service.listConsumers(offset = 0, limit = 10, search = "   ")

        // Assert — должны вернуться все клиенты
        StepVerifier.create(result1)
            .assertNext { response ->
                assertThat(response.total).isEqualTo(2)
                assertThat(response.items).hasSize(2)
            }
            .verifyComplete()

        StepVerifier.create(result2)
            .assertNext { response ->
                assertThat(response.total).isEqualTo(2)
                assertThat(response.items).hasSize(2)
            }
            .verifyComplete()
    }

    @Test
    fun `listConsumers применяет пагинацию`() {
        // Arrange
        val clients = (1..5).map {
            KeycloakClient("uuid-$it", "consumer-$it", null, true, true, it * 1000L)
        }

        val emptyRateLimits = PagedResponse<ConsumerRateLimitResponse>(items = emptyList(), total = 0, offset = 0, limit = 10000)

        whenever(keycloakAdminService.listConsumers()).thenReturn(Mono.just(clients))
        whenever(consumerRateLimitService.listRateLimits(0, 10000)).thenReturn(Mono.just(emptyRateLimits))

        // Act — offset=2, limit=2 (пропустить 2, взять 2)
        val result = service.listConsumers(offset = 2, limit = 2, search = null)

        // Assert
        StepVerifier.create(result)
            .assertNext { response ->
                assertThat(response.total).isEqualTo(5)
                assertThat(response.items).hasSize(2)
                assertThat(response.items.map { it.clientId }).containsExactly("consumer-3", "consumer-4")
            }
            .verifyComplete()
    }

    // ============ AC2: getConsumer ============

    @Test
    fun `getConsumer возвращает consumer с rate limit`() {
        // Arrange
        val client = KeycloakClient("uuid-1", "test-consumer", "Test", true, true, 1000L)
        val rateLimit = ConsumerRateLimitResponse(
            id = UUID.randomUUID(),
            consumerId = "test-consumer",
            requestsPerSecond = 100,
            burstSize = 150,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            createdBy = null
        )

        whenever(keycloakAdminService.getConsumer("test-consumer")).thenReturn(Mono.just(client))
        whenever(consumerRateLimitService.getRateLimit("test-consumer")).thenReturn(Mono.just(rateLimit))

        // Act
        val result = service.getConsumer("test-consumer")

        // Assert
        StepVerifier.create(result)
            .assertNext { consumer ->
                assertThat(consumer.clientId).isEqualTo("test-consumer")
                assertThat(consumer.rateLimit).isNotNull
                assertThat(consumer.rateLimit?.requestsPerSecond).isEqualTo(100)
            }
            .verifyComplete()
    }

    @Test
    fun `getConsumer возвращает consumer без rate limit если его нет`() {
        // Arrange
        val client = KeycloakClient("uuid-1", "test-consumer", "Test", true, true, 1000L)

        whenever(keycloakAdminService.getConsumer("test-consumer")).thenReturn(Mono.just(client))
        whenever(consumerRateLimitService.getRateLimit("test-consumer"))
            .thenReturn(Mono.error(NotFoundException("Rate limit not found")))

        // Act
        val result = service.getConsumer("test-consumer")

        // Assert
        StepVerifier.create(result)
            .assertNext { consumer ->
                assertThat(consumer.clientId).isEqualTo("test-consumer")
                assertThat(consumer.rateLimit).isNull()
            }
            .verifyComplete()
    }

    @Test
    fun `getConsumer выбрасывает NotFoundException если consumer не найден`() {
        // Arrange
        whenever(keycloakAdminService.getConsumer("non-existent")).thenReturn(Mono.empty())

        // Act
        val result = service.getConsumer("non-existent")

        // Assert
        StepVerifier.create(result)
            .expectErrorMatches { error ->
                error is NotFoundException && error.message == "Consumer 'non-existent' not found"
            }
            .verify()
    }

    // ============ AC2: createConsumer ============

    @Test
    fun `createConsumer создаёт client в Keycloak и возвращает secret`() {
        // Arrange
        val testUser = AuthenticatedUser(UUID.randomUUID(), "admin-test", Role.ADMIN)
        val authentication = UsernamePasswordAuthenticationToken(testUser, null, testUser.authorities)

        val clientWithSecret = KeycloakClientWithSecret(
            id = "uuid-new",
            clientId = "new-consumer",
            description = "New Consumer",
            enabled = true,
            serviceAccountsEnabled = true,
            createdTimestamp = 1000L,
            secret = "super-secret-abc123"
        )

        whenever(keycloakAdminService.createConsumer("new-consumer", "New Consumer"))
            .thenReturn(Mono.just(clientWithSecret))

        // Act
        val result = service.createConsumer("new-consumer", "New Consumer")
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))

        // Assert
        StepVerifier.create(result)
            .assertNext { response ->
                assertThat(response.clientId).isEqualTo("new-consumer")
                assertThat(response.secret).isEqualTo("super-secret-abc123")
                assertThat(response.message).contains("Сохраните этот secret")
            }
            .verifyComplete()

        verify(keycloakAdminService).createConsumer("new-consumer", "New Consumer")
    }

    @Test
    fun `createConsumer возвращает ошибку если client уже существует (409 Conflict)`() {
        // Arrange
        val testUser = AuthenticatedUser(UUID.randomUUID(), "admin-test", Role.ADMIN)
        val authentication = UsernamePasswordAuthenticationToken(testUser, null, testUser.authorities)

        val conflictError = RuntimeException("Client already exists")

        whenever(keycloakAdminService.createConsumer("existing-consumer", null))
            .thenReturn(Mono.error(conflictError))

        // Act
        val result = service.createConsumer("existing-consumer", null)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))

        // Assert
        StepVerifier.create(result)
            .expectError(RuntimeException::class.java)
            .verify()

        verify(keycloakAdminService).createConsumer("existing-consumer", null)
    }

    // ============ AC3: rotateSecret ============

    @Test
    fun `rotateSecret генерирует новый secret`() {
        // Arrange
        val testUser = AuthenticatedUser(UUID.randomUUID(), "admin-test", Role.ADMIN)
        val authentication = UsernamePasswordAuthenticationToken(testUser, null, testUser.authorities)

        whenever(keycloakAdminService.rotateSecret("test-consumer"))
            .thenReturn(Mono.just("new-secret-xyz789"))

        // Act
        val result = service.rotateSecret("test-consumer")
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))

        // Assert
        StepVerifier.create(result)
            .assertNext { response ->
                assertThat(response.clientId).isEqualTo("test-consumer")
                assertThat(response.secret).isEqualTo("new-secret-xyz789")
                assertThat(response.message).contains("Старый secret более недействителен")
            }
            .verifyComplete()

        verify(keycloakAdminService).rotateSecret("test-consumer")
    }

    // ============ AC4, AC5: disable/enable Consumer ============

    @Test
    fun `disableConsumer вызывает Keycloak Admin API`() {
        // Arrange
        whenever(keycloakAdminService.disableConsumer("test-consumer")).thenReturn(Mono.empty())

        // Act
        val result = service.disableConsumer("test-consumer")

        // Assert
        StepVerifier.create(result)
            .verifyComplete()

        verify(keycloakAdminService).disableConsumer("test-consumer")
    }

    @Test
    fun `enableConsumer вызывает Keycloak Admin API`() {
        // Arrange
        whenever(keycloakAdminService.enableConsumer("test-consumer")).thenReturn(Mono.empty())

        // Act
        val result = service.enableConsumer("test-consumer")

        // Assert
        StepVerifier.create(result)
            .verifyComplete()

        verify(keycloakAdminService).enableConsumer("test-consumer")
    }
}
