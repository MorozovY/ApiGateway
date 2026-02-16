package com.company.gateway.core.exception

import com.company.gateway.common.exception.ErrorResponse
import com.company.gateway.core.filter.CorrelationIdFilter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.netty.channel.ConnectTimeoutException
import io.netty.handler.timeout.ReadTimeoutException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.server.RequestPath
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import reactor.util.context.Context
import java.net.ConnectException
import java.net.URI
import java.util.concurrent.TimeoutException

/**
 * Unit тесты для GlobalExceptionHandler (Story 1.4)
 *
 * Тесты обработки исключений:
 * - ConnectException -> 502 Bad Gateway
 * - ConnectTimeoutException -> 502 Bad Gateway
 * - ReadTimeoutException -> 504 Gateway Timeout
 * - TimeoutException -> 504 Gateway Timeout
 * - WebClientRequestException (обёртка сетевых ошибок)
 * - Глубоко вложенные цепочки исключений
 * - Соответствие формату RFC 7807
 * - Отсутствие внутренних деталей
 */
class GlobalExceptionHandlerTest {

    companion object {
        const val TEST_CORRELATION_ID = "test-correlation-id-12345"
    }

    private lateinit var handler: GlobalExceptionHandler
    private val objectMapper = jacksonObjectMapper()

    private lateinit var exchange: ServerWebExchange
    private lateinit var request: ServerHttpRequest
    private lateinit var response: ServerHttpResponse
    private val bufferFactory = DefaultDataBufferFactory()
    private var writtenBuffer: DataBuffer? = null
    private var capturedStatusCode: HttpStatus? = null

    @BeforeEach
    fun setUp() {
        handler = GlobalExceptionHandler(objectMapper)

        val requestPath: RequestPath = mock {
            on { value() } doReturn "/api/test/resource"
        }

        val requestHeaders: HttpHeaders = HttpHeaders()

        request = mock {
            on { path } doReturn requestPath
            on { headers } doReturn requestHeaders
        }

        val headers: HttpHeaders = HttpHeaders()

        response = mock {
            on { bufferFactory() } doReturn bufferFactory
            on { this.headers } doReturn headers
            on { writeWith(any<Mono<DataBuffer>>()) } doReturn Mono.empty<Void>().doOnSubscribe {
                // Capture the written buffer when writeWith is called
            }
        }

        // Capture status code when set (doAnswer captures the setter call)
        whenever(response.setStatusCode(any())).thenAnswer { invocation ->
            capturedStatusCode = invocation.getArgument(0)
            true
        }

        exchange = mock {
            on { this.request } doReturn request
            on { this.response } doReturn response
        }
    }

    // ============================================
    // ConnectException -> 502 Bad Gateway
    // ============================================

    @Test
    fun `ConnectException возвращает 502 Bad Gateway`() {
        val exception = ConnectException("Connection refused")

        setupResponseCapture()

        StepVerifier.create(
            handler.handle(exchange, exception)
                .contextWrite(Context.of(CorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY, TEST_CORRELATION_ID))
        )
            .verifyComplete()

        // Проверяем, что HTTP status code установлен корректно
        assertStatusCode(HttpStatus.BAD_GATEWAY)

        val errorResponse = parseResponse()
        assert(errorResponse.type == "https://api.gateway/errors/upstream-unavailable") {
            "Ожидался type 'upstream-unavailable', получено '${errorResponse.type}'"
        }
        assert(errorResponse.title == "Bad Gateway") {
            "Ожидался title 'Bad Gateway', получено '${errorResponse.title}'"
        }
        assert(errorResponse.status == 502) {
            "Ожидался status 502, получено ${errorResponse.status}"
        }
        assert(errorResponse.detail == "Upstream service is unavailable") {
            "Ожидался detail 'Upstream service is unavailable', получено '${errorResponse.detail}'"
        }
        assert(errorResponse.correlationId == TEST_CORRELATION_ID) {
            "Ожидался correlationId '$TEST_CORRELATION_ID', получено '${errorResponse.correlationId}'"
        }
    }

    @Test
    fun `ConnectTimeoutException возвращает 502 Bad Gateway`() {
        val exception = ConnectTimeoutException("Connection timed out")

        setupResponseCapture()

        StepVerifier.create(handler.handle(exchange, exception))
            .verifyComplete()

        assertStatusCode(HttpStatus.BAD_GATEWAY)

        val errorResponse = parseResponse()
        assert(errorResponse.type == "https://api.gateway/errors/upstream-unavailable") {
            "Ожидался type 'upstream-unavailable', получено '${errorResponse.type}'"
        }
        assert(errorResponse.title == "Bad Gateway") {
            "Ожидался title 'Bad Gateway', получено '${errorResponse.title}'"
        }
        assert(errorResponse.status == 502) {
            "Ожидался status 502, получено ${errorResponse.status}"
        }
    }

    // ============================================
    // TimeoutException -> 504 Gateway Timeout
    // ============================================

