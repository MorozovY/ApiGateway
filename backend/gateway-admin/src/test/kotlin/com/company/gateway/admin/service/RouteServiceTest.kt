package com.company.gateway.admin.service

import com.company.gateway.admin.dto.RouteWithCreator
import com.company.gateway.admin.repository.RateLimitRepository
import com.company.gateway.admin.repository.RouteRepository
import com.company.gateway.admin.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * Unit тесты для RouteService.findPendingRoutes (Story 4.3).
 *
 * Покрывает AC1, AC2, AC3, AC5:
 * - AC1: Список pending маршрутов с сортировкой по submittedAt asc по умолчанию
 * - AC2: Список pending маршрутов с сортировкой по submittedAt desc
 * - AC3: Пустой список когда нет pending маршрутов
 * - AC5: Пагинация результатов
 */
class RouteServiceTest {

    private lateinit var routeRepository: RouteRepository
    private lateinit var rateLimitRepository: RateLimitRepository
    private lateinit var userRepository: UserRepository
    private lateinit var auditService: AuditService
    private lateinit var routeService: RouteService

    @BeforeEach
    fun setUp() {
        routeRepository = mock()
        rateLimitRepository = mock()
        userRepository = mock()
        auditService = mock()
        routeService = RouteService(routeRepository, rateLimitRepository, userRepository, auditService)
    }

    // ============================================
    // AC1: Список pending маршрутов — сортировка по умолчанию
    // ============================================

    @Nested
    inner class AC1_СписокPendingМаршрутов {

        @Test
        fun `возвращает pending маршруты отсортированные по submittedAt asc по умолчанию`() {
            // Given
            val userId = UUID.randomUUID()
            val olderRoute = createTestRouteWithCreator(
                id = UUID.randomUUID(),
                path = "/api/older",
                submittedAt = Instant.parse("2026-02-17T10:00:00Z"),
                createdBy = userId
            )
            val newerRoute = createTestRouteWithCreator(
                id = UUID.randomUUID(),
                path = "/api/newer",
                submittedAt = Instant.parse("2026-02-17T11:00:00Z"),
                createdBy = userId
            )

            // Репозиторий возвращает в порядке ASC (oldest first)
            whenever(routeRepository.findPendingWithCreator(
                eq("submitted_at"), eq("ASC"), eq(0), eq(20)
            )).thenReturn(Flux.just(olderRoute, newerRoute))
            whenever(routeRepository.countPending()).thenReturn(Mono.just(2L))

            // When
            StepVerifier.create(routeService.findPendingRoutes(null, 0, 20))
                // Then
                .expectNextMatches { response ->
                    response.total == 2L &&
                    response.items.size == 2 &&
                    response.items[0].path == "/api/older" &&
                    response.items[1].path == "/api/newer"
                }
                .verifyComplete()

            // Проверяем что репозиторий вызван с ASC
            verify(routeRepository).findPendingWithCreator("submitted_at", "ASC", 0, 20)
        }

        @Test
        fun `включает информацию о создателе маршрута`() {
            // Given
            val userId = UUID.randomUUID()
            val username = "maria"
            val route = createTestRouteWithCreator(
                id = UUID.randomUUID(),
                path = "/api/orders",
                createdBy = userId,
                creatorUsername = username
            )

            whenever(routeRepository.findPendingWithCreator(any(), any(), any(), any()))
                .thenReturn(Flux.just(route))
            whenever(routeRepository.countPending()).thenReturn(Mono.just(1L))

            // When
            StepVerifier.create(routeService.findPendingRoutes(null, 0, 20))
                // Then
                .expectNextMatches { response ->
                    response.items.size == 1 &&
                    response.items[0].createdBy == userId &&
                    response.items[0].creatorUsername == username
                }
                .verifyComplete()
        }
    }

    // ============================================
    // AC2: Сортировка по submittedAt descending
    // ============================================

    @Nested
    inner class AC2_СортировкаDesc {

        @Test
        fun `возвращает pending маршруты отсортированные по submittedAt desc`() {
            // Given
            val userId = UUID.randomUUID()
            val route = createTestRouteWithCreator(path = "/api/test", createdBy = userId)

            whenever(routeRepository.findPendingWithCreator(
                eq("submitted_at"), eq("DESC"), eq(0), eq(20)
            )).thenReturn(Flux.just(route))
            whenever(routeRepository.countPending()).thenReturn(Mono.just(1L))

            // When
            StepVerifier.create(routeService.findPendingRoutes("submittedAt:desc", 0, 20))
                // Then
                .expectNextMatches { response -> response.total == 1L }
                .verifyComplete()

            // Проверяем что репозиторий вызван с DESC
            verify(routeRepository).findPendingWithCreator("submitted_at", "DESC", 0, 20)
        }

        @Test
        fun `парсит sort параметр submittedAt asc как ASC`() {
            // Given
            whenever(routeRepository.findPendingWithCreator(any(), eq("ASC"), any(), any()))
                .thenReturn(Flux.empty())
            whenever(routeRepository.countPending()).thenReturn(Mono.just(0L))

            // When
            StepVerifier.create(routeService.findPendingRoutes("submittedAt:asc", 0, 20))
                .expectNextCount(1)
                .verifyComplete()

            verify(routeRepository).findPendingWithCreator("submitted_at", "ASC", 0, 20)
        }
    }

