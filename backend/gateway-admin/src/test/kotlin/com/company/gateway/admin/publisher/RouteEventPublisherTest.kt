package com.company.gateway.admin.publisher

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.UUID

/**
 * Unit тесты для RouteEventPublisher.
 *
 * Story 4.2, AC2: Автоматическая публикация после одобрения маршрута
 * Story 13.10: Добавлен prefix "gateway:" для изоляции в централизованном Redis
 */
@ExtendWith(MockitoExtension::class)
class RouteEventPublisherTest {

    @Mock
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Test
    fun `публикует событие изменения маршрута в Redis`() {
        // Given
        val routeId = UUID.randomUUID()
        val publisher = RouteEventPublisher(redisTemplate)

        whenever(redisTemplate.convertAndSend(
            eq(RouteEventPublisher.ROUTE_CACHE_CHANNEL),
            eq(routeId.toString())
        )).thenReturn(Mono.just(1L))

        // When & Then
        StepVerifier.create(publisher.publishRouteChanged(routeId))
            .expectNext(1L)
            .verifyComplete()

        verify(redisTemplate).convertAndSend(
            eq(RouteEventPublisher.ROUTE_CACHE_CHANNEL),
            eq(routeId.toString())
        )
    }

    @Test
    fun `возвращает 0L когда Redis недоступен`() {
        // Given
        val routeId = UUID.randomUUID()
        val publisher = RouteEventPublisher(null)

        // When & Then
        StepVerifier.create(publisher.publishRouteChanged(routeId))
            .expectNext(0L)
            .verifyComplete()
    }

    @Test
    fun `обрабатывает ошибку Redis gracefully`() {
        // Given
        val routeId = UUID.randomUUID()
        val publisher = RouteEventPublisher(redisTemplate)

        whenever(redisTemplate.convertAndSend(any<String>(), any<String>()))
            .thenReturn(Mono.error(RuntimeException("Redis connection failed")))

        // When & Then
        StepVerifier.create(publisher.publishRouteChanged(routeId))
            .expectError(RuntimeException::class.java)
            .verify()
    }

    @Test
    fun `использует правильный канал с gateway prefix`() {
        // Given
        val routeId = UUID.randomUUID()
        val publisher = RouteEventPublisher(redisTemplate)

        // Story 13.10: проверяем что канал содержит prefix "gateway:"
        whenever(redisTemplate.convertAndSend(
            eq(RouteEventPublisher.ROUTE_CACHE_CHANNEL),
            any<String>()
        )).thenReturn(Mono.just(2L))

        // When & Then
        StepVerifier.create(publisher.publishRouteChanged(routeId))
            .expectNext(2L)
            .verifyComplete()

        // Проверяем что используется правильный канал с gateway: prefix
        verify(redisTemplate).convertAndSend(
            eq(RouteEventPublisher.ROUTE_CACHE_CHANNEL),
            eq(routeId.toString())
        )

        // Дополнительная проверка: канал должен начинаться с "gateway:"
        assert(RouteEventPublisher.ROUTE_CACHE_CHANNEL.startsWith("gateway:")) {
            "Channel должен начинаться с 'gateway:' для изоляции в централизованном Redis"
        }
    }
}
