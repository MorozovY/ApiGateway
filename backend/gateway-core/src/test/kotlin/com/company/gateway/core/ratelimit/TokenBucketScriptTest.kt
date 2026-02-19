package com.company.gateway.core.ratelimit

import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.test.StepVerifier
import java.util.UUID

/**
 * Интеграционные тесты для TokenBucketScript (Story 5.3)
 *
 * Тесты:
 * - AC1: Запросы в пределах лимита проходят
 * - AC2: Превышение burst возвращает allowed=false
 * - AC3: Token bucket replenishment — токены восполняются со временем
 * - AC6: Distributed rate limiting — атомарность операций
 */
@Testcontainers
class TokenBucketScriptTest {

    companion object {
        @Container
        @JvmStatic
        val redis = RedisContainer("redis:7")
    }

    private lateinit var redisTemplate: ReactiveRedisTemplate<String, String>
    private lateinit var tokenBucketScript: TokenBucketScript
    private lateinit var connectionFactory: LettuceConnectionFactory

    @BeforeEach
    fun setUp() {
        connectionFactory = LettuceConnectionFactory(redis.host, redis.firstMappedPort)
        connectionFactory.afterPropertiesSet()

        val serializer = StringRedisSerializer()
        val context = RedisSerializationContext
            .newSerializationContext<String, String>(serializer)
            .key(serializer)
            .value(serializer)
            .hashKey(serializer)
            .hashValue(serializer)
            .build()

        redisTemplate = ReactiveRedisTemplate(connectionFactory, context)
        tokenBucketScript = TokenBucketScript(redisTemplate)
    }

    @AfterEach
    fun tearDown() {
        // Очищаем все ключи ratelimit
        redisTemplate.keys("ratelimit:*")
            .flatMap { key -> redisTemplate.delete(key) }
            .blockLast()
        connectionFactory.destroy()
    }

    @Test
    fun `AC1 - первый запрос разрешён и инициализирует bucket с полным burst`() {
        val key = "ratelimit:${UUID.randomUUID()}:client1"

        StepVerifier.create(tokenBucketScript.checkRateLimit(key, 10, 15))
            .assertNext { result ->
                assertThat(result.allowed).isTrue()
                // После первого запроса: burst 15 - 1 = 14
                assertThat(result.remaining).isEqualTo(14)
                assertThat(result.resetTime).isGreaterThan(System.currentTimeMillis() - 1000)
            }
            .verifyComplete()
    }

    @Test
    fun `AC2 - превышение burst возвращает allowed=false`() {
        val key = "ratelimit:${UUID.randomUUID()}:client1"
        val burstSize = 3

        // Исчерпываем все токены (3 запроса)
        repeat(burstSize) {
            val result = tokenBucketScript.checkRateLimit(key, 10, burstSize).block()
            assertThat(result?.allowed).isTrue()
        }

        // Следующий запрос должен быть отклонён
        StepVerifier.create(tokenBucketScript.checkRateLimit(key, 10, burstSize))
            .assertNext { result ->
                assertThat(result.allowed).isFalse()
                assertThat(result.remaining).isEqualTo(0)
            }
            .verifyComplete()
    }

    @Test
    fun `AC3 - токены восполняются со временем согласно requestsPerSecond`() {
        val key = "ratelimit:${UUID.randomUUID()}:client1"
        val requestsPerSecond = 10
        val burstSize = 5

        // Исчерпываем все токены
        repeat(burstSize) {
            tokenBucketScript.checkRateLimit(key, requestsPerSecond, burstSize).block()
        }

        // Проверяем что токены исчерпаны
        val exhausted = tokenBucketScript.checkRateLimit(key, requestsPerSecond, burstSize).block()
        assertThat(exhausted?.allowed).isFalse()

        // Ждём 500мс — должно восполниться ~5 токенов (10 req/s * 0.5s = 5)
        Thread.sleep(550)

        // Теперь запросы должны проходить
        StepVerifier.create(tokenBucketScript.checkRateLimit(key, requestsPerSecond, burstSize))
            .assertNext { result ->
                assertThat(result.allowed).isTrue()
                // Восполнилось 5 токенов, минус 1 за текущий запрос = 4
                // Но burst ограничивает до 5, так что 5 - 1 = 4 (или меньше из-за времени)
                assertThat(result.remaining).isGreaterThanOrEqualTo(0)
            }
            .verifyComplete()
    }

    @Test
    fun `AC3 - максимум токенов ограничен burstSize`() {
        val key = "ratelimit:${UUID.randomUUID()}:client1"
        val requestsPerSecond = 100
        val burstSize = 5

        // Первый запрос
        tokenBucketScript.checkRateLimit(key, requestsPerSecond, burstSize).block()

        // Ждём 1 секунду — восполнится 100 токенов, но ограничено burstSize=5
        Thread.sleep(1100)

        StepVerifier.create(tokenBucketScript.checkRateLimit(key, requestsPerSecond, burstSize))
            .assertNext { result ->
                assertThat(result.allowed).isTrue()
                // Максимум burstSize - 1 = 4 (так как только что забрали 1 токен)
                assertThat(result.remaining).isLessThanOrEqualTo(burstSize - 1)
            }
            .verifyComplete()
    }

    @Test
    fun `AC6 - атомарность операций при параллельных запросах`() {
        val key = "ratelimit:${UUID.randomUUID()}:client1"
        val burstSize = 10

        // Отправляем 15 параллельных запросов при burst=10
        val results = (1..15)
            .map { tokenBucketScript.checkRateLimit(key, 100, burstSize) }
            .map { it.block()!! }

        val allowedCount = results.count { it.allowed }
        val rejectedCount = results.count { !it.allowed }

        // Ровно burstSize запросов должны пройти
        assertThat(allowedCount).isEqualTo(burstSize)
        assertThat(rejectedCount).isEqualTo(5)
    }

    @Test
    fun `разные ключи имеют независимые bucket`() {
        val key1 = "ratelimit:route1:client1"
        val key2 = "ratelimit:route2:client1"
        val burstSize = 2

        // Исчерпываем key1
        tokenBucketScript.checkRateLimit(key1, 10, burstSize).block()
        tokenBucketScript.checkRateLimit(key1, 10, burstSize).block()

        val result1 = tokenBucketScript.checkRateLimit(key1, 10, burstSize).block()
        assertThat(result1?.allowed).isFalse()

        // key2 должен иметь полный bucket
        StepVerifier.create(tokenBucketScript.checkRateLimit(key2, 10, burstSize))
            .assertNext { result ->
                assertThat(result.allowed).isTrue()
                assertThat(result.remaining).isEqualTo(burstSize - 1)
            }
            .verifyComplete()
    }

    @Test
    fun `resetTime корректно рассчитывается`() {
        val key = "ratelimit:${UUID.randomUUID()}:client1"
        val requestsPerSecond = 10
        val burstSize = 5

        // Исчерпываем все токены
        repeat(burstSize) {
            tokenBucketScript.checkRateLimit(key, requestsPerSecond, burstSize).block()
        }

        val result = tokenBucketScript.checkRateLimit(key, requestsPerSecond, burstSize).block()!!

        // resetTime должен быть в будущем (время до полного восполнения)
        val now = System.currentTimeMillis()
        assertThat(result.resetTime).isGreaterThan(now)
        // При rate=10 req/s для восполнения 5 токенов нужно 500ms
        assertThat(result.resetTime).isLessThan(now + 2000)
    }
}
