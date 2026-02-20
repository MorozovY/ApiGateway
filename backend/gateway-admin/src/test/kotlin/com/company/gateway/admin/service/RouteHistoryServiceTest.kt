package com.company.gateway.admin.service

import com.company.gateway.admin.exception.NotFoundException
import com.company.gateway.admin.exception.ValidationException
import com.company.gateway.admin.repository.AuditLogRepository
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.common.model.AuditLog
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Unit тесты для RouteHistoryService.
 *
 * Story 7.3: Route Change History API.
 * Task 5: Unit тесты RouteHistoryService.
 */
@ExtendWith(MockitoExtension::class)
class RouteHistoryServiceTest {

    @Mock
    private lateinit var routeRepository: RouteRepository

    @Mock
    private lateinit var auditLogRepository: AuditLogRepository

    private val objectMapper = ObjectMapper().apply {
        findAndRegisterModules()
    }

    private lateinit var routeHistoryService: RouteHistoryService

    private val testRouteId = UUID.randomUUID()
    private val testUserId = UUID.randomUUID()
    private val testUsername = "testuser"
    private val testPath = "/api/orders"

    @BeforeEach
    fun setUp() {
        routeHistoryService = RouteHistoryService(
            routeRepository,
            auditLogRepository,
            objectMapper
        )
    }

    // ============================================
    // Тест: getRouteHistory() возвращает историю в хронологическом порядке
    // ============================================

