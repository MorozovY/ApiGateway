package com.company.gateway.admin.service

import com.company.gateway.admin.dto.AuditFilterRequest
import com.company.gateway.admin.repository.AuditLogRepository
import com.company.gateway.common.model.AuditLog
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Unit тесты для метода AuditService.findAll() (Story 7.2).
 *
 * Story 7.2, Task 5: Unit тесты AuditService
 * - Тест: findAll() без фильтров возвращает все записи с пагинацией
 * - Тест: findAll() с userId фильтром
 * - Тест: findAll() с action фильтром
 * - Тест: findAll() с entityType фильтром
 * - Тест: findAll() с dateFrom/dateTo фильтрами
 * - Тест: findAll() с комбинацией фильтров (AND logic)
 */
class AuditServiceFilterTest {

    private lateinit var auditLogRepository: AuditLogRepository
    private lateinit var objectMapper: ObjectMapper
    private lateinit var auditService: AuditService

    private val userId1 = UUID.randomUUID()
    private val userId2 = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        auditLogRepository = mock()
        objectMapper = ObjectMapper().findAndRegisterModules()
        auditService = AuditService(auditLogRepository, objectMapper)
    }

    /**
     * Создаёт тестовый AuditLog.
     */
    private fun createAuditLog(
        id: UUID = UUID.randomUUID(),
        entityType: String = "route",
        entityId: String = UUID.randomUUID().toString(),
        action: String = "created",
        userId: UUID = userId1,
        username: String = "testuser",
        changes: String? = null,
        ipAddress: String? = null,
        correlationId: String? = null,
        createdAt: Instant = Instant.now()
    ) = AuditLog(
        id = id,
        entityType = entityType,
        entityId = entityId,
        action = action,
        userId = userId,
        username = username,
        changes = changes,
        ipAddress = ipAddress,
        correlationId = correlationId,
        createdAt = createdAt
    )

    // ============================================
    // AC1: Базовый список без фильтров
    // ============================================

    @Nested
    inner class БезФильтров {

        @Test
        fun `findAll() без фильтров возвращает все записи с пагинацией`() {
            // Given
            val filter = AuditFilterRequest(offset = 0, limit = 50)
            val auditLogs = listOf(
                createAuditLog(action = "created"),
                createAuditLog(action = "updated"),
                createAuditLog(action = "deleted")
            )

            whenever(auditLogRepository.findAllWithFilters(filter))
                .thenReturn(Flux.fromIterable(auditLogs))
            whenever(auditLogRepository.countWithFilters(filter))
                .thenReturn(Mono.just(3L))

            // When & Then
            StepVerifier.create(auditService.findAll(filter))
                .assertNext { response ->
                    assert(response.items.size == 3) { "Должно быть 3 элемента" }
                    assert(response.total == 3L) { "Total должен быть 3" }
                    assert(response.offset == 0) { "Offset должен быть 0" }
                    assert(response.limit == 50) { "Limit должен быть 50" }
                }
                .verifyComplete()

            verify(auditLogRepository).findAllWithFilters(filter)
            verify(auditLogRepository).countWithFilters(filter)
        }

        @Test
        fun `findAll() возвращает пустой список когда записей нет`() {
            // Given
            val filter = AuditFilterRequest(offset = 0, limit = 50)

            whenever(auditLogRepository.findAllWithFilters(filter))
                .thenReturn(Flux.empty())
            whenever(auditLogRepository.countWithFilters(filter))
                .thenReturn(Mono.just(0L))

            // When & Then
            StepVerifier.create(auditService.findAll(filter))
                .assertNext { response ->
                    assert(response.items.isEmpty()) { "Список должен быть пустым" }
                    assert(response.total == 0L) { "Total должен быть 0" }
                }
                .verifyComplete()
        }

        @Test
        fun `findAll() корректно применяет пагинацию`() {
            // Given
            val filter = AuditFilterRequest(offset = 10, limit = 5)
            val auditLogs = listOf(
                createAuditLog(),
                createAuditLog()
            )

            whenever(auditLogRepository.findAllWithFilters(filter))
                .thenReturn(Flux.fromIterable(auditLogs))
            whenever(auditLogRepository.countWithFilters(filter))
                .thenReturn(Mono.just(100L))

            // When & Then
            StepVerifier.create(auditService.findAll(filter))
                .assertNext { response ->
                    assert(response.items.size == 2) { "Должно быть 2 элемента на странице" }
                    assert(response.total == 100L) { "Total должен быть 100" }
                    assert(response.offset == 10) { "Offset должен быть 10" }
                    assert(response.limit == 5) { "Limit должен быть 5" }
                }
                .verifyComplete()
        }
    }

    // ============================================
    // AC2: Фильтрация по userId
    // ============================================

    @Nested
    inner class ФильтрПоUserId {

        @Test
        fun `findAll() с userId фильтром возвращает только записи этого пользователя`() {
            // Given
            val filter = AuditFilterRequest(userId = userId1)
            val auditLogs = listOf(
                createAuditLog(userId = userId1),
                createAuditLog(userId = userId1)
            )

            whenever(auditLogRepository.findAllWithFilters(filter))
                .thenReturn(Flux.fromIterable(auditLogs))
            whenever(auditLogRepository.countWithFilters(filter))
                .thenReturn(Mono.just(2L))

            // When & Then
            StepVerifier.create(auditService.findAll(filter))
                .assertNext { response ->
                    assert(response.items.size == 2) { "Должно быть 2 записи" }
                    assert(response.items.all { it.user.id == userId1 }) { "Все записи должны быть от userId1" }
                }
                .verifyComplete()

            verify(auditLogRepository).findAllWithFilters(filter)
        }
    }

    // ============================================
    // AC3: Фильтрация по action
    // ============================================

    @Nested
    inner class ФильтрПоAction {

        @Test
        fun `findAll() с action фильтром возвращает только записи с этим action`() {
            // Given
            val filter = AuditFilterRequest(action = "approved")
            val auditLogs = listOf(
                createAuditLog(action = "approved"),
                createAuditLog(action = "approved")
            )

            whenever(auditLogRepository.findAllWithFilters(filter))
                .thenReturn(Flux.fromIterable(auditLogs))
            whenever(auditLogRepository.countWithFilters(filter))
                .thenReturn(Mono.just(2L))

            // When & Then
            StepVerifier.create(auditService.findAll(filter))
                .assertNext { response ->
                    assert(response.items.size == 2) { "Должно быть 2 записи" }
                    assert(response.items.all { it.action == "approved" }) { "Все записи должны иметь action=approved" }
                }
                .verifyComplete()
        }
    }

    // ============================================
    // AC4: Фильтрация по entityType
    // ============================================

    @Nested
    inner class ФильтрПоEntityType {

        @Test
        fun `findAll() с entityType фильтром возвращает только записи этого типа`() {
            // Given
            val filter = AuditFilterRequest(entityType = "route")
            val auditLogs = listOf(
                createAuditLog(entityType = "route"),
                createAuditLog(entityType = "route")
            )

            whenever(auditLogRepository.findAllWithFilters(filter))
                .thenReturn(Flux.fromIterable(auditLogs))
            whenever(auditLogRepository.countWithFilters(filter))
                .thenReturn(Mono.just(2L))

            // When & Then
            StepVerifier.create(auditService.findAll(filter))
                .assertNext { response ->
                    assert(response.items.size == 2) { "Должно быть 2 записи" }
                    assert(response.items.all { it.entityType == "route" }) { "Все записи должны иметь entityType=route" }
                }
                .verifyComplete()
        }
    }

    // ============================================
    // AC5: Фильтрация по диапазону дат
    // ============================================

    @Nested
    inner class ФильтрПоДатам {

        @Test
        fun `findAll() с dateFrom и dateTo возвращает записи в диапазоне`() {
            // Given
            val dateFrom = LocalDate.of(2026, 2, 1)
            val dateTo = LocalDate.of(2026, 2, 11)
            val filter = AuditFilterRequest(dateFrom = dateFrom, dateTo = dateTo)
            val auditLogs = listOf(
                createAuditLog(createdAt = Instant.parse("2026-02-05T10:00:00Z")),
                createAuditLog(createdAt = Instant.parse("2026-02-10T15:30:00Z"))
            )

            whenever(auditLogRepository.findAllWithFilters(filter))
                .thenReturn(Flux.fromIterable(auditLogs))
            whenever(auditLogRepository.countWithFilters(filter))
                .thenReturn(Mono.just(2L))

            // When & Then
            StepVerifier.create(auditService.findAll(filter))
                .assertNext { response ->
                    assert(response.items.size == 2) { "Должно быть 2 записи в диапазоне" }
                }
                .verifyComplete()
        }

        @Test
        fun `findAll() только с dateFrom возвращает записи начиная с этой даты`() {
            // Given
            val dateFrom = LocalDate.of(2026, 2, 1)
            val filter = AuditFilterRequest(dateFrom = dateFrom)
            val auditLogs = listOf(
                createAuditLog(createdAt = Instant.parse("2026-02-15T10:00:00Z"))
            )

            whenever(auditLogRepository.findAllWithFilters(filter))
                .thenReturn(Flux.fromIterable(auditLogs))
            whenever(auditLogRepository.countWithFilters(filter))
                .thenReturn(Mono.just(1L))

            // When & Then
            StepVerifier.create(auditService.findAll(filter))
                .assertNext { response ->
                    assert(response.items.size == 1) { "Должна быть 1 запись" }
                }
                .verifyComplete()
        }

        @Test
        fun `findAll() только с dateTo возвращает записи до этой даты`() {
            // Given
            val dateTo = LocalDate.of(2026, 2, 11)
            val filter = AuditFilterRequest(dateTo = dateTo)
            val auditLogs = listOf(
                createAuditLog(createdAt = Instant.parse("2026-02-01T10:00:00Z"))
            )

            whenever(auditLogRepository.findAllWithFilters(filter))
                .thenReturn(Flux.fromIterable(auditLogs))
            whenever(auditLogRepository.countWithFilters(filter))
                .thenReturn(Mono.just(1L))

            // When & Then
            StepVerifier.create(auditService.findAll(filter))
                .assertNext { response ->
                    assert(response.items.size == 1) { "Должна быть 1 запись" }
                }
                .verifyComplete()
        }
    }

    // ============================================
    // AC6: Комбинация фильтров (AND logic)
    // ============================================

    @Nested
    inner class КомбинацияФильтров {

        @Test
        fun `findAll() с комбинацией фильтров применяет AND логику`() {
            // Given
            val filter = AuditFilterRequest(
                entityType = "route",
                action = "rejected",
                userId = userId1
            )
            val auditLogs = listOf(
                createAuditLog(entityType = "route", action = "rejected", userId = userId1)
            )

            whenever(auditLogRepository.findAllWithFilters(filter))
                .thenReturn(Flux.fromIterable(auditLogs))
            whenever(auditLogRepository.countWithFilters(filter))
                .thenReturn(Mono.just(1L))

            // When & Then
            StepVerifier.create(auditService.findAll(filter))
                .assertNext { response ->
                    assert(response.items.size == 1) { "Должна быть 1 запись, соответствующая всем фильтрам" }
                    val item = response.items.first()
                    assert(item.entityType == "route") { "entityType должен быть route" }
                    assert(item.action == "rejected") { "action должен быть rejected" }
                    assert(item.user.id == userId1) { "userId должен совпадать" }
                }
                .verifyComplete()
        }

        @Test
        fun `findAll() с полной комбинацией фильтров включая даты`() {
            // Given
            val filter = AuditFilterRequest(
                entityType = "route",
                action = "approved",
                userId = userId1,
                dateFrom = LocalDate.of(2026, 2, 1),
                dateTo = LocalDate.of(2026, 2, 28),
                offset = 0,
                limit = 10
            )
            val auditLogs = listOf(
                createAuditLog(
                    entityType = "route",
                    action = "approved",
                    userId = userId1,
                    createdAt = Instant.parse("2026-02-15T12:00:00Z")
                )
            )

            whenever(auditLogRepository.findAllWithFilters(filter))
                .thenReturn(Flux.fromIterable(auditLogs))
            whenever(auditLogRepository.countWithFilters(filter))
                .thenReturn(Mono.just(1L))

            // When & Then
            StepVerifier.create(auditService.findAll(filter))
                .assertNext { response ->
                    assert(response.items.size == 1) { "Должна быть 1 запись" }
                    assert(response.total == 1L) { "Total должен быть 1" }
                }
                .verifyComplete()
        }
    }

    // ============================================
    // Преобразование AuditLog → AuditLogResponse
    // ============================================

    @Nested
    inner class ПреобразованиеВDTO {

        @Test
        fun `findAll() корректно преобразует AuditLog в AuditLogResponse`() {
            // Given
            val id = UUID.randomUUID()
            val entityId = UUID.randomUUID().toString()
            val createdAt = Instant.parse("2026-02-20T10:30:00Z")
            val changes = """{"oldStatus":"draft","newStatus":"published"}"""

            val filter = AuditFilterRequest()
            val auditLog = createAuditLog(
                id = id,
                entityType = "route",
                entityId = entityId,
                action = "published",
                userId = userId1,
                username = "admin",
                changes = changes,
                ipAddress = "192.168.1.100",
                correlationId = "test-corr-id",
                createdAt = createdAt
            )

            whenever(auditLogRepository.findAllWithFilters(filter))
                .thenReturn(Flux.just(auditLog))
            whenever(auditLogRepository.countWithFilters(filter))
                .thenReturn(Mono.just(1L))

            // When & Then
            StepVerifier.create(auditService.findAll(filter))
                .assertNext { response ->
                    assert(response.items.size == 1) { "Должен быть 1 элемент" }

                    val item = response.items.first()
                    assert(item.id == id) { "ID должен совпадать" }
                    assert(item.entityType == "route") { "entityType должен совпадать" }
                    assert(item.entityId == entityId) { "entityId должен совпадать" }
                    assert(item.action == "published") { "action должен совпадать" }
                    assert(item.user.id == userId1) { "user.id должен совпадать" }
                    assert(item.user.username == "admin") { "user.username должен совпадать" }
                    assert(item.timestamp == createdAt) { "timestamp должен совпадать" }
                    assert(item.ipAddress == "192.168.1.100") { "ipAddress должен совпадать" }
                    assert(item.correlationId == "test-corr-id") { "correlationId должен совпадать" }
                    assert(item.changes != null) { "changes не должен быть null" }
                    assert(item.changes!!["oldStatus"] == "draft") { "changes.oldStatus должен быть draft" }
                    assert(item.changes!!["newStatus"] == "published") { "changes.newStatus должен быть published" }
                }
                .verifyComplete()
        }

        @Test
        fun `findAll() обрабатывает null changes корректно`() {
            // Given
            val filter = AuditFilterRequest()
            val auditLog = createAuditLog(changes = null)

            whenever(auditLogRepository.findAllWithFilters(filter))
                .thenReturn(Flux.just(auditLog))
            whenever(auditLogRepository.countWithFilters(filter))
                .thenReturn(Mono.just(1L))

            // When & Then
            StepVerifier.create(auditService.findAll(filter))
                .assertNext { response ->
                    assert(response.items.first().changes == null) { "changes должен быть null" }
                }
                .verifyComplete()
        }
    }
}
