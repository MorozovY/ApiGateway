# Story 12.5: Gateway Core — Consumer Identity Filter

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **System**,
I want to identify the consumer for every request,
So that metrics and rate limits can be applied per-consumer (FR42, FR43, FR44, FR45).

## Feature Context

**Source:** Epic 12 — Keycloak Integration & Multi-tenant Metrics (Phase 2 PRD 2026-02-23)
**Business Value:** Consumer Identity обеспечивает идентификацию каждого запроса по consumer_id — ключевое требование для multi-tenant метрик (12.6), per-consumer rate limits (12.8) и аналитики использования API по компаниям.

**Blocking Dependencies:**
- Story 12.1 (Keycloak Setup & Configuration) — DONE ✅
- Story 12.2 (Admin UI Keycloak Auth Migration) — DONE ✅
- Story 12.3 (Gateway Admin Keycloak JWT Validation) — DONE ✅
- Story 12.4 (Gateway Core JWT Authentication Filter) — DONE ✅

**Blocked By This Story:**
- Story 12.6 (Multi-tenant Metrics) — использует consumer_id в MetricsFilter
- Story 12.8 (Per-consumer Rate Limits) — использует consumer_id в RateLimitFilter

## Acceptance Criteria

### AC1: Consumer ID from JWT azp Claim
**Given** request with valid JWT token
**When** ConsumerIdentityFilter processes request
**Then** consumer_id is extracted from JWT `azp` claim
**And** consumer_id is stored in Reactor Context
**And** consumer_id is added to MDC for logging

### AC2: Consumer ID from X-Consumer-ID Header (Public Routes)
**Given** request without JWT (public route)
**When** `X-Consumer-ID` header is present
**Then** consumer_id is taken from header value

### AC3: Anonymous Consumer (Fallback)
**Given** request without JWT and without `X-Consumer-ID` header
**When** ConsumerIdentityFilter processes request
**Then** consumer_id is set to "anonymous"

### AC4: Consumer ID Propagation to Downstream Filters
**Given** consumer_id is determined
**When** request continues through filter chain
**Then** MetricsFilter has access to consumer_id
**And** RateLimitFilter has access to consumer_id
**And** LoggingFilter has access to consumer_id (via MDC)

### AC5: Consumer ID in Structured Logs
**Given** structured log output
**When** request is logged
**Then** log entry includes `consumerId` field in MDC

## Tasks / Subtasks

- [x] Task 0: Pre-flight Checklist (PA-09)
  - [x] 0.1 Проверить что gateway-core запускается и маршрутизация работает
  - [x] 0.2 Проверить что JwtAuthenticationFilter устанавливает consumer_id в exchange.attributes (Story 12.4)
  - [x] 0.3 Проверить что все тесты проходят: `./gradlew :gateway-core:test`

