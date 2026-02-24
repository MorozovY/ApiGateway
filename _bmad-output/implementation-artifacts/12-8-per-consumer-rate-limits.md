# Story 12.8: Per-consumer Rate Limits

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an **Admin**,
I want to set rate limits per consumer,
So that I can control API usage independently of per-route limits (FR50, FR51, FR52, FR53).

## Feature Context

**Source:** Epic 12 — Keycloak Integration & Multi-tenant Metrics (Phase 2 PRD 2026-02-23)
**Business Value:** Позволяет Admin'ам контролировать API usage индивидуально для каждого consumer (Keycloak клиента). Это особенно важно для multi-tenant сценариев, где разные партнёры имеют разные SLA и квоты.

**Blocking Dependencies:**
- Story 12.1 (Keycloak Setup & Configuration) — DONE ✅
- Story 12.4 (Gateway Core JWT Authentication Filter) — DONE ✅
- Story 12.5 (Gateway Core Consumer Identity Filter) — DONE ✅ — определяет consumer_id
- Story 5.3 (Rate Limiting Filter Implementation) — DONE ✅ — существующий per-route rate limiting

**Blocked By This Story:**
- Story 12.9 (Consumer Management UI) — нужен backend для управления consumer rate limits
- Story 12.10 (E2E Tests) — тестирование per-consumer rate limiting

## Acceptance Criteria

### AC1: Database Migration — Consumer Rate Limits Table
**Given** migration V12__add_consumer_rate_limits.sql
**When** executed
**Then** `consumer_rate_limits` table is created:
- `id` (UUID, PK)
- `consumer_id` (VARCHAR(255), UNIQUE)
- `requests_per_second` (INTEGER, NOT NULL)
- `burst_size` (INTEGER, NOT NULL)
- `created_at` (TIMESTAMP WITH TIME ZONE)
- `updated_at` (TIMESTAMP WITH TIME ZONE)
- `created_by` (UUID, FK to users)

### AC2: Admin API — Set Consumer Rate Limit
**Given** admin user
**When** PUT `/api/v1/consumers/{consumerId}/rate-limit`
**Then** per-consumer rate limit is set (create or update)
**And** Redis key `rate_limit:consumer:{consumerId}` is used for enforcement
**And** response includes created/updated rate limit details

### AC3: HTTP 429 with Rate Limit Type Header
**Given** request from consumer with per-consumer rate limit
**When** rate limit is exceeded
**Then** HTTP 429 is returned
**And** `X-RateLimit-Type: consumer` header indicates which limit was hit
**And** response includes correlationId in RFC 7807 format

### AC4: Two-level Rate Limiting (Stricter Wins)
**Given** both per-route and per-consumer limits exist
**When** request is processed
**Then** both limits are checked
**And** stricter limit is enforced (lower of two)
**And** `X-RateLimit-Type` indicates which limit was applied (route/consumer)

### AC5: Fallback to Per-route Limit Only
**Given** consumer without specific rate limit
**When** request is processed
**Then** only per-route limit applies (if any)
**And** `X-RateLimit-Type: route` header is returned (if rate limit exists)

### AC6: Admin API — Get Consumer Rate Limit
**Given** admin user
**When** GET `/api/v1/consumers/{consumerId}/rate-limit`
**Then** response includes consumer rate limit details
**Or** HTTP 404 if no rate limit exists for consumer

### AC7: Admin API — Delete Consumer Rate Limit
**Given** admin user
**When** DELETE `/api/v1/consumers/{consumerId}/rate-limit`
**Then** per-consumer rate limit is removed
**And** consumer falls back to per-route limits only

### AC8: Admin API — List All Consumer Rate Limits
**Given** admin user
**When** GET `/api/v1/consumer-rate-limits`
**Then** response includes paginated list of all consumer rate limits
**And** supports filtering by consumerId prefix

## Tasks / Subtasks

- [x] Task 0: Pre-flight Checklist (PA-09)
  - [x] 0.1 Проверить что gateway-admin запускается: `./gradlew :gateway-admin:bootRun`
  - [x] 0.2 Проверить что gateway-core запускается: `./gradlew :gateway-core:bootRun`
  - [x] 0.3 Проверить что ConsumerIdentityFilter работает (consumer_id в exchange attributes)
  - [x] 0.4 Проверить что существующий rate limiting работает: `./gradlew :gateway-core:test --tests "*RateLimitFilter*"`
  - [x] 0.5 Проверить что все тесты проходят: `./gradlew test`

