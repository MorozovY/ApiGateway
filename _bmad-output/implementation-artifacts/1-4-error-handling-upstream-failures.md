# Story 1.4: Error Handling for Upstream Failures

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **DevOps Engineer**,
I want proper error responses when upstream services are unavailable,
So that clients receive meaningful error information (FR29).

## Acceptance Criteria

1. **AC1:** Given a published route exists
   When the upstream service is unreachable (connection refused)
   Then the gateway returns HTTP 502 Bad Gateway
   And the response body follows RFC 7807 format:
   ```json
   {
     "type": "https://api.gateway/errors/upstream-unavailable",
     "title": "Bad Gateway",
     "status": 502,
     "detail": "Upstream service is unavailable",
     "correlationId": "abc-123"
   }
   ```

2. **AC2:** Given a published route exists
   When the upstream service times out (>30 seconds default)
   Then the gateway returns HTTP 504 Gateway Timeout
   And the response body follows RFC 7807 format:
   ```json
   {
     "type": "https://api.gateway/errors/upstream-timeout",
     "title": "Gateway Timeout",
     "status": 504,
     "detail": "Upstream service did not respond in time",
     "correlationId": "abc-123"
   }
   ```

3. **AC3:** Given a published route exists
   When the upstream returns 5xx error
   Then the gateway passes through the upstream error response unchanged
   And preserves the original status code (500, 502, 503, etc.)

4. **AC4:** All gateway-generated error responses include correlationId field
   (Placeholder for Story 1.6 - correlationId generation)

5. **AC5:** Error responses do not expose internal system details (hostnames, stack traces)

## Tasks / Subtasks

