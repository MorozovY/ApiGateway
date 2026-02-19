package com.company.gateway.core.route

import com.company.gateway.core.cache.RouteCacheManager
import com.company.gateway.core.filter.RateLimitFilter
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import java.net.URI

/**
 * Динамический локатор маршрутов, загружающий конфигурацию из кэша.
 *
 * Маршруты кэшируются в RouteCacheManager и обновляются при:
 * - Старте приложения
 * - Получении события инвалидации из Redis
 *
 * При совпадении маршрута устанавливает атрибуты exchange для:
 * - Rate limiting (routeId, rateLimit policy)
 */
@Component
class DynamicRouteLocator(
    private val cacheManager: RouteCacheManager
) : RouteLocator {

    private val log = LoggerFactory.getLogger(DynamicRouteLocator::class.java)

    override fun getRoutes(): Flux<Route> {
        return Flux.fromIterable(cacheManager.getCachedRoutes())
            .filter { dbRoute ->
                // Фильтруем маршруты с null id (не должно происходить в нормальной работе)
                if (dbRoute.id == null) {
                    log.warn("Маршрут с path '${dbRoute.path}' имеет null id, пропускаем")
                    false
                } else {
                    true
                }
            }
            .map { dbRoute ->
                Route.async()
                    .id(dbRoute.id!!.toString())
                    .uri(URI.create(dbRoute.upstreamUrl))
                    .predicate { exchange ->
                        val path = exchange.request.path.value()
                        val method = exchange.request.method.name()
                        val pathMatches = matchesPrefix(path, dbRoute.path)
                        val methodMatches = dbRoute.methods.isEmpty() ||
                            dbRoute.methods.any { it.equals(method, ignoreCase = true) }

                        // Устанавливаем атрибуты для rate limiting при совпадении маршрута
                        if (pathMatches && methodMatches) {
                            exchange.attributes[RateLimitFilter.ROUTE_ID_ATTRIBUTE] = dbRoute.id

                            // Загружаем rate limit политику из кэша, если назначена
                            dbRoute.rateLimitId?.let { rateLimitId ->
                                cacheManager.getCachedRateLimit(rateLimitId)?.let { rateLimit ->
                                    exchange.attributes[RateLimitFilter.RATE_LIMIT_ATTRIBUTE] = rateLimit
                                }
                            }
                        }

                        pathMatches && methodMatches
                    }
                    .build()
            }
            .onErrorResume { ex ->
                log.error("Ошибка загрузки маршрутов из кэша: ${ex.message}", ex)
                Flux.empty()
            }
    }

    /**
     * Path prefix matching:
     * Route path `/api/orders` matches:
     *   - `/api/orders`       (exact)
     *   - `/api/orders/`      (trailing slash)
     *   - `/api/orders/123`   (with ID)
     * But NOT:
     *   - `/api/ordershistory` (no path separator after prefix)
     */
    internal fun matchesPrefix(requestPath: String, routePath: String): Boolean {
        if (requestPath == routePath) return true
        return requestPath.startsWith(routePath + "/")
    }
}
