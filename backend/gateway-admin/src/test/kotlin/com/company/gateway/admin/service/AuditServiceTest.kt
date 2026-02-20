package com.company.gateway.admin.service

import com.company.gateway.admin.repository.AuditLogRepository
import com.company.gateway.admin.security.AuditContextFilter.Companion.AUDIT_CORRELATION_ID_KEY
import com.company.gateway.admin.security.AuditContextFilter.Companion.AUDIT_IP_ADDRESS_KEY
import com.company.gateway.common.model.AuditLog
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.UUID

/**
 * Unit тесты для AuditService (Story 7.1).
 *
 * Story 7.1, Task 7: Unit тесты AuditService
 * - Тест: log() сохраняет запись с новыми полями (ipAddress, correlationId)
 * - Тест: ошибка записи не propagate
 * - Тест: approve/reject создают correct audit entries
 */
class AuditServiceTest {

    private lateinit var auditLogRepository: AuditLogRepository
    private lateinit var objectMapper: ObjectMapper
    private lateinit var auditService: AuditService

    private val userId = UUID.randomUUID()
    private val username = "testuser"

    @BeforeEach
    fun setUp() {
        auditLogRepository = mock()
        objectMapper = ObjectMapper()
        auditService = AuditService(auditLogRepository, objectMapper)
    }

    // ============================================
    // AC1: Новые поля (ipAddress, correlationId)
    // ============================================

    @Nested
    inner class НовыеПоляAuditLog {

        @Test
        fun `log() сохраняет запись с ipAddress и correlationId`() {
            // Given
            val entityType = "route"
            val entityId = UUID.randomUUID().toString()
            val action = "approved"
            val ipAddress = "192.168.1.100"
            val correlationId = "abc-123-def-456"
            val changes = mapOf("status" to "published")

            val auditLogCaptor = argumentCaptor<AuditLog>()
            whenever(auditLogRepository.save(auditLogCaptor.capture())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<AuditLog>(0))
            }

            // When
            StepVerifier.create(
                auditService.log(
                    entityType = entityType,
                    entityId = entityId,
                    action = action,
                    userId = userId,
                    username = username,
                    changes = changes,
                    ipAddress = ipAddress,
                    correlationId = correlationId
                )
            )
                .expectNextCount(1)
                .verifyComplete()

            // Then
            val savedAuditLog = auditLogCaptor.firstValue
            assert(savedAuditLog.entityType == entityType) { "entityType должен совпадать" }
            assert(savedAuditLog.entityId == entityId) { "entityId должен совпадать" }
            assert(savedAuditLog.action == action) { "action должен совпадать" }
            assert(savedAuditLog.userId == userId) { "userId должен совпадать" }
            assert(savedAuditLog.username == username) { "username должен совпадать" }
            assert(savedAuditLog.ipAddress == ipAddress) { "ipAddress должен совпадать" }
            assert(savedAuditLog.correlationId == correlationId) { "correlationId должен совпадать" }
        }

        @Test
        fun `log() сохраняет запись без optional полей`() {
            // Given
            val entityType = "route"
            val entityId = UUID.randomUUID().toString()
            val action = "created"

            val auditLogCaptor = argumentCaptor<AuditLog>()
            whenever(auditLogRepository.save(auditLogCaptor.capture())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<AuditLog>(0))
            }

            // When
            StepVerifier.create(
                auditService.log(
                    entityType = entityType,
                    entityId = entityId,
                    action = action,
                    userId = userId,
                    username = username
                )
            )
                .expectNextCount(1)
                .verifyComplete()

