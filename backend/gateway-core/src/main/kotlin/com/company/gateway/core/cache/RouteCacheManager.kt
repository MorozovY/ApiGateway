package com.company.gateway.core.cache

import com.company.gateway.common.model.RateLimit
import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import com.company.gateway.core.repository.RateLimitRepository
import com.company.gateway.core.repository.RouteRepository
import com.github.benmanes.caffeine.cache.Cache
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.cloud.gateway.event.RefreshRoutesEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Менеджер кэша маршрутов и политик rate limiting.
 *
 * Загружает published маршруты и связанные с ними rate limit политики
 * при старте приложения и при получении событий инвалидации кэша.
 */
@Component
class RouteCacheManager(
    private val routeRepository: RouteRepository,
    private val rateLimitRepository: RateLimitRepository,
    private val caffeineRouteCache: Cache<String, List<Route>>,
    private val caffeineRateLimitCache: Cache<UUID, RateLimit>,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val cachedRoutes = AtomicReference<List<Route>>(emptyList())
    private val cachedRateLimits = AtomicReference<Map<UUID, RateLimit>>(emptyMap())
    private val logger = LoggerFactory.getLogger(RouteCacheManager::class.java)

    companion object {
        private const val ROUTE_CACHE_KEY = "all_published_routes"
    }

    @EventListener(ApplicationReadyEvent::class)
    fun initializeCache() {
        logger.info("Инициализация кэша маршрутов и rate limits при старте")
        refreshCache()
            .doOnSuccess { logger.info("Кэш маршрутов и rate limits успешно инициализирован") }
            .doOnError { e -> logger.error("Ошибка инициализации кэша при старте: {}", e.message, e) }
            .subscribe()
    }

    /**
     * Обновляет кэш маршрутов и связанных rate limit политик.
     * Загружает все published маршруты, затем batch-загружает все уникальные rate limits.
     */
    fun refreshCache(): Mono<Void> =
        routeRepository.findByStatus(RouteStatus.PUBLISHED)
            .collectList()
            .flatMap { routes ->
                // Собираем уникальные rateLimitId из маршрутов
                val rateLimitIds = routes.mapNotNull { it.rateLimitId }.distinct()

                if (rateLimitIds.isEmpty()) {
                    // Нет rate limits для загрузки
                    Mono.just(routes to emptyMap<UUID, RateLimit>())
                } else {
                    // Batch загрузка всех rate limits за один запрос
                    rateLimitRepository.findAllByIdIn(rateLimitIds)
                        .collectList()
                        .map { rateLimits ->
                            routes to rateLimits.associateBy { it.id!! }
                        }
                }
            }
            .doOnNext { (routes, rateLimits) ->
                // Обновляем кэш маршрутов
                cachedRoutes.set(routes)
                caffeineRouteCache.put(ROUTE_CACHE_KEY, routes)

                // Обновляем кэш rate limits
                cachedRateLimits.set(rateLimits)
                rateLimits.forEach { (id, rateLimit) ->
                    caffeineRateLimitCache.put(id, rateLimit)
                }

                logger.info("Кэш обновлён: {} маршрутов, {} rate limit политик", routes.size, rateLimits.size)

                // Уведомляем Spring Cloud Gateway о необходимости обновить маршруты
                eventPublisher.publishEvent(RefreshRoutesEvent(this))
            }
            .then()
            .doOnError { e ->
                logger.error("Ошибка обновления кэша: {}", e.message, e)
            }

    /**
     * Возвращает список закэшированных маршрутов.
     */
    fun getCachedRoutes(): List<Route> {
        // Сначала пробуем in-memory AtomicReference (устанавливается при Redis событии или старте)
        val routes = cachedRoutes.get()
        if (routes.isNotEmpty()) {
            return routes
        }
        // Fallback на Caffeine TTL кэш
        return caffeineRouteCache.getIfPresent(ROUTE_CACHE_KEY) ?: emptyList()
    }

    /**
     * Возвращает rate limit политику по ID из кэша.
     * @return RateLimit или null если политика не найдена в кэше
     */
    fun getCachedRateLimit(id: UUID): RateLimit? {
        // Сначала пробуем in-memory AtomicReference
        val rateLimits = cachedRateLimits.get()
        rateLimits[id]?.let { return it }

        // Fallback на Caffeine кэш
        return caffeineRateLimitCache.getIfPresent(id)
    }

    /**
     * Возвращает все закэшированные rate limit политики.
     */
    fun getCachedRateLimits(): Map<UUID, RateLimit> = cachedRateLimits.get()

    /**
     * Возвращает количество закэшированных маршрутов.
     * Полезно для health checks и мониторинга.
     */
    fun getCacheSize(): Int = cachedRoutes.get().size

    /**
     * Возвращает количество закэшированных rate limit политик.
     */
    fun getRateLimitCacheSize(): Int = cachedRateLimits.get().size

    /**
     * Загружает одну политику rate limit по ID и обновляет кэш.
     * Используется при получении события из Redis pub/sub.
     *
     * Story 5.8, AC1: Rate limit политики синхронизируются немедленно
     *
     * @param rateLimitId ID политики для загрузки
     * @return Mono<Void> завершение операции
     */
    fun refreshRateLimitCache(rateLimitId: UUID): Mono<Void> =
        rateLimitRepository.findById(rateLimitId)
            .doOnNext { rateLimit ->
                // Обновляем AtomicReference map
                cachedRateLimits.updateAndGet { currentMap ->
                    currentMap.toMutableMap().apply {
                        put(rateLimitId, rateLimit)
                    }
                }
                // Обновляем Caffeine кэш
                caffeineRateLimitCache.put(rateLimitId, rateLimit)
                logger.info("Политика rate limit обновлена в кэше: {}", rateLimitId)

                // Уведомляем Spring Cloud Gateway о необходимости обновить маршруты
                eventPublisher.publishEvent(RefreshRoutesEvent(this))
            }
            .switchIfEmpty(
                Mono.fromRunnable {
                    // Политика удалена — удаляем из кэша
                    cachedRateLimits.updateAndGet { currentMap ->
                        currentMap.toMutableMap().apply { remove(rateLimitId) }
                    }
                    caffeineRateLimitCache.invalidate(rateLimitId)
                    logger.info("Политика rate limit удалена из кэша: {}", rateLimitId)
                }
            )
            .doOnError { e ->
                logger.warn("Ошибка загрузки политики {}: {}", rateLimitId, e.message)
            }
            .then()

    /**
     * Синхронная загрузка политики — fallback для DynamicRouteLocator.
     * ВАЖНО: Блокирующий вызов, использовать только для fallback!
     *
     * Story 5.8, AC2: Маршруты с rate limit работают сразу после publish
     *
     * @param rateLimitId ID политики для загрузки
     * @return RateLimit или null если политика не найдена
     */
    fun loadRateLimitSync(rateLimitId: UUID): RateLimit? {
        return try {
            rateLimitRepository.findById(rateLimitId)
                .doOnNext { rateLimit ->
                    // Кэшируем загруженную политику для следующих запросов
                    cachedRateLimits.updateAndGet { map ->
                        map.toMutableMap().apply { put(rateLimitId, rateLimit) }
                    }
                    caffeineRateLimitCache.put(rateLimitId, rateLimit)
                    logger.info("Политика rate limit загружена через fallback и закэширована: {}", rateLimitId)
                }
                .block(java.time.Duration.ofSeconds(5))
        } catch (e: Exception) {
            logger.error("Ошибка синхронной загрузки политики {}: {}", rateLimitId, e.message)
            null
        }
    }
}