    @Test
    fun `ReadTimeoutException возвращает 504 Gateway Timeout`() {
        val exception = ReadTimeoutException.INSTANCE

        setupResponseCapture()

        StepVerifier.create(handler.handle(exchange, exception))
            .verifyComplete()

        assertStatusCode(HttpStatus.GATEWAY_TIMEOUT)

        val errorResponse = parseResponse()
        assert(errorResponse.type == "https://api.gateway/errors/upstream-timeout") {
            "Ожидался type 'upstream-timeout', получено '${errorResponse.type}'"
        }
        assert(errorResponse.title == "Gateway Timeout") {
            "Ожидался title 'Gateway Timeout', получено '${errorResponse.title}'"
        }
        assert(errorResponse.status == 504) {
            "Ожидался status 504, получено ${errorResponse.status}"
        }
        assert(errorResponse.detail == "Upstream service did not respond in time") {
            "Ожидался detail 'Upstream service did not respond in time', получено '${errorResponse.detail}'"
        }
    }

    @Test
    fun `TimeoutException возвращает 504 Gateway Timeout`() {
        val exception = TimeoutException("Operation timed out")

        setupResponseCapture()

        StepVerifier.create(handler.handle(exchange, exception))
            .verifyComplete()

        assertStatusCode(HttpStatus.GATEWAY_TIMEOUT)

        val errorResponse = parseResponse()
        assert(errorResponse.type == "https://api.gateway/errors/upstream-timeout") {
            "Ожидался type 'upstream-timeout', получено '${errorResponse.type}'"
        }
        assert(errorResponse.title == "Gateway Timeout") {
            "Ожидался title 'Gateway Timeout', получено '${errorResponse.title}'"
        }
        assert(errorResponse.status == 504) {
            "Ожидался status 504, получено ${errorResponse.status}"
        }
    }

    // ============================================
    // Обработка WebClientRequestException
    // ============================================

    @Test
    fun `WebClientRequestException с cause ConnectException возвращает 502`() {
        val cause = ConnectException("Connection refused to host")
        val httpMethod = org.springframework.http.HttpMethod.GET
        val exception = WebClientRequestException(cause, httpMethod, URI.create("http://upstream:8080"), HttpHeaders())

        setupResponseCapture()

        StepVerifier.create(handler.handle(exchange, exception))
            .verifyComplete()

        assertStatusCode(HttpStatus.BAD_GATEWAY)

        val errorResponse = parseResponse()
        assert(errorResponse.type == "https://api.gateway/errors/upstream-unavailable") {
            "Ожидался type 'upstream-unavailable', получено '${errorResponse.type}'"
        }
        assert(errorResponse.status == 502) {
            "Ожидался status 502, получено ${errorResponse.status}"
        }
    }

    @Test
    fun `WebClientRequestException с cause TimeoutException возвращает 504`() {
        val cause = TimeoutException("Read timed out")
        val httpMethod = org.springframework.http.HttpMethod.GET
        val exception = WebClientRequestException(cause, httpMethod, URI.create("http://upstream:8080"), HttpHeaders())

        setupResponseCapture()

        StepVerifier.create(handler.handle(exchange, exception))
            .verifyComplete()

        assertStatusCode(HttpStatus.GATEWAY_TIMEOUT)

        val errorResponse = parseResponse()
        assert(errorResponse.type == "https://api.gateway/errors/upstream-timeout") {
            "Ожидался type 'upstream-timeout', получено '${errorResponse.type}'"
        }
        assert(errorResponse.status == 504) {
            "Ожидался status 504, получено ${errorResponse.status}"
        }
    }

    // ============================================
    // Глубоко вложенные цепочки исключений (H3 fix)
    // ============================================

    @Test
    fun `глубоко вложенный ConnectException разворачивается и возвращает 502`() {
        // Симулируем: WebClientRequestException -> IOException -> ConnectException
        val rootCause = ConnectException("Connection refused")
        val middleException = java.io.IOException("Network error", rootCause)
        val httpMethod = org.springframework.http.HttpMethod.GET
        val exception = WebClientRequestException(middleException, httpMethod, URI.create("http://upstream:8080"), HttpHeaders())

        setupResponseCapture()

        StepVerifier.create(handler.handle(exchange, exception))
            .verifyComplete()

        assertStatusCode(HttpStatus.BAD_GATEWAY)

        val errorResponse = parseResponse()
        assert(errorResponse.type == "https://api.gateway/errors/upstream-unavailable") {
            "Ожидалось, что глубоко вложенный ConnectException будет развёрнут, получено type '${errorResponse.type}'"
        }
        assert(errorResponse.status == 502) {
            "Ожидался status 502 для глубоко вложенного ConnectException, получено ${errorResponse.status}"
        }
    }

