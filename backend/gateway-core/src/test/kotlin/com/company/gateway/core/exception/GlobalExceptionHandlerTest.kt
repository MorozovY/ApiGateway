package com.company.gateway.core.exception

import com.company.gateway.common.exception.ErrorResponse
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
import java.net.ConnectException
import java.net.URI
import java.util.concurrent.TimeoutException

/**
 * Unit tests for GlobalExceptionHandler (Story 1.4)
 *
 * Tests exception handling for:
 * - ConnectException -> 502 Bad Gateway
 * - ConnectTimeoutException -> 502 Bad Gateway
 * - ReadTimeoutException -> 504 Gateway Timeout
 * - TimeoutException -> 504 Gateway Timeout
 * - WebClientRequestException (wrapping network errors)
 * - Deeply nested exception chains
 * - RFC 7807 format compliance
 * - No internal details exposure
 */
class GlobalExceptionHandlerTest {

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

        request = mock {
            on { path } doReturn requestPath
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
    fun `ConnectException returns 502 Bad Gateway`() {
        val exception = ConnectException("Connection refused")

        setupResponseCapture()

        StepVerifier.create(handler.handle(exchange, exception))
            .verifyComplete()

        // Verify HTTP status code was set correctly
        assertStatusCode(HttpStatus.BAD_GATEWAY)

        val errorResponse = parseResponse()
        assert(errorResponse.type == "https://api.gateway/errors/upstream-unavailable") {
            "Expected type 'upstream-unavailable', got '${errorResponse.type}'"
        }
        assert(errorResponse.title == "Bad Gateway") {
            "Expected title 'Bad Gateway', got '${errorResponse.title}'"
        }
        assert(errorResponse.status == 502) {
            "Expected status 502, got ${errorResponse.status}"
        }
        assert(errorResponse.detail == "Upstream service is unavailable") {
            "Expected detail 'Upstream service is unavailable', got '${errorResponse.detail}'"
        }
    }

    @Test
    fun `ConnectTimeoutException returns 502 Bad Gateway`() {
        val exception = ConnectTimeoutException("Connection timed out")

        setupResponseCapture()

        StepVerifier.create(handler.handle(exchange, exception))
            .verifyComplete()

        assertStatusCode(HttpStatus.BAD_GATEWAY)

        val errorResponse = parseResponse()
        assert(errorResponse.type == "https://api.gateway/errors/upstream-unavailable") {
            "Expected type 'upstream-unavailable', got '${errorResponse.type}'"
        }
        assert(errorResponse.title == "Bad Gateway") {
            "Expected title 'Bad Gateway', got '${errorResponse.title}'"
        }
        assert(errorResponse.status == 502) {
            "Expected status 502, got ${errorResponse.status}"
        }
    }

    // ============================================
    // TimeoutException -> 504 Gateway Timeout
    // ============================================

    @Test
    fun `ReadTimeoutException returns 504 Gateway Timeout`() {
        val exception = ReadTimeoutException.INSTANCE

        setupResponseCapture()

        StepVerifier.create(handler.handle(exchange, exception))
            .verifyComplete()

        assertStatusCode(HttpStatus.GATEWAY_TIMEOUT)

        val errorResponse = parseResponse()
        assert(errorResponse.type == "https://api.gateway/errors/upstream-timeout") {
            "Expected type 'upstream-timeout', got '${errorResponse.type}'"
        }
        assert(errorResponse.title == "Gateway Timeout") {
            "Expected title 'Gateway Timeout', got '${errorResponse.title}'"
        }
        assert(errorResponse.status == 504) {
            "Expected status 504, got ${errorResponse.status}"
        }
        assert(errorResponse.detail == "Upstream service did not respond in time") {
            "Expected detail 'Upstream service did not respond in time', got '${errorResponse.detail}'"
        }
    }

    @Test
    fun `TimeoutException returns 504 Gateway Timeout`() {
        val exception = TimeoutException("Operation timed out")

        setupResponseCapture()

        StepVerifier.create(handler.handle(exchange, exception))
            .verifyComplete()

        assertStatusCode(HttpStatus.GATEWAY_TIMEOUT)

        val errorResponse = parseResponse()
        assert(errorResponse.type == "https://api.gateway/errors/upstream-timeout") {
            "Expected type 'upstream-timeout', got '${errorResponse.type}'"
        }
        assert(errorResponse.title == "Gateway Timeout") {
            "Expected title 'Gateway Timeout', got '${errorResponse.title}'"
        }
        assert(errorResponse.status == 504) {
            "Expected status 504, got ${errorResponse.status}"
        }
    }

    // ============================================
    // WebClientRequestException handling
    // ============================================

    @Test
    fun `WebClientRequestException with ConnectException cause returns 502`() {
        val cause = ConnectException("Connection refused to host")
        val httpMethod = org.springframework.http.HttpMethod.GET
        val exception = WebClientRequestException(cause, httpMethod, URI.create("http://upstream:8080"), HttpHeaders())

        setupResponseCapture()

        StepVerifier.create(handler.handle(exchange, exception))
            .verifyComplete()

        assertStatusCode(HttpStatus.BAD_GATEWAY)

        val errorResponse = parseResponse()
        assert(errorResponse.type == "https://api.gateway/errors/upstream-unavailable") {
            "Expected type 'upstream-unavailable', got '${errorResponse.type}'"
        }
        assert(errorResponse.status == 502) {
            "Expected status 502, got ${errorResponse.status}"
        }
    }

    @Test
    fun `WebClientRequestException with TimeoutException cause returns 504`() {
        val cause = TimeoutException("Read timed out")
        val httpMethod = org.springframework.http.HttpMethod.GET
        val exception = WebClientRequestException(cause, httpMethod, URI.create("http://upstream:8080"), HttpHeaders())

        setupResponseCapture()

        StepVerifier.create(handler.handle(exchange, exception))
            .verifyComplete()

        assertStatusCode(HttpStatus.GATEWAY_TIMEOUT)

        val errorResponse = parseResponse()
        assert(errorResponse.type == "https://api.gateway/errors/upstream-timeout") {
            "Expected type 'upstream-timeout', got '${errorResponse.type}'"
        }
        assert(errorResponse.status == 504) {
            "Expected status 504, got ${errorResponse.status}"
        }
    }

    // ============================================
    // Deeply nested exception chains (H3 fix)
    // ============================================

    @Test
    fun `deeply nested ConnectException is unwrapped and returns 502`() {
        // Simulate: WebClientRequestException -> IOException -> ConnectException
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
            "Expected deeply nested ConnectException to be unwrapped, got type '${errorResponse.type}'"
        }
        assert(errorResponse.status == 502) {
            "Expected status 502 for deeply nested ConnectException, got ${errorResponse.status}"
        }
    }