- [x] Task 1: Database Migration — Consumer Rate Limits Table (AC: #1)
  - [x] 1.1 Создать V12__add_consumer_rate_limits.sql в gateway-admin
  - [x] 1.2 Таблица consumer_rate_limits с полями: id, consumer_id, requests_per_second, burst_size, created_at, updated_at, created_by
  - [x] 1.3 UNIQUE constraint на consumer_id
  - [x] 1.4 FK constraint на created_by → users(id)
  - [x] 1.5 Index на consumer_id для быстрого lookup

- [x] Task 2: Entity & Repository — ConsumerRateLimit (AC: #1)
  - [x] 2.1 Создать ConsumerRateLimit entity в gateway-common/model
  - [x] 2.2 Создать ConsumerRateLimitRepository (R2DBC) в gateway-admin
  - [x] 2.3 Методы: findByConsumerId, findAll с пагинацией, save, deleteByConsumerId

- [x] Task 3: DTOs — Consumer Rate Limit Request/Response (AC: #2, #6, #8)
  - [x] 3.1 Создать ConsumerRateLimitRequest DTO (requestsPerSecond, burstSize)
  - [x] 3.2 Создать ConsumerRateLimitResponse DTO (id, consumerId, requestsPerSecond, burstSize, createdAt, updatedAt, createdBy)
  - [x] 3.3 PagedResponse используется для пагинации (существующий DTO)

- [x] Task 4: Service — ConsumerRateLimitService (AC: #2, #6, #7, #8)
  - [x] 4.1 Создать ConsumerRateLimitService в gateway-admin
  - [x] 4.2 Метод setRateLimit(consumerId, request) — create or update
  - [x] 4.3 Метод getRateLimit(consumerId) — get or throw NotFoundException
  - [x] 4.4 Метод deleteRateLimit(consumerId) — delete
  - [x] 4.5 Метод listRateLimits(offset, limit, filter) — paginated list

- [x] Task 5: Controller — ConsumerRateLimitController (AC: #2, #6, #7, #8)
  - [x] 5.1 Создать ConsumerRateLimitController в gateway-admin
  - [x] 5.2 PUT `/api/v1/consumers/{consumerId}/rate-limit` — set rate limit
  - [x] 5.3 GET `/api/v1/consumers/{consumerId}/rate-limit` — get rate limit
  - [x] 5.4 DELETE `/api/v1/consumers/{consumerId}/rate-limit` — delete rate limit
  - [x] 5.5 GET `/api/v1/consumer-rate-limits` — list all rate limits
  - [x] 5.6 @RequireRole(Role.ADMIN) на все endpoints

- [x] Task 6: Gateway Core — ConsumerRateLimitRepository & CacheManager (AC: #3, #4, #5)
  - [x] 6.1 Создать ConsumerRateLimitRepository в gateway-core
  - [x] 6.2 Создать ConsumerRateLimitCacheManager с Caffeine кэшированием
  - [x] 6.3 Метод getConsumerRateLimit(consumerId) для lookup в filter
  - [x] 6.4 Интеграция с RouteRefreshService для Redis pub/sub invalidation

- [x] Task 7: Extend RateLimitFilter — Two-level Rate Limiting (AC: #3, #4, #5)
  - [x] 7.1 Получить consumer_id из JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE
  - [x] 7.2 Lookup per-consumer rate limit из ConsumerRateLimitCacheManager
  - [x] 7.3 Проверить оба лимита (per-route и per-consumer)
  - [x] 7.4 Применить более строгий лимит (меньшее значение)
  - [x] 7.5 Добавить заголовок X-RateLimit-Type: route | consumer

- [x] Task 8: Extend RateLimitService — Consumer Rate Limit (AC: #3, #4, #5)
  - [x] 8.1 Добавить метод checkConsumerRateLimit(consumerId, rateLimit)
  - [x] 8.2 Redis ключ: ratelimit:consumer:{consumerId}
  - [x] 8.3 Добавить метод checkBothLimits(routeId, clientKey, consumerId, routeLimit, consumerLimit)
  - [x] 8.4 Создать RateLimitCheckResult с limitType (route/consumer)

- [x] Task 9: Unit Tests — Backend (AC: #1-8)
  - [x] 9.1 ConsumerRateLimitServiceTest — CRUD operations (10 тестов)
  - [x] 9.2 RateLimitFilterTest — two-level rate limiting (4 новых теста)
  - [x] 9.3 RateLimitServiceTest — consumer rate limit check (6 новых тестов)
  - [x] 9.4 RouteRefreshServiceTest — consumer rate limit subscription (2 новых теста)

- [x] Task 10: Integration Tests — Backend (AC: #3, #4, #5)
  - [x] 10.1 Тест: per-consumer rate limit enforcement
  - [x] 10.2 Тест: stricter limit wins (per-route vs per-consumer)
  - [x] 10.3 Тест: X-RateLimit-Type header
  - [x] 10.4 Тест: fallback to per-route only when no consumer limit

- [ ] Task 11: Documentation (AC: #2, #6, #7, #8)
  - [ ] 11.1 KDoc комментарии для всех public methods
  - [ ] 11.2 Swagger annotations для API endpoints
  - [ ] 11.3 Обновить architecture.md если нужно

- [ ] Task 12: Manual Verification (User)
  - [ ] 12.1 Создать consumer rate limit через PUT API
  - [ ] 12.2 Проверить GET возвращает созданный limit
  - [ ] 12.3 Отправить запросы и проверить X-RateLimit-Type header
  - [ ] 12.4 Превысить consumer limit — получить 429 с X-RateLimit-Type: consumer
  - [ ] 12.5 Удалить consumer limit — проверить fallback на route limit

## API Dependencies Checklist

**Backend API endpoints, создаваемые в этой story:**

| Endpoint | Method | Параметры | Статус |
|----------|--------|-----------|--------|
| `/api/v1/consumers/{consumerId}/rate-limit` | PUT | `requestsPerSecond`, `burstSize` | ❌ Требуется |
| `/api/v1/consumers/{consumerId}/rate-limit` | GET | — | ❌ Требуется |
| `/api/v1/consumers/{consumerId}/rate-limit` | DELETE | — | ❌ Требуется |
| `/api/v1/consumer-rate-limits` | GET | `page`, `size`, `filter` | ❌ Требуется |

**Зависимости от существующих компонентов:**

- [x] ConsumerIdentityFilter (Story 12.5) — определяет consumer_id
- [x] JwtAuthenticationFilter (Story 12.4) — извлекает azp claim
- [x] RateLimitFilter (Story 5.3) — существующий per-route rate limiting
- [x] RateLimitService (Story 5.3) — token bucket через Redis

## Dev Notes

### Database Schema

**Migration V12__add_consumer_rate_limits.sql:**

```sql
-- Таблица для per-consumer rate limits
CREATE TABLE consumer_rate_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consumer_id VARCHAR(255) NOT NULL,
    requests_per_second INTEGER NOT NULL,
    burst_size INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by UUID REFERENCES users(id),

    CONSTRAINT uq_consumer_rate_limits_consumer UNIQUE (consumer_id),
    CONSTRAINT chk_consumer_rate_limits_rps CHECK (requests_per_second > 0),
    CONSTRAINT chk_consumer_rate_limits_burst CHECK (burst_size > 0)
);

COMMENT ON TABLE consumer_rate_limits IS 'Per-consumer rate limiting policies';

CREATE INDEX idx_consumer_rate_limits_consumer_id ON consumer_rate_limits(consumer_id);
```

### ConsumerRateLimit Entity

**gateway-common/src/main/kotlin/com/company/gateway/common/model/ConsumerRateLimit.kt:**

```kotlin
package com.company.gateway.common.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Per-consumer rate limit policy.
 * Ограничивает количество запросов для конкретного consumer (Keycloak клиента).
 */
@Table("consumer_rate_limits")
data class ConsumerRateLimit(
    @Id
    val id: UUID? = null,

    /** Consumer ID (Keycloak client_id / azp claim) */
    @Column("consumer_id")
    val consumerId: String,

    /** Лимит запросов в секунду */
    @Column("requests_per_second")
    val requestsPerSecond: Int,

    /** Максимальный burst (пик запросов) */
    @Column("burst_size")
    val burstSize: Int,

    /** Время создания */
    @Column("created_at")
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    /** Время последнего обновления */
    @Column("updated_at")
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),

    /** ID пользователя, создавшего лимит */
    @Column("created_by")
    val createdBy: UUID? = null
)
```

### Redis Key Structure

```
# Per-route rate limit (существующий)
rate_limit:{routeId}:{clientIp} → token bucket state

# Per-consumer rate limit (новый)
rate_limit:consumer:{consumerId} → token bucket state
```

**Важно:** Per-consumer rate limit НЕ привязан к конкретному маршруту. Один consumer имеет глобальный лимит на ВСЕ маршруты.

### RateLimitFilter Extension

```kotlin
override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
    val routeId = exchange.getAttribute<UUID>(ROUTE_ID_ATTRIBUTE)
    val routeLimit = exchange.getAttribute<RateLimit>(RATE_LIMIT_ATTRIBUTE)
    val consumerId = exchange.getAttribute<String>(JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE)
        ?: ConsumerIdentityFilter.ANONYMOUS

    // Получаем per-consumer rate limit
    return consumerRateLimitRepository.findByConsumerId(consumerId)
        .flatMap { consumerLimit ->
            // Проверяем оба лимита и применяем более строгий
            checkBothLimits(routeId, extractClientKey(exchange), consumerId, routeLimit, consumerLimit)
        }
        .switchIfEmpty {
            // Нет consumer лимита — проверяем только route лимит
            if (routeLimit != null && routeId != null) {
                rateLimitService.checkRateLimit(routeId, extractClientKey(exchange), routeLimit)
                    .map { result -> RateLimitCheckResult(result, "route") }
            } else {
                Mono.just(RateLimitCheckResult(RateLimitResult(allowed = true, ...), null))
            }
        }
        .flatMap { checkResult ->
            if (checkResult.result.allowed) {
                addRateLimitHeaders(exchange, checkResult)
                chain.filter(exchange)
            } else {
                rejectRequest(exchange, checkResult)
            }
        }
}
```

### X-RateLimit-Type Header

Добавить заголовок `X-RateLimit-Type` в ответы:
- `route` — применён per-route лимит
- `consumer` — применён per-consumer лимит

Это помогает клиентам понять, какой лимит сработал, и соответствующим образом адаптироваться.

### Two-level Rate Limiting Logic

```kotlin
/**
 * Проверяет оба лимита и применяет более строгий.
 *
 * Алгоритм:
 * 1. Проверить per-route лимит
 * 2. Проверить per-consumer лимит
 * 3. Если хотя бы один не разрешён — отказать
 * 4. Если оба разрешены — вернуть данные от более строгого (меньше remaining)
 */
fun checkBothLimits(
    routeId: UUID?,
    clientKey: String,
    consumerId: String,
    routeLimit: RateLimit?,
    consumerLimit: ConsumerRateLimit
): Mono<RateLimitCheckResult> {
    val routeCheck = if (routeId != null && routeLimit != null) {
        checkRateLimit(routeId, clientKey, routeLimit)
    } else {
        Mono.just(RateLimitResult(allowed = true, remaining = Int.MAX_VALUE, resetTime = 0))
    }

    val consumerCheck = checkConsumerRateLimit(consumerId, consumerLimit)

    return Mono.zip(routeCheck, consumerCheck)
        .map { (routeResult, consumerResult) ->
            when {
                !routeResult.allowed -> RateLimitCheckResult(routeResult, "route")
                !consumerResult.allowed -> RateLimitCheckResult(consumerResult, "consumer")
                // Оба разрешены — возвращаем с меньшим remaining
                routeResult.remaining <= consumerResult.remaining ->
                    RateLimitCheckResult(routeResult, "route")
                else ->
                    RateLimitCheckResult(consumerResult, "consumer")
            }
        }
}
```

### API Endpoints

**PUT /api/v1/consumers/{consumerId}/rate-limit**

Request:
```json
{
  "requestsPerSecond": 100,
  "burstSize": 150
}
```

Response (201 Created / 200 OK):
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "consumerId": "company-a",
  "requestsPerSecond": 100,
  "burstSize": 150,
  "createdAt": "2026-02-24T10:00:00Z",
  "updatedAt": "2026-02-24T10:00:00Z",
  "createdBy": {
    "id": "...",
    "username": "admin"
  }
}
```

**GET /api/v1/consumer-rate-limits**

Response:
```json
{
  "content": [
    {
      "id": "...",
      "consumerId": "company-a",
      "requestsPerSecond": 100,
      "burstSize": 150,
      "createdAt": "...",
      "updatedAt": "..."
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "page": 0,
  "size": 20
}
```

### Caching Strategy

**Gateway Core:**
- Caffeine cache для ConsumerRateLimit lookup
- TTL: 60 секунд (как для routes)
- При изменении через Admin API — invalidate через Redis pub/sub

**Gateway Admin:**
- После create/update/delete — publish event на Redis channel `consumer-rate-limits:invalidate`

### File Structure

```
backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/
├── dto/
│   ├── ConsumerRateLimitRequest.kt      # СОЗДАТЬ
│   └── ConsumerRateLimitResponse.kt     # СОЗДАТЬ
├── controller/
│   └── ConsumerRateLimitController.kt   # СОЗДАТЬ
├── service/
│   └── ConsumerRateLimitService.kt      # СОЗДАТЬ
├── repository/
│   └── ConsumerRateLimitRepository.kt   # СОЗДАТЬ
└── resources/db/migration/
    └── V12__add_consumer_rate_limits.sql # СОЗДАТЬ

backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/
├── controller/
│   └── ConsumerRateLimitControllerTest.kt  # СОЗДАТЬ
├── service/
│   └── ConsumerRateLimitServiceTest.kt     # СОЗДАТЬ

backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/
└── ConsumerRateLimit.kt                 # СОЗДАТЬ

backend/gateway-core/src/main/kotlin/com/company/gateway/core/
├── filter/
│   └── RateLimitFilter.kt               # МОДИФИЦИРОВАТЬ — two-level rate limiting
├── ratelimit/
│   ├── RateLimitService.kt              # МОДИФИЦИРОВАТЬ — consumer rate limit
│   └── RateLimitCheckResult.kt          # СОЗДАТЬ — result с limitType
├── repository/
│   └── ConsumerRateLimitRepository.kt   # СОЗДАТЬ
└── cache/
    └── ConsumerRateLimitCacheManager.kt # СОЗДАТЬ (опционально)

backend/gateway-core/src/test/kotlin/com/company/gateway/core/
├── filter/
│   └── RateLimitFilterTest.kt           # МОДИФИЦИРОВАТЬ — тесты two-level
├── ratelimit/
│   └── RateLimitServiceTest.kt          # МОДИФИЦИРОВАТЬ — тесты consumer limit
└── integration/
    └── ConsumerRateLimitIntegrationTest.kt # СОЗДАТЬ
```

### Testing Strategy

1. **Unit Tests (gateway-admin):**
   - ConsumerRateLimitServiceTest — CRUD operations
   - ConsumerRateLimitControllerTest — API endpoints, validation

2. **Unit Tests (gateway-core):**
   - RateLimitFilterTest — two-level rate limiting logic
   - RateLimitServiceTest — consumer rate limit check

3. **Integration Tests:**
   - Full flow: set consumer limit → send requests → verify 429 with X-RateLimit-Type
   - Stricter limit wins scenario
   - Fallback to route-only when no consumer limit

4. **Manual Testing:**
   - PUT consumer rate limit
   - Exceed limit → verify 429 + header
   - DELETE limit → verify fallback

### Critical Constraints

1. **Per-consumer limit is GLOBAL** — не привязан к конкретному маршруту
2. **Stricter limit wins** — применяется меньший из двух лимитов
3. **Anonymous consumers** — могут не иметь per-consumer лимита, только per-route
4. **Cache invalidation** — при изменении через Admin API нужно инвалидировать кэш в gateway-core
5. **Redis key TTL** — consumer rate limit keys должны иметь TTL для автоочистки

### Previous Story Intelligence

Из Story 12.5-12.7:
- ConsumerIdentityFilter определяет consumer_id из JWT azp или X-Consumer-ID header
- consumer_id доступен через exchange.getAttribute(JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE)
- "anonymous" используется для неидентифицированных запросов

Из Story 5.3:
- RateLimitFilter использует token bucket алгоритм через Redis Lua script
- Graceful degradation с локальным Caffeine fallback
- X-RateLimit-* headers уже реализованы

### References

- [Source: architecture.md#Per-consumer Rate Limiting]
- [Source: architecture.md#Admin API for Consumer Rate Limits]
- [Source: epics.md#Story 12.8]
- [Source: prd.md#FR50-FR53]
- [Source: 5-3-rate-limiting-filter-implementation.md] — существующий rate limiting
- [Source: 12-5-gateway-core-consumer-identity-filter.md] — ConsumerIdentityFilter
- [Source: backend/gateway-core/src/main/kotlin/com/company/gateway/core/filter/RateLimitFilter.kt]
- [Source: backend/gateway-core/src/main/kotlin/com/company/gateway/core/filter/ConsumerIdentityFilter.kt]
- [Source: backend/gateway-core/src/main/kotlin/com/company/gateway/core/ratelimit/RateLimitService.kt]

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

1. **BUG FIX: Caffeine Cache NPE** — Caffeine cache не поддерживает null values, несмотря на nullable тип в объявлении. Изменён тип кэша на `Cache<String, Optional<ConsumerRateLimit>>` в CacheConfig.kt и соответствующая логика в ConsumerRateLimitCacheManager.kt.

2. **BUG FIX: Mono.defer для switchIfEmpty** — В RateLimitFilter исправлена проблема с преждевременной оценкой кода внутри switchIfEmpty. Код внутри switchIfEmpty выполняется во время сборки reactive chain, а не во время subscription. Исправлено оборачиванием в `Mono.defer { ... }`.

3. **Pattern: Boolean marker для Mono<Void>** — Для switchIfEmpty с Mono<Void> используется паттерн с boolean маркером (`.then(Mono.just(true))` + `.defaultIfEmpty(false)`), так как Mono.empty() триггерит switchIfEmpty даже при успешном завершении.

4. **Tests Coverage:**
   - ConsumerRateLimitServiceTest: 10 unit тестов (CRUD operations)
   - RateLimitFilterTest: 4 новых теста (two-level rate limiting)
   - RateLimitServiceTest: 6 новых тестов (consumer rate limit check)
   - RouteRefreshServiceTest: 2 новых теста (consumer rate limit subscription)
   - RateLimitIntegrationTest: 4 новых интеграционных теста для Story 12.8 (AC3-AC5)

5. **CODE REVIEW FIXES (2026-02-24):**
   - CRITICAL-1: Добавлены интеграционные тесты Task 10 в RateLimitIntegrationTest.kt
   - CRITICAL-2: Переименована миграция V11 → V12 (соответствие AC1)
   - MEDIUM-2: Удалён избыточный inMemoryCache в ConsumerRateLimitCacheManager (Caffeine достаточно)

### File List

**New Files:**
- `backend/gateway-admin/src/main/resources/db/migration/V12__add_consumer_rate_limits.sql`
- `backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/ConsumerRateLimit.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/ConsumerRateLimitRequest.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/ConsumerRateLimitResponse.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/ConsumerRateLimitRepository.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/ConsumerRateLimitService.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/ConsumerRateLimitController.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/publisher/ConsumerRateLimitEventPublisher.kt`
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/repository/ConsumerRateLimitRepository.kt`
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/cache/ConsumerRateLimitCacheManager.kt`
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/ratelimit/RateLimitCheckResult.kt`
- `backend/gateway-core/src/test/resources/db/migration/V12__add_consumer_rate_limits.sql`

**Modified Files:**
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/filter/RateLimitFilter.kt`
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/ratelimit/RateLimitService.kt`
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/route/RouteRefreshService.kt`
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/cache/CacheConfig.kt`
- `backend/gateway-core/src/test/kotlin/com/company/gateway/core/filter/RateLimitFilterTest.kt`
- `backend/gateway-core/src/test/kotlin/com/company/gateway/core/ratelimit/RateLimitServiceTest.kt`
- `backend/gateway-core/src/test/kotlin/com/company/gateway/core/route/RouteRefreshServiceTest.kt`
- `backend/gateway-core/src/test/kotlin/com/company/gateway/core/integration/RateLimitIntegrationTest.kt` — добавлены 4 теста для Story 12.8

**Test Files:**
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/service/ConsumerRateLimitServiceTest.kt`

**Note:** Изменения в `RouteService.kt`, `RouteControllerIntegrationTest.kt`, `RouteDetailsCloneIntegrationTest.kt` относятся к Story 12.7 (hotfix для clone и sanitizeAllowedConsumers) и должны быть закоммичены отдельно.