    @Test
    fun `глубоко вложенный TimeoutException разворачивается и возвращает 504`() {
        // Симулируем: RuntimeException -> IOException -> TimeoutException
        val rootCause = TimeoutException("Read timed out")
        val middleException = java.io.IOException("IO error", rootCause)
        val wrapperException = RuntimeException("Wrapped", middleException)

        setupResponseCapture()

        StepVerifier.create(handler.handle(exchange, wrapperException))
            .verifyComplete()

        assertStatusCode(HttpStatus.GATEWAY_TIMEOUT)

        val errorResponse = parseResponse()
        assert(errorResponse.type == "https://api.gateway/errors/upstream-timeout") {
            "Ожидалось, что глубоко вложенный TimeoutException будет развёрнут, получено type '${errorResponse.type}'"
        }
        assert(errorResponse.status == 504) {
            "Ожидался status 504 для глубоко вложенного TimeoutException, получено ${errorResponse.status}"
        }
    }

    // ============================================
    // Соответствие формату RFC 7807
    // ============================================

    @Test
    fun `error response соответствует формату RFC 7807`() {
        val exception = ConnectException("Connection refused")

        setupResponseCapture()

        StepVerifier.create(handler.handle(exchange, exception))
            .verifyComplete()

        val errorResponse = parseResponse()
        // RFC 7807 обязательные поля
        assert(errorResponse.type.startsWith("https://")) { "type должен быть URI" }
        assert(errorResponse.title.isNotBlank()) { "title должен присутствовать" }
        assert(errorResponse.status > 0) { "status должен быть положительным HTTP status code" }
        assert(errorResponse.detail.isNotBlank()) { "detail должен присутствовать" }
        // instance должен быть путём запроса
        assert(errorResponse.instance == "/api/test/resource") {
            "instance должен быть '/api/test/resource', получено '${errorResponse.instance}'"
        }
    }

    // ============================================
    // Correlation ID в error responses (Story 1.6)
    // ============================================

    @Test
    fun `error response включает correlation ID из context`() {
        val exception = ConnectException("Connection refused")

        setupResponseCapture()

        StepVerifier.create(
            handler.handle(exchange, exception)
                .contextWrite(Context.of(CorrelationIdFilter.CORRELATION_ID_CONTEXT_KEY, TEST_CORRELATION_ID))
        )
            .verifyComplete()

        val errorResponse = parseResponse()
        assert(errorResponse.correlationId == TEST_CORRELATION_ID) {
            "Ожидался correlationId '$TEST_CORRELATION_ID', получено '${errorResponse.correlationId}'"
        }
    }

    @Test
    fun `error response генерирует UUID когда correlation ID отсутствует в context или headers`() {
        val exception = ConnectException("Connection refused")

        setupResponseCapture()

        // Context не установлен - должен сгенерировать UUID
        StepVerifier.create(handler.handle(exchange, exception))
            .verifyComplete()

        val errorResponse = parseResponse()
        assert(errorResponse.correlationId != null) {
            "Ожидалось, что correlationId будет сгенерирован, получено null"
        }
        assert(errorResponse.correlationId!!.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))) {
            "Ожидалось, что correlationId будет валидным UUID, получено '${errorResponse.correlationId}'"
        }
    }

    // ============================================
    // Безопасность: внутренние детали не раскрываются
    // ============================================

    @Test
    fun `error response не раскрывает internal hostname`() {
        val exception = ConnectException("Connection refused: localhost/127.0.0.1:8080")

        setupResponseCapture()

        StepVerifier.create(handler.handle(exchange, exception))
            .verifyComplete()

        val errorResponse = parseResponse()
        val responseJson = objectMapper.writeValueAsString(errorResponse)

        assert(!responseJson.contains("localhost", ignoreCase = true)) {
            "Response не должен содержать 'localhost': $responseJson"
        }
        assert(!responseJson.contains("127.0.0.1")) {
            "Response не должен содержать IP адреса: $responseJson"
        }
    }

    @Test
    fun `error response не раскрывает имена классов исключений`() {
        val exception = ConnectException("java.net.ConnectException: Connection refused")

        setupResponseCapture()

        StepVerifier.create(handler.handle(exchange, exception))
            .verifyComplete()

        val errorResponse = parseResponse()
        val responseJson = objectMapper.writeValueAsString(errorResponse)

        assert(!responseJson.contains("ConnectException", ignoreCase = true)) {
            "Response не должен содержать имена классов исключений: $responseJson"
        }
        assert(!responseJson.contains("java.net")) {
            "Response не должен содержать имена Java пакетов: $responseJson"
        }
    }

    // ============================================
    // Вспомогательные методы
    // ============================================

    private fun setupResponseCapture() {
        whenever(response.writeWith(any<Mono<DataBuffer>>())).thenAnswer { invocation ->
            val publisher = invocation.getArgument<Mono<DataBuffer>>(0)
            publisher.doOnNext { buffer ->
                writtenBuffer = buffer
            }.then()
        }
    }

    private fun parseResponse(): ErrorResponse {
        requireNotNull(writtenBuffer) { "No response was written" }
        val bytes = ByteArray(writtenBuffer!!.readableByteCount())
        writtenBuffer!!.read(bytes)
        return objectMapper.readValue(bytes, ErrorResponse::class.java)
    }

    private fun assertStatusCode(expected: HttpStatus) {
        assert(capturedStatusCode == expected) {
            "Expected HTTP status $expected, but was $capturedStatusCode"
        }
    }
}
