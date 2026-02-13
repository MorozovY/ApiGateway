package com.company.gateway.core.exception

import com.company.gateway.common.exception.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler
import org.springframework.cloud.gateway.support.NotFoundException
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
@Order(-1)
class GlobalExceptionHandler(
    private val objectMapper: ObjectMapper
) : ErrorWebExceptionHandler {

    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        val response = exchange.response
        val requestPath = exchange.request.path.value()

        val (httpStatus, errorResponse) = when (ex) {
            is RouteNotFoundException -> Pair(
                HttpStatus.NOT_FOUND,
                ErrorResponse(
                    type = "https://api.gateway/errors/route-not-found",
                    title = "Not Found",
                    status = HttpStatus.NOT_FOUND.value(),
                    detail = "No route found for path: ${ex.path}",
                    instance = requestPath
                )
            )
            is NotFoundException -> Pair(
                HttpStatus.NOT_FOUND,
                ErrorResponse(
                    type = "https://api.gateway/errors/route-not-found",
                    title = "Not Found",
                    status = HttpStatus.NOT_FOUND.value(),
                    detail = "No route found for path: $requestPath",
                    instance = requestPath
                )
            )
            is ResponseStatusException -> {
                val status = ex.statusCode as? HttpStatus ?: HttpStatus.INTERNAL_SERVER_ERROR
                // Handle generic 404 from WebFlux as route not found
                if (status == HttpStatus.NOT_FOUND) {
                    Pair(
                        HttpStatus.NOT_FOUND,
                        ErrorResponse(
                            type = "https://api.gateway/errors/route-not-found",
                            title = "Not Found",
                            status = HttpStatus.NOT_FOUND.value(),
                            detail = "No route found for path: $requestPath",
                            instance = requestPath
                        )
                    )
                } else {
                    Pair(
                        status,
                        ErrorResponse(
                            type = "https://api.gateway/errors/request-error",
                            title = ex.reason ?: status.reasonPhrase,
                            status = status.value(),
                            detail = ex.message ?: "Request error",
                            instance = requestPath
                        )
                    )
                }
            }
            else -> Pair(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorResponse(
                    type = "https://api.gateway/errors/internal-error",
                    title = "Internal Server Error",
                    status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    detail = "An unexpected error occurred",
                    instance = requestPath
                )
            )
        }

        response.statusCode = httpStatus
        response.headers.contentType = MediaType.APPLICATION_JSON

        val bytes = objectMapper.writeValueAsBytes(errorResponse)
        val buffer = response.bufferFactory().wrap(bytes)
        return response.writeWith(Mono.just(buffer))
    }
}
