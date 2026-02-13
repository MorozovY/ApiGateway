package com.company.gateway.core.route

import com.company.gateway.common.model.RouteStatus
import com.company.gateway.core.repository.RouteRepository
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import java.net.URI

@Component
class DynamicRouteLocator(
    private val routeRepository: RouteRepository
) : RouteLocator {

    private val log = LoggerFactory.getLogger(DynamicRouteLocator::class.java)

    override fun getRoutes(): Flux<Route> {
        return routeRepository.findByStatus(RouteStatus.PUBLISHED)
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
                        matchesPrefix(path, dbRoute.path)
                    }
                    .build()
            }
            .onErrorResume { ex ->
                log.error("Failed to load routes from database: ${ex.message}", ex)
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
