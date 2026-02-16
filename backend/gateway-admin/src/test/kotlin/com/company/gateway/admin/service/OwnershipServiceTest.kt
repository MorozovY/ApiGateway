package com.company.gateway.admin.service

import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * Unit тесты для OwnershipService.
 */
class OwnershipServiceTest {

    private lateinit var routeRepository: RouteRepository
    private lateinit var ownershipService: OwnershipService

    private val routeId = UUID.randomUUID()
    private val ownerId = UUID.randomUUID()
    private val otherId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        routeRepository = mock()
        ownershipService = OwnershipService(routeRepository)
    }

    // ============================================
    // isOwner тесты
    // ============================================

    @Test
    fun `isOwner возвращает true когда пользователь является владельцем`() {
        val route = createRoute(routeId, ownerId, RouteStatus.DRAFT)
        whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

        StepVerifier.create(ownershipService.isOwner(routeId, ownerId))
            .expectNext(true)
            .verifyComplete()
    }

    @Test
    fun `isOwner возвращает false когда пользователь не является владельцем`() {
        val route = createRoute(routeId, ownerId, RouteStatus.DRAFT)
        whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

        StepVerifier.create(ownershipService.isOwner(routeId, otherId))
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    fun `isOwner возвращает false когда маршрут не найден`() {
        whenever(routeRepository.findById(routeId)).thenReturn(Mono.empty())

        StepVerifier.create(ownershipService.isOwner(routeId, ownerId))
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    fun `isOwner возвращает false когда createdBy равен null`() {
        // Маршрут без владельца (createdBy = null)
        val routeWithoutOwner = createRouteWithoutOwner(routeId, RouteStatus.DRAFT)
        whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(routeWithoutOwner))

        StepVerifier.create(ownershipService.isOwner(routeId, ownerId))
            .expectNext(false)
            .verifyComplete()
    }

    // ============================================
    // canModifyRoute тесты
    // ============================================

    @Test
    fun `canModifyRoute возвращает true для владельца`() {
        val route = createRoute(routeId, ownerId, RouteStatus.DRAFT)
        whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

        StepVerifier.create(ownershipService.canModifyRoute(routeId, ownerId))
            .expectNext(true)
            .verifyComplete()
    }

    @Test
    fun `canModifyRoute возвращает false для не-владельца`() {
        val route = createRoute(routeId, ownerId, RouteStatus.DRAFT)
        whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

        StepVerifier.create(ownershipService.canModifyRoute(routeId, otherId))
            .expectNext(false)
            .verifyComplete()
    }

    // ============================================
    // canDeleteRoute тесты
    // ============================================

    @Test
    fun `canDeleteRoute возвращает Allowed для владельца draft маршрута`() {
        val route = createRoute(routeId, ownerId, RouteStatus.DRAFT)
        whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

        StepVerifier.create(ownershipService.canDeleteRoute(routeId, ownerId))
            .expectNext(DeleteCheckResult.Allowed)
            .verifyComplete()
    }

    @Test
    fun `canDeleteRoute возвращает NotOwner когда пользователь не владелец`() {
        val route = createRoute(routeId, ownerId, RouteStatus.DRAFT)
        whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

        StepVerifier.create(ownershipService.canDeleteRoute(routeId, otherId))
            .expectNext(DeleteCheckResult.NotOwner)
            .verifyComplete()
    }

    @Test
    fun `canDeleteRoute возвращает NotDraft для маршрута в статусе PENDING`() {
        val route = createRoute(routeId, ownerId, RouteStatus.PENDING)
        whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

        StepVerifier.create(ownershipService.canDeleteRoute(routeId, ownerId))
            .expectNextMatches { it is DeleteCheckResult.NotDraft && it.currentStatus == RouteStatus.PENDING }
            .verifyComplete()
    }

    @Test
    fun `canDeleteRoute возвращает NotDraft для маршрута в статусе PUBLISHED`() {
        val route = createRoute(routeId, ownerId, RouteStatus.PUBLISHED)
        whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(route))

        StepVerifier.create(ownershipService.canDeleteRoute(routeId, ownerId))
            .expectNextMatches { it is DeleteCheckResult.NotDraft && it.currentStatus == RouteStatus.PUBLISHED }
            .verifyComplete()
    }

    @Test
    fun `canDeleteRoute возвращает NotFound когда маршрут не существует`() {
        whenever(routeRepository.findById(routeId)).thenReturn(Mono.empty())

        StepVerifier.create(ownershipService.canDeleteRoute(routeId, ownerId))
            .expectNext(DeleteCheckResult.NotFound)
            .verifyComplete()
    }

    @Test
    fun `canDeleteRoute возвращает NotOwner когда createdBy равен null`() {
        // Маршрут без владельца (createdBy = null)
        val routeWithoutOwner = createRouteWithoutOwner(routeId, RouteStatus.DRAFT)
        whenever(routeRepository.findById(routeId)).thenReturn(Mono.just(routeWithoutOwner))

        StepVerifier.create(ownershipService.canDeleteRoute(routeId, ownerId))
            .expectNext(DeleteCheckResult.NotOwner)
            .verifyComplete()
    }

    // ============================================
    // Вспомогательные методы
    // ============================================

    private fun createRoute(id: UUID, createdBy: UUID, status: RouteStatus): Route {
        return Route(
            id = id,
            path = "/api/test",
            upstreamUrl = "http://localhost:8080",
            methods = listOf("GET"),
            status = status,
            createdBy = createdBy,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    private fun createRouteWithoutOwner(id: UUID, status: RouteStatus): Route {
        return Route(
            id = id,
            path = "/api/test",
            upstreamUrl = "http://localhost:8080",
            methods = listOf("GET"),
            status = status,
            createdBy = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}
