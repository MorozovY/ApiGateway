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
 * Unit тесты для RateLimitEventPublisher.
 *
 * Story 5.8, AC1: Rate limit политики синхронизируются немедленно
 */
@ExtendWith(MockitoExtension::class)
class RateLimitEventPublisherTest {

    @Mock
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Test
    fun `публикует событие изменения политики в Redis`() {
        // Given
        val rateLimitId = UUID.randomUUID()
        val publisher = RateLimitEventPublisher(redisTemplate)

        whenever(redisTemplate.convertAndSend(
            eq(RateLimitEventPublisher.RATELIMIT_CACHE_CHANNEL),
            eq(rateLimitId.toString())
        )).thenReturn(Mono.just(1L))

        // When & Then
        StepVerifier.create(publisher.publishRateLimitChanged(rateLimitId))
            .expectNext(1L)
            .verifyComplete()

        verify(redisTemplate).convertAndSend(
            eq(RateLimitEventPublisher.RATELIMIT_CACHE_CHANNEL),
            eq(rateLimitId.toString())
        )
    }

    @Test
    fun `возвращает 0L когда Redis недоступен`() {
        // Given
        val rateLimitId = UUID.randomUUID()
        val publisher = RateLimitEventPublisher(null)

        // When & Then
        StepVerifier.create(publisher.publishRateLimitChanged(rateLimitId))
            .expectNext(0L)
            .verifyComplete()
    }

    @Test
    fun `обрабатывает ошибку Redis gracefully`() {
        // Given
        val rateLimitId = UUID.randomUUID()
        val publisher = RateLimitEventPublisher(redisTemplate)

        whenever(redisTemplate.convertAndSend(any<String>(), any<String>()))
            .thenReturn(Mono.error(RuntimeException("Redis connection failed")))

        // When & Then
        StepVerifier.create(publisher.publishRateLimitChanged(rateLimitId))
            .expectError(RuntimeException::class.java)
            .verify()
    }

    @Test
    fun `использует правильный канал для публикации`() {
        // Given
        val rateLimitId = UUID.randomUUID()
        val publisher = RateLimitEventPublisher(redisTemplate)

        whenever(redisTemplate.convertAndSend(
            eq("ratelimit-cache-invalidation"),
            any<String>()
        )).thenReturn(Mono.just(2L))

        // When & Then
        StepVerifier.create(publisher.publishRateLimitChanged(rateLimitId))
            .expectNext(2L)
            .verifyComplete()

        // Проверяем что используется правильный канал
        verify(redisTemplate).convertAndSend(
            eq("ratelimit-cache-invalidation"),
            eq(rateLimitId.toString())
        )
    }
}
