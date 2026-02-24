package com.company.gateway.admin.service

import com.company.gateway.admin.dto.ConsumerRateLimitRequest
import com.company.gateway.admin.exception.NotFoundException
import com.company.gateway.admin.exception.ValidationException
import com.company.gateway.admin.publisher.ConsumerRateLimitEventPublisher
import com.company.gateway.admin.repository.ConsumerRateLimitRepository
import com.company.gateway.admin.repository.UserRepository
import com.company.gateway.common.model.ConsumerRateLimit
import com.company.gateway.common.model.Role
import com.company.gateway.common.model.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * Unit тесты для ConsumerRateLimitService (Story 12.8)
 *
 * Тесты CRUD операций:
 * - AC2: setRateLimit — create or update
 * - AC6: getRateLimit — получение по consumer ID
 * - AC7: deleteRateLimit — удаление
 * - AC8: listRateLimits — пагинированный список
 */
@ExtendWith(MockitoExtension::class)
class ConsumerRateLimitServiceTest {

    @Mock
    private lateinit var consumerRateLimitRepository: ConsumerRateLimitRepository

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var auditService: AuditService

    @Mock
    private lateinit var eventPublisher: ConsumerRateLimitEventPublisher

    private lateinit var service: ConsumerRateLimitService

    private val testUserId = UUID.randomUUID()
    private val testUsername = "admin"
    private val testConsumerId = "test-consumer"

    @BeforeEach
    fun setUp() {
        service = ConsumerRateLimitService(
            consumerRateLimitRepository,
            userRepository,
            auditService,
            eventPublisher
        )
    }

    // ============ AC2: setRateLimit ============

