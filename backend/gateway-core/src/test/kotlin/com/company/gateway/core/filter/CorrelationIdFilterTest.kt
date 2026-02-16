package com.company.gateway.core.filter

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

/**
 * Unit тесты для CorrelationIdFilter (Story 1.6)
 *
 * Тесты:
 * - AC1: Генерирует UUID correlation ID когда header отсутствует
 * - AC2: Сохраняет существующий correlation ID
 * - Добавляет correlation ID в headers запроса (upstream propagation)
 * - Добавляет correlation ID в headers ответа (client response)
 * - Сохраняет correlation ID в Reactor Context
 */
class CorrelationIdFilterTest {

    private val filter = CorrelationIdFilter()

    @Test
    fun `генерирует UUID correlation ID когда X-Correlation-ID header отсутствует`() {
        val request = MockServerHttpRequest.get("/api/test").build()
        val exchange = MockServerWebExchange.from(request)

        val chain = mock<GatewayFilterChain>()
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Проверяем, что correlation ID добавлен в headers ответа
        val correlationId = exchange.response.headers.getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER)
        assert(correlationId != null) { "Correlation ID должен присутствовать в headers ответа" }
        assert(correlationId!!.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))) {
            "Correlation ID должен быть в валидном UUID формате, получено: $correlationId"
        }

        // Проверяем, что correlation ID сохранён в атрибутах exchange
        val attributeCorrelationId = exchange.getAttribute<String>(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE)
        assert(attributeCorrelationId == correlationId) {
            "Correlation ID в атрибутах должен совпадать с header ответа"
        }
    }

    @Test
    fun `сохраняет существующий X-Correlation-ID header`() {
        val existingId = "test-correlation-id-123"
        val request = MockServerHttpRequest.get("/api/test")
            .header(CorrelationIdFilter.CORRELATION_ID_HEADER, existingId)
            .build()
        val exchange = MockServerWebExchange.from(request)

        val chain = mock<GatewayFilterChain>()
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Проверяем, что существующий correlation ID сохранён в ответе
        val responseCorrelationId = exchange.response.headers.getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER)
        assert(responseCorrelationId == existingId) {
            "Ожидался correlation ID '$existingId', получено '$responseCorrelationId'"
        }

        // Проверяем, что сохранён в атрибутах exchange
        val attributeCorrelationId = exchange.getAttribute<String>(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE)
        assert(attributeCorrelationId == existingId) {
            "Ожидался attribute correlation ID '$existingId', получено '$attributeCorrelationId'"
        }
    }

    @Test
    fun `добавляет correlation ID в мутированный запрос для upstream propagation`() {
        val request = MockServerHttpRequest.get("/api/test").build()
        val exchange = MockServerWebExchange.from(request)

        val chain = mock<GatewayFilterChain>()
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        // Проверяем, что chain.filter был вызван с мутированным exchange, содержащим correlation ID header
        verify(chain).filter(argThat { mutatedExchange ->
            val headerValue = mutatedExchange.request.headers.getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER)
            headerValue != null && headerValue.isNotBlank()
        })
    }

    @Test
    fun `сохраняет correlation ID в Reactor Context`() {
        val existingId = "context-test-id"
        val request = MockServerHttpRequest.get("/api/test")
            .header(CorrelationIdFilter.CORRELATION_ID_HEADER, existingId)
            .build()
        val exchange = MockServerWebExchange.from(request)

        val chain = mock<GatewayFilterChain>()
        // Возвращаем Mono, который проверяет context
        whenever(chain.filter(any())).thenReturn(
            Mono.deferContextual { context ->
                val correlationId = context.getOrDefault(CorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY, "not-found")
                assert(correlationId == existingId) {
                    "Context должен содержать correlation ID '$existingId', получено '$correlationId'"
                }
                Mono.empty()
            }
        )

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()
    }

    @Test
    fun `использует HIGHEST_PRECEDENCE order`() {
        assert(filter.order == org.springframework.core.Ordered.HIGHEST_PRECEDENCE) {
            "CorrelationIdFilter должен иметь HIGHEST_PRECEDENCE order"
        }
    }

    @Test
    fun `correlation ID уникален для каждого запроса когда не предоставлен`() {
        val chain = mock<GatewayFilterChain>()
        whenever(chain.filter(any())).thenReturn(Mono.empty())

        val request1 = MockServerHttpRequest.get("/api/test1").build()
        val exchange1 = MockServerWebExchange.from(request1)

        val request2 = MockServerHttpRequest.get("/api/test2").build()
        val exchange2 = MockServerWebExchange.from(request2)

        StepVerifier.create(filter.filter(exchange1, chain)).verifyComplete()
        StepVerifier.create(filter.filter(exchange2, chain)).verifyComplete()

        val id1 = exchange1.response.headers.getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER)
        val id2 = exchange2.response.headers.getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER)

        assert(id1 != id2) {
            "Каждый запрос должен получить уникальный correlation ID, но получен одинаковый ID для обоих: $id1"
        }
    }
}
