package com.company.gateway.core.cache

import com.company.gateway.common.model.Route
import com.company.gateway.common.model.RouteStatus
import com.company.gateway.core.repository.RouteRepository
import com.github.benmanes.caffeine.cache.Cache
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.cloud.gateway.event.RefreshRoutesEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicReference

@Component
class RouteCacheManager(
    private val routeRepository: RouteRepository,
    private val caffeineCache: Cache<String, List<Route>>,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val cachedRoutes = AtomicReference<List<Route>>(emptyList())
    private val logger = LoggerFactory.getLogger(RouteCacheManager::class.java)

    companion object {
        private const val ROUTE_CACHE_KEY = "all_published_routes"
    }

    @EventListener(ApplicationReadyEvent::class)
    fun initializeCache() {
        logger.info("Initializing route cache on startup")
        refreshCache()
            .doOnSuccess { logger.info("Route cache initialized successfully") }
            .doOnError { e -> logger.error("Failed to initialize route cache on startup: {}", e.message, e) }
            .subscribe()
    }

    fun refreshCache(): Mono<Void> =
        routeRepository.findByStatus(RouteStatus.PUBLISHED)
            .collectList()
            .doOnNext { routes ->
                cachedRoutes.set(routes)
                caffeineCache.put(ROUTE_CACHE_KEY, routes)
                logger.info("Route cache refreshed: {} routes loaded", routes.size)
                // Notify Spring Cloud Gateway to refresh its route cache
                eventPublisher.publishEvent(RefreshRoutesEvent(this))
            }
            .then()
            .doOnError { e ->
                logger.error("Failed to refresh route cache: {}", e.message, e)
            }

    fun getCachedRoutes(): List<Route> {
        // Try in-memory atomic reference first (set on Redis event or startup)
        val routes = cachedRoutes.get()
        if (routes.isNotEmpty()) {
            return routes
        }
        // Fallback to Caffeine TTL cache
        return caffeineCache.getIfPresent(ROUTE_CACHE_KEY) ?: emptyList()
    }

    /**
     * Returns the number of currently cached routes.
     * Useful for health checks and monitoring.
     */
    fun getCacheSize(): Int = cachedRoutes.get().size
}