    // ============================================
    // AC3: Пустой список pending маршрутов
    // ============================================

    @Nested
    inner class AC3_ПустойСписок {

        @Test
        fun `возвращает пустой список когда нет pending маршрутов`() {
            // Given
            whenever(routeRepository.findPendingWithCreator(any(), any(), any(), any()))
                .thenReturn(Flux.empty())
            whenever(routeRepository.countPending()).thenReturn(Mono.just(0L))

            // When
            StepVerifier.create(routeService.findPendingRoutes(null, 0, 20))
                // Then
                .expectNextMatches { response ->
                    response.items.isEmpty() &&
                    response.total == 0L &&
                    response.offset == 0 &&
                    response.limit == 20
                }
                .verifyComplete()
        }
    }

    // ============================================
    // AC5: Пагинация
    // ============================================

    @Nested
    inner class AC5_Пагинация {

        @Test
        fun `применяет пагинацию к результатам`() {
            // Given
            val userId = UUID.randomUUID()
            val routes = (1..10).map { i ->
                createTestRouteWithCreator(path = "/api/route-$i", createdBy = userId)
            }

            whenever(routeRepository.findPendingWithCreator(any(), any(), eq(0), eq(10)))
                .thenReturn(Flux.fromIterable(routes))
            // Полное количество — 25 маршрутов
            whenever(routeRepository.countPending()).thenReturn(Mono.just(25L))

            // When
            StepVerifier.create(routeService.findPendingRoutes(null, 0, 10))
                // Then
                .expectNextMatches { response ->
                    response.items.size == 10 &&
                    response.total == 25L &&
                    response.offset == 0 &&
                    response.limit == 10
                }
                .verifyComplete()

            // Проверяем что пагинация передана в репозиторий
            verify(routeRepository).findPendingWithCreator("submitted_at", "ASC", 0, 10)
        }

        @Test
        fun `передаёт offset в репозиторий`() {
            // Given
            whenever(routeRepository.findPendingWithCreator(any(), any(), eq(10), eq(10)))
                .thenReturn(Flux.empty())
            whenever(routeRepository.countPending()).thenReturn(Mono.just(25L))

            // When
            StepVerifier.create(routeService.findPendingRoutes(null, 10, 10))
                // Then
                .expectNextMatches { response ->
                    response.offset == 10 &&
                    response.limit == 10 &&
                    response.total == 25L
                }
                .verifyComplete()
        }
    }

    // ============================================
    // Тесты parseSort
    // ============================================

    @Nested
    inner class ParseSort {

        @Test
        fun `parseSort возвращает ASC по умолчанию для null`() {
            val (field, direction) = routeService.parseSort(null)
            assert(field == "submitted_at") { "field должен быть 'submitted_at'" }
            assert(direction == "ASC") { "direction должен быть 'ASC'" }
        }

        @Test
        fun `parseSort возвращает ASC по умолчанию для пустой строки`() {
            val (field, direction) = routeService.parseSort("")
            assert(field == "submitted_at") { "field должен быть 'submitted_at'" }
            assert(direction == "ASC") { "direction должен быть 'ASC'" }
        }

        @Test
        fun `parseSort корректно парсит submittedAt desc`() {
            val (field, direction) = routeService.parseSort("submittedAt:desc")
            assert(field == "submitted_at") { "field должен быть 'submitted_at'" }
            assert(direction == "DESC") { "direction должен быть 'DESC'" }
        }

        @Test
        fun `parseSort корректно парсит submittedAt asc`() {
            val (field, direction) = routeService.parseSort("submittedAt:asc")
            assert(field == "submitted_at") { "field должен быть 'submitted_at'" }
            assert(direction == "ASC") { "direction должен быть 'ASC'" }
        }
    }

    // ============================================
    // Вспомогательные методы
    // ============================================

    private fun createTestRouteWithCreator(
        id: UUID = UUID.randomUUID(),
        path: String = "/api/test",
        createdBy: UUID = UUID.randomUUID(),
        creatorUsername: String = "testuser",
        submittedAt: Instant = Instant.now()
    ): RouteWithCreator {
        return RouteWithCreator(
            id = id,
            path = path,
            upstreamUrl = "http://test-service:8080",
            methods = listOf("GET", "POST"),
            description = "Test route",
            status = "pending",
            createdBy = createdBy,
            creatorUsername = creatorUsername,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            submittedAt = submittedAt
        )
    }
}