    @Nested
    inner class ChronologicalOrder {

        @Test
        fun `возвращает историю маршрута с несколькими событиями`() {
            // Given
            val route = createTestRoute()
            val olderTimestamp = Instant.parse("2026-02-10T10:00:00Z")
            val newerTimestamp = Instant.parse("2026-02-10T11:00:00Z")

            val auditLogs = listOf(
                createAuditLog("created", olderTimestamp),
                createAuditLog("approved", newerTimestamp)
            )

            whenever(routeRepository.findById(testRouteId)).thenReturn(Mono.just(route))
            whenever(
                auditLogRepository.findByEntityIdWithFilters(
                    eq("route"),
                    eq(testRouteId.toString()),
                    isNull(),
                    isNull()
                )
            ).thenReturn(Flux.fromIterable(auditLogs))

            // When & Then
            StepVerifier.create(routeHistoryService.getRouteHistory(testRouteId))
                .assertNext { response ->
                    assert(response.routeId == testRouteId) { "routeId should match" }
                    assert(response.currentPath == testPath) { "currentPath should match" }
                    assert(response.history.size == 2) { "should have 2 history entries" }

                    // Проверяем хронологический порядок (AC5)
                    assert(response.history[0].action == "created") { "first action should be 'created'" }
                    assert(response.history[0].timestamp == olderTimestamp) { "first timestamp should be older" }
                    assert(response.history[1].action == "approved") { "second action should be 'approved'" }
                    assert(response.history[1].timestamp == newerTimestamp) { "second timestamp should be newer" }

                    // Проверяем UserInfo
                    assert(response.history[0].user.id == testUserId) { "user.id should match" }
                    assert(response.history[0].user.username == testUsername) { "user.username should match" }
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает пустую историю если нет событий`() {
            // Given
            val route = createTestRoute()

            whenever(routeRepository.findById(testRouteId)).thenReturn(Mono.just(route))
            whenever(
                auditLogRepository.findByEntityIdWithFilters(
                    eq("route"),
                    eq(testRouteId.toString()),
                    isNull(),
                    isNull()
                )
            ).thenReturn(Flux.empty())

            // When & Then
            StepVerifier.create(routeHistoryService.getRouteHistory(testRouteId))
                .assertNext { response ->
                    assert(response.routeId == testRouteId) { "routeId should match" }
                    assert(response.currentPath == testPath) { "currentPath should match" }
                    assert(response.history.isEmpty()) { "history should be empty" }
                }
                .verifyComplete()
        }
    }

    // ============================================
    // Тест: getRouteHistory() с фильтрами from/to
    // ============================================

    @Nested
    inner class DateFiltering {

        @Test
        fun `передаёт фильтры в репозиторий`() {
            // Given
            val route = createTestRoute()
            val dateFrom = LocalDate.of(2026, 2, 1)
            val dateTo = LocalDate.of(2026, 2, 10)

            whenever(routeRepository.findById(testRouteId)).thenReturn(Mono.just(route))
            whenever(
                auditLogRepository.findByEntityIdWithFilters(
                    eq("route"),
                    eq(testRouteId.toString()),
                    eq(dateFrom),
                    eq(dateTo)
                )
            ).thenReturn(Flux.empty())

            // When & Then
            StepVerifier.create(
                routeHistoryService.getRouteHistory(testRouteId, dateFrom, dateTo)
            )
                .assertNext { response ->
                    assert(response.routeId == testRouteId) { "routeId should match" }
                    assert(response.history.isEmpty()) { "history should be empty" }
                }
                .verifyComplete()
        }

        @Test
        fun `передаёт null фильтры когда не указаны`() {
            // Given
            val route = createTestRoute()

            whenever(routeRepository.findById(testRouteId)).thenReturn(Mono.just(route))
            whenever(
                auditLogRepository.findByEntityIdWithFilters(
                    eq("route"),
                    eq(testRouteId.toString()),
                    isNull(),
                    isNull()
                )
            ).thenReturn(Flux.empty())

            // When & Then
            StepVerifier.create(routeHistoryService.getRouteHistory(testRouteId, null, null))
                .assertNext { response ->
                    assert(response.routeId == testRouteId) { "routeId should match" }
                }
                .verifyComplete()
        }
    }

    // ============================================
    // Тест: getRouteHistory() валидация диапазона дат
    // ============================================

    @Nested
    inner class DateRangeValidation {

        @Test
        fun `выбрасывает ValidationException когда from позже to`() {
            // Given
            val invalidFrom = LocalDate.of(2026, 2, 20)
            val invalidTo = LocalDate.of(2026, 2, 10)

            // When & Then
            StepVerifier.create(
                routeHistoryService.getRouteHistory(testRouteId, invalidFrom, invalidTo)
            )
                .expectErrorMatches { error ->
                    error is ValidationException &&
                        error.message.contains("Некорректный диапазон дат")
                }
                .verify()
        }

        @Test
        fun `принимает валидный диапазон дат когда from равно to`() {
            // Given
            val route = createTestRoute()
            val sameDate = LocalDate.of(2026, 2, 10)

            whenever(routeRepository.findById(testRouteId)).thenReturn(Mono.just(route))
            whenever(
                auditLogRepository.findByEntityIdWithFilters(
                    eq("route"),
                    eq(testRouteId.toString()),
                    eq(sameDate),
                    eq(sameDate)
                )
            ).thenReturn(Flux.empty())

            // When & Then
            StepVerifier.create(
                routeHistoryService.getRouteHistory(testRouteId, sameDate, sameDate)
            )
                .assertNext { response ->
                    assert(response.routeId == testRouteId) { "routeId should match" }
                }
                .verifyComplete()
        }
    }

    // ============================================
    // Тест: getRouteHistory() для несуществующего маршрута → NotFoundException
    // ============================================

    @Nested
    inner class NonExistentRoute {

        @Test
        fun `выбрасывает NotFoundException когда маршрут не существует`() {
            // Given
            val nonExistentRouteId = UUID.randomUUID()

            whenever(routeRepository.findById(nonExistentRouteId)).thenReturn(Mono.empty())

            // When & Then
            StepVerifier.create(routeHistoryService.getRouteHistory(nonExistentRouteId))
                .expectErrorMatches { error ->
                    error is NotFoundException &&
                        error.message.contains(nonExistentRouteId.toString())
                }
                .verify()
        }
    }

    // ============================================
    // Тест: changes содержит только изменённые поля
    // ============================================

    @Nested
    inner class ChangesMapping {

        @Test
        fun `корректно парсит JSON changes`() {
            // Given
            val route = createTestRoute()
            val changesJson = """{"before":{"upstreamUrl":"http://v1:8080"},"after":{"upstreamUrl":"http://v2:8080"}}"""
            val auditLog = createAuditLog("updated", Instant.now(), changesJson)

            whenever(routeRepository.findById(testRouteId)).thenReturn(Mono.just(route))
            whenever(
                auditLogRepository.findByEntityIdWithFilters(
                    eq("route"),
                    eq(testRouteId.toString()),
                    isNull(),
                    isNull()
                )
            ).thenReturn(Flux.just(auditLog))

            // When & Then
            StepVerifier.create(routeHistoryService.getRouteHistory(testRouteId))
                .assertNext { response ->
                    assert(response.history.size == 1) { "should have 1 entry" }
                    val entry = response.history[0]

                    // Проверяем, что changes не null и содержит before/after
                    assert(entry.changes != null) { "changes should not be null" }
                    assert(entry.changes!!.has("before")) { "changes should have 'before'" }
                    assert(entry.changes!!.has("after")) { "changes should have 'after'" }
                    assert(entry.changes!!["before"]["upstreamUrl"].asText() == "http://v1:8080") {
                        "before.upstreamUrl should be http://v1:8080"
                    }
                    assert(entry.changes!!["after"]["upstreamUrl"].asText() == "http://v2:8080") {
                        "after.upstreamUrl should be http://v2:8080"
                    }
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает null changes когда JSON отсутствует`() {
            // Given
            val route = createTestRoute()
            val auditLog = createAuditLog("route.submitted", Instant.now(), null)

            whenever(routeRepository.findById(testRouteId)).thenReturn(Mono.just(route))
            whenever(
                auditLogRepository.findByEntityIdWithFilters(
                    eq("route"),
                    eq(testRouteId.toString()),
                    isNull(),
                    isNull()
                )
            ).thenReturn(Flux.just(auditLog))

            // When & Then
            StepVerifier.create(routeHistoryService.getRouteHistory(testRouteId))
                .assertNext { response ->
                    assert(response.history.size == 1) { "should have 1 entry" }
                    assert(response.history[0].changes == null) { "changes should be null" }
                }
                .verifyComplete()
        }

        @Test
        fun `возвращает null changes при невалидном JSON`() {
            // Given
            val route = createTestRoute()
            val invalidJson = "not a valid json"
            val auditLog = createAuditLog("updated", Instant.now(), invalidJson)

            whenever(routeRepository.findById(testRouteId)).thenReturn(Mono.just(route))
            whenever(
                auditLogRepository.findByEntityIdWithFilters(
                    eq("route"),
                    eq(testRouteId.toString()),
                    isNull(),
                    isNull()
                )
            ).thenReturn(Flux.just(auditLog))

            // When & Then
            StepVerifier.create(routeHistoryService.getRouteHistory(testRouteId))
                .assertNext { response ->
                    assert(response.history.size == 1) { "should have 1 entry" }
                    // Невалидный JSON должен привести к null changes
                    assert(response.history[0].changes == null) { "changes should be null for invalid JSON" }
                }
                .verifyComplete()
        }
    }

    // ============================================
    // Вспомогательные методы
    // ============================================

    private fun createTestRoute(): Route {
        return Route(
            id = testRouteId,
            path = testPath,
            upstreamUrl = "http://orders-service:8080",
            methods = listOf("GET", "POST"),
            createdBy = testUserId,
            status = RouteStatus.PUBLISHED
        )
    }

    private fun createAuditLog(
        action: String,
        timestamp: Instant,
        changes: String? = null
    ): AuditLog {
        return AuditLog(
            id = UUID.randomUUID(),
            entityType = "route",
            entityId = testRouteId.toString(),
            action = action,
            userId = testUserId,
            username = testUsername,
            changes = changes,
            ipAddress = "192.168.1.100",
            correlationId = "test-correlation-id",
            createdAt = timestamp
        )
    }
}
