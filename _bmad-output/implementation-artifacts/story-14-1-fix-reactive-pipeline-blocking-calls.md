# Story 14.1: Fix Reactive Pipeline Blocking Calls

Status: review

## Story

As a **DevOps Engineer**,
I want the gateway-core to use fully async database calls in reactive pipelines,
So that the Netty event loop is never blocked and gateway latency remains predictable.

## Acceptance Criteria

### AC1: Async Rate Limit Loading
**Given** route predicate is evaluating and rate limit is not in cache
**When** fallback loading is needed
**Then** loading happens asynchronously via `flatMap`/`switchIfEmpty`
**And** no `.block()` call is made in the reactive chain
**And** exchange attributes are set after async completion

### AC2: Cache Initialization Remains Async
**Given** gateway-core application starts
**When** `@EventListener(ApplicationReadyEvent)` initializes cache
**Then** rate limits are loaded in batch via `Mono.zip` or `Flux.collectList`
**And** no blocking calls during startup

### AC3: Flyway Configuration Fixed
**Given** gateway-admin starts
**When** Flyway executes migrations
**Then** `out-of-order: false` is set in application.yml
**And** migrations execute strictly in version order

### AC4: Unit Tests Pass
**Given** reactive pipeline changes
**When** existing unit tests run
**Then** all tests pass
**And** new tests verify async behaviour

## Tasks / Subtasks

- [x] Task 1: Refactor DynamicRouteLocator async predicate (AC: 1)
  - [x] 1.1 Replace `loadRateLimitSync()` call with async chain
  - [x] 1.2 Use `Mono.defer()` + `flatMap` for deferred rate limit loading
  - [x] 1.3 Move exchange attribute setting into `.doOnNext()` after async load
  - [x] 1.4 Add error handling with graceful degradation (skip rate limit if load fails)
- [x] Task 2: Update RouteCacheManager (AC: 1, 2)
  - [x] 2.1 Add new method `loadRateLimitAsync(UUID): Mono<RateLimit?>`
  - [x] 2.2 Deprecate or remove `loadRateLimitSync()` method
  - [x] 2.3 Verify initialization uses only async operations
- [x] Task 3: Fix Flyway configuration (AC: 3)
  - [x] 3.1 Change `out-of-order: true` → `false` in gateway-admin application.yml
  - [x] 3.2 Verify migration numbering is sequential (no gaps in production)
- [x] Task 4: Write/update tests (AC: 4)
  - [x] 4.1 Add test for async rate limit loading in DynamicRouteLocator
  - [x] 4.2 Verify no `.block()` calls via test or static analysis comment
  - [x] 4.3 Run full test suite `./gradlew :gateway-core:test`

## Dev Notes

### Проблема 1: Blocking call в reactive chain

**Файл:** `backend/gateway-core/src/main/kotlin/com/company/gateway/core/route/DynamicRouteLocator.kt:88`

```kotlin
// ТЕКУЩИЙ КОД (ПРОБЛЕМА)
dbRoute.rateLimitId?.let { rateLimitId ->
    var rateLimit = cacheManager.getCachedRateLimit(rateLimitId)
    if (rateLimit == null) {
        log.warn("Политика {} не найдена в кэше, выполняем fallback загрузку", rateLimitId)
        rateLimit = cacheManager.loadRateLimitSync(rateLimitId)  // <-- .block() внутри!
    }
    // ...
}
```

**Файл:** `backend/gateway-core/src/main/kotlin/com/company/gateway/core/cache/RouteCacheManager.kt:185-201`

```kotlin
// loadRateLimitSync() содержит .block()
fun loadRateLimitSync(rateLimitId: UUID): RateLimit? {
    return try {
        rateLimitRepository.findById(rateLimitId)
            .doOnNext { ... }
            .block(java.time.Duration.ofSeconds(5))  // <-- БЛОКИРУЮЩИЙ ВЫЗОВ
    } catch (e: Exception) { ... }
}
```

**Импакт:** При cache miss блокируется Netty event loop, что может вызвать latency spikes и деградацию throughput под нагрузкой.

### Проблема 2: Flyway out-of-order

**Файл:** `backend/gateway-admin/src/main/resources/application.yml:22`

```yaml
flyway:
  out-of-order: true  # ОПАСНО — должно быть false
```

**Импакт:** Flyway может выполнять миграции не в порядке версий. В текущей нумерации есть:
- V3_1 (subversion после V3)
- V5_1 (subversion после V5)
- Пропуск V11 (V10 → V12)

### Решение для Task 1

**Паттерн async predicate с deferred loading:**

