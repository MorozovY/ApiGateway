package com.company.gateway.admin.security

import com.company.gateway.admin.security.AuditContextFilter.Companion.AUDIT_CORRELATION_ID_KEY
import com.company.gateway.admin.security.AuditContextFilter.Companion.AUDIT_IP_ADDRESS_KEY
import com.company.gateway.common.Constants.CORRELATION_ID_HEADER
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.net.InetSocketAddress

/**
 * Unit тесты для AuditContextFilter (Story 7.1, AC3).
 *
 * Проверяет:
 * - Извлечение IP из X-Forwarded-For (первый IP из списка)
 * - Извлечение IP из X-Real-IP
 * - Fallback на remoteAddress
 * - Генерация UUID если correlation ID отсутствует
 * - Сохранение correlation ID из header
 */
class AuditContextFilterTest {

    private lateinit var filter: AuditContextFilter
    private lateinit var exchange: ServerWebExchange
    private lateinit var request: ServerHttpRequest
    private lateinit var headers: HttpHeaders
    private lateinit var chain: WebFilterChain

    @BeforeEach
    fun setUp() {
        filter = AuditContextFilter()
        exchange = mock()
        request = mock()
        headers = HttpHeaders()
        chain = mock()

        whenever(exchange.request).thenReturn(request)
        whenever(request.headers).thenReturn(headers)
    }

    // ============================================
    // IP Address Extraction
    // ============================================

    @Nested
    inner class IPAddressExtraction {

        @Test
        fun `извлекает IP из X-Forwarded-For header`() {
            // Given
            val clientIp = "192.168.1.100"
            headers.add("X-Forwarded-For", clientIp)
            whenever(request.remoteAddress).thenReturn(null)

            var capturedIp: String? = null
            whenever(chain.filter(any())).thenReturn(
                Mono.deferContextual { ctx ->
                    capturedIp = ctx.getOrDefault(AUDIT_IP_ADDRESS_KEY, null)
                    Mono.empty()
                }
            )

            // When
            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            // Then
            assert(capturedIp == clientIp) { "IP должен быть извлечён из X-Forwarded-For: expected $clientIp, got $capturedIp" }
        }

        @Test
        fun `извлекает первый IP из X-Forwarded-For с несколькими адресами`() {
            // Given
            val clientIp = "192.168.1.100"
            headers.add("X-Forwarded-For", "$clientIp, 10.0.0.1, 10.0.0.2")
            whenever(request.remoteAddress).thenReturn(null)

            var capturedIp: String? = null
            whenever(chain.filter(any())).thenReturn(
                Mono.deferContextual { ctx ->
                    capturedIp = ctx.getOrDefault(AUDIT_IP_ADDRESS_KEY, null)
                    Mono.empty()
                }
            )

            // When
            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            // Then
            assert(capturedIp == clientIp) { "Должен быть первый IP из списка: expected $clientIp, got $capturedIp" }
        }

        @Test
        fun `извлекает IP из X-Real-IP когда X-Forwarded-For отсутствует`() {
            // Given
            val clientIp = "10.0.0.50"
            headers.add("X-Real-IP", clientIp)
            whenever(request.remoteAddress).thenReturn(null)

            var capturedIp: String? = null
            whenever(chain.filter(any())).thenReturn(
                Mono.deferContextual { ctx ->
                    capturedIp = ctx.getOrDefault(AUDIT_IP_ADDRESS_KEY, null)
                    Mono.empty()
                }
            )

            // When
            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            // Then
            assert(capturedIp == clientIp) { "IP должен быть извлечён из X-Real-IP: expected $clientIp, got $capturedIp" }
        }

        @Test
        fun `использует remoteAddress когда headers отсутствуют`() {
            // Given
            val remoteIp = "127.0.0.1"
            whenever(request.remoteAddress).thenReturn(InetSocketAddress(remoteIp, 12345))

            var capturedIp: String? = null
            whenever(chain.filter(any())).thenReturn(
                Mono.deferContextual { ctx ->
                    capturedIp = ctx.getOrDefault(AUDIT_IP_ADDRESS_KEY, null)
                    Mono.empty()
                }
            )

            // When
            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            // Then
            assert(capturedIp == remoteIp) { "IP должен быть извлечён из remoteAddress: expected $remoteIp, got $capturedIp" }
        }

        @Test
        fun `X-Forwarded-For имеет приоритет над X-Real-IP`() {
            // Given
            val forwardedIp = "192.168.1.100"
            val realIp = "10.0.0.50"
            headers.add("X-Forwarded-For", forwardedIp)
            headers.add("X-Real-IP", realIp)
            whenever(request.remoteAddress).thenReturn(null)

            var capturedIp: String? = null
            whenever(chain.filter(any())).thenReturn(
                Mono.deferContextual { ctx ->
                    capturedIp = ctx.getOrDefault(AUDIT_IP_ADDRESS_KEY, null)
                    Mono.empty()
                }
            )

            // When
            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            // Then
            assert(capturedIp == forwardedIp) { "X-Forwarded-For должен иметь приоритет: expected $forwardedIp, got $capturedIp" }
        }

        @Test
        fun `IP null когда все источники отсутствуют`() {
            // Given
            whenever(request.remoteAddress).thenReturn(null)

            var capturedIp: String? = "not-null"
            whenever(chain.filter(any())).thenReturn(
                Mono.deferContextual { ctx ->
                    capturedIp = ctx.getOrDefault(AUDIT_IP_ADDRESS_KEY, null)
                    Mono.empty()
                }
            )

            // When
            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            // Then
            assert(capturedIp == null) { "IP должен быть null когда источники отсутствуют" }
        }
    }

