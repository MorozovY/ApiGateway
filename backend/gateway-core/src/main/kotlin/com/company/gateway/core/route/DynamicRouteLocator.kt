package com.company.gateway.core.route

import com.company.gateway.core.cache.RouteCacheManager
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import java.net.URI

@Component
class DynamicRouteLocator(
    private val cacheManager: RouteCacheManager
) : RouteLocator {

    private val log = LoggerFactory.getLogger(DynamicRouteLocator::class.java)

    override fun getRoutes(): Flux<Route> {
        return Flux.fromIterable(cacheManager.getCachedRoutes())
            .filter { dbRoute ->
                // Filter out routes with null id (should not happen in normal operation)
                if (dbRoute.id == null) {
                    log.warn("Route with path '${dbRoute.path}' has null id, skipping")
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
                        pathMatches && methodMatches
                    }
                    .build()
            }
            .onErrorResume { ex ->
                log.error("Failed to load routes from cache: ${ex.message}", ex)
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