    @Test
    fun `setRateLimit создаёт новый rate limit когда он не существует`() {
        // Arrange
        val request = ConsumerRateLimitRequest(requestsPerSecond = 100, burstSize = 150)
        val savedEntity = ConsumerRateLimit(
            id = UUID.randomUUID(),
            consumerId = testConsumerId,
            requestsPerSecond = 100,
            burstSize = 150,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            createdBy = testUserId
        )
        val testUser = User(
            id = testUserId,
            username = testUsername,
            email = "admin@example.com",
            passwordHash = "",
            role = Role.ADMIN
        )

        whenever(consumerRateLimitRepository.findByConsumerId(testConsumerId))
            .thenReturn(Mono.empty())
        whenever(consumerRateLimitRepository.save(any<ConsumerRateLimit>()))
            .thenReturn(Mono.just(savedEntity))
        whenever(auditService.logCreated(any(), any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(Mono.empty())
        whenever(eventPublisher.publishConsumerRateLimitChanged(testConsumerId))
            .thenReturn(Mono.just(1L))
        whenever(userRepository.findById(testUserId))
            .thenReturn(Mono.just(testUser))

        // Act & Assert
        StepVerifier.create(service.setRateLimit(testConsumerId, request, testUserId, testUsername))
            .assertNext { response ->
                assertThat(response.consumerId).isEqualTo(testConsumerId)
                assertThat(response.requestsPerSecond).isEqualTo(100)
                assertThat(response.burstSize).isEqualTo(150)
                assertThat(response.createdBy?.username).isEqualTo(testUsername)
            }
            .verifyComplete()

        verify(consumerRateLimitRepository).save(any())
        verify(eventPublisher).publishConsumerRateLimitChanged(testConsumerId)
    }

    @Test
    fun `setRateLimit обновляет существующий rate limit`() {
        // Arrange
        val existingEntity = ConsumerRateLimit(
            id = UUID.randomUUID(),
            consumerId = testConsumerId,
            requestsPerSecond = 50,
            burstSize = 75,
            createdAt = Instant.now().minusSeconds(3600),
            updatedAt = Instant.now().minusSeconds(3600),
            createdBy = testUserId
        )
        val request = ConsumerRateLimitRequest(requestsPerSecond = 100, burstSize = 150)
        val testUser = User(
            id = testUserId,
            username = testUsername,
            email = "admin@example.com",
            passwordHash = "",
            role = Role.ADMIN
        )

        whenever(consumerRateLimitRepository.findByConsumerId(testConsumerId))
            .thenReturn(Mono.just(existingEntity))
        whenever(consumerRateLimitRepository.save(any<ConsumerRateLimit>()))
            .thenAnswer { invocation ->
                val entity = invocation.getArgument<ConsumerRateLimit>(0)
                Mono.just(entity.copy(updatedAt = Instant.now()))
            }
        whenever(auditService.logUpdated(any(), any(), any(), any(), any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(Mono.empty())
        whenever(eventPublisher.publishConsumerRateLimitChanged(testConsumerId))
            .thenReturn(Mono.just(1L))
        whenever(userRepository.findById(testUserId))
            .thenReturn(Mono.just(testUser))

        // Act & Assert
        StepVerifier.create(service.setRateLimit(testConsumerId, request, testUserId, testUsername))
            .assertNext { response ->
                assertThat(response.consumerId).isEqualTo(testConsumerId)
                assertThat(response.requestsPerSecond).isEqualTo(100)
                assertThat(response.burstSize).isEqualTo(150)
            }
            .verifyComplete()

        verify(consumerRateLimitRepository).save(argThat<ConsumerRateLimit> { rateLimit ->
            rateLimit.requestsPerSecond == 100 && rateLimit.burstSize == 150
        })
        verify(eventPublisher).publishConsumerRateLimitChanged(testConsumerId)
    }

    @Test
    fun `setRateLimit отклоняет когда burstSize меньше requestsPerSecond`() {
        // Arrange: невалидный запрос
        val request = ConsumerRateLimitRequest(requestsPerSecond = 100, burstSize = 50)

        // Act & Assert
        StepVerifier.create(service.setRateLimit(testConsumerId, request, testUserId, testUsername))
            .expectError(ValidationException::class.java)
            .verify()

        verify(consumerRateLimitRepository, never()).save(any<ConsumerRateLimit>())
        verify(eventPublisher, never()).publishConsumerRateLimitChanged(any())
    }

    // ============ AC6: getRateLimit ============

    @Test
    fun `getRateLimit возвращает rate limit по consumer ID`() {
        // Arrange
        val entity = ConsumerRateLimit(
            id = UUID.randomUUID(),
            consumerId = testConsumerId,
            requestsPerSecond = 100,
            burstSize = 150,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            createdBy = testUserId
        )
        val testUser = User(
            id = testUserId,
            username = testUsername,
            email = "admin@example.com",
            passwordHash = "",
            role = Role.ADMIN
        )

        whenever(consumerRateLimitRepository.findByConsumerId(testConsumerId))
            .thenReturn(Mono.just(entity))
        whenever(userRepository.findById(testUserId))
            .thenReturn(Mono.just(testUser))

        // Act & Assert
        StepVerifier.create(service.getRateLimit(testConsumerId))
            .assertNext { response ->
                assertThat(response.consumerId).isEqualTo(testConsumerId)
                assertThat(response.requestsPerSecond).isEqualTo(100)
                assertThat(response.burstSize).isEqualTo(150)
            }
            .verifyComplete()
    }

    @Test
    fun `getRateLimit выбрасывает NotFoundException когда rate limit не найден`() {
        // Arrange
        whenever(consumerRateLimitRepository.findByConsumerId(testConsumerId))
            .thenReturn(Mono.empty())

        // Act & Assert
        StepVerifier.create(service.getRateLimit(testConsumerId))
            .expectError(NotFoundException::class.java)
            .verify()
    }

    // ============ AC7: deleteRateLimit ============

    @Test
    fun `deleteRateLimit удаляет существующий rate limit`() {
        // Arrange
        val entity = ConsumerRateLimit(
            id = UUID.randomUUID(),
            consumerId = testConsumerId,
            requestsPerSecond = 100,
            burstSize = 150,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            createdBy = testUserId
        )

        whenever(consumerRateLimitRepository.findByConsumerId(testConsumerId))
            .thenReturn(Mono.just(entity))
        whenever(consumerRateLimitRepository.delete(entity))
            .thenReturn(Mono.empty())
        whenever(auditService.logDeleted(any(), any(), any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn(Mono.empty())
        whenever(eventPublisher.publishConsumerRateLimitChanged(testConsumerId))
            .thenReturn(Mono.just(1L))

        // Act & Assert
        StepVerifier.create(service.deleteRateLimit(testConsumerId, testUserId, testUsername))
            .verifyComplete()

        verify(consumerRateLimitRepository).delete(entity)
        verify(eventPublisher).publishConsumerRateLimitChanged(testConsumerId)
    }

    @Test
    fun `deleteRateLimit выбрасывает NotFoundException когда rate limit не найден`() {
        // Arrange
        whenever(consumerRateLimitRepository.findByConsumerId(testConsumerId))
            .thenReturn(Mono.empty())

        // Act & Assert
        StepVerifier.create(service.deleteRateLimit(testConsumerId, testUserId, testUsername))
            .expectError(NotFoundException::class.java)
            .verify()

        verify(consumerRateLimitRepository, never()).delete(any<ConsumerRateLimit>())
        verify(eventPublisher, never()).publishConsumerRateLimitChanged(any())
    }

    // ============ AC8: listRateLimits ============

    @Test
    fun `listRateLimits возвращает пагинированный список`() {
        // Arrange
        val entity1 = ConsumerRateLimit(
            id = UUID.randomUUID(),
            consumerId = "consumer-1",
            requestsPerSecond = 100,
            burstSize = 150,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            createdBy = testUserId
        )
        val entity2 = ConsumerRateLimit(
            id = UUID.randomUUID(),
            consumerId = "consumer-2",
            requestsPerSecond = 50,
            burstSize = 75,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            createdBy = testUserId
        )
        val testUser = User(
            id = testUserId,
            username = testUsername,
            email = "admin@example.com",
            passwordHash = "",
            role = Role.ADMIN
        )

        whenever(consumerRateLimitRepository.findAllWithPagination(0, 20))
            .thenReturn(Flux.just(entity1, entity2))
        whenever(consumerRateLimitRepository.countAll())
            .thenReturn(Mono.just(2L))
        whenever(userRepository.findAllById(listOf(testUserId)))
            .thenReturn(Flux.just(testUser))

        // Act & Assert
        StepVerifier.create(service.listRateLimits(offset = 0, limit = 20, filter = null))
            .assertNext { pagedResponse ->
                assertThat(pagedResponse.items).hasSize(2)
                assertThat(pagedResponse.total).isEqualTo(2L)
                assertThat(pagedResponse.items[0].consumerId).isEqualTo("consumer-1")
                assertThat(pagedResponse.items[1].consumerId).isEqualTo("consumer-2")
            }
            .verifyComplete()
    }

    @Test
    fun `listRateLimits поддерживает фильтрацию по consumer ID prefix`() {
        // Arrange
        val entity = ConsumerRateLimit(
            id = UUID.randomUUID(),
            consumerId = "company-a-client",
            requestsPerSecond = 100,
            burstSize = 150,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            createdBy = null
        )

        whenever(consumerRateLimitRepository.findAllByConsumerIdStartingWith("company-a", 0, 20))
            .thenReturn(Flux.just(entity))
        whenever(consumerRateLimitRepository.countByConsumerIdStartingWith("company-a"))
            .thenReturn(Mono.just(1L))

        // Act & Assert
        StepVerifier.create(service.listRateLimits(offset = 0, limit = 20, filter = "company-a"))
            .assertNext { pagedResponse ->
                assertThat(pagedResponse.items).hasSize(1)
                assertThat(pagedResponse.total).isEqualTo(1L)
                assertThat(pagedResponse.items[0].consumerId).isEqualTo("company-a-client")
            }
            .verifyComplete()
    }

    @Test
    fun `listRateLimits возвращает пустой список когда нет rate limits`() {
        // Arrange
        whenever(consumerRateLimitRepository.findAllWithPagination(0, 20))
            .thenReturn(Flux.empty())
        whenever(consumerRateLimitRepository.countAll())
            .thenReturn(Mono.just(0L))

        // Act & Assert
        StepVerifier.create(service.listRateLimits(offset = 0, limit = 20, filter = null))
            .assertNext { pagedResponse ->
                assertThat(pagedResponse.items).isEmpty()
                assertThat(pagedResponse.total).isEqualTo(0L)
            }
            .verifyComplete()
    }
}