    // ============================================
    // Correlation ID Extraction
    // ============================================

    @Nested
    inner class CorrelationIDExtraction {

        @Test
        fun `использует correlation ID из header`() {
            // Given
            val correlationId = "test-correlation-id-12345"
            headers.add(CORRELATION_ID_HEADER, correlationId)
            whenever(request.remoteAddress).thenReturn(null)

            var capturedCorrelationId: String? = null
            whenever(chain.filter(any())).thenReturn(
                Mono.deferContextual { ctx ->
                    capturedCorrelationId = ctx.getOrDefault(AUDIT_CORRELATION_ID_KEY, null)
                    Mono.empty()
                }
            )

            // When
            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            // Then
            assert(capturedCorrelationId == correlationId) {
                "Correlation ID должен быть из header: expected $correlationId, got $capturedCorrelationId"
            }
        }

        @Test
        fun `генерирует UUID когда correlation ID header отсутствует`() {
            // Given
            whenever(request.remoteAddress).thenReturn(null)

            var capturedCorrelationId: String? = null
            whenever(chain.filter(any())).thenReturn(
                Mono.deferContextual { ctx ->
                    capturedCorrelationId = ctx.getOrDefault(AUDIT_CORRELATION_ID_KEY, null)
                    Mono.empty()
                }
            )

            // When
            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            // Then
            assert(capturedCorrelationId != null) { "Correlation ID должен быть сгенерирован" }
            assert(capturedCorrelationId!!.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))) {
                "Correlation ID должен быть в формате UUID: $capturedCorrelationId"
            }
        }

        @Test
        fun `генерирует уникальные UUID для разных запросов`() {
            // Given
            whenever(request.remoteAddress).thenReturn(null)

            val correlationIds = mutableListOf<String>()
            whenever(chain.filter(any())).thenReturn(
                Mono.deferContextual { ctx ->
                    correlationIds.add(ctx.getOrDefault(AUDIT_CORRELATION_ID_KEY, "")!!)
                    Mono.empty()
                }
            )

            // When
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete()
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete()

            // Then
            assert(correlationIds.size == 2) { "Должно быть 2 correlation ID" }
            assert(correlationIds[0] != correlationIds[1]) { "Correlation IDs должны быть уникальными" }
        }
    }

    // ============================================
    // Context Propagation
    // ============================================

    @Nested
    inner class ContextPropagation {

        @Test
        fun `оба значения доступны в context`() {
            // Given
            val clientIp = "192.168.1.100"
            val correlationId = "test-corr-id"
            headers.add("X-Forwarded-For", clientIp)
            headers.add(CORRELATION_ID_HEADER, correlationId)
            whenever(request.remoteAddress).thenReturn(null)

            var capturedIp: String? = null
            var capturedCorrelationId: String? = null
            whenever(chain.filter(any())).thenReturn(
                Mono.deferContextual { ctx ->
                    capturedIp = ctx.getOrDefault(AUDIT_IP_ADDRESS_KEY, null)
                    capturedCorrelationId = ctx.getOrDefault(AUDIT_CORRELATION_ID_KEY, null)
                    Mono.empty()
                }
            )

            // When
            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            // Then
            assert(capturedIp == clientIp) { "IP должен быть в context" }
            assert(capturedCorrelationId == correlationId) { "Correlation ID должен быть в context" }
        }
    }
}
