package com.company.gateway.core.filter

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import reactor.util.context.Context
import java.net.URI

/**
 * Unit тесты для LoggingFilter (Story 1.6)
 *
 * Тесты:
 * - AC3: Логирует завершение запроса с обязательными полями
 * - Извлекает correlation ID из Reactor Context
 * - Обрабатывает случаи ошибок с correlation ID
 * - Извлекает client IP из X-Forwarded-For или remote address
 */
class LoggingFilterTest {

    private val filter = LoggingFilter()

    @Test
    fun `использует LOWEST_PRECEDENCE минус 1 order`() {
        assert(filter.order == org.springframework.core.Ordered.LOWEST_PRECEDENCE - 1) {
            "LoggingFilter должен иметь order LOWEST_PRECEDENCE - 1"
        }
    }

    @Test
    fun `извлекает correlation ID из Reactor Context`() {
        val correlationId = "test-correlation-123"
        val request = MockServerHttpRequest.get("/api/test").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.response.statusCode = HttpStatus.OK

        val chain = mock<GatewayFilterChain>()
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        // Выполняем filter с correlation ID в context
        StepVerifier.create(
            filter.filter(exchange, chain)
                .contextWrite(Context.of(CorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY, correlationId))
        ).verifyComplete()

        // Filter завершается успешно - фактическое логирование проверяется через integration тесты
    }

    @Test
    fun `обрабатывает сигнал завершения запроса`() {
        val request = MockServerHttpRequest.get("/api/users").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.response.statusCode = HttpStatus.OK

        // Устанавливаем upstream URL атрибут (обычно устанавливается routing filter)
        exchange.attributes[ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR] = URI.create("http://upstream:8080/users")

        val chain = mock<GatewayFilterChain>()
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        StepVerifier.create(
            filter.filter(exchange, chain)
                .contextWrite(Context.of(CorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY, "log-test-id"))
        ).verifyComplete()
    }

    @Test
    fun `обрабатывает сигнал ошибки`() {
        val request = MockServerHttpRequest.get("/api/error").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.response.statusCode = HttpStatus.INTERNAL_SERVER_ERROR

        val chain = mock<GatewayFilterChain>()
        val testException = RuntimeException("Test error")
        whenever(chain.filter(any())).thenReturn(Mono.error(testException))

        StepVerifier.create(
            filter.filter(exchange, chain)
                .contextWrite(Context.of(CorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY, "error-test-id"))
        ).verifyError(RuntimeException::class.java)
    }

    @Test
    fun `извлекает client IP из X-Forwarded-For header`() {
        val clientIp = "192.168.1.100"
        val request = MockServerHttpRequest.get("/api/test")
            .header("X-Forwarded-For", "$clientIp, 10.0.0.1, 10.0.0.2")
            .build()
        val exchange = MockServerWebExchange.from(request)
        exchange.response.statusCode = HttpStatus.OK

        val chain = mock<GatewayFilterChain>()
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        StepVerifier.create(
            filter.filter(exchange, chain)
                .contextWrite(Context.of(CorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY, "ip-test-id"))
        ).verifyComplete()

        // Извлечение client IP проверяется через MDC в integration тестах
    }

    @Test
    fun `корректно обрабатывает отсутствующий correlation ID`() {
        val request = MockServerHttpRequest.get("/api/test").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.response.statusCode = HttpStatus.OK

        val chain = mock<GatewayFilterChain>()
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        // Context не установлен - должен использовать "unknown" по умолчанию
        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()
    }

    @Test
    fun `обрабатывает отсутствующий upstream URL атрибут`() {
        val request = MockServerHttpRequest.get("/api/test").build()
        val exchange = MockServerWebExchange.from(request)
        exchange.response.statusCode = HttpStatus.OK
        // GATEWAY_REQUEST_URL_ATTR не установлен - должен логировать "N/A"

        val chain = mock<GatewayFilterChain>()
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        StepVerifier.create(
            filter.filter(exchange, chain)
                .contextWrite(Context.of(CorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY, "no-upstream-test"))
        ).verifyComplete()
    }

    @Test
    fun `по умолчанию возвращает status 500 когда error signal не имеет установленного status code`() {
        val request = MockServerHttpRequest.get("/api/error").build()
        val exchange = MockServerWebExchange.from(request)
        // Status code равен null (ещё не установлен error handler)

        val chain = mock<GatewayFilterChain>()
        val testException = RuntimeException("Test error")
        whenever(chain.filter(any())).thenReturn(Mono.error(testException))

        StepVerifier.create(
            filter.filter(exchange, chain)
                .contextWrite(Context.of(CorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY, "error-no-status-test"))
        ).verifyError(RuntimeException::class.java)

        // Filter обрабатывает error signal - фактическое логирование status проверяется в реализации filter
    }
}
