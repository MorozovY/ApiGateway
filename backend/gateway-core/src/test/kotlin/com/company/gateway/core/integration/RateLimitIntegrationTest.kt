package com.company.gateway.core.integration

import com.company.gateway.common.model.RouteStatus
import com.company.gateway.core.cache.ConsumerRateLimitCacheManager
import com.company.gateway.core.cache.RouteCacheManager
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID

/**
 * Интеграционные тесты для Rate Limiting (Story 5.3)
 *
 * Тесты:
 * - AC1: Запросы в пределах лимита проходят
 * - AC2: Превышение burst возвращает 429
 * - AC3: Token bucket replenishment (тестируется в TokenBucketScriptTest)
 * - AC4: Graceful degradation при недоступности Redis (тестируется отдельно)
 * - AC5: Маршрут без rate limit проходит без заголовков
 * - AC6: Distributed rate limiting — разные клиенты имеют независимые лимиты
 * - AC7: Rate limit заголовки в успешных ответах
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RateLimitIntegrationTest {

    companion object {
        // Проверяем запущены ли мы в CI
        private val isTestcontainersDisabled = System.getenv("TESTCONTAINERS_DISABLED") == "true"

        // Контейнеры — управляем lifecycle вручную (без @Container/@Testcontainers)
        private var postgres: PostgreSQLContainer<*>? = null
        private var redis: RedisContainer? = null
        private lateinit var wireMock: WireMockServer

        @BeforeAll
        @JvmStatic
        fun startContainers() {
            // Запускаем контейнеры только локально
            if (!isTestcontainersDisabled) {
                postgres = PostgreSQLContainer("postgres:16")
                    .withDatabaseName("gateway")
                    .withUsername("gateway")
                    .withPassword("gateway")
                postgres?.start()

                redis = RedisContainer("redis:7")
                redis?.start()
            }
            // WireMock нужен всегда
            wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
            wireMock.start()
        }

        @AfterAll
        @JvmStatic
        fun stopContainers() {
            wireMock.stop()
            postgres?.stop()
            redis?.stop()
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            if (!isTestcontainersDisabled) {
                // Локально настраиваем Testcontainers
                postgres?.let { pg ->
                    registry.add("spring.r2dbc.url") {
                        "r2dbc:postgresql://${pg.host}:${pg.firstMappedPort}/${pg.databaseName}"
                    }
                    registry.add("spring.r2dbc.username", pg::getUsername)
                    registry.add("spring.r2dbc.password", pg::getPassword)
                    registry.add("spring.flyway.url", pg::getJdbcUrl)
                    registry.add("spring.flyway.user", pg::getUsername)
                    registry.add("spring.flyway.password", pg::getPassword)
                }
                redis?.let { rd ->
                    registry.add("spring.data.redis.host", rd::getHost)
                    registry.add("spring.data.redis.port") { rd.firstMappedPort }
                }
            }
            // Configuration (нужно всегда)
            registry.add("gateway.cache.invalidation-channel") { "route-cache-invalidation" }
            registry.add("gateway.cache.ttl-seconds") { 60 }
            registry.add("gateway.cache.max-routes") { 1000 }
            registry.add("gateway.ratelimit.fallback-enabled") { true }
            registry.add("gateway.ratelimit.fallback-reduction") { 0.5 }
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Autowired
    private lateinit var cacheManager: RouteCacheManager

    @Autowired
    private lateinit var consumerRateLimitCacheManager: ConsumerRateLimitCacheManager

    @Autowired
    private lateinit var redisTemplate: ReactiveRedisTemplate<String, String>

    private lateinit var rateLimitId: UUID

    @BeforeEach
    fun setUp() {
        // Очищаем Redis ключи rate limit для изоляции тестов
        redisTemplate.keys("ratelimit:*")
            .flatMap { key -> redisTemplate.delete(key) }
            .blockLast()

        // Очищаем данные (порядок важен из-за FK)
        databaseClient.sql("UPDATE routes SET rate_limit_id = NULL").fetch().rowsUpdated().block()
        databaseClient.sql("DELETE FROM routes").fetch().rowsUpdated().block()
        databaseClient.sql("DELETE FROM rate_limits").fetch().rowsUpdated().block()
        databaseClient.sql("DELETE FROM consumer_rate_limits").fetch().rowsUpdated().block()

        // Очищаем кэши маршрутов и consumer rate limits
        cacheManager.refreshCache().block()
        consumerRateLimitCacheManager.invalidateAll().block()

        // Создаём rate limit policy
        rateLimitId = UUID.randomUUID()
        databaseClient.sql(
            """
            INSERT INTO rate_limits (id, name, requests_per_second, burst_size, created_by)
            VALUES (:id, :name, :rps, :burst, :createdBy)
            """.trimIndent()
        )
            .bind("id", rateLimitId)
            .bind("name", "test-policy-${UUID.randomUUID()}")
            .bind("rps", 5) // 5 запросов в секунду
            .bind("burst", 3) // burst = 3 токена
            .bind("createdBy", UUID.randomUUID())
            .fetch()
            .rowsUpdated()
            .block()

        // Настраиваем WireMock для всех /api/* путей
        wireMock.stubFor(
            WireMock.get(WireMock.urlMatching("/api/.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"status": "ok"}""")
                )
        )
    }

    @AfterEach
    fun tearDown() {
        wireMock.resetAll()
    }

    @Test
    fun `AC1 - запросы в пределах лимита проходят успешно`() {
        // Создаём маршрут с rate limit
        insertRouteWithRateLimit("/api/test", rateLimitId)

        // Первые 3 запроса должны пройти (burst = 3)
        repeat(3) {
            webTestClient.get()
                .uri("/api/test")
                .exchange()
                .expectStatus().isOk
                .expectHeader().exists("X-RateLimit-Limit")
                .expectHeader().exists("X-RateLimit-Remaining")
                .expectHeader().exists("X-RateLimit-Reset")
        }

        // Проверяем, что WireMock получил запросы
        wireMock.verify(3, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/api/test")))
    }

    @Test
    fun `AC2 - превышение burst возвращает 429`() {
        // Создаём маршрут с rate limit
        insertRouteWithRateLimit("/api/limited", rateLimitId)

        // Исчерпываем burst (3 запроса)
        repeat(3) {
            webTestClient.get()
                .uri("/api/limited")
                .exchange()
                .expectStatus().isOk
        }

        // Следующий запрос должен получить 429
        webTestClient.get()
            .uri("/api/limited")
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectHeader().valueEquals("X-RateLimit-Limit", "5")
            .expectHeader().valueEquals("X-RateLimit-Remaining", "0")
            .expectHeader().exists("X-RateLimit-Reset")
            .expectHeader().exists("Retry-After")
            .expectBody()
            .jsonPath("$.type").isEqualTo("https://api.gateway/errors/rate-limit-exceeded")
            .jsonPath("$.status").isEqualTo(429)
            .jsonPath("$.title").isEqualTo("Too Many Requests")
    }

    @Test
    fun `AC5 - маршрут без rate limit проходит без заголовков rate limit`() {
        // Создаём маршрут БЕЗ rate limit
        insertRoute("/api/unlimited")

        webTestClient.get()
            .uri("/api/unlimited")
            .exchange()
            .expectStatus().isOk
            .expectHeader().doesNotExist("X-RateLimit-Limit")
            .expectHeader().doesNotExist("X-RateLimit-Remaining")
    }

    @Test
    fun `AC7 - rate limit заголовки в успешных ответах`() {
        // Создаём маршрут с rate limit
        insertRouteWithRateLimit("/api/headers", rateLimitId)

        webTestClient.get()
            .uri("/api/headers")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("X-RateLimit-Limit", "5")
            .expectHeader().exists("X-RateLimit-Remaining")
            .expectHeader().exists("X-RateLimit-Reset")
    }

    @Test
    fun `разные клиенты имеют независимые rate limits`() {
        // Создаём маршрут с rate limit
        insertRouteWithRateLimit("/api/multiuser", rateLimitId)

        // Клиент 1 исчерпывает лимит
        repeat(3) {
            webTestClient.get()
                .uri("/api/multiuser")
                .header("X-Forwarded-For", "10.0.0.1")
                .exchange()
                .expectStatus().isOk
        }

        // Клиент 1 получает 429
        webTestClient.get()
            .uri("/api/multiuser")
            .header("X-Forwarded-For", "10.0.0.1")
            .exchange()
            .expectStatus().isEqualTo(429)

        // Клиент 2 всё ещё может отправлять запросы
        webTestClient.get()
            .uri("/api/multiuser")
            .header("X-Forwarded-For", "10.0.0.2")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `AC3 - токены восполняются со временем`() {
        // Создаём маршрут с rate limit (5 req/s, burst 3)
        insertRouteWithRateLimit("/api/replenish", rateLimitId)

        // Исчерпываем все токены (burst = 3)
        repeat(3) {
            webTestClient.get()
                .uri("/api/replenish")
                .exchange()
                .expectStatus().isOk
        }

        // Следующий запрос должен быть заблокирован
        webTestClient.get()
            .uri("/api/replenish")
            .exchange()
            .expectStatus().isEqualTo(429)

        // Ждём 1 секунду — восполнится 5 токенов (5 req/s)
        Thread.sleep(1100)

        // Теперь запрос должен пройти
        webTestClient.get()
            .uri("/api/replenish")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `AC6 - distributed rate limiting через Redis`() {
        // Этот тест проверяет, что rate limit работает через Redis
        // и несколько клиентов с одним IP разделяют общий bucket

        insertRouteWithRateLimit("/api/distributed", rateLimitId)

        val clientIp = "192.168.1.100"

        // Отправляем 3 запроса с одного IP (burst = 3)
        repeat(3) {
            webTestClient.get()
                .uri("/api/distributed")
                .header("X-Forwarded-For", clientIp)
                .exchange()
                .expectStatus().isOk
        }

        // 4-й запрос с того же IP должен быть заблокирован
        webTestClient.get()
            .uri("/api/distributed")
            .header("X-Forwarded-For", clientIp)
            .exchange()
            .expectStatus().isEqualTo(429)

        // Запрос с другого IP должен пройти (независимый bucket)
        webTestClient.get()
            .uri("/api/distributed")
            .header("X-Forwarded-For", "192.168.1.200")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `AC2 - 429 ответ содержит RFC 7807 структуру с correlationId`() {
        insertRouteWithRateLimit("/api/rfc7807", rateLimitId)

        // Используем уникальный IP для изоляции от других тестов
        val testClientIp = "10.99.99.99"

        // Исчерпываем burst
        repeat(3) {
            webTestClient.get()
                .uri("/api/rfc7807")
                .header("X-Forwarded-For", testClientIp)
                .exchange()
                .expectStatus().isOk
        }

        // Проверяем RFC 7807 формат
        webTestClient.get()
            .uri("/api/rfc7807")
            .header("X-Forwarded-For", testClientIp)
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectBody()
            .jsonPath("$.type").isEqualTo("https://api.gateway/errors/rate-limit-exceeded")
            .jsonPath("$.title").isEqualTo("Too Many Requests")
            .jsonPath("$.status").isEqualTo(429)
            .jsonPath("$.detail").exists()
            .jsonPath("$.correlationId").exists()
    }

    @Test
    fun `BUGFIX 10-1 - при 20 быстрых запросах только burst количество проходит`() {
        // Story 10.1: Rate limit 5 req/s, burst 3
        // При быстрых запросах (без задержки) только 3 должны пройти
        // Остальные 17 должны получить 429
        insertRouteWithRateLimit("/api/stress", rateLimitId)

        val testClientIp = "10.1.1.1"
        var successCount = 0
        var rateLimitedCount = 0

        // Отправляем 20 запросов быстро (без пауз)
        repeat(20) {
            val result = webTestClient.get()
                .uri("/api/stress")
                .header("X-Forwarded-For", testClientIp)
                .exchange()
                .returnResult(String::class.java)

            if (result.status.value() == 200) {
                successCount++
            } else if (result.status.value() == 429) {
                rateLimitedCount++
            }
        }

        // Ожидаем: burst=3 запроса проходят, остальные блокируются
        // Допускаем небольшое отклонение (+1) из-за timing восполнения токенов
        org.assertj.core.api.Assertions.assertThat(successCount)
            .describedAs("Количество успешных запросов должно быть примерно равно burst (3)")
            .isBetween(3, 4)

        org.assertj.core.api.Assertions.assertThat(rateLimitedCount)
            .describedAs("Остальные запросы должны получить 429")
            .isBetween(16, 17)

        org.assertj.core.api.Assertions.assertThat(successCount + rateLimitedCount)
            .describedAs("Всего должно быть 20 запросов")
            .isEqualTo(20)
    }

    @Test
    fun `BUGFIX 10-1 - за 2 секунды при 5 req_s проходит примерно 10-14 запросов`() {
        // Story 10.1: Проверка восполнения токенов со временем
        // Rate limit: 5 req/s, burst 3
        // За 2 секунды должно пройти: burst(3) + 2sec * 5req/s = ~13 запросов
        insertRouteWithRateLimit("/api/timed", rateLimitId)

        val testClientIp = "10.2.2.2"
        var successCount = 0
        var rateLimitedCount = 0
        val startTime = System.currentTimeMillis()

        // Отправляем запросы в течение 2 секунд с небольшими интервалами
        while (System.currentTimeMillis() - startTime < 2000) {
            val result = webTestClient.get()
                .uri("/api/timed")
                .header("X-Forwarded-For", testClientIp)
                .exchange()
                .returnResult(String::class.java)

            if (result.status.value() == 200) {
                successCount++
            } else if (result.status.value() == 429) {
                rateLimitedCount++
            }

            // Небольшая пауза между запросами (50ms)
            Thread.sleep(50)
        }

        // За 2 секунды при 5 req/s ожидаем примерно 10-13 успешных запросов
        // burst(3) + время(~2сек) * rate(5) = 13, но timing может немного отличаться
        org.assertj.core.api.Assertions.assertThat(successCount)
            .describedAs("За 2 секунды при 5 req/s должно пройти 10-14 запросов")
            .isBetween(10, 14)
    }

    @Test
    fun `BUGFIX 10-1 - X-RateLimit заголовки присутствуют при rate limiting`() {
        // Story 10.1 AC2: Проверка наличия rate limit заголовков
        insertRouteWithRateLimit("/api/headers-test", rateLimitId)

        val testClientIp = "10.3.3.3"

        // Первый запрос — успешный с заголовками
        webTestClient.get()
            .uri("/api/headers-test")
            .header("X-Forwarded-For", testClientIp)
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("X-RateLimit-Limit", "5")
            .expectHeader().exists("X-RateLimit-Remaining")
            .expectHeader().exists("X-RateLimit-Reset")

        // Исчерпываем burst (burst=3, нужно ещё 2 запроса после первого)
        // Делаем 5 дополнительных запросов чтобы гарантированно исчерпать burst
        // даже если между запросами восполняется часть токенов
        repeat(5) {
            webTestClient.get()
                .uri("/api/headers-test")
                .header("X-Forwarded-For", testClientIp)
                .exchange()
        }

        // 429 ответ тоже должен содержать заголовки
        // После 6 запросов подряд burst точно исчерпан
        webTestClient.get()
            .uri("/api/headers-test")
            .header("X-Forwarded-For", testClientIp)
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectHeader().valueEquals("X-RateLimit-Limit", "5")
            .expectHeader().valueEquals("X-RateLimit-Remaining", "0")
            .expectHeader().exists("X-RateLimit-Reset")
            .expectHeader().exists("Retry-After")
    }

    // ============ Story 12.8: Per-consumer Rate Limiting Integration Tests ============

    @Test
    fun `Story 12-8 AC3 - per-consumer rate limit enforcement`() {
        // Создаём маршрут БЕЗ route rate limit
        insertRoute("/api/consumer-only")

        // Создаём consumer rate limit (3 req/s, burst 2)
        insertConsumerRateLimit("test-consumer-1", requestsPerSecond = 3, burstSize = 2)

        // Первые 2 запроса проходят (burst = 2)
        repeat(2) {
            webTestClient.get()
                .uri("/api/consumer-only")
                .header("X-Consumer-ID", "test-consumer-1")
                .exchange()
                .expectStatus().isOk
                .expectHeader().valueEquals("X-RateLimit-Type", "consumer")
        }

        // 3-й запрос блокируется
        webTestClient.get()
            .uri("/api/consumer-only")
            .header("X-Consumer-ID", "test-consumer-1")
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectHeader().valueEquals("X-RateLimit-Type", "consumer")
            .expectHeader().valueEquals("X-RateLimit-Limit", "3")
    }

    @Test
    fun `Story 12-8 AC4 - stricter limit wins когда consumer limit строже`() {
        // Route limit: 10 req/s, burst 5
        // Consumer limit: 3 req/s, burst 2 (строже)
        insertRouteWithRateLimit("/api/both-limits", rateLimitId)
        insertConsumerRateLimit("strict-consumer", requestsPerSecond = 3, burstSize = 2)

        // Consumer лимит строже (burst 2 < burst 3 route)
        // Первые 2 запроса проходят
        repeat(2) {
            webTestClient.get()
                .uri("/api/both-limits")
                .header("X-Consumer-ID", "strict-consumer")
                .exchange()
                .expectStatus().isOk
        }

        // 3-й запрос блокируется consumer лимитом
        webTestClient.get()
            .uri("/api/both-limits")
            .header("X-Consumer-ID", "strict-consumer")
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectHeader().valueEquals("X-RateLimit-Type", "consumer")
    }

    @Test
    fun `Story 12-8 AC3 - X-RateLimit-Type header указывает consumer`() {
        insertRoute("/api/type-header")
        insertConsumerRateLimit("header-consumer", requestsPerSecond = 10, burstSize = 5)

        webTestClient.get()
            .uri("/api/type-header")
            .header("X-Consumer-ID", "header-consumer")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("X-RateLimit-Type", "consumer")
            .expectHeader().valueEquals("X-RateLimit-Limit", "10")
            .expectHeader().exists("X-RateLimit-Remaining")
    }

    @Test
    fun `Story 12-8 AC5 - fallback на per-route лимит когда consumer лимит отсутствует`() {
        // Создаём маршрут с route rate limit
        insertRouteWithRateLimit("/api/route-fallback", rateLimitId)
        // НЕ создаём consumer rate limit для этого consumer

        // Запрос без consumer rate limit использует route limit
        webTestClient.get()
            .uri("/api/route-fallback")
            .header("X-Consumer-ID", "no-limit-consumer")
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals("X-RateLimit-Type", "route")
            .expectHeader().valueEquals("X-RateLimit-Limit", "5") // route limit = 5 req/s
    }

    private fun insertConsumerRateLimit(consumerId: String, requestsPerSecond: Int, burstSize: Int) {
        databaseClient.sql(
            """
            INSERT INTO consumer_rate_limits (id, consumer_id, requests_per_second, burst_size)
            VALUES (:id, :consumerId, :rps, :burst)
            """.trimIndent()
        )
            .bind("id", UUID.randomUUID())
            .bind("consumerId", consumerId)
            .bind("rps", requestsPerSecond)
            .bind("burst", burstSize)
            .fetch()
            .rowsUpdated()
            .block()

        // Инвалидируем кэш чтобы тест подхватил новый consumer rate limit
        consumerRateLimitCacheManager.invalidateCache(consumerId).block()
    }

    private fun insertRouteWithRateLimit(path: String, rateLimitId: UUID) {
        databaseClient.sql(
            """
            INSERT INTO routes (id, path, upstream_url, methods, status, rate_limit_id)
            VALUES (:id, :path, :upstreamUrl, ARRAY['GET','POST'], :status, :rateLimitId)
            """.trimIndent()
        )
            .bind("id", UUID.randomUUID())
            .bind("path", path)
            .bind("upstreamUrl", "http://localhost:${wireMock.port()}")
            .bind("status", RouteStatus.PUBLISHED.name.lowercase())
            .bind("rateLimitId", rateLimitId)
            .fetch()
            .rowsUpdated()
            .block()

        cacheManager.refreshCache().block()
    }

    private fun insertRoute(path: String) {
        databaseClient.sql(
            """
            INSERT INTO routes (id, path, upstream_url, methods, status)
            VALUES (:id, :path, :upstreamUrl, ARRAY['GET','POST'], :status)
            """.trimIndent()
        )
            .bind("id", UUID.randomUUID())
            .bind("path", path)
            .bind("upstreamUrl", "http://localhost:${wireMock.port()}")
            .bind("status", RouteStatus.PUBLISHED.name.lowercase())
            .fetch()
            .rowsUpdated()
            .block()

        cacheManager.refreshCache().block()
    }
}
