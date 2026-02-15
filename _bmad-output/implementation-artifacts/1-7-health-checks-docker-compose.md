# Story 1.7: Health Checks & Docker Compose

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **DevOps Engineer**,
I want health check endpoints and a complete local development environment,
So that I can verify system health and develop locally (NFR20).

## Acceptance Criteria

1. **AC1: Health Endpoint Returns UP Status**
   **Given** gateway-core and gateway-admin are running
   **When** a request is made to `/actuator/health`
   **Then** the response returns HTTP 200 with status "UP"
   **And** includes health of: database, redis, diskSpace

2. **AC2: Readiness Probe Returns DOWN During Startup**
   **Given** gateway-core is starting but database is not ready
   **When** a request is made to `/actuator/health/readiness`
   **Then** the response returns HTTP 503 with status "DOWN"

3. **AC3: Liveness Probe Returns UP When Application Running**
   **Given** gateway-core is running
   **When** a request is made to `/actuator/health/liveness`
   **Then** the response returns HTTP 200 with status "UP"

4. **AC4: Docker Compose Starts All Services**
   **Given** Docker Compose file exists
   **When** `docker-compose up -d` is executed
   **Then** the following services start:
   - PostgreSQL 16 on port 5432
   - Redis 7 on port 6379
   **And** services have health checks configured
   **And** gateway applications can connect to both services

5. **AC5: Health Details Include Component Status**
   **Given** gateway-core is running with all dependencies healthy
   **When** a request is made to `/actuator/health`
   **Then** response includes detailed component statuses:
   ```json
   {
     "status": "UP",
     "components": {
       "db": { "status": "UP", "details": { ... } },
       "redis": { "status": "UP", "details": { ... } },
       "diskSpace": { "status": "UP", "details": { ... } }
     }
   }
   ```

6. **AC6: Health Endpoints Accessible Without Authentication**
   **Given** health endpoints are configured
   **When** a request is made to `/actuator/health` without authentication
   **Then** the request is allowed (public endpoint)
   **And** detailed health info may be restricted based on configuration

## Tasks / Subtasks