```kotlin
.asyncPredicate { exchange ->
    val path = exchange.request.path.value()
    val method = exchange.request.method.name()
    val pathMatches = matchesPrefix(path, dbRoute.path)
    val methodMatches = dbRoute.methods.isEmpty() ||
        dbRoute.methods.any { it.equals(method, ignoreCase = true) }

    if (!pathMatches || !methodMatches) {
        return@asyncPredicate Mono.just(false)
    }

    // Устанавливаем синхронные атрибуты
    exchange.attributes[RateLimitFilter.ROUTE_ID_ATTRIBUTE] = dbRoute.id
    exchange.attributes[JwtAuthenticationFilter.AUTH_REQUIRED_ATTRIBUTE] = dbRoute.authRequired
    dbRoute.allowedConsumers?.let { consumers ->
        exchange.attributes[JwtAuthenticationFilter.ALLOWED_CONSUMERS_ATTRIBUTE] = consumers
    }

    // Асинхронная загрузка rate limit
    val rateLimitMono = dbRoute.rateLimitId?.let { rateLimitId ->
        val cached = cacheManager.getCachedRateLimit(rateLimitId)
        if (cached != null) {
            Mono.just(cached)
        } else {
            cacheManager.loadRateLimitAsync(rateLimitId)
                .doOnNext { rateLimit ->
                    exchange.attributes[RateLimitFilter.RATE_LIMIT_ATTRIBUTE] = rateLimit
                }
                .onErrorResume { e ->
                    log.warn("Ошибка загрузки rate limit {}: {}", rateLimitId, e.message)
                    Mono.empty()  // Graceful degradation: no rate limit
                }
        }
    } ?: Mono.empty()

    rateLimitMono.thenReturn(true).defaultIfEmpty(true)
}
```

### Architecture Compliance

- **Reactive Pattern:** WebFlux требует отсутствия `.block()` в reactive chains
- **Error Handling:** RFC 7807 не применяется (internal logic), используем graceful degradation
- **Caching:** Redis + Caffeine fallback сохраняется

### Testing Strategy

1. **Unit Test:** Mock `RouteCacheManager.loadRateLimitAsync()` с задержкой, verify no blocking
2. **Integration Test:** StepVerifier для async predicate flow
3. **Regression:** Existing tests должны проходить

### Project Structure Notes

**Файлы для изменения:**
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/route/DynamicRouteLocator.kt`
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/cache/RouteCacheManager.kt`
- `backend/gateway-admin/src/main/resources/application.yml`

**Тесты:**
- `backend/gateway-core/src/test/kotlin/com/company/gateway/core/route/DynamicRouteLocatorTest.kt`
- `backend/gateway-core/src/test/kotlin/com/company/gateway/core/cache/RouteCacheManagerTest.kt`

### References

- [Source: architecture-audit-2026-03-01.md#1.4 Критические проблемы]
- [Source: CLAUDE.md#Reactive Patterns (Spring WebFlux)]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

N/A

### Completion Notes List

- **Task 1:** Рефакторинг DynamicRouteLocator для асинхронной загрузки rate limit. Заменён синхронный вызов `loadRateLimitSync()` на асинхронную цепочку с `loadRateLimitAsync()`. Добавлен early return для несовпадающих path/method. Реализован graceful degradation при ошибках загрузки.

- **Task 2:** Добавлен метод `loadRateLimitAsync(UUID): Mono<RateLimit>` в RouteCacheManager. Метод `loadRateLimitSync()` помечен как `@Deprecated`. Инициализация кэша (`initializeCache()`) уже использует только async операции через `refreshCache().subscribe()`.

- **Task 3:** Исправлена конфигурация Flyway: `out-of-order: true` → `false`. Проверена нумерация миграций — существующие записи в `flyway_schema_history` сохранены.

- **Task 4:** Добавлены 4 новых теста для async rate limit loading в DynamicRouteLocatorTest и 3 теста для loadRateLimitAsync в RouteCacheManagerTest. Полный test suite gateway-core и gateway-admin успешно проходит.

### File List

**Изменённые файлы:**
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/route/DynamicRouteLocator.kt`
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/cache/RouteCacheManager.kt`
- `backend/gateway-admin/src/main/resources/application.yml`
- `backend/gateway-core/src/test/kotlin/com/company/gateway/core/route/DynamicRouteLocatorTest.kt`
- `backend/gateway-core/src/test/kotlin/com/company/gateway/core/cache/RouteCacheManagerTest.kt`

## Change Log

- 2026-03-02: Story 14.1 implemented — async rate limit loading in reactive pipelines, Flyway configuration fixed

