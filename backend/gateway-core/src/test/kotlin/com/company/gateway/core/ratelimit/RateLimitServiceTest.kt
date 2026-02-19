package com.company.gateway.core.ratelimit

import com.company.gateway.common.model.RateLimit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * Unit тесты для RateLimitService (Story 5.3)
 *
 * Тесты:
 * - Проверка rate limit через Redis
 * - Переключение на fallback при ошибке Redis (AC4)
 * - Возврат к Redis после восстановления
 * - Логирование при переключении режимов
 */
@ExtendWith(MockitoExtension::class)
class RateLimitServiceTest {

    @Mock
    private lateinit var tokenBucketScript: TokenBucketScript

    @Mock
    private lateinit var localRateLimiter: LocalRateLimiter

    private lateinit var service: RateLimitService

    private val testRouteId = UUID.randomUUID()
    private val testClientKey = "192.168.1.1"
    private val testRateLimit = RateLimit(
        id = UUID.randomUUID(),
        name = "test-policy",
        requestsPerSecond = 10,
        burstSize = 15,
        createdBy = UUID.randomUUID(),
        createdAt = Instant.now()
    )

    @BeforeEach
    fun setUp() {
        service = RateLimitService(
            tokenBucketScript = tokenBucketScript,
            localRateLimiter = localRateLimiter,
            fallbackEnabled = true,
            redisKeyPrefix = "ratelimit"
        )
    }

    @Test
    fun `успешная проверка через Redis возвращает результат`() {
        // Arrange
        val expectedResult = RateLimitResult(allowed = true, remaining = 14, resetTime = System.currentTimeMillis())
        whenever(tokenBucketScript.checkRateLimit(any(), eq(10), eq(15), any()))
            .thenReturn(Mono.just(expectedResult))

        // Act & Assert
        StepVerifier.create(service.checkRateLimit(testRouteId, testClientKey, testRateLimit))
            .assertNext { result ->
                assertThat(result.allowed).isTrue()
                assertThat(result.remaining).isEqualTo(14)
            }
            .verifyComplete()

        verify(localRateLimiter, never()).checkRateLimit(any(), any(), any())
    }

    @Test
    fun `при ошибке Redis переключается на локальный fallback (AC4)`() {
        // Arrange
        whenever(tokenBucketScript.checkRateLimit(any(), any(), any(), any()))
            .thenReturn(Mono.error(RuntimeException("Redis connection failed")))

        val fallbackResult = RateLimitResult(allowed = true, remaining = 7, resetTime = System.currentTimeMillis())
        whenever(localRateLimiter.checkRateLimit(any(), eq(10), eq(15)))
            .thenReturn(fallbackResult)

        // Act & Assert
        StepVerifier.create(service.checkRateLimit(testRouteId, testClientKey, testRateLimit))
            .assertNext { result ->
                assertThat(result.allowed).isTrue()
                assertThat(result.remaining).isEqualTo(7)
            }
            .verifyComplete()

        // Проверяем что fallback был вызван
        verify(localRateLimiter).checkRateLimit(any(), eq(10), eq(15))
        assertThat(service.isUsingFallback()).isTrue()
    }

    @Test
    fun `при отключённом fallback пропускает запрос при ошибке Redis`() {
        // Arrange: fallback отключён
        val serviceNoFallback = RateLimitService(
            tokenBucketScript = tokenBucketScript,
            localRateLimiter = localRateLimiter,
            fallbackEnabled = false,
            redisKeyPrefix = "ratelimit"
        )

        whenever(tokenBucketScript.checkRateLimit(any(), any(), any(), any()))
            .thenReturn(Mono.error(RuntimeException("Redis connection failed")))

        // Act & Assert
        StepVerifier.create(serviceNoFallback.checkRateLimit(testRouteId, testClientKey, testRateLimit))
            .assertNext { result ->
                // Запрос пропускается (graceful degradation)
                assertThat(result.allowed).isTrue()
                assertThat(result.remaining).isEqualTo(testRateLimit.burstSize)
            }
            .verifyComplete()

        // Локальный limiter не должен вызываться
        verify(localRateLimiter, never()).checkRateLimit(any(), any(), any())
    }

    @Test
    fun `возвращается к Redis после восстановления соединения`() {
        // Arrange: сначала ошибка Redis
        whenever(tokenBucketScript.checkRateLimit(any(), any(), any(), any()))
            .thenReturn(Mono.error(RuntimeException("Redis connection failed")))

        val fallbackResult = RateLimitResult(allowed = true, remaining = 7, resetTime = System.currentTimeMillis())
        whenever(localRateLimiter.checkRateLimit(any(), any(), any()))
            .thenReturn(fallbackResult)

        // Первый вызов — переключение на fallback
        service.checkRateLimit(testRouteId, testClientKey, testRateLimit).block()
        assertThat(service.isUsingFallback()).isTrue()

        // Arrange: Redis восстановлен
        val redisResult = RateLimitResult(allowed = true, remaining = 14, resetTime = System.currentTimeMillis())
        whenever(tokenBucketScript.checkRateLimit(any(), any(), any(), any()))
            .thenReturn(Mono.just(redisResult))

        // Act: второй вызов — возврат к Redis
        StepVerifier.create(service.checkRateLimit(testRouteId, testClientKey, testRateLimit))
            .assertNext { result ->
                assertThat(result.remaining).isEqualTo(14)
            }
            .verifyComplete()

        // Assert: флаг fallback сброшен
        assertThat(service.isUsingFallback()).isFalse()
    }

    @Test
    fun `формирует правильный ключ Redis`() {
        // Arrange
        val expectedKey = "ratelimit:$testRouteId:$testClientKey"
        val result = RateLimitResult(allowed = true, remaining = 10, resetTime = System.currentTimeMillis())
        whenever(tokenBucketScript.checkRateLimit(eq(expectedKey), any(), any(), any()))
            .thenReturn(Mono.just(result))

        // Act
        service.checkRateLimit(testRouteId, testClientKey, testRateLimit).block()

        // Assert
        verify(tokenBucketScript).checkRateLimit(eq(expectedKey), any(), any(), any())
    }

    @Test
    fun `isUsingFallback возвращает false изначально`() {
        assertThat(service.isUsingFallback()).isFalse()
    }
}