- [x] Task 1: ConsumerIdentityFilter Implementation (AC: #1, #2, #3)
  - [x] 1.1 Создать `ConsumerIdentityFilter.kt` в `filter/`
  - [x] 1.2 Implements GlobalFilter, Ordered (order = HIGHEST_PRECEDENCE + 8)
  - [x] 1.3 Приоритет 1: exchange.attributes["gateway.consumerId"] (от JwtAuthenticationFilter)
  - [x] 1.4 Приоритет 2: X-Consumer-ID header (для public routes)
  - [x] 1.5 Приоритет 3: "anonymous" (fallback)
  - [x] 1.6 Сохранить в Reactor Context: `context.put(CONSUMER_ID_KEY, consumerId)`

- [x] Task 2: MDC Integration for Logging (AC: #4, #5)
  - [x] 2.1 LoggingFilter читает consumerId из Reactor Context через signal.contextView
  - [x] 2.2 MDC.put("consumerId", consumerId) в doOnEach callback
  - [x] 2.3 MDC очищается в finally блоке LoggingFilter

- [x] Task 3: Update LoggingFilter (AC: #5)
  - [x] 3.1 Добавить `consumerId` в MDC.put() перед логированием
  - [x] 3.2 Получать consumerId из Reactor Context через signal.contextView

- [x] Task 4: Unit Tests
  - [x] 4.1 ConsumerIdentityFilterTest — тест JWT consumer (AC1)
  - [x] 4.2 Тест X-Consumer-ID header (AC2)
  - [x] 4.3 Тест anonymous fallback (AC3)
  - [x] 4.4 Тест propagation через Context (AC4)
  - [x] 4.5 Тест MDC integration (AC5)

- [x] Task 5: Integration Tests
  - [x] 5.1 Существующие интеграционные тесты проходят
  - [x] 5.2 ConsumerIdentityFilter зарегистрирован в filter chain с правильным order

- [x] Task 6: Manual Verification
  - [x] 6.1 Логи содержат consumerId поле (добавлен в logback-spring.xml)
  - [x] 6.2 ConsumerIdentityFilter в filter chain с order -2147483640
  - [x] 6.3 Filter chain порядок: CorrelationId → JwtAuth → ConsumerIdentity → Metrics
  - [x] 6.4 gateway-core запускается без ошибок

## API Dependencies Checklist

**Backend — ConsumerIdentityFilter:**

| Field | Source | Priority |
|-------|--------|----------|
| `consumer_id` | `exchange.attributes["gateway.consumerId"]` | 1 (от JWT) |
| `consumer_id` | `X-Consumer-ID` header | 2 (public route) |
| `consumer_id` | `"anonymous"` | 3 (fallback) |

**Зависимости от Story 12.4:**
- `JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE = "gateway.consumerId"` ✅
- Установка consumer_id из JWT azp claim ✅

**ВАЖНО:** ConsumerIdentityFilter НЕ выполняет JWT валидацию — это делает JwtAuthenticationFilter (Story 12.4). Этот фильтр только консолидирует consumer identity из разных источников.

## Dev Notes

### Filter Order в Gateway Core (Phase 2)

```
CorrelationIdFilter     (HIGHEST_PRECEDENCE)      — генерация X-Correlation-ID
JwtAuthenticationFilter (HIGHEST_PRECEDENCE + 5)  — валидация JWT (Story 12.4)
ConsumerIdentityFilter  (HIGHEST_PRECEDENCE + 8)  — consumer identity ← ЭТА STORY
MetricsFilter           (HIGHEST_PRECEDENCE + 10) — метрики
RateLimitFilter         (HIGHEST_PRECEDENCE + 100)— rate limiting
LoggingFilter           (LOWEST_PRECEDENCE - 1)   — logging
```

### Почему отдельный фильтр?

1. **Separation of Concerns**: JwtAuthenticationFilter — аутентификация, ConsumerIdentityFilter — идентификация
2. **Public Routes**: Для public routes (authRequired=false) JWT нет, но consumer может быть определён через header
3. **Централизация**: Единая точка определения consumer_id для MetricsFilter, RateLimitFilter, LoggingFilter

### Consumer ID Sources

```kotlin
// Приоритет определения consumer_id:
val consumerId = exchange.getAttribute<String>(JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE)  // От JWT (azp claim)
    ?: exchange.request.headers.getFirst("X-Consumer-ID")?.takeIf { it.isNotBlank() }         // От header (public route)
    ?: "anonymous"                                                                              // Fallback
```

**X-Consumer-ID Header:**
- Используется для public routes где JWT не требуется
- Полезно для legacy интеграций или internal services
- Валидируется: max 64 символа, только `[a-zA-Z0-9._-]` (предотвращает log injection)

### Reactor Context Propagation

```kotlin
@Component
class ConsumerIdentityFilter : GlobalFilter, Ordered {

    companion object {
        const val CONSUMER_ID_KEY = "consumerId"
        const val ANONYMOUS = "anonymous"
        const val FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 8
    }

    override fun getOrder() = FILTER_ORDER

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        // Приоритет 1: из JwtAuthenticationFilter (JWT azp claim)
        val jwtConsumerId = exchange.getAttribute<String>(JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE)
            ?.takeIf { it != "unknown" && it != "anonymous" }

        // Приоритет 2: X-Consumer-ID header (для public routes)
        val headerConsumerId = exchange.request.headers.getFirst("X-Consumer-ID")
            ?.takeIf { it.isNotBlank() }

        // Приоритет 3: anonymous
        val consumerId = jwtConsumerId ?: headerConsumerId ?: ANONYMOUS

        // Сохраняем в exchange attributes для MetricsFilter и RateLimitFilter
        exchange.attributes[CONSUMER_ID_KEY] = consumerId

        return chain.filter(exchange)
            .contextWrite { context ->
                context.put(CONSUMER_ID_KEY, consumerId)
            }
    }
}
```

### MDC Integration для LoggingFilter

LoggingFilter уже использует MDC для correlationId. Добавим consumerId:

```kotlin
// В LoggingFilter.filter(), внутри doOnEach:
val consumerId = signal.contextView
    .getOrDefault(ConsumerIdentityFilter.CONSUMER_ID_KEY, "anonymous")

MDC.put("consumerId", consumerId)  // Добавить к существующим MDC полям
```

### Тестирование без Keycloak

ConsumerIdentityFilter работает независимо от keycloak.enabled flag:
- Если Keycloak включён → JwtAuthenticationFilter устанавливает consumer_id
- Если Keycloak выключен → ConsumerIdentityFilter использует header или anonymous

### Previous Story Intelligence

Из Story 12.4:
- `JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE = "gateway.consumerId"` — константа для атрибута
- `extractConsumerId()` использует приоритет: azp → clientId → "unknown"
- При authRequired=false и без токена устанавливается "anonymous"

Из CorrelationIdFilter:
- Паттерн context propagation через `contextWrite(Context.of(...))`
- Паттерн хранения в exchange.attributes для доступа вне reactive chain

Из LoggingFilter:
- MDC.put() для структурированного логирования
- Доступ к Context через `signal.contextView.getOrDefault(...)`
- Очистка MDC в finally блоке

### Critical Constraints

1. **Order = HIGHEST_PRECEDENCE + 8** — после JwtAuthenticationFilter (+ 5), до MetricsFilter (+ 10)
2. **Не блокировать запрос** — только чтение и propagation, без 4xx/5xx
3. **Reactor Context для downstream operators** — MDC bridge для LoggingFilter
4. **Exchange attributes для synchronous access** — MetricsFilter, RateLimitFilter
5. **Логировать только debug** — не спамить production логи

### File Structure

```
backend/gateway-core/src/main/kotlin/com/company/gateway/core/
├── filter/
│   ├── CorrelationIdFilter.kt       # СУЩЕСТВУЮЩИЙ
│   ├── JwtAuthenticationFilter.kt   # Story 12.4 ✅
│   ├── ConsumerIdentityFilter.kt    # НОВЫЙ ← ЭТА STORY
│   ├── MetricsFilter.kt             # МОДИФИЦИРОВАТЬ (Story 12.6)
│   ├── RateLimitFilter.kt           # МОДИФИЦИРОВАТЬ (Story 12.8)
│   └── LoggingFilter.kt             # МОДИФИЦИРОВАТЬ

backend/gateway-core/src/test/kotlin/com/company/gateway/core/filter/
└── ConsumerIdentityFilterTest.kt    # НОВЫЙ
```

### Testing Strategy

1. **Unit Tests:**
   - ConsumerIdentityFilter с mocked exchange и chain
   - Все три приоритета consumer_id determination
   - Context propagation verification

2. **Integration Tests:**
   - Full filter chain с реальным consumer_id flow
   - Verification что MetricsFilter получает consumer_id

3. **Manual Testing:**
   - Проверка логов на наличие consumerId
   - cURL с JWT token → проверка azp extraction
   - cURL с X-Consumer-ID header → проверка header extraction
   - cURL без header → "anonymous"

### Project Structure Notes

- ConsumerIdentityFilter в том же package что и JwtAuthenticationFilter
- Использует константы из JwtAuthenticationFilter
- Не требует новых зависимостей (Spring WebFlux + Reactor)

### References

- [Source: architecture.md#ConsumerIdentityFilter]
- [Source: architecture.md#Gateway Core Filter Chain (Phase 2)]
- [Source: epics.md#Story 12.5]
- [Source: 12-4-gateway-core-jwt-authentication-filter.md] — JwtAuthenticationFilter implementation
- [Spring Cloud Gateway GlobalFilter](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway/global-filters.html)
- [Project Reactor Context Propagation](https://projectreactor.io/docs/core/release/reference/#context)

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- ConsumerIdentityFilter загружен в Spring context с order -2147483640
- Filter chain порядок подтверждён в логах gateway-core

### Completion Notes List

- ConsumerIdentityFilter создан с поддержкой трёх источников consumer_id:
  1. JWT azp claim (от JwtAuthenticationFilter)
  2. X-Consumer-ID header (для public routes)
  3. "anonymous" fallback
- LoggingFilter обновлён для чтения consumerId из Reactor Context
- logback-spring.xml обновлён для включения consumerId в MDC
- 24 unit тестов добавлено для ConsumerIdentityFilter (включая валидацию header)
- Интеграционные тесты добавлены в RequestLoggingIntegrationTest
- Все тесты gateway-core проходят

**Code Review Fixes (2026-02-24):**
- H1: Удалено дублирование CONSUMER_ID_ATTRIBUTE — используется JwtAuthenticationFilter.CONSUMER_ID_ATTRIBUTE
- M1: Добавлен consumerId в test profile logback-spring.xml
- M2: Добавлены интеграционные тесты для ConsumerIdentityFilter
- M3: Добавлена валидация X-Consumer-ID header (max 64 chars, regex `^[a-zA-Z0-9._-]+$`)

### File List

- backend/gateway-core/src/main/kotlin/com/company/gateway/core/filter/ConsumerIdentityFilter.kt (NEW)
- backend/gateway-core/src/main/kotlin/com/company/gateway/core/filter/LoggingFilter.kt (MODIFIED)
- backend/gateway-core/src/main/resources/logback-spring.xml (MODIFIED)
- backend/gateway-core/src/test/kotlin/com/company/gateway/core/filter/ConsumerIdentityFilterTest.kt (NEW)
- backend/gateway-core/src/test/kotlin/com/company/gateway/core/integration/RequestLoggingIntegrationTest.kt (MODIFIED)

### Change Log

- 2026-02-24: Code review fixes — removed constant duplication, added header validation, integration tests
- 2026-02-24: Story 12.5 implemented — ConsumerIdentityFilter with JWT, header, and anonymous fallback support