- [x] **Task 1: Upstream Error Filter Implementation** (AC: #1, #2, #4, #5)
  - [x] Subtask 1.1: Create UpstreamErrorFilter.kt as GlobalFilter with Order after routing
    - Note: Implemented via GlobalExceptionHandler enhancement (better approach per Dev Notes)
  - [x] Subtask 1.2: Handle ConnectException and WebClientRequestException for connection refused
  - [x] Subtask 1.3: Handle TimeoutException for upstream timeouts
  - [x] Subtask 1.4: Return RFC 7807 ErrorResponse with appropriate error types
  - [x] Subtask 1.5: Log upstream errors with correlation context (for debugging)

- [x] **Task 2: Gateway Timeout Configuration** (AC: #2)
  - [x] Subtask 2.1: Configure default timeout in application.yml (30 seconds)
  - [x] Subtask 2.2: Add connect-timeout and response-timeout settings
  - [x] Subtask 2.3: Document timeout configuration options

- [x] **Task 3: GlobalExceptionHandler Enhancement** (AC: #1, #2, #4, #5)
  - [x] Subtask 3.1: Add handling for ConnectException -> 502 Bad Gateway
  - [x] Subtask 3.2: Add handling for TimeoutException -> 504 Gateway Timeout
  - [x] Subtask 3.3: Add handling for WebClientRequestException
  - [x] Subtask 3.4: Ensure no internal details leak in error messages
  - [x] Subtask 3.5: Add placeholder for correlationId (actual generation in Story 1.6)

- [x] **Task 4: Upstream Passthrough for 5xx** (AC: #3)
  - [x] Subtask 4.1: Verify default Spring Cloud Gateway behavior passes through 5xx
  - [x] Subtask 4.2: Ensure error filter does NOT intercept upstream 5xx responses
  - [x] Subtask 4.3: Add test to verify 5xx passthrough

- [x] **Task 5: Unit Tests** (AC: #1-#5)
  - [x] Subtask 5.1: Test connection refused returns 502
  - [x] Subtask 5.2: Test timeout returns 504
  - [x] Subtask 5.3: Test upstream 5xx passes through
  - [x] Subtask 5.4: Test error responses follow RFC 7807 format
  - [x] Subtask 5.5: Test no internal details exposed

- [x] **Task 6: Integration Tests** (AC: #1-#3)
  - [x] Subtask 6.1: Test with WireMock simulating connection refused
  - [x] Subtask 6.2: Test with WireMock simulating delayed response (timeout)
  - [x] Subtask 6.3: Test with WireMock returning 500, 502, 503 responses

## Dev Notes

### Previous Story Intelligence (Story 1.3)

**Completed Foundation:**
- `GlobalExceptionHandler.kt` already exists with RFC 7807 support
- `ErrorResponse.kt` in gateway-common with type, title, status, detail, instance, correlationId fields
- `DynamicRouteLocator.kt` loads published routes from database
- RFC 7807 format is established and working for 404 errors
- WireMock test infrastructure exists in GatewayRoutingIntegrationTest.kt

**Key Learnings from Story 1.3 Code Review:**
- GlobalExceptionHandler uses Order(-1) to intercept before default handlers
- Handle Spring's NotFoundException type properly
- Reactive error handling with Mono.error() and onErrorResume()
- Test infrastructure uses Testcontainers + WireMock

**Existing Relevant Files:**
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/exception/GlobalExceptionHandler.kt` - RFC 7807 handler
- `backend/gateway-common/src/main/kotlin/com/company/gateway/common/exception/ErrorResponse.kt` - Error DTO
- `backend/gateway-core/src/main/resources/application.yml` - Gateway configuration
- `backend/gateway-core/src/test/kotlin/com/company/gateway/core/integration/GatewayRoutingIntegrationTest.kt` - Test patterns

**Port Configuration:**
- gateway-core: 8080
- gateway-admin: 8081
- PostgreSQL: 5432
- Redis: 6379
- WireMock in tests: dynamic port

### Architecture Compliance

**Spring Cloud Gateway Error Handling Architecture:**
```
┌──────────┐     ┌─────────────────────────────────────────────────────────────┐
│  Client  │────▶│                     gateway-core                            │
└──────────┘     │  ┌───────────────────────────────────────────────────────┐  │
                 │  │                    Filter Chain                        │  │
                 │  │  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐  │  │
                 │  │  │ Pre-filters │─▶│   Routing    │─▶│ Post-filters │  │  │
                 │  │  └─────────────┘  └──────────────┘  └──────────────┘  │  │
                 │  │          │               │                  │          │  │
                 │  │          ▼               ▼                  ▼          │  │
                 │  │  ┌────────────────────────────────────────────────┐   │  │
                 │  │  │           GlobalExceptionHandler              │   │  │
                 │  │  │  - ConnectException -> 502 Bad Gateway        │   │  │
                 │  │  │  - TimeoutException -> 504 Gateway Timeout    │   │  │
                 │  │  │  - Upstream 5xx -> Passthrough                │   │  │
                 │  │  └────────────────────────────────────────────────┘   │  │
                 │  └───────────────────────────────────────────────────────┘  │
                 └─────────────────────────────────────────────────────────────┘
                                            │
                                            ▼
                                   ┌──────────────┐
                                   │   Upstream   │
                                   │   Service    │
                                   │  (may fail)  │
                                   └──────────────┘
```

**MANDATORY: RFC 7807 Problem Details Format:**
```json
{
  "type": "https://api.gateway/errors/{error-type}",
  "title": "Human-readable title",
  "status": 502,
  "detail": "User-friendly error message",
  "instance": "/path/that/failed",
  "correlationId": "uuid-for-tracing"
}
```

**Error Type URIs:**
| Error | Type URI | HTTP Status |
|-------|----------|-------------|
| Upstream unreachable | `https://api.gateway/errors/upstream-unavailable` | 502 |
| Upstream timeout | `https://api.gateway/errors/upstream-timeout` | 504 |
| Route not found | `https://api.gateway/errors/route-not-found` | 404 |
| Internal error | `https://api.gateway/errors/internal-error` | 500 |

### Technical Requirements

**Exception Types to Handle:**

| Exception | Cause | Response |
|-----------|-------|----------|
| `java.net.ConnectException` | Connection refused | 502 Bad Gateway |
| `io.netty.channel.ConnectTimeoutException` | Connect timeout | 502 Bad Gateway |
| `io.netty.handler.timeout.ReadTimeoutException` | Response timeout | 504 Gateway Timeout |
| `java.util.concurrent.TimeoutException` | General timeout | 504 Gateway Timeout |
| `org.springframework.web.reactive.function.client.WebClientRequestException` | Various network errors | 502/504 |

**GlobalExceptionHandler Enhancement Pattern:**
```kotlin
// Add to existing GlobalExceptionHandler.kt
is ConnectException, is ConnectTimeoutException -> Pair(
    HttpStatus.BAD_GATEWAY,
    ErrorResponse(
        type = "https://api.gateway/errors/upstream-unavailable",
        title = "Bad Gateway",
        status = HttpStatus.BAD_GATEWAY.value(),
        detail = "Upstream service is unavailable",
        instance = requestPath
        // correlationId will be added in Story 1.6
    )
)

is ReadTimeoutException, is TimeoutException -> Pair(
    HttpStatus.GATEWAY_TIMEOUT,
    ErrorResponse(
        type = "https://api.gateway/errors/upstream-timeout",
        title = "Gateway Timeout",
        status = HttpStatus.GATEWAY_TIMEOUT.value(),
        detail = "Upstream service did not respond in time",
        instance = requestPath
    )
)
```

**Timeout Configuration (application.yml):**
```yaml
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 5000  # 5 seconds to establish connection
        response-timeout: 30s  # 30 seconds to receive response
```

**Security: No Internal Details in Errors:**
- DO NOT include hostnames/IPs of upstream services
- DO NOT include exception stack traces
- DO NOT include internal service names beyond what's in the route
- DO include: error type, generic message, correlationId (for support)

### Upstream 5xx Passthrough

**Default Behavior Verification:**
Spring Cloud Gateway by default passes through upstream responses including 5xx errors. The error filter must NOT intercept these responses.

**Filter Order Considerations:**
- GlobalExceptionHandler handles exceptions (unhandled errors)
- Upstream 5xx responses are NOT exceptions - they are valid HTTP responses
- No additional filter needed for passthrough - verify default behavior works

**Test Pattern for Passthrough:**
```kotlin
@Test
fun `upstream 503 is passed through unchanged`() {
    // Configure WireMock to return 503
    wireMockServer.stubFor(
        get(urlPathMatching("/api/orders.*"))
            .willReturn(aResponse()
                .withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("""{"error": "Service Unavailable"}"""))
    )

    // Insert published route
    insertRoute(publishedRoute)
    refreshRoutes()

    // Make request
    webTestClient.get()
        .uri("/api/orders/123")
        .exchange()
        .expectStatus().isEqualTo(503)
        .expectBody()
        .jsonPath("$.error").isEqualTo("Service Unavailable")
}
```

### Project Structure Notes

**Files to Modify:**

```
backend/gateway-core/src/main/kotlin/com/company/gateway/core/
├── exception/
│   └── GlobalExceptionHandler.kt  # Add upstream error handling
└── src/main/resources/
    └── application.yml            # Add timeout configuration
```

**Files to Create:**

```
backend/gateway-core/src/test/kotlin/com/company/gateway/core/
└── integration/
    └── UpstreamErrorHandlingIntegrationTest.kt  # New test class
```

**Optional Files (if cleaner separation needed):**
```
backend/gateway-core/src/main/kotlin/com/company/gateway/core/
└── exception/
    └── UpstreamExceptionHandler.kt  # Separate handler for upstream errors
```

### Testing Strategy

**Unit Tests:**
- Mock exception scenarios in GlobalExceptionHandler
- Verify correct HTTP status and RFC 7807 format

**Integration Tests (with Testcontainers + WireMock):**
```kotlin
@SpringBootTest
@AutoConfigureWebTestClient
class UpstreamErrorHandlingIntegrationTest {

    @Test
    fun `connection refused returns 502 Bad Gateway`() {
        // Configure route to non-existent port
        insertRoute(routeToDeadUpstream)
        refreshRoutes()

        webTestClient.get()
            .uri("/api/dead-service/test")
            .exchange()
            .expectStatus().isEqualTo(502)
            .expectBody()
            .jsonPath("$.type").isEqualTo("https://api.gateway/errors/upstream-unavailable")
            .jsonPath("$.title").isEqualTo("Bad Gateway")
            .jsonPath("$.status").isEqualTo(502)
    }

    @Test
    fun `slow upstream returns 504 Gateway Timeout`() {
        // Configure WireMock with delay > timeout
        wireMockServer.stubFor(
            get(urlPathMatching("/api/slow.*"))
                .willReturn(aResponse()
                    .withFixedDelay(35000)  // 35 seconds > 30s timeout
                    .withStatus(200))
        )
        // ...
    }
}
```

**WireMock Scenarios:**
1. **Connection refused:** Route to closed port (e.g., localhost:9999)
2. **Timeout:** WireMock with `.withFixedDelay(35000)` (> 30s timeout)
3. **Upstream 500:** WireMock returning `.withStatus(500)`
4. **Upstream 502:** WireMock returning `.withStatus(502)`
5. **Upstream 503:** WireMock returning `.withStatus(503)`

### References

- [Source: epics.md#Story 1.4: Error Handling for Upstream Failures] - Original acceptance criteria
- [Source: architecture.md#API & Communication Patterns] - RFC 7807 error format
- [Source: architecture.md#Core Architectural Decisions] - Graceful degradation
- [Source: prd.md#Functional Requirements] - FR29: System returns correct error codes
- [Source: prd.md#Non-Functional Requirements] - NFR7: Graceful degradation
- [Source: 1-3-basic-gateway-routing.md] - GlobalExceptionHandler, ErrorResponse, test patterns

### Anti-Patterns to Avoid

- **DO NOT** expose internal hostnames or IPs in error messages
- **DO NOT** include exception stack traces in API responses
- **DO NOT** intercept upstream 5xx responses (let them pass through)
- **DO NOT** use generic "Internal Server Error" for network failures (be specific: 502 vs 504)
- **DO NOT** use blocking operations for error handling
- **DO NOT** log full request/response bodies (privacy, size concerns)
- **DO NOT** hardcode timeouts (use configuration)

### Performance Considerations

- Error handling must be non-blocking (reactive)
- Timeout configuration affects latency - balance between allowing slow services and failing fast
- Error logging should be async where possible
- Consider using circuit breaker pattern in future (Story scope: Resilience4j in Phase 3)

### Web Research Context

**Spring Cloud Gateway Error Handling Best Practices (2024):**
- Use GlobalFilter or ErrorWebExceptionHandler for centralized error handling
- WebFlux exception handling differs from MVC - use reactive patterns
- Netty-specific timeouts: connect-timeout and response-timeout
- NettyRoutingFilter handles upstream communication

**Netty Timeout Configuration:**
- `connect-timeout`: Time to establish TCP connection (default: 45s)
- `response-timeout`: Time to receive first byte of response
- Both configurable via `spring.cloud.gateway.httpclient.*`

**Exception Hierarchy:**
```
Throwable
├── Exception
│   ├── ConnectException
│   │   └── ConnectTimeoutException (netty)
│   └── WebClientRequestException (wraps network errors)
└── TimeoutException
    └── ReadTimeoutException (netty)
```

### CorrelationId Placeholder

**Story 1.6 Scope:** Request Logging & Correlation IDs

For this story (1.4), the correlationId field in ErrorResponse will remain null. The actual generation and propagation of correlation IDs will be implemented in Story 1.6.

**Preparation for Story 1.6:**
- ErrorResponse already has correlationId field
- GlobalExceptionHandler can accept correlationId as parameter
- Header extraction logic will be added in Story 1.6

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- All unit tests passed: GlobalExceptionHandlerTest (12 tests, including 2 new deep chain tests)
- All integration tests passed: UpstreamErrorHandlingIntegrationTest (11 tests, including improved AC4)
- All existing tests passed: GatewayRoutingIntegrationTest (regression check)

### Completion Notes List

- Enhanced GlobalExceptionHandler to handle upstream errors (ConnectException, ConnectTimeoutException, ReadTimeoutException, TimeoutException, WebClientRequestException)
- Implemented RFC 7807 error responses for 502 Bad Gateway and 504 Gateway Timeout
- Added timeout configuration in application.yml (connect-timeout: 5s, response-timeout: 30s)
- Verified upstream 5xx passthrough behavior (default Spring Cloud Gateway behavior)
- Added logging for upstream errors (internal details only in logs, not in responses)
- correlationId placeholder ready for Story 1.6
- Security: No internal details (hostnames, stack traces, exception class names) exposed in error responses

### File List

- backend/gateway-core/src/main/kotlin/com/company/gateway/core/exception/GlobalExceptionHandler.kt (MODIFIED)
- backend/gateway-core/src/main/resources/application.yml (MODIFIED)
- backend/gateway-core/src/test/kotlin/com/company/gateway/core/exception/GlobalExceptionHandlerTest.kt (CREATED)
- backend/gateway-core/src/test/kotlin/com/company/gateway/core/integration/UpstreamErrorHandlingIntegrationTest.kt (CREATED)

### Change Log

- 2026-02-14: Implemented Story 1.4 - Error Handling for Upstream Failures (AC1-AC5)
- 2026-02-14: Code Review fixes applied:
  - H1: Added HTTP status code verification in unit tests (capturedStatusCode)
  - H2: Improved AC4 test to validate RFC 7807 structure and correlationId field existence
  - H3: Fixed getRootCause() to unwrap deeply nested exception chains
  - M1: Fixed HttpStatus casting using HttpStatus.resolve() for Spring 6 compatibility
  - M2: Unified timeout format in application.yml (both now use Duration strings: 5s, 30s)
  - M3: Added Thread.sleep(100) in insertRoute to fix flaky tests (race condition)
  - M4: Improved logging to show root cause info for wrapped exceptions
  - M5: Added tests for deeply nested exception chains
  - Added handling for ResponseStatusException with 502/504 status (Spring Cloud Gateway wraps errors)