- [x] **Task 1: Configure Health Actuator in gateway-core** (AC: #1, #2, #3, #5, #6)
  - [x] Subtask 1.1: Update `application.yml` to enable health endpoint with details
  - [x] Subtask 1.2: Configure readiness and liveness probes
  - [x] Subtask 1.3: Add `management.health.redis.enabled=true` for Redis health indicator
  - [x] Subtask 1.4: Configure `management.endpoints.web.exposure.include=health,info,prometheus`
  - [x] Subtask 1.5: Set `management.endpoint.health.show-details=when_authorized` (or `always` for dev)

- [x] **Task 2: Configure Health Actuator in gateway-admin** (AC: #1, #2, #3, #5, #6)
  - [x] Subtask 2.1: Update `application.yml` to enable health endpoint with details
  - [x] Subtask 2.2: Configure readiness and liveness probes
  - [x] Subtask 2.3: Ensure database health indicator is enabled
  - [x] Subtask 2.4: Configure `management.endpoints.web.exposure.include=health,info,prometheus`

- [x] **Task 3: Add Redis Health Indicator** (AC: #1, #5)
  - [x] Subtask 3.1: Verify `spring-boot-starter-data-redis-reactive` includes health indicator
  - [x] Subtask 3.2: Create custom Redis health indicator if needed (ReactiveHealthIndicator) - N/A, auto-configured
  - [x] Subtask 3.3: Test Redis health status when Redis is available
  - [x] Subtask 3.4: Test Redis health status when Redis is unavailable (DOWN)

- [x] **Task 4: Create SecurityConfig for Health Endpoints** (AC: #6)
  - [x] Subtask 4.1: Create/update `SecurityConfig.kt` in gateway-core to permit `/actuator/health/**`
  - [x] Subtask 4.2: Create/update `SecurityConfig.kt` in gateway-admin to permit `/actuator/health/**`
  - [x] Subtask 4.3: Ensure `/actuator/prometheus` remains accessible for scraping
  - [x] Subtask 4.4: Other actuator endpoints should require authentication

- [x] **Task 5: Update Docker Compose for Health Checks** (AC: #4)
  - [x] Subtask 5.1: Verify existing PostgreSQL health check is correct
  - [x] Subtask 5.2: Verify existing Redis health check is correct
  - [x] Subtask 5.3: Add `depends_on` with health conditions for gateway services (when added) - N/A, no gateway services in docker-compose yet
  - [x] Subtask 5.4: Document health check configuration in comments

- [x] **Task 6: Unit Tests for Health Configuration** (AC: #1-#6)
  - [x] Subtask 6.1: Test health endpoint returns 200 when all components healthy
  - [x] Subtask 6.2: Test health endpoint structure includes r2dbc, redis, diskSpace
  - [x] Subtask 6.3: Test readiness probe returns 503 when Redis unavailable
  - [x] Subtask 6.4: Test liveness probe returns 200 regardless of dependency state

- [x] **Task 7: Integration Tests** (AC: #1-#5)
  - [x] Subtask 7.1: Test `/actuator/health` with Testcontainers (PostgreSQL + Redis running)
  - [x] Subtask 7.2: Test `/actuator/health/readiness` during startup sequence
  - [x] Subtask 7.3: Test `/actuator/health/liveness` independent of dependencies
  - [x] Subtask 7.4: Test health endpoint without authentication
  - [x] Subtask 7.5: Test component details in health response

## Dev Notes

### Previous Story Intelligence (Story 1.6 - Request Logging & Correlation IDs)

**Files Created in Story 1.6:**
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/filter/CorrelationIdFilter.kt`
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/filter/LoggingFilter.kt`
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/config/MdcContextConfig.kt`
- `backend/gateway-core/src/main/resources/logback-spring.xml`

**Key Patterns from Story 1.6:**
- `@Configuration` classes in `config/` directory
- Test infrastructure: `@SpringBootTest` + `@AutoConfigureWebTestClient` + Testcontainers (PostgreSQL, Redis)
- All 85 tests passing after Story 1.6
- Reactive patterns with Reactor Context

**From Stories 1.1-1.5 (completed):**
- `CacheConfig.kt`, `RouteCacheManager.kt`, `RouteRefreshService.kt` exist in gateway-core
- Redis connection is already configured in `application.yml`
- PostgreSQL R2DBC connection is configured
- Docker Compose has PostgreSQL and Redis with health checks

**Git Commit Reference (Story 1.6):**
```
efad8c4 feat: Request logging with correlation IDs (Story 1.6)
- CorrelationIdFilter, LoggingFilter, MdcContextConfig created
- JSON structured logging configured
- 85 tests passing
```

### Architecture Compliance

**Health Check Architecture (from architecture.md):**
- Spring Actuator for `/health`, `/ready` endpoints
- Prometheus metrics endpoint `/actuator/prometheus`
- Health checks include: database, redis, diskSpace

**NFR20 Compliance:** Liveness and Readiness endpoints
**NFR18:** Prometheus-compatible endpoint (already configured in previous stories)

**Actuator Endpoints:**
| Endpoint | Purpose | Access |
|----------|---------|--------|
| `/actuator/health` | Overall health status | Public |
| `/actuator/health/liveness` | Kubernetes liveness probe | Public |
| `/actuator/health/readiness` | Kubernetes readiness probe | Public |
| `/actuator/prometheus` | Metrics scraping | Public (for Prometheus) |
| `/actuator/info` | Application info | Public |

### Technical Requirements

**Configuration Updates (application.yml):**

```yaml
# gateway-core/src/main/resources/application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      show-details: when_authorized  # or 'always' for dev
      probes:
        enabled: true  # Enables /health/liveness and /health/readiness
      group:
        readiness:
          include: db,redis
        liveness:
          include: ping
  health:
    redis:
      enabled: true
    db:
      enabled: true
    diskspace:
      enabled: true
```

**Spring Security Configuration for Health Endpoints:**

```kotlin
@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers("/actuator/health/**").permitAll()
                    .pathMatchers("/actuator/info").permitAll()
                    .pathMatchers("/actuator/prometheus").permitAll()
                    .anyExchange().authenticated()
            }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .csrf { it.disable() }
            .build()
    }
}
```

**Health Response Format (when healthy):**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 500107862016,
        "free": 250053931008,
        "threshold": 10485760,
        "path": "...",
        "exists": true
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.2.4"
      }
    }
  }
}
```

### Files to Modify

```
backend/gateway-core/
├── src/main/kotlin/com/company/gateway/core/
│   └── config/
│       └── SecurityConfig.kt          # CREATE - permit health endpoints
└── src/main/resources/
    └── application.yml                  # MODIFY - add actuator configuration

backend/gateway-admin/
├── src/main/kotlin/com/company/gateway/admin/
│   └── config/
│       └── SecurityConfig.kt          # CREATE/MODIFY - permit health endpoints
└── src/main/resources/
    └── application.yml                  # MODIFY - add actuator configuration
```

**Files to Create (Tests):**

```
backend/gateway-core/src/test/kotlin/com/company/gateway/core/
├── actuator/
│   └── HealthEndpointTest.kt           # Unit tests for health configuration
└── integration/
    └── HealthEndpointIntegrationTest.kt # Integration tests with Testcontainers
```

### Existing Docker Compose Analysis

**Current docker-compose.yml (already exists):**
- PostgreSQL 16 with health check: `pg_isready -U gateway`
- Redis 7 with health check: `redis-cli ping`
- Health checks configured: interval 10s, timeout 5s, retries 5
- Network: gateway-network (bridge)

**No changes needed to docker-compose.yml** - health checks are already properly configured from Story 1.1.

### Anti-Patterns to Avoid

- DO NOT expose sensitive actuator endpoints without authentication
- DO NOT include `/actuator/**` wildcard - be explicit about allowed endpoints
- DO NOT forget to configure readiness group to include critical dependencies
- DO NOT use blocking calls in custom health indicators - use ReactiveHealthIndicator
- DO NOT disable health indicators for required components

### Testing Strategy

**Unit Test Pattern (`HealthEndpointTest.kt`):**
```kotlin
@WebFluxTest
@AutoConfigureWebTestClient
class HealthEndpointTest {

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `health endpoint returns 200 when accessible`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }
}
```

**Integration Test Pattern (`HealthEndpointIntegrationTest.kt`):**
```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class HealthEndpointIntegrationTest {

    @Container
    val postgres = PostgreSQLContainer("postgres:16")

    @Container
    val redis = GenericContainer("redis:7").withExposedPorts(6379)

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `health endpoint includes all component statuses`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
            .jsonPath("$.components.db.status").isEqualTo("UP")
            .jsonPath("$.components.redis.status").isEqualTo("UP")
            .jsonPath("$.components.diskSpace.status").isEqualTo("UP")
    }

    @Test
    fun `readiness probe returns UP when dependencies ready`() {
        webTestClient.get()
            .uri("/actuator/health/readiness")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }

    @Test
    fun `liveness probe returns UP`() {
        webTestClient.get()
            .uri("/actuator/health/liveness")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
    }

    @Test
    fun `health endpoint accessible without authentication`() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk  // Not 401 or 403
    }
}
```

### Project Structure Notes

**Alignment with Architecture:**
- `config/` directory exists in gateway-core (MdcContextConfig.kt added in Story 1.6)
- SecurityConfig.kt will be created in `config/` directory
- Test structure follows `src/test/kotlin/.../integration/` pattern
- Package: `com.company.gateway.core` (consistent with existing files)

**Existing Configuration Files:**
- `backend/gateway-core/src/main/resources/application.yml` - exists, needs actuator config
- `backend/gateway-admin/src/main/resources/application.yml` - exists, needs actuator config

### References

- [Source: epics.md#Story 1.7: Health Checks & Docker Compose] - Original acceptance criteria, NFR20
- [Source: architecture.md#Infrastructure & Deployment] - Health Checks: Spring Actuator
- [Source: architecture.md#Core Architectural Decisions] - Observability: Prometheus metrics, health checks
- [Source: prd.md#NonFunctional Requirements] - NFR20: Liveness и Readiness endpoints
- [Source: 1-6-request-logging-correlation-ids.md] - Test infrastructure, config patterns
- [Source: 1-5-configuration-hot-reload.md] - Redis and cache configuration patterns

### Web Research Context

**Spring Boot 3.4.x Actuator Health (2025-2026):**
- Health probes (liveness/readiness) enabled via `management.endpoint.health.probes.enabled=true`
- Kubernetes probe groups: `management.health.probes.group.readiness.include=db,redis`
- Show-details options: `never`, `when_authorized`, `always`
- Spring Boot 3.4.x auto-configures health indicators for Redis Reactive

**Reactive Health Indicators:**
- `ReactiveRedisHealthIndicator` auto-configured with `spring-boot-starter-data-redis-reactive`
- R2DBC health indicator auto-configured with `spring-boot-starter-data-r2dbc`
- Both return `Mono<Health>` for non-blocking checks

**Spring Security WebFlux Configuration:**
- Use `@EnableWebFluxSecurity` for reactive applications
- `ServerHttpSecurity` instead of `HttpSecurity`
- `SecurityWebFilterChain` bean for filter chain configuration

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Health indicator name is `r2dbc` for R2DBC PostgreSQL (not `db` as mentioned in AC1/AC5 - this is a naming clarification)
- Redis health indicator auto-configured by spring-boot-starter-data-redis-reactive
- Prometheus endpoint requires `io.micrometer:micrometer-registry-prometheus` dependency
- Test profiles need explicit management.endpoint.health.group.readiness.include when Redis is disabled
- AC2 readiness behavior verified: returns 503 DOWN when any dependency (r2dbc or redis) is unavailable

### Completion Notes List

- Configured Spring Boot Actuator health endpoints for gateway-core and gateway-admin
- Implemented liveness/readiness probes with Kubernetes-compatible groups
- Created SecurityConfig for gateway-core with profile-based security (dev/test permit all, prod requires auth)
- Updated gateway-admin SecurityConfig to include prometheus and health/** patterns
- Verified Docker Compose health checks are already properly configured
- Added documentation comments to docker-compose.yml
- Created comprehensive integration tests with Testcontainers
- Created unit tests for Redis unavailability scenarios
- All tests passing (full regression suite)

### File List

**Created:**
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/config/SecurityConfig.kt`
- `backend/gateway-core/src/test/kotlin/com/company/gateway/core/integration/HealthEndpointIntegrationTest.kt`
- `backend/gateway-core/src/test/kotlin/com/company/gateway/core/actuator/HealthEndpointTest.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/HealthEndpointIntegrationTest.kt`
- `backend/gateway-admin/src/test/resources/application-test.yml`

**Modified:**
- `backend/gateway-core/src/main/resources/application.yml` - added actuator health configuration
- `backend/gateway-core/build.gradle.kts` - added spring-security dependency
- `backend/gateway-admin/src/main/resources/application.yml` - added actuator health configuration
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/config/SecurityConfig.kt` - added prometheus endpoint, health/** pattern, test profile
- `backend/gateway-admin/build.gradle.kts` - added micrometer-prometheus, redis testcontainers
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/repository/RouteRepositoryTest.kt` - added Redis health disable for tests
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/DatabaseIntegrationTest.kt` - added Redis health disable for tests
- `docker-compose.yml` - added documentation comments

## Change Log

| Date | Change | Author |
|------|--------|--------|
| 2026-02-15 | Implemented health endpoints (AC1-AC6) for gateway-core and gateway-admin | Claude Opus 4.5 |
| 2026-02-15 | Added SecurityConfig with profile-based security for actuator endpoints | Claude Opus 4.5 |
| 2026-02-15 | Created integration and unit tests for health endpoints | Claude Opus 4.5 |
| 2026-02-15 | Documented Docker Compose health check configuration | Claude Opus 4.5 |
| 2026-02-15 | **Code Review Fixes:** Fixed SecurityConfig consistency (M1), enhanced application-test.yml (M2), added health details assertions to tests (H2) | Claude Opus 4.5 |
