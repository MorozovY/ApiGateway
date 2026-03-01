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

/**
 * Unit тесты для ConsumerRateLimitEventPublisher.
 *
 * Story 12.8, AC2: Consumer rate limits синхронизируются немедленно
 * Story 13.10: Добавлен prefix "gateway:" для изоляции в централизованном Redis
 */
@ExtendWith(MockitoExtension::class)
class ConsumerRateLimitEventPublisherTest {

    @Mock
    private lateinit var redisTemplate: ReactiveStringRedisTemplate

    @Test
    fun `публикует событие изменения consumer rate limit в Redis`() {
        // Given
        val consumerId = "test-consumer-123"
        val publisher = ConsumerRateLimitEventPublisher(redisTemplate)

        whenever(redisTemplate.convertAndSend(
            eq(ConsumerRateLimitEventPublisher.CONSUMER_RATELIMIT_CACHE_CHANNEL),
            eq(consumerId)
        )).thenReturn(Mono.just(1L))

        // When & Then
        StepVerifier.create(publisher.publishConsumerRateLimitChanged(consumerId))
            .expectNext(1L)
            .verifyComplete()

        verify(redisTemplate).convertAndSend(
            eq(ConsumerRateLimitEventPublisher.CONSUMER_RATELIMIT_CACHE_CHANNEL),
            eq(consumerId)
        )
    }

    @Test
    fun `возвращает 0L когда Redis недоступен`() {
        // Given
        val consumerId = "test-consumer-456"
        val publisher = ConsumerRateLimitEventPublisher(null)

        // When & Then
        StepVerifier.create(publisher.publishConsumerRateLimitChanged(consumerId))
            .expectNext(0L)
            .verifyComplete()
    }

    @Test
    fun `обрабатывает ошибку Redis gracefully`() {
        // Given
        val consumerId = "test-consumer-789"
        val publisher = ConsumerRateLimitEventPublisher(redisTemplate)

        whenever(redisTemplate.convertAndSend(any<String>(), any<String>()))
            .thenReturn(Mono.error(RuntimeException("Redis connection failed")))

        // When & Then
        StepVerifier.create(publisher.publishConsumerRateLimitChanged(consumerId))
            .expectError(RuntimeException::class.java)
            .verify()
    }

    @Test
    fun `использует правильный канал с gateway prefix`() {
        // Given
        val consumerId = "premium-user"
        val publisher = ConsumerRateLimitEventPublisher(redisTemplate)

        // Story 13.10: проверяем что канал содержит prefix "gateway:"
        whenever(redisTemplate.convertAndSend(
            eq(ConsumerRateLimitEventPublisher.CONSUMER_RATELIMIT_CACHE_CHANNEL),
            any<String>()
        )).thenReturn(Mono.just(3L))

        // When & Then
        StepVerifier.create(publisher.publishConsumerRateLimitChanged(consumerId))
            .expectNext(3L)
            .verifyComplete()

        // Проверяем что используется правильный канал с gateway: prefix
        verify(redisTemplate).convertAndSend(
            eq(ConsumerRateLimitEventPublisher.CONSUMER_RATELIMIT_CACHE_CHANNEL),
            eq(consumerId)
        )

        // Дополнительная проверка: канал должен начинаться с "gateway:"
        assert(ConsumerRateLimitEventPublisher.CONSUMER_RATELIMIT_CACHE_CHANNEL.startsWith("gateway:")) {
            "Channel должен начинаться с 'gateway:' для изоляции в централизованном Redis"
        }
    }

    @Test
    fun `публикует событие для разных типов consumer ID`() {
        // Given
        val publisher = ConsumerRateLimitEventPublisher(redisTemplate)

        whenever(redisTemplate.convertAndSend(any<String>(), any<String>()))
            .thenReturn(Mono.just(1L))

        // Test с UUID-like consumer ID
        StepVerifier.create(publisher.publishConsumerRateLimitChanged("550e8400-e29b-41d4-a716-446655440000"))
            .expectNext(1L)
            .verifyComplete()

        // Test с email-like consumer ID
        StepVerifier.create(publisher.publishConsumerRateLimitChanged("user@example.com"))
            .expectNext(1L)
            .verifyComplete()

        // Test с simple string consumer ID
        StepVerifier.create(publisher.publishConsumerRateLimitChanged("api-key-abc123"))
            .expectNext(1L)
            .verifyComplete()
    }
}
