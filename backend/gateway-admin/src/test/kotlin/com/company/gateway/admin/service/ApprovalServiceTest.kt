package com.company.gateway.admin.service

import com.company.gateway.admin.exception.AccessDeniedException
import com.company.gateway.admin.exception.ConflictException
import com.company.gateway.admin.exception.NotFoundException
import com.company.gateway.admin.exception.ValidationException
import com.company.gateway.admin.publisher.RouteEventPublisher
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.common.model.AuditLog
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * Unit тесты для ApprovalService (Story 4.1, Story 4.2, Story 4.4).
 *
 * Story 4.1: Submit for Approval API
 * - AC1: Успешная отправка на согласование
 * - AC2: Нельзя отправить не-draft и не-rejected маршрут
 * - AC3: Нельзя отправить чужой маршрут
 * - AC4: Валидация перед отправкой
 *
 * Story 4.2: Approval & Rejection API
 * - AC1: Успешное одобрение маршрута
 * - AC3: Успешное отклонение маршрута
 * - AC4: Отклонение без причины
 * - AC6: Маршрут не в статусе pending
 *
 * Story 4.4: Route Status Tracking
 * - AC4: Повторная подача отклонённого маршрута (resubmission flow)
 */
class ApprovalServiceTest {

    private lateinit var routeRepository: RouteRepository
    private lateinit var auditService: AuditService
    private lateinit var routeEventPublisher: RouteEventPublisher
    private lateinit var approvalService: ApprovalService

    private val userId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val username = "developer"
    private val securityUsername = "security"

    @BeforeEach
    fun setUp() {
        routeRepository = mock()
        auditService = mock()
        routeEventPublisher = mock()
        approvalService = ApprovalService(routeRepository, auditService, routeEventPublisher)
    }

    // ============================================
    // AC1: Успешная отправка на согласование
    // ============================================

