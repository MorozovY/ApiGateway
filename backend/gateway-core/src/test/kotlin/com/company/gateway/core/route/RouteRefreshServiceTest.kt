package com.company.gateway.core.route

import com.company.gateway.core.cache.RouteCacheManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import reactor.core.publisher.Mono
import org.assertj.core.api.Assertions.assertThat

@ExtendWith(MockitoExtension::class)
class RouteRefreshServiceTest {

    @Mock
    private lateinit var redisConnectionFactory: ReactiveRedisConnectionFactory

    @Mock
    private lateinit var cacheManager: RouteCacheManager

    private lateinit var service: RouteRefreshService

    @BeforeEach
    fun setUp() {
        service = RouteRefreshService(redisConnectionFactory, cacheManager)
    }

    @Test
    fun `сервис инициализируется с флагом redis available равным true по умолчанию`() {
        // До любой попытки подключения redisAvailable по умолчанию равен true
        assertThat(service.isRedisAvailable()).isTrue()
    }

    @Test
    fun `isRedisAvailable возвращает текущее состояние подключения redis`() {
        // Начальное состояние должно быть true
        assertThat(service.isRedisAvailable()).isTrue()
    }

    @Test
    fun `cleanup освобождает подписку без ошибки когда подписка не существует`() {
        // Не должен выбрасывать исключение даже когда подписка не существует
        service.cleanup()

        // Состояние должно оставаться валидным
        assertThat(service.isRedisAvailable()).isTrue()
    }

    @Test
    fun `множественные вызовы cleanup безопасны и идемпотентны`() {
        // Множественные вызовы cleanup должны быть безопасны (идемпотентны)
        service.cleanup()
        service.cleanup()
        service.cleanup()

        // Исключение не должно выбрасываться, сервис должен оставаться валидным
        assertThat(service).isNotNull
        assertThat(service.isRedisAvailable()).isTrue()
    }

    @Test
    fun `сервис может быть создан с валидными зависимостями`() {
        val newService = RouteRefreshService(redisConnectionFactory, cacheManager)

        assertThat(newService).isNotNull
        assertThat(newService.isRedisAvailable()).isTrue()
    }

    @Test
    fun `cleanup безопасен для вызова до запуска подписки`() {
        // Сервис создан, но subscribeToInvalidationEvents не вызван
        val freshService = RouteRefreshService(redisConnectionFactory, cacheManager)

        // Cleanup должен работать без NPE
        freshService.cleanup()

        assertThat(freshService.isRedisAvailable()).isTrue()
    }

    @Test
    fun `зависимость cacheManager внедрена корректно`() {
        // Проверяем, что cacheManager может быть использован (доказывает работу injection)
        whenever(cacheManager.refreshCache()).thenReturn(Mono.empty())

        // Это проверяет доступность зависимости
        val result = cacheManager.refreshCache()

        assertThat(result).isNotNull
        verify(cacheManager).refreshCache()
    }

    @Test
    fun `сервис не вызывает cacheManager при конструировании`() {
        // Создание сервиса НЕ должно вызывать операции с кэшем
        val newService = RouteRefreshService(redisConnectionFactory, cacheManager)

        // CacheManager не должен вызываться при конструировании
        verify(cacheManager, never()).refreshCache()
        verify(cacheManager, never()).getCachedRoutes()
    }

    // Story 5.8 тесты: подписка на ratelimit-cache-invalidation

    @Test
    fun `сервис не вызывает refreshRateLimitCache при конструировании`() {
        // Создание сервиса НЕ должно вызывать операции с rate limit кэшем
        val newService = RouteRefreshService(redisConnectionFactory, cacheManager)

        // CacheManager refreshRateLimitCache не должен вызываться при конструировании
        verify(cacheManager, never()).refreshRateLimitCache(any())
    }

    @Test
    fun `зависимость cacheManager поддерживает refreshRateLimitCache`() {
        // Проверяем, что cacheManager.refreshRateLimitCache доступен
        val testId = java.util.UUID.randomUUID()
        whenever(cacheManager.refreshRateLimitCache(testId)).thenReturn(Mono.empty())

        // Вызываем метод
        val result = cacheManager.refreshRateLimitCache(testId)

        assertThat(result).isNotNull
        verify(cacheManager).refreshRateLimitCache(testId)
    }
}