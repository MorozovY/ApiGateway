package com.company.gateway.core.ratelimit

import com.company.gateway.common.model.ConsumerRateLimit
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

    // ============ Story 12.8: Two-level Rate Limiting ============

    @Test
    fun `Story 12-8 AC4 - checkBothLimits возвращает consumer limit когда он строже`() {
        // Arrange
        val consumerRateLimit = ConsumerRateLimit(
            consumerId = "test-consumer",
            requestsPerSecond = 5,
            burstSize = 10,
            createdAt = Instant.now()
        )

        // Route лимит разрешён с remaining = 10
        val routeResult = RateLimitResult(allowed = true, remaining = 10, resetTime = System.currentTimeMillis() + 1000)
        whenever(tokenBucketScript.checkRateLimit(any(), eq(10), eq(15), any()))
            .thenReturn(Mono.just(routeResult))

        // Consumer лимит разрешён с remaining = 4 (строже)
        val consumerResult = RateLimitResult(allowed = true, remaining = 4, resetTime = System.currentTimeMillis() + 1000)
        whenever(tokenBucketScript.checkRateLimit(any(), eq(5), eq(10), any()))
            .thenReturn(Mono.just(consumerResult))

        // Act & Assert
        StepVerifier.create(service.checkBothLimits(testRouteId, testClientKey, "test-consumer", testRateLimit, consumerRateLimit))
            .assertNext { checkResult ->
                // Возвращается consumer limit (строже)
                assertThat(checkResult.result.allowed).isTrue()
                assertThat(checkResult.result.remaining).isEqualTo(4)
                assertThat(checkResult.limitType).isEqualTo(RateLimitCheckResult.TYPE_CONSUMER)
                assertThat(checkResult.limit).isEqualTo(5)
            }
            .verifyComplete()
    }

    @Test
    fun `Story 12-8 AC4 - checkBothLimits возвращает route limit когда он строже`() {
        // Arrange
        val consumerRateLimit = ConsumerRateLimit(
            consumerId = "generous-consumer",
            requestsPerSecond = 100,
            burstSize = 150,
            createdAt = Instant.now()
        )

        // Route лимит разрешён с remaining = 3 (строже)
        val routeResult = RateLimitResult(allowed = true, remaining = 3, resetTime = System.currentTimeMillis() + 1000)
        whenever(tokenBucketScript.checkRateLimit(any(), eq(10), eq(15), any()))
            .thenReturn(Mono.just(routeResult))

        // Consumer лимит разрешён с remaining = 100
        val consumerResult = RateLimitResult(allowed = true, remaining = 100, resetTime = System.currentTimeMillis() + 1000)
        whenever(tokenBucketScript.checkRateLimit(any(), eq(100), eq(150), any()))
            .thenReturn(Mono.just(consumerResult))

        // Act & Assert
        StepVerifier.create(service.checkBothLimits(testRouteId, testClientKey, "generous-consumer", testRateLimit, consumerRateLimit))
            .assertNext { checkResult ->
                // Возвращается route limit (строже)
                assertThat(checkResult.result.allowed).isTrue()
                assertThat(checkResult.result.remaining).isEqualTo(3)
                assertThat(checkResult.limitType).isEqualTo(RateLimitCheckResult.TYPE_ROUTE)
                assertThat(checkResult.limit).isEqualTo(10)
            }
            .verifyComplete()
    }

    @Test
    fun `Story 12-8 AC3 - checkBothLimits отклоняет когда consumer limit превышен`() {
        // Arrange
        val consumerRateLimit = ConsumerRateLimit(
            consumerId = "limited-consumer",
            requestsPerSecond = 2,
            burstSize = 3,
            createdAt = Instant.now()
        )

        // Route лимит разрешён
        val routeResult = RateLimitResult(allowed = true, remaining = 10, resetTime = System.currentTimeMillis() + 1000)
        whenever(tokenBucketScript.checkRateLimit(any(), eq(10), eq(15), any()))
            .thenReturn(Mono.just(routeResult))

        // Consumer лимит превышен
        val consumerResult = RateLimitResult(allowed = false, remaining = 0, resetTime = System.currentTimeMillis() + 5000)
        whenever(tokenBucketScript.checkRateLimit(any(), eq(2), eq(3), any()))
            .thenReturn(Mono.just(consumerResult))

        // Act & Assert
        StepVerifier.create(service.checkBothLimits(testRouteId, testClientKey, "limited-consumer", testRateLimit, consumerRateLimit))
            .assertNext { checkResult ->
                // Отказ с consumer type
                assertThat(checkResult.result.allowed).isFalse()
                assertThat(checkResult.limitType).isEqualTo(RateLimitCheckResult.TYPE_CONSUMER)
                assertThat(checkResult.limit).isEqualTo(2)
            }
            .verifyComplete()
    }

    @Test
    fun `Story 12-8 - checkBothLimits отклоняет когда route limit превышен`() {
        // Arrange
        val consumerRateLimit = ConsumerRateLimit(
            consumerId = "test-consumer",
            requestsPerSecond = 100,
            burstSize = 150,
            createdAt = Instant.now()
        )

        // Route лимит превышен
        val routeResult = RateLimitResult(allowed = false, remaining = 0, resetTime = System.currentTimeMillis() + 5000)
        whenever(tokenBucketScript.checkRateLimit(any(), eq(10), eq(15), any()))
            .thenReturn(Mono.just(routeResult))

        // Consumer лимит разрешён
        val consumerResult = RateLimitResult(allowed = true, remaining = 99, resetTime = System.currentTimeMillis() + 1000)
        whenever(tokenBucketScript.checkRateLimit(any(), eq(100), eq(150), any()))
            .thenReturn(Mono.just(consumerResult))

        // Act & Assert
        StepVerifier.create(service.checkBothLimits(testRouteId, testClientKey, "test-consumer", testRateLimit, consumerRateLimit))
            .assertNext { checkResult ->
                // Отказ с route type
                assertThat(checkResult.result.allowed).isFalse()
                assertThat(checkResult.limitType).isEqualTo(RateLimitCheckResult.TYPE_ROUTE)
            }
            .verifyComplete()
    }

    @Test
    fun `Story 12-8 - checkBothLimits работает без route limit`() {
        // Arrange: только consumer limit, без route limit
        val consumerRateLimit = ConsumerRateLimit(
            consumerId = "test-consumer",
            requestsPerSecond = 5,
            burstSize = 10,
            createdAt = Instant.now()
        )

        // Consumer лимит разрешён
        val consumerResult = RateLimitResult(allowed = true, remaining = 8, resetTime = System.currentTimeMillis() + 1000)
        whenever(tokenBucketScript.checkRateLimit(any(), eq(5), eq(10), any()))
            .thenReturn(Mono.just(consumerResult))

        // Act & Assert: routeLimit = null
        StepVerifier.create(service.checkBothLimits(null, testClientKey, "test-consumer", null, consumerRateLimit))
            .assertNext { checkResult ->
                // Возвращается только consumer limit
                assertThat(checkResult.result.allowed).isTrue()
                assertThat(checkResult.result.remaining).isEqualTo(8)
                assertThat(checkResult.limitType).isEqualTo(RateLimitCheckResult.TYPE_CONSUMER)
                assertThat(checkResult.limit).isEqualTo(5)
            }
            .verifyComplete()
    }

    @Test
    fun `Story 12-8 - checkConsumerRateLimit формирует правильный Redis ключ`() {
        // Arrange
        val consumerRateLimit = ConsumerRateLimit(
            consumerId = "test-consumer",
            requestsPerSecond = 10,
            burstSize = 20,
            createdAt = Instant.now()
        )
        val expectedKey = "ratelimit:consumer:test-consumer"
        val result = RateLimitResult(allowed = true, remaining = 19, resetTime = System.currentTimeMillis())
        whenever(tokenBucketScript.checkRateLimit(eq(expectedKey), eq(10), eq(20), any()))
            .thenReturn(Mono.just(result))

        // Act
        service.checkConsumerRateLimit("test-consumer", consumerRateLimit).block()

        // Assert
        verify(tokenBucketScript).checkRateLimit(eq(expectedKey), eq(10), eq(20), any())
    }
}