    @Test
    fun `deeply nested TimeoutException is unwrapped and returns 504`() {
        // Simulate: RuntimeException -> IOException -> TimeoutException
        val rootCause = TimeoutException("Read timed out")
        val middleException = java.io.IOException("IO error", rootCause)
        val wrapperException = RuntimeException("Wrapped", middleException)

        setupResponseCapture()

        StepVerifier.create(handler.handle(exchange, wrapperException))
            .verifyComplete()

        assertStatusCode(HttpStatus.GATEWAY_TIMEOUT)

        val errorResponse = parseResponse()
        assert(errorResponse.type == "https://api.gateway/errors/upstream-timeout") {
            "Expected deeply nested TimeoutException to be unwrapped, got type '${errorResponse.type}'"
        }
        assert(errorResponse.status == 504) {
            "Expected status 504 for deeply nested TimeoutException, got ${errorResponse.status}"
        }
    }

    // ============================================
    // RFC 7807 format compliance
    // ============================================

    @Test
    fun `error response follows RFC 7807 format`() {
        val exception = ConnectException("Connection refused")

        setupResponseCapture()

        StepVerifier.create(handler.handle(exchange, exception))
            .verifyComplete()

        val errorResponse = parseResponse()
        // RFC 7807 required fields
        assert(errorResponse.type.startsWith("https://")) { "type should be a URI" }
        assert(errorResponse.title.isNotBlank()) { "title should be present" }
        assert(errorResponse.status > 0) { "status should be positive HTTP status code" }
        assert(errorResponse.detail.isNotBlank()) { "detail should be present" }
        // instance should be the request path
        assert(errorResponse.instance == "/api/test/resource") {
            "instance should be '/api/test/resource', got '${errorResponse.instance}'"
        }
    }

    // ============================================
    // Security: No internal details exposed
    // ============================================

    @Test
    fun `error response does not expose internal hostname`() {
        val exception = ConnectException("Connection refused: localhost/127.0.0.1:8080")

        setupResponseCapture()

        StepVerifier.create(handler.handle(exchange, exception))
            .verifyComplete()

        val errorResponse = parseResponse()
        val responseJson = objectMapper.writeValueAsString(errorResponse)

        assert(!responseJson.contains("localhost", ignoreCase = true)) {
            "Response should not contain 'localhost': $responseJson"
        }
        assert(!responseJson.contains("127.0.0.1")) {
            "Response should not contain IP addresses: $responseJson"
        }
    }

    @Test
    fun `error response does not expose exception class names`() {
        val exception = ConnectException("java.net.ConnectException: Connection refused")

        setupResponseCapture()

        StepVerifier.create(handler.handle(exchange, exception))
            .verifyComplete()

        val errorResponse = parseResponse()
        val responseJson = objectMapper.writeValueAsString(errorResponse)

        assert(!responseJson.contains("ConnectException", ignoreCase = true)) {
            "Response should not contain exception class names: $responseJson"
        }
        assert(!responseJson.contains("java.net")) {
            "Response should not contain Java package names: $responseJson"
        }
    }

    // ============================================
    // Helper methods
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
