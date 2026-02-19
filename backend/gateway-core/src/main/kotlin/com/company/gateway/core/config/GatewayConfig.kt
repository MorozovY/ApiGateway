package com.company.gateway.core.config

import org.springframework.context.annotation.Configuration

/**
 * Конфигурация Spring Cloud Gateway.
 *
 * DynamicRouteLocator автоматически регистрируется как RouteLocator
 * благодаря аннотации @Component и реализации интерфейса RouteLocator.
 *
 * Hop-by-hop headers удаляются через application.yml default-filters.
 */
@Configuration
class GatewayConfig