    @Nested
    inner class AC1_УспешнаяОтправка {

        @Test
        fun `успешно отправляет draft маршрут на согласование`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.DRAFT,
                createdBy = userId
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))
            whenever(routeRepository.save(any<Route>())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<Route>(0))
            }
            whenever(auditService.log(any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(mock()))

            // When
            StepVerifier.create(approvalService.submitForApproval(routeId, userId, username))
                // Then
                .expectNextMatches { response ->
                    response.status == "pending" &&
                    response.id == routeId
                }
                .verifyComplete()
        }

        @Test
        fun `устанавливает submittedAt timestamp при отправке`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.DRAFT,
                createdBy = userId
            )

            val routeCaptor = argumentCaptor<Route>()
            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))
            whenever(routeRepository.save(routeCaptor.capture())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<Route>(0))
            }
            whenever(auditService.log(any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(mock()))

            // When
            StepVerifier.create(approvalService.submitForApproval(routeId, userId, username))
                .expectNextCount(1)
                .verifyComplete()

            // Then
            val savedRoute = routeCaptor.firstValue
            assert(savedRoute.submittedAt != null) { "submittedAt должен быть установлен" }
            assert(savedRoute.status == RouteStatus.PENDING) { "status должен быть PENDING" }
        }

        @Test
        fun `создаёт audit log entry при успешной отправке`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.DRAFT,
                createdBy = userId
            )

            val changesCaptor = argumentCaptor<Map<String, String>>()
            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))
            whenever(routeRepository.save(any<Route>())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<Route>(0))
            }
            whenever(auditService.log(any(), any(), any(), any(), any(), changesCaptor.capture()))
                .thenReturn(Mono.just(mock()))

            // When
            StepVerifier.create(approvalService.submitForApproval(routeId, userId, username))
                .expectNextCount(1)
                .verifyComplete()

            // Then — проверяем вызов audit log
            verify(auditService).log(
                eq("route"),
                eq(routeId.toString()),
                eq("route.submitted"),
                eq(userId),
                eq(username),
                any()
            )

            // Then — проверяем содержимое changes map
            val changes = changesCaptor.firstValue
            assert(changes["newStatus"] == "pending") { "newStatus должен быть 'pending'" }
            assert(changes["submittedAt"] != null) { "submittedAt должен быть указан" }
        }
    }

    // ============================================
    // AC2: Нельзя отправить маршрут в недопустимом статусе
    // ============================================

    @Nested
    inner class AC2_НедопустимыйСтатусДляОтправки {

        @Test
        fun `отклоняет отправку pending маршрута`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.PENDING,
                createdBy = userId
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

            // When & Then
            StepVerifier.create(approvalService.submitForApproval(routeId, userId, username))
                .expectErrorMatches { ex ->
                    ex is ConflictException &&
                    ex.detail == "Only draft or rejected routes can be submitted for approval"
                }
                .verify()
        }

        @Test
        fun `отклоняет отправку published маршрута`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.PUBLISHED,
                createdBy = userId
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

            // When & Then
            StepVerifier.create(approvalService.submitForApproval(routeId, userId, username))
                .expectErrorMatches { ex ->
                    ex is ConflictException &&
                    ex.detail == "Only draft or rejected routes can be submitted for approval"
                }
                .verify()
        }

        @Test
        fun `не сохраняет маршрут при ошибке статуса`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.PENDING,
                createdBy = userId
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

            // When
            StepVerifier.create(approvalService.submitForApproval(routeId, userId, username))
                .expectError(ConflictException::class.java)
                .verify()

            // Then
            verify(routeRepository, never()).save(any<Route>())
        }
    }

    // ============================================
    // Story 4.4 AC4: Повторная подача отклонённого маршрута
    // ============================================

    @Nested
    inner class Story44_AC4_ResubmissionFlow {

        @Test
        fun `успешно повторно подаёт rejected маршрут`() {
            // Given
            val routeId = UUID.randomUUID()
            val rejectorId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.REJECTED,
                createdBy = userId
            ).copy(
                rejectedBy = rejectorId,
                rejectedAt = Instant.now(),
                rejectionReason = "Security issue"
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))
            whenever(routeRepository.save(any<Route>())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<Route>(0))
            }
            whenever(auditService.log(any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(mock()))

            // When
            StepVerifier.create(approvalService.submitForApproval(routeId, userId, username))
                // Then
                .expectNextMatches { response ->
                    response.status == "pending" &&
                    response.id == routeId
                }
                .verifyComplete()
        }

        @Test
        fun `очищает rejection-поля при resubmission`() {
            // Given
            val routeId = UUID.randomUUID()
            val rejectorId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.REJECTED,
                createdBy = userId
            ).copy(
                rejectedBy = rejectorId,
                rejectedAt = Instant.now(),
                rejectionReason = "Security issue"
            )

            val routeCaptor = argumentCaptor<Route>()
            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))
            whenever(routeRepository.save(routeCaptor.capture())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<Route>(0))
            }
            whenever(auditService.log(any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(mock()))

            // When
            StepVerifier.create(approvalService.submitForApproval(routeId, userId, username))
                .expectNextCount(1)
                .verifyComplete()

            // Then — rejection-поля должны быть очищены
            val savedRoute = routeCaptor.firstValue
            assert(savedRoute.rejectionReason == null) { "rejectionReason должен быть null после resubmission" }
            assert(savedRoute.rejectedBy == null) { "rejectedBy должен быть null после resubmission" }
            assert(savedRoute.rejectedAt == null) { "rejectedAt должен быть null после resubmission" }
            assert(savedRoute.status == RouteStatus.PENDING) { "status должен быть PENDING" }
            assert(savedRoute.submittedAt != null) { "submittedAt должен быть обновлён" }
        }

        @Test
        fun `создаёт audit log с action route_resubmitted для rejected маршрута`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.REJECTED,
                createdBy = userId
            ).copy(
                rejectedBy = UUID.randomUUID(),
                rejectedAt = Instant.now(),
                rejectionReason = "Security issue"
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))
            whenever(routeRepository.save(any<Route>())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<Route>(0))
            }
            whenever(auditService.log(any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(mock()))

            // When
            StepVerifier.create(approvalService.submitForApproval(routeId, userId, username))
                .expectNextCount(1)
                .verifyComplete()

            // Then — audit log должен использовать action "route.resubmitted"
            verify(auditService).log(
                eq("route"),
                eq(routeId.toString()),
                eq("route.resubmitted"),
                eq(userId),
                eq(username),
                any()
            )
        }

        @Test
        fun `создаёт audit log с action route_submitted для draft маршрута`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.DRAFT,
                createdBy = userId
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))
            whenever(routeRepository.save(any<Route>())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<Route>(0))
            }
            whenever(auditService.log(any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(mock()))

            // When
            StepVerifier.create(approvalService.submitForApproval(routeId, userId, username))
                .expectNextCount(1)
                .verifyComplete()

            // Then — audit log должен использовать action "route.submitted"
            verify(auditService).log(
                eq("route"),
                eq(routeId.toString()),
                eq("route.submitted"),
                eq(userId),
                eq(username),
                any()
            )
        }

        @Test
        fun `отклоняет resubmission чужого rejected маршрута`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.REJECTED,
                createdBy = otherUserId // другой пользователь
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

            // When & Then
            StepVerifier.create(approvalService.submitForApproval(routeId, userId, username))
                .expectErrorMatches { ex ->
                    ex is AccessDeniedException &&
                    ex.detail == "You can only submit your own routes"
                }
                .verify()
        }
    }

    // ============================================
    // AC3: Нельзя отправить чужой маршрут
    // ============================================

    @Nested
    inner class AC3_НельзяОтправитьЧужой {

        @Test
        fun `отклоняет отправку чужого маршрута`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.DRAFT,
                createdBy = otherUserId // другой пользователь
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

            // When & Then
            StepVerifier.create(approvalService.submitForApproval(routeId, userId, username))
                .expectErrorMatches { ex ->
                    ex is AccessDeniedException &&
                    ex.detail == "You can only submit your own routes"
                }
                .verify()
        }

        @Test
        fun `не сохраняет маршрут при ошибке ownership`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.DRAFT,
                createdBy = otherUserId
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

            // When
            StepVerifier.create(approvalService.submitForApproval(routeId, userId, username))
                .expectError(AccessDeniedException::class.java)
                .verify()

            // Then
            verify(routeRepository, never()).save(any<Route>())
        }
    }

    // ============================================
    // AC4: Валидация перед отправкой
    // ============================================

    @Nested
    inner class AC4_ВалидацияПередОтправкой {

        @Test
        fun `отклоняет маршрут с пустым path`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.DRAFT,
                createdBy = userId,
                path = "   " // пустой path
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

            // When & Then
            StepVerifier.create(approvalService.submitForApproval(routeId, userId, username))
                .expectErrorMatches { ex ->
                    ex is ValidationException &&
                    ex.detail.contains("Path cannot be empty")
                }
                .verify()
        }

        @Test
        fun `отклоняет маршрут с невалидным upstreamUrl`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.DRAFT,
                createdBy = userId,
                upstreamUrl = "not-a-valid-url"
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

            // When & Then
            StepVerifier.create(approvalService.submitForApproval(routeId, userId, username))
                .expectErrorMatches { ex ->
                    ex is ValidationException &&
                    ex.detail.contains("Upstream URL must be a valid HTTP/HTTPS URL")
                }
                .verify()
        }

        @Test
        fun `отклоняет маршрут с пустым списком methods`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.DRAFT,
                createdBy = userId,
                methods = emptyList()
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

            // When & Then
            StepVerifier.create(approvalService.submitForApproval(routeId, userId, username))
                .expectErrorMatches { ex ->
                    ex is ValidationException &&
                    ex.detail.contains("At least one HTTP method must be specified")
                }
                .verify()
        }

        @Test
        fun `отклоняет маршрут с пустым upstreamUrl`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.DRAFT,
                createdBy = userId,
                upstreamUrl = ""
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

            // When & Then
            StepVerifier.create(approvalService.submitForApproval(routeId, userId, username))
                .expectErrorMatches { ex ->
                    ex is ValidationException &&
                    ex.detail.contains("Upstream URL cannot be empty")
                }
                .verify()
        }

        @Test
        fun `собирает все ошибки валидации`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.DRAFT,
                createdBy = userId,
                path = "",
                upstreamUrl = "",
                methods = emptyList()
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

            // When & Then
            StepVerifier.create(approvalService.submitForApproval(routeId, userId, username))
                .expectErrorMatches { ex ->
                    ex is ValidationException &&
                    ex.detail.contains("Path cannot be empty") &&
                    ex.detail.contains("Upstream URL cannot be empty") &&
                    ex.detail.contains("At least one HTTP method must be specified")
                }
                .verify()
        }

        @Test
        fun `не сохраняет маршрут при ошибке валидации`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.DRAFT,
                createdBy = userId,
                methods = emptyList()
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

            // When
            StepVerifier.create(approvalService.submitForApproval(routeId, userId, username))
                .expectError(ValidationException::class.java)
                .verify()

            // Then
            verify(routeRepository, never()).save(any<Route>())
        }
    }

    // ============================================
    // AC5: Маршрут не найден
    // ============================================

    @Nested
    inner class AC5_МаршрутНеНайден {

        @Test
        fun `возвращает NotFoundException для несуществующего маршрута`() {
            // Given
            val routeId = UUID.randomUUID()
            whenever(routeRepository.findById(routeId)).thenReturn(Mono.empty())

            // When & Then
            StepVerifier.create(approvalService.submitForApproval(routeId, userId, username))
                .expectErrorMatches { ex ->
                    ex is NotFoundException &&
                    ex.detail == "Route not found"
                }
                .verify()
        }
    }

    // ============================================
    // Story 4.2: Approve - Успешное одобрение
    // ============================================

    @Nested
    inner class Story42_УспешноеОдобрение {

        @Test
        fun `успешно одобряет pending маршрут`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.PENDING,
                createdBy = otherUserId // создан другим пользователем
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))
            whenever(routeRepository.save(any<Route>())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<Route>(0))
            }
            whenever(routeEventPublisher.publishRouteChanged(any())).thenReturn(Mono.just(1L))
            whenever(auditService.log(any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(mock()))

            // When
            StepVerifier.create(approvalService.approve(routeId, userId, securityUsername))
                // Then
                .expectNextMatches { response ->
                    response.status == "published" &&
                    response.id == routeId
                }
                .verifyComplete()
        }

        @Test
        fun `устанавливает approvedBy и approvedAt при одобрении`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.PENDING,
                createdBy = otherUserId
            )

            val routeCaptor = argumentCaptor<Route>()
            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))
            whenever(routeRepository.save(routeCaptor.capture())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<Route>(0))
            }
            whenever(routeEventPublisher.publishRouteChanged(any())).thenReturn(Mono.just(1L))
            whenever(auditService.log(any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(mock()))

            // When
            StepVerifier.create(approvalService.approve(routeId, userId, securityUsername))
                .expectNextCount(1)
                .verifyComplete()

            // Then
            val savedRoute = routeCaptor.firstValue
            assert(savedRoute.approvedBy == userId) { "approvedBy должен быть установлен" }
            assert(savedRoute.approvedAt != null) { "approvedAt должен быть установлен" }
            assert(savedRoute.status == RouteStatus.PUBLISHED) { "status должен быть PUBLISHED" }
        }

        @Test
        fun `создаёт audit log entry при одобрении`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.PENDING,
                createdBy = otherUserId
            )

            val changesCaptor = argumentCaptor<Map<String, String>>()
            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))
            whenever(routeRepository.save(any<Route>())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<Route>(0))
            }
            whenever(routeEventPublisher.publishRouteChanged(any())).thenReturn(Mono.just(1L))
            whenever(auditService.log(any(), any(), any(), any(), any(), changesCaptor.capture()))
                .thenReturn(Mono.just(mock()))

            // When
            StepVerifier.create(approvalService.approve(routeId, userId, securityUsername))
                .expectNextCount(1)
                .verifyComplete()

            // Then — проверяем вызов audit log
            verify(auditService).log(
                eq("route"),
                eq(routeId.toString()),
                eq("approved"),
                eq(userId),
                eq(securityUsername),
                any()
            )

            // Then — проверяем содержимое changes map
            val changes = changesCaptor.firstValue
            assert(changes["oldStatus"] == "pending") { "oldStatus должен быть 'pending'" }
            assert(changes["newStatus"] == "published") { "newStatus должен быть 'published'" }
            assert(changes["approvedAt"] != null) { "approvedAt должен быть указан" }
        }

        @Test
        fun `публикует cache invalidation при approve`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.PENDING,
                createdBy = otherUserId
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))
            whenever(routeRepository.save(any<Route>())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<Route>(0))
            }
            whenever(routeEventPublisher.publishRouteChanged(any())).thenReturn(Mono.just(1L))
            whenever(auditService.log(any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(mock()))

            // When
            StepVerifier.create(approvalService.approve(routeId, userId, securityUsername))
                .expectNextCount(1)
                .verifyComplete()

            // Then
            verify(routeEventPublisher).publishRouteChanged(routeId)
        }
    }

    // ============================================
    // Story 4.2: Approve - Ошибки статуса
    // ============================================

    @Nested
    inner class Story42_ApproveОшибкиСтатуса {

        @Test
        fun `отклоняет approve для draft маршрута`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.DRAFT,
                createdBy = otherUserId
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

            // When & Then
            StepVerifier.create(approvalService.approve(routeId, userId, securityUsername))
                .expectErrorMatches { ex ->
                    ex is ConflictException &&
                    ex.detail == "Only pending routes can be approved/rejected"
                }
                .verify()
        }

        @Test
        fun `отклоняет approve для published маршрута`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.PUBLISHED,
                createdBy = otherUserId
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

            // When & Then
            StepVerifier.create(approvalService.approve(routeId, userId, securityUsername))
                .expectErrorMatches { ex ->
                    ex is ConflictException &&
                    ex.detail == "Only pending routes can be approved/rejected"
                }
                .verify()
        }

        @Test
        fun `отклоняет approve для rejected маршрута`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.REJECTED,
                createdBy = otherUserId
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

            // When & Then
            StepVerifier.create(approvalService.approve(routeId, userId, securityUsername))
                .expectErrorMatches { ex ->
                    ex is ConflictException &&
                    ex.detail == "Only pending routes can be approved/rejected"
                }
                .verify()
        }

        @Test
        fun `возвращает NotFoundException для несуществующего маршрута при approve`() {
            // Given
            val routeId = UUID.randomUUID()
            whenever(routeRepository.findById(routeId)).thenReturn(Mono.empty())

            // When & Then
            StepVerifier.create(approvalService.approve(routeId, userId, securityUsername))
                .expectErrorMatches { ex ->
                    ex is NotFoundException &&
                    ex.detail == "Route not found"
                }
                .verify()
        }

        @Test
        fun `не сохраняет маршрут при ошибке статуса approve`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.DRAFT,
                createdBy = otherUserId
            )

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

            // When
            StepVerifier.create(approvalService.approve(routeId, userId, securityUsername))
                .expectError(ConflictException::class.java)
                .verify()

            // Then
            verify(routeRepository, never()).save(any<Route>())
        }
    }

    // ============================================
    // Story 4.2: Reject - Успешное отклонение
    // ============================================

    @Nested
    inner class Story42_УспешноеОтклонение {

        @Test
        fun `успешно отклоняет pending маршрут с причиной`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.PENDING,
                createdBy = otherUserId
            )
            val reason = "Upstream URL points to internal service not allowed for external access"

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))
            whenever(routeRepository.save(any<Route>())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<Route>(0))
            }
            whenever(auditService.log(any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(mock()))

            // When
            StepVerifier.create(approvalService.reject(routeId, userId, securityUsername, reason))
                // Then
                .expectNextMatches { response ->
                    response.status == "rejected" &&
                    response.id == routeId &&
                    response.rejectionReason == reason
                }
                .verifyComplete()
        }

        @Test
        fun `устанавливает rejectedBy, rejectedAt, rejectionReason при отклонении`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.PENDING,
                createdBy = otherUserId
            )
            val reason = "Security issue found"

            val routeCaptor = argumentCaptor<Route>()
            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))
            whenever(routeRepository.save(routeCaptor.capture())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<Route>(0))
            }
            whenever(auditService.log(any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(mock()))

            // When
            StepVerifier.create(approvalService.reject(routeId, userId, securityUsername, reason))
                .expectNextCount(1)
                .verifyComplete()

            // Then
            val savedRoute = routeCaptor.firstValue
            assert(savedRoute.rejectedBy == userId) { "rejectedBy должен быть установлен" }
            assert(savedRoute.rejectedAt != null) { "rejectedAt должен быть установлен" }
            assert(savedRoute.rejectionReason == reason) { "rejectionReason должен быть установлен" }
            assert(savedRoute.status == RouteStatus.REJECTED) { "status должен быть REJECTED" }
        }

        @Test
        fun `создаёт audit log entry при отклонении`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.PENDING,
                createdBy = otherUserId
            )
            val reason = "Security issue"

            val changesCaptor = argumentCaptor<Map<String, String>>()
            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))
            whenever(routeRepository.save(any<Route>())).thenAnswer { invocation ->
                Mono.just(invocation.getArgument<Route>(0))
            }
            whenever(auditService.log(any(), any(), any(), any(), any(), changesCaptor.capture()))
                .thenReturn(Mono.just(mock()))

            // When
            StepVerifier.create(approvalService.reject(routeId, userId, securityUsername, reason))
                .expectNextCount(1)
                .verifyComplete()

            // Then — проверяем вызов audit log
            verify(auditService).log(
                eq("route"),
                eq(routeId.toString()),
                eq("rejected"),
                eq(userId),
                eq(securityUsername),
                any()
            )

            // Then — проверяем содержимое changes map
            val changes = changesCaptor.firstValue
            assert(changes["oldStatus"] == "pending") { "oldStatus должен быть 'pending'" }
            assert(changes["newStatus"] == "rejected") { "newStatus должен быть 'rejected'" }
            assert(changes["rejectedAt"] != null) { "rejectedAt должен быть указан" }
            assert(changes["rejectionReason"] == reason) { "rejectionReason должен быть '$reason'" }
        }
    }

    // ============================================
    // Story 4.2: Reject - Валидация причины
    // ============================================

    @Nested
    inner class Story42_RejectВалидацияПричины {

        @Test
        fun `требует reason при отклонении - пустая строка`() {
            // Given
            val routeId = UUID.randomUUID()
            val reason = ""

            // When & Then
            StepVerifier.create(approvalService.reject(routeId, userId, securityUsername, reason))
                .expectErrorMatches { ex ->
                    ex is ValidationException &&
                    ex.detail == "Rejection reason is required"
                }
                .verify()
        }

        @Test
        fun `требует reason при отклонении - пробелы`() {
            // Given
            val routeId = UUID.randomUUID()
            val reason = "   "

            // When & Then
            StepVerifier.create(approvalService.reject(routeId, userId, securityUsername, reason))
                .expectErrorMatches { ex ->
                    ex is ValidationException &&
                    ex.detail == "Rejection reason is required"
                }
                .verify()
        }

        @Test
        fun `не обращается к репозиторию при пустой причине`() {
            // Given
            val routeId = UUID.randomUUID()
            val reason = ""

            // When
            StepVerifier.create(approvalService.reject(routeId, userId, securityUsername, reason))
                .expectError(ValidationException::class.java)
                .verify()

            // Then
            verify(routeRepository, never()).findById(any<UUID>())
        }
    }

    // ============================================
    // Story 4.2: Reject - Ошибки статуса
    // ============================================

    @Nested
    inner class Story42_RejectОшибкиСтатуса {

        @Test
        fun `отклоняет reject для draft маршрута`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.DRAFT,
                createdBy = otherUserId
            )
            val reason = "Security issue"

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

            // When & Then
            StepVerifier.create(approvalService.reject(routeId, userId, securityUsername, reason))
                .expectErrorMatches { ex ->
                    ex is ConflictException &&
                    ex.detail == "Only pending routes can be approved/rejected"
                }
                .verify()
        }

        @Test
        fun `отклоняет reject для published маршрута`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.PUBLISHED,
                createdBy = otherUserId
            )
            val reason = "Security issue"

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

            // When & Then
            StepVerifier.create(approvalService.reject(routeId, userId, securityUsername, reason))
                .expectErrorMatches { ex ->
                    ex is ConflictException &&
                    ex.detail == "Only pending routes can be approved/rejected"
                }
                .verify()
        }

        @Test
        fun `отклоняет reject для уже rejected маршрута`() {
            // Given
            val routeId = UUID.randomUUID()
            val route = createTestRoute(
                id = routeId,
                status = RouteStatus.REJECTED,
                createdBy = otherUserId
            )
            val reason = "Another security issue"

            whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

            // When & Then
            StepVerifier.create(approvalService.reject(routeId, userId, securityUsername, reason))
                .expectErrorMatches { ex ->
                    ex is ConflictException &&
                    ex.detail == "Only pending routes can be approved/rejected"
                }
                .verify()
        }

        @Test
        fun `возвращает NotFoundException для несуществующего маршрута при reject`() {
            // Given
            val routeId = UUID.randomUUID()
            val reason = "Security issue"
            whenever(routeRepository.findById(routeId)).thenReturn(Mono.empty())

            // When & Then
            StepVerifier.create(approvalService.reject(routeId, userId, securityUsername, reason))
                .expectErrorMatches { ex ->
                    ex is NotFoundException &&
                    ex.detail == "Route not found"
                }
                .verify()
        }
    }

    // ============================================
    // Вспомогательные методы
    // ============================================

    private fun createTestRoute(
        id: UUID = UUID.randomUUID(),
        path: String = "/api/test",
        upstreamUrl: String = "http://test-service:8080",
        methods: List<String> = listOf("GET", "POST"),
        status: RouteStatus = RouteStatus.DRAFT,
        createdBy: UUID = userId
    ): Route {
        return Route(
            id = id,
            path = path,
            upstreamUrl = upstreamUrl,
            methods = methods,
            status = status,
            createdBy = createdBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}