            // Then
            val savedAuditLog = auditLogCaptor.firstValue
            assert(savedAuditLog.ipAddress == null) { "ipAddress должен быть null" }
            assert(savedAuditLog.correlationId == null) { "correlationId должен быть null" }
            assert(savedAuditLog.changes == null) { "changes должен быть null" }
        }

        @Test
        fun `log() сериализует changes в JSON`() {
            // Given
            val entityType = "route"
            val entityId = UUID.randomUUID().toString()
            val action = "approved"
            val changes = mapOf(
                "previousStatus" to "pending",
                "newStatus" to "published",
                "approvedAt" to "2026-02-20T10:00:00Z"
            )

            val auditLogCaptor = argumentCaptor<AuditLog>()
            whenever(auditLogRepository.save(auditLogCaptor.capture())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<AuditLog>(0))
            }

            // When
            StepVerifier.create(
                auditService.log(
                    entityType = entityType,
                    entityId = entityId,
                    action = action,
                    userId = userId,
                    username = username,
                    changes = changes
                )
            )
                .expectNextCount(1)
                .verifyComplete()

            // Then
            val savedAuditLog = auditLogCaptor.firstValue
            assert(savedAuditLog.changes != null) { "changes не должен быть null" }
            assert(savedAuditLog.changes!!.contains("previousStatus")) { "changes должен содержать previousStatus" }
            assert(savedAuditLog.changes!!.contains("newStatus")) { "changes должен содержать newStatus" }
        }
    }

    // ============================================
    // AC3: logWithContext() извлекает из Reactor Context
    // ============================================

    @Nested
    inner class LogWithContextТесты {

        @Test
        fun `logWithContext() извлекает ipAddress и correlationId из context`() {
            // Given
            val entityType = "route"
            val entityId = UUID.randomUUID().toString()
            val action = "approved"
            val ipAddress = "10.0.0.1"
            val correlationId = "ctx-corr-id-123"

            val auditLogCaptor = argumentCaptor<AuditLog>()
            whenever(auditLogRepository.save(auditLogCaptor.capture())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<AuditLog>(0))
            }

            // When
            StepVerifier.create(
                auditService.logWithContext(
                    entityType = entityType,
                    entityId = entityId,
                    action = action,
                    userId = userId,
                    username = username
                )
                    .contextWrite { ctx ->
                        ctx.put(AUDIT_IP_ADDRESS_KEY, ipAddress)
                            .put(AUDIT_CORRELATION_ID_KEY, correlationId)
                    }
            )
                .expectNextCount(1)
                .verifyComplete()

            // Then
            val savedAuditLog = auditLogCaptor.firstValue
            assert(savedAuditLog.ipAddress == ipAddress) { "ipAddress должен быть извлечён из context" }
            assert(savedAuditLog.correlationId == correlationId) { "correlationId должен быть извлечён из context" }
        }

        @Test
        fun `logWithContext() работает без context данных`() {
            // Given
            val entityType = "route"
            val entityId = UUID.randomUUID().toString()
            val action = "created"

            val auditLogCaptor = argumentCaptor<AuditLog>()
            whenever(auditLogRepository.save(auditLogCaptor.capture())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<AuditLog>(0))
            }

            // When
            StepVerifier.create(
                auditService.logWithContext(
                    entityType = entityType,
                    entityId = entityId,
                    action = action,
                    userId = userId,
                    username = username
                )
            )
                .expectNextCount(1)
                .verifyComplete()

            // Then
            val savedAuditLog = auditLogCaptor.firstValue
            assert(savedAuditLog.ipAddress == null) { "ipAddress должен быть null без context" }
            assert(savedAuditLog.correlationId == null) { "correlationId должен быть null без context" }
        }
    }

    // ============================================
    // AC5, AC6: Graceful Degradation
    // ============================================

    @Nested
    inner class GracefulDegradationТесты {

        @Test
        fun `log() возвращает ошибку при сбое repository`() {
            // Given
            val entityType = "route"
            val entityId = UUID.randomUUID().toString()
            val action = "approved"

            whenever(auditLogRepository.save(any<AuditLog>()))
                .thenReturn(Mono.error(RuntimeException("DB connection failed")))

            // When & Then
            StepVerifier.create(
                auditService.log(
                    entityType = entityType,
                    entityId = entityId,
                    action = action,
                    userId = userId,
                    username = username
                )
            )
                .expectError(RuntimeException::class.java)
                .verify()
        }

        @Test
        fun `logWithContextAsync() не пропагирует ошибки`() {
            // Given
            val entityType = "route"
            val entityId = UUID.randomUUID().toString()
            val action = "approved"

            whenever(auditLogRepository.save(any<AuditLog>()))
                .thenReturn(Mono.error(RuntimeException("DB connection failed")))

            // When & Then — async метод возвращает Mono.empty() при ошибке
            StepVerifier.create(
                auditService.logWithContextAsync(
                    entityType = entityType,
                    entityId = entityId,
                    action = action,
                    userId = userId,
                    username = username
                )
            )
                .verifyComplete() // Не должно быть ошибки
        }
    }

    // ============================================
    // AC2: Методы approve/reject/published
    // ============================================

    @Nested
    inner class ApproveRejectPublishedМетоды {

        @Test
        fun `logApproved() записывает action approved`() {
            // Given
            val entityType = "route"
            val entityId = UUID.randomUUID().toString()
            val changes = mapOf("newStatus" to "published")

            val auditLogCaptor = argumentCaptor<AuditLog>()
            whenever(auditLogRepository.save(auditLogCaptor.capture())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<AuditLog>(0))
            }

            // When
            StepVerifier.create(
                auditService.logApproved(
                    entityType = entityType,
                    entityId = entityId,
                    userId = userId,
                    username = username,
                    changes = changes
                )
            )
                .expectNextCount(1)
                .verifyComplete()

            // Then
            val savedAuditLog = auditLogCaptor.firstValue
            assert(savedAuditLog.action == "approved") { "action должен быть 'approved'" }
        }

        @Test
        fun `logRejected() записывает action rejected`() {
            // Given
            val entityType = "route"
            val entityId = UUID.randomUUID().toString()
            val changes = mapOf("rejectionReason" to "Security issue")

            val auditLogCaptor = argumentCaptor<AuditLog>()
            whenever(auditLogRepository.save(auditLogCaptor.capture())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<AuditLog>(0))
            }

            // When
            StepVerifier.create(
                auditService.logRejected(
                    entityType = entityType,
                    entityId = entityId,
                    userId = userId,
                    username = username,
                    changes = changes
                )
            )
                .expectNextCount(1)
                .verifyComplete()

            // Then
            val savedAuditLog = auditLogCaptor.firstValue
            assert(savedAuditLog.action == "rejected") { "action должен быть 'rejected'" }
        }

        @Test
        fun `logPublished() записывает action published`() {
            // Given
            val entityType = "route"
            val entityId = UUID.randomUUID().toString()
            val changes = mapOf("publishedAt" to "2026-02-20T10:00:00Z", "approvedBy" to "security")

            val auditLogCaptor = argumentCaptor<AuditLog>()
            whenever(auditLogRepository.save(auditLogCaptor.capture())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<AuditLog>(0))
            }

            // When
            StepVerifier.create(
                auditService.logPublished(
                    entityType = entityType,
                    entityId = entityId,
                    userId = userId,
                    username = username,
                    changes = changes
                )
            )
                .expectNextCount(1)
                .verifyComplete()

            // Then
            val savedAuditLog = auditLogCaptor.firstValue
            assert(savedAuditLog.action == "published") { "action должен быть 'published'" }
        }
    }

    // ============================================
    // Существующие методы (logCreated, logUpdated, logDeleted, logRoleChanged)
    // ============================================

    @Nested
    inner class СуществующиеМетоды {

        @Test
        fun `logCreated() передаёт ipAddress и correlationId`() {
            // Given
            val entityType = "route"
            val entityId = UUID.randomUUID().toString()
            val ipAddress = "172.16.0.1"
            val correlationId = "create-corr-id"

            val auditLogCaptor = argumentCaptor<AuditLog>()
            whenever(auditLogRepository.save(auditLogCaptor.capture())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<AuditLog>(0))
            }

            // When
            StepVerifier.create(
                auditService.logCreated(
                    entityType = entityType,
                    entityId = entityId,
                    userId = userId,
                    username = username,
                    entity = mapOf("path" to "/api/test"),
                    ipAddress = ipAddress,
                    correlationId = correlationId
                )
            )
                .expectNextCount(1)
                .verifyComplete()

            // Then
            val savedAuditLog = auditLogCaptor.firstValue
            assert(savedAuditLog.action == "created") { "action должен быть 'created'" }
            assert(savedAuditLog.ipAddress == ipAddress) { "ipAddress должен совпадать" }
            assert(savedAuditLog.correlationId == correlationId) { "correlationId должен совпадать" }
        }

        @Test
        fun `logDeleted() передаёт ipAddress и correlationId`() {
            // Given
            val entityType = "route"
            val entityId = UUID.randomUUID().toString()
            val ipAddress = "172.16.0.2"
            val correlationId = "delete-corr-id"

            val auditLogCaptor = argumentCaptor<AuditLog>()
            whenever(auditLogRepository.save(auditLogCaptor.capture())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<AuditLog>(0))
            }

            // When
            StepVerifier.create(
                auditService.logDeleted(
                    entityType = entityType,
                    entityId = entityId,
                    userId = userId,
                    username = username,
                    ipAddress = ipAddress,
                    correlationId = correlationId
                )
            )
                .expectNextCount(1)
                .verifyComplete()

            // Then
            val savedAuditLog = auditLogCaptor.firstValue
            assert(savedAuditLog.action == "deleted") { "action должен быть 'deleted'" }
            assert(savedAuditLog.ipAddress == ipAddress) { "ipAddress должен совпадать" }
            assert(savedAuditLog.correlationId == correlationId) { "correlationId должен совпадать" }
        }

        @Test
        fun `logRoleChanged() передаёт ipAddress и correlationId`() {
            // Given
            val targetUserId = UUID.randomUUID()
            val ipAddress = "172.16.0.3"
            val correlationId = "role-change-corr-id"

            val auditLogCaptor = argumentCaptor<AuditLog>()
            whenever(auditLogRepository.save(auditLogCaptor.capture())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<AuditLog>(0))
            }

            // When
            StepVerifier.create(
                auditService.logRoleChanged(
                    targetUserId = targetUserId,
                    targetUsername = "targetuser",
                    oldRole = "developer",
                    newRole = "security",
                    performedByUserId = userId,
                    performedByUsername = username,
                    ipAddress = ipAddress,
                    correlationId = correlationId
                )
            )
                .expectNextCount(1)
                .verifyComplete()

            // Then
            val savedAuditLog = auditLogCaptor.firstValue
            assert(savedAuditLog.action == "role_changed") { "action должен быть 'role_changed'" }
            assert(savedAuditLog.ipAddress == ipAddress) { "ipAddress должен совпадать" }
            assert(savedAuditLog.correlationId == correlationId) { "correlationId должен совпадать" }
        }
    }
}
