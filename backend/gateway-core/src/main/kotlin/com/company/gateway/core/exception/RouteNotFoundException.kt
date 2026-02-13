package com.company.gateway.core.exception

class RouteNotFoundException(
    val path: String
) : RuntimeException("No route found for path: $path")
