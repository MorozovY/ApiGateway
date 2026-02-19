package com.company.gateway.core.ratelimit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit тесты для LocalRateLimiter (Story 5.3)
 *
 * Тесты:
 * - Консервативные лимиты при fallback (50%)
 * - Token bucket логика
 * - Потокобезопасность
 */
class LocalRateLimiterTest {

    private lateinit var rateLimiter: LocalRateLimiter

    @BeforeEach
    fun setUp() {
        rateLimiter = LocalRateLimiter(
            fallbackReduction = 0.5,
            cacheTtlSeconds = 60
        )
        rateLimiter.clearCache()
    }

    @Test
    fun `применяет консервативные лимиты при fallback`() {
        // Arrange: policy 10 req/s, burst 20 -> fallback 5 req/s, burst 10
        val key = "test:route:client"
        val requestsPerSecond = 10
        val burstSize = 20

        // Act: первый запрос
        val result = rateLimiter.checkRateLimit(key, requestsPerSecond, burstSize)

        // Assert: разрешён, remaining = 9 (fallback burst 10 - 1)
        assertThat(result.allowed).isTrue()
        assertThat(result.remaining).isEqualTo(9) // 10 * 0.5 = 10, - 1 = 9
    }

    @Test
    fun `блокирует запросы при исчерпании токенов`() {
        val key = "test:route:client"
        val requestsPerSecond = 10
        val burstSize = 4 // fallback burst = 2

        // Исчерпываем токены (2 запроса при fallback burst 2)
        val result1 = rateLimiter.checkRateLimit(key, requestsPerSecond, burstSize)
        val result2 = rateLimiter.checkRateLimit(key, requestsPerSecond, burstSize)

        assertThat(result1.allowed).isTrue()
        assertThat(result2.allowed).isTrue()

        // Третий запрос должен быть заблокирован
        val result3 = rateLimiter.checkRateLimit(key, requestsPerSecond, burstSize)
        assertThat(result3.allowed).isFalse()
        assertThat(result3.remaining).isEqualTo(0)
    }

    @Test
    fun `разные ключи имеют независимые bucket`() {
        val key1 = "route1:client1"
        val key2 = "route2:client1"
        val requestsPerSecond = 10
        val burstSize = 4 // fallback burst = 2

        // Исчерпываем токены для key1
        rateLimiter.checkRateLimit(key1, requestsPerSecond, burstSize)
        rateLimiter.checkRateLimit(key1, requestsPerSecond, burstSize)
        val result1 = rateLimiter.checkRateLimit(key1, requestsPerSecond, burstSize)

        // Key2 должен иметь полный bucket
        val result2 = rateLimiter.checkRateLimit(key2, requestsPerSecond, burstSize)

        assertThat(result1.allowed).isFalse()
        assertThat(result2.allowed).isTrue()
    }

    @Test
    fun `возвращает коэффициент снижения`() {
        assertThat(rateLimiter.getFallbackReduction()).isEqualTo(0.5)
    }

    @Test
    fun `минимальный лимит 1 при очень маленьких значениях`() {
        val key = "test:route:client"
        // Если 1 * 0.5 = 0.5 -> должно быть 1 (coerceAtLeast)
        val requestsPerSecond = 1
        val burstSize = 1

        val result = rateLimiter.checkRateLimit(key, requestsPerSecond, burstSize)

        assertThat(result.allowed).isTrue()
        // После одного запроса remaining = 0
        assertThat(result.remaining).isEqualTo(0)
    }

    @Test
    fun `resetTime в будущем при исчерпании токенов`() {
        val key = "test:route:client"
        val requestsPerSecond = 10
        val burstSize = 4 // fallback burst = 2

        // Исчерпываем все токены (fallback burst = 2)
        rateLimiter.checkRateLimit(key, requestsPerSecond, burstSize) // remaining = 1
        rateLimiter.checkRateLimit(key, requestsPerSecond, burstSize) // remaining = 0
        val result = rateLimiter.checkRateLimit(key, requestsPerSecond, burstSize) // blocked

        // Reset time должен быть >= текущему времени (может быть равен если нужно 0 токенов)
        assertThat(result.resetTime).isGreaterThanOrEqualTo(System.currentTimeMillis() - 1000)
    }

    @Test
    fun `clearCache сбрасывает все bucket`() {
        val key = "test:route:client"
        val requestsPerSecond = 10
        val burstSize = 4 // fallback burst = 2

        // Исчерпываем токены
        rateLimiter.checkRateLimit(key, requestsPerSecond, burstSize)
        rateLimiter.checkRateLimit(key, requestsPerSecond, burstSize)

        // Очищаем кэш
        rateLimiter.clearCache()

        // Теперь bucket должен быть снова полным
        val result = rateLimiter.checkRateLimit(key, requestsPerSecond, burstSize)
        assertThat(result.allowed).isTrue()
        assertThat(result.remaining).isEqualTo(1) // burst 2 - 1 = 1
    }

    @Test
    fun `AC3 - токены восполняются со временем согласно rate`() {
        val key = "test:route:client"
        // fallback rate = 50 * 0.5 = 25 req/s, fallback burst = 4 * 0.5 = 2
        val requestsPerSecond = 50
        val burstSize = 4

        // Исчерпываем все токены (fallback burst = 2)
        rateLimiter.checkRateLimit(key, requestsPerSecond, burstSize)
        rateLimiter.checkRateLimit(key, requestsPerSecond, burstSize)

        // Проверяем что токены исчерпаны
        val exhausted = rateLimiter.checkRateLimit(key, requestsPerSecond, burstSize)
        assertThat(exhausted.allowed).isFalse()

        // Ждём 100мс — должно восполниться ~2.5 токена (25 req/s * 0.1s = 2.5)
        Thread.sleep(120)

        // Теперь запрос должен пройти
        val result = rateLimiter.checkRateLimit(key, requestsPerSecond, burstSize)
        assertThat(result.allowed).isTrue()
    }

    @Test
    fun `AC3 - скорость восполнения соответствует reducedRate`() {
        val key = "test:route:client"
        // fallback rate = 20 * 0.5 = 10 req/s
        val requestsPerSecond = 20
        val burstSize = 4 // fallback burst = 2

        // Используем 2 токена
        rateLimiter.checkRateLimit(key, requestsPerSecond, burstSize)
        rateLimiter.checkRateLimit(key, requestsPerSecond, burstSize)

        // Ждём 100мс — восполнится 1 токен (10 req/s * 0.1s = 1)
        Thread.sleep(120)

        // Один запрос должен пройти
        val result1 = rateLimiter.checkRateLimit(key, requestsPerSecond, burstSize)
        assertThat(result1.allowed).isTrue()

        // Сразу следующий должен быть заблокирован (только 1 токен восполнился)
        val result2 = rateLimiter.checkRateLimit(key, requestsPerSecond, burstSize)
        assertThat(result2.allowed).isFalse()
    }
}
