package com.company.gateway.admin.security

import com.company.gateway.common.Constants.CORRELATION_ID_HEADER
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * WebFilter для заполнения Reactor Context данными для аудит-логов.
 *
 * Извлекает IP адрес и Correlation ID из запроса и сохраняет их
 * в Reactor Context для последующего использования в AuditService.
 *
 * Story 7.1, AC3: IP Address и Correlation ID записываются.
 */
@Component
@Order(-1) // Выполняется раньше JwtAuthenticationFilter
class AuditContextFilter : WebFilter {

    companion object {
        /** Ключ для IP адреса в Reactor Context */
        const val AUDIT_IP_ADDRESS_KEY = "audit.ipAddress"

        /** Ключ для Correlation ID в Reactor Context */
        const val AUDIT_CORRELATION_ID_KEY = "audit.correlationId"
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val ipAddress = extractIpAddress(exchange)
        val correlationId = extractCorrelationId(exchange)

        return chain.filter(exchange)
            .contextWrite { ctx ->
                var context = ctx
                if (ipAddress != null) {
                    context = context.put(AUDIT_IP_ADDRESS_KEY, ipAddress)
                }
                context = context.put(AUDIT_CORRELATION_ID_KEY, correlationId)
                context
            }
    }

    /**
     * Извлекает IP адрес клиента из запроса.
     *
     * Сначала пробует X-Forwarded-For (для работы за proxy),
     * затем X-Real-IP, и наконец remote address.
     *
     * @param exchange ServerWebExchange
     * @return IP адрес или null если не удалось извлечь
     */
    private fun extractIpAddress(exchange: ServerWebExchange): String? {
        // X-Forwarded-For: client, proxy1, proxy2
        val xForwardedFor = exchange.request.headers.getFirst("X-Forwarded-For")
        if (!xForwardedFor.isNullOrBlank()) {
            return xForwardedFor.split(",").firstOrNull()?.trim()
        }

        // X-Real-IP (часто используется Nginx)
        val xRealIp = exchange.request.headers.getFirst("X-Real-IP")
        if (!xRealIp.isNullOrBlank()) {
            return xRealIp.trim()
        }

        // Fallback на remote address
        return exchange.request.remoteAddress?.address?.hostAddress
    }

    /**
     * Извлекает Correlation ID из запроса или генерирует новый.
     *
     * @param exchange ServerWebExchange
     * @return Correlation ID (существующий или сгенерированный)
     */
    private fun extractCorrelationId(exchange: ServerWebExchange): String {
        return exchange.request.headers.getFirst(CORRELATION_ID_HEADER)
            ?: UUID.randomUUID().toString()
    }
}
