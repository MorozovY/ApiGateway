package com.company.gateway.core.route

import com.company.gateway.core.cache.RouteCacheManager
import com.company.gateway.core.filter.RateLimitFilter
import org.slf4j.LoggerFactory
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter
import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
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
        val routes = cacheManager.getCachedRoutes()
        log.debug("getRoutes() called, routes in cache: {}", routes.size)
        return Flux.fromIterable(routes)
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
                val upstreamUri = URI.create(dbRoute.upstreamUrl)
                val upstreamPath = upstreamUri.path ?: ""

                Route.async()
                    .id(dbRoute.id!!.toString())
                    .uri(upstreamUri)
                    .filter(createRewritePathFilter(dbRoute.path, upstreamPath))
                    .asyncPredicate { exchange ->
                        val path = exchange.request.path.value()
                        val method = exchange.request.method.name()
                        val pathMatches = matchesPrefix(path, dbRoute.path)
                        val methodMatches = dbRoute.methods.isEmpty() ||
                            dbRoute.methods.any { it.equals(method, ignoreCase = true) }

                        log.debug("Predicate: path={}, routePath={}, method={}, pathMatches={}, methodMatches={}",
                            path, dbRoute.path, method, pathMatches, methodMatches)

                        // Устанавливаем атрибуты для rate limiting при совпадении маршрута
                        if (pathMatches && methodMatches) {
                            exchange.attributes[RateLimitFilter.ROUTE_ID_ATTRIBUTE] = dbRoute.id

                            // Загружаем rate limit политику из кэша, если назначена
                            // Story 5.8, AC2: fallback загрузка если политика не в кэше
                            dbRoute.rateLimitId?.let { rateLimitId ->
                                var rateLimit = cacheManager.getCachedRateLimit(rateLimitId)

                                // Fallback: если политика не в кэше, загружаем напрямую из БД
                                if (rateLimit == null) {
                                    log.warn("Политика {} не найдена в кэше, выполняем fallback загрузку", rateLimitId)
                                    rateLimit = cacheManager.loadRateLimitSync(rateLimitId)
                                }

                                if (rateLimit != null) {
                                    exchange.attributes[RateLimitFilter.RATE_LIMIT_ATTRIBUTE] = rateLimit
                                } else {
                                    log.warn("Политика {} не найдена даже при fallback, rate limiting отключён для маршрута {}",
                                        rateLimitId, dbRoute.id)
                                }
                            }
                        }

                        Mono.just(pathMatches && methodMatches)
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

    /**
     * Создаёт GatewayFilter для перезаписи path.
     *
     * Story 5.10: Корректное формирование upstream path.
     *
     * Логика:
     * - Запускается ПОСЛЕ RouteToRequestUrlFilter (order=10000)
     * - Модифицирует атрибут GATEWAY_REQUEST_URL_ATTR с корректным path
     * - Извлекает relative path (часть request path после route path)
     * - Формирует новый path = upstream path + relative path
     *
     * Пример:
     * - Route path: `/gateway`
     * - Upstream path: `/api/v1`
     * - Request: `/gateway/orders/123`
     * - Relative: `/orders/123`
     * - Result: `/api/v1/orders/123`
     *
     * @param routePath path маршрута (prefix для matching)
     * @param upstreamPath path из upstream URL
     * @return GatewayFilter для перезаписи path (order=10001, после RouteToRequestUrlFilter)
     */
    private fun createRewritePathFilter(routePath: String, upstreamPath: String): GatewayFilter {
        val filter = GatewayFilter { exchange: ServerWebExchange, chain: GatewayFilterChain ->
            val requestPath = exchange.request.path.value()

            // Вычисляем relative path (часть после route path)
            val relativePath = when {
                requestPath == routePath -> ""
                requestPath.startsWith(routePath + "/") -> requestPath.substring(routePath.length)
                else -> requestPath // fallback — не должно происходить если predicate работает
            }

            // Формируем новый path: upstream path + relative path
            // Убираем двойные слэши если upstream path заканчивается на /
            val newPath = if (upstreamPath.endsWith("/") && relativePath.startsWith("/")) {
                upstreamPath + relativePath.substring(1)
            } else if (upstreamPath.isEmpty()) {
                relativePath.ifEmpty { "/" }
            } else {
                upstreamPath + relativePath
            }

            log.debug("RewritePath: {} -> {} (route={}, upstream={})",
                requestPath, newPath, routePath, upstreamPath)

            // Модифицируем GATEWAY_REQUEST_URL_ATTR — это URL который будет использован для upstream запроса
            // RouteToRequestUrlFilter (order=10000) уже установил этот атрибут, мы его корректируем
            val currentUrl = exchange.getAttribute<URI>(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR)
            if (currentUrl != null) {
                val newUrl = UriComponentsBuilder.fromUri(currentUrl)
                    .replacePath(newPath)
                    .build()
                    .toUri()
                exchange.attributes[ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR] = newUrl
                log.debug("Modified GATEWAY_REQUEST_URL_ATTR: {} -> {}", currentUrl, newUrl)
            }

            chain.filter(exchange)
        }

        // Order 10001: запускается сразу ПОСЛЕ RouteToRequestUrlFilter (order=10000)
        return OrderedGatewayFilter(filter, 10001)
    }
}
