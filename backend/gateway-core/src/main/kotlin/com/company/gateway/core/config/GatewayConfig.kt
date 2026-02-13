package com.company.gateway.core.config

import com.company.gateway.core.route.DynamicRouteLocator
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GatewayConfig {

    /**
     * Registers DynamicRouteLocator as the primary RouteLocator bean.
     * Routes are loaded from database on each getRoutes() call.
     * Hop-by-hop headers are removed via application.yml default-filters.
     */
    @Bean
    fun routeLocator(dynamicRouteLocator: DynamicRouteLocator): RouteLocator {
        return dynamicRouteLocator
    }
}
