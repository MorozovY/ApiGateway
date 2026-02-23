# Story 11.5: WebFlux Patterns Documentation

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **new developer**,
I want documentation explaining Spring WebFlux reactive patterns,
So that I can write correct non-blocking code.

## Feature Context

**Source:** Epic 10 Retrospective (2026-02-23) — DOC-03
**Business Value:** Новые разработчики часто допускают ошибки при работе с reactive stack: используют блокирующие вызовы, неправильно обрабатывают ошибки, создают memory leaks. Документация предотвратит типичные ошибки и ускорит onboarding.

## Acceptance Criteria

### AC1: Документация объясняет основы Mono и Flux
**Given** developer opens architecture.md or dedicated docs
**When** searching for reactive patterns
**Then** documentation explains:
- Когда использовать Mono (0..1 элемент) vs Flux (0..N элементов)
- Базовые операторы: map, flatMap, filter
- Cold vs Hot publishers
- Примеры из проекта с пояснениями

### AC2: Документация объясняет распространённые операторы
**Given** documentation about reactive patterns
**When** developer reads it
**Then** operators are explained with examples:
- `flatMap` — async transformation (запрос в БД, вызов другого сервиса)
- `switchIfEmpty` — альтернативный Mono если исходный пустой
- `defaultIfEmpty` — default значение если Mono пустой
- `zip` / `zipWith` — объединение нескольких Mono
- `doOnNext`, `doOnError`, `doOnSuccess` — side effects

### AC3: Документация описывает паттерны обработки ошибок
**Given** documentation about reactive patterns
**When** developer reads it
**Then** error handling patterns are explained:
- `onErrorResume` — fallback Mono при ошибке
- `onErrorReturn` — fallback значение при ошибке
- `onErrorMap` — трансформация исключений
- Когда использовать какой паттерн
- RFC 7807 error responses в WebFlux

### AC4: Документация описывает тестирование reactive кода
**Given** documentation about reactive patterns
**When** developer reads it
**Then** testing patterns are explained:
- StepVerifier для unit тестов
- WebTestClient для integration тестов
- Awaitility для async assertions
- Testcontainers для внешних зависимостей

### AC5: Документация описывает anti-patterns
**Given** documentation about reactive patterns
**When** developer reads it
**Then** anti-patterns are explained:
- Почему нельзя использовать `.block()` в reactive контексте
- Почему нельзя использовать `@PostConstruct` (использовать `@EventListener(ApplicationReadyEvent::class)`)
- Почему нельзя использовать `ThreadLocal` без context propagation
- Почему нельзя использовать `synchronized` блоки (использовать `AtomicReference`)

## Tasks / Subtasks

- [x] Task 1: Create docs/webflux-patterns.md documentation file (AC: #1, #2, #3, #4, #5)
  - [x] 1.1 Section: Введение — зачем нужен reactive stack
  - [x] 1.2 Section: Mono vs Flux — когда использовать
  - [x] 1.3 Section: Основные операторы с примерами
  - [x] 1.4 Section: Обработка ошибок (onErrorResume, onErrorReturn, onErrorMap)
  - [x] 1.5 Section: Комбинирование потоков (flatMap, zip, merge)
  - [x] 1.6 Section: Side Effects (doOnNext, doOnError, doOnSuccess)
  - [x] 1.7 Section: Тестирование (StepVerifier, WebTestClient, Awaitility)
  - [x] 1.8 Section: Anti-patterns и типичные ошибки
  - [x] 1.9 Section: Ссылки на код проекта с примерами

- [x] Task 2: Add reference to new doc in architecture.md
  - [x] 2.1 Add link in Requirements to Structure Mapping table

- [x] Task 3: Manual verification
  - [x] 3.1 Verify documentation covers all ACs
  - [x] 3.2 Verify code references are correct (file paths exist)
  - [x] 3.3 Verify examples compile conceptually

## Dev Notes

### Существующий reactive код в проекте

**RouteRefreshService** — отличный пример reactive subscription:
```kotlin
// backend/gateway-core/src/main/kotlin/com/company/gateway/core/route/RouteRefreshService.kt

@EventListener(ApplicationReadyEvent::class)  // НЕ @PostConstruct!
fun subscribeToInvalidationEvents() {
    startRouteSubscription()
}

private fun startRouteSubscription() {
    val newSubscription = newContainer.receive(ChannelTopic.of(channel))
        .flatMap { message ->                    // async operation
            cacheManager.refreshCache()
        }
        .doOnSubscribe {                         // side effect
            logger.info("Подписка активирована")
        }
        .doOnError { error ->                    // error logging
            logger.warn("Ошибка: {}", error.message)
        }
        .onErrorResume { _ ->                    // fallback
            Mono.empty()
        }
        .subscribe()
}
```

**RouteService** — пример flatMap chains и switchIfEmpty:
```kotlin
// backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/RouteService.kt

fun create(request: CreateRouteRequest, userId: UUID): Mono<RouteResponse> {
    return routeRepository.existsByPath(request.path)
        .flatMap { exists ->
            if (exists) {
                Mono.error(ConflictException("Route with path already exists"))
            } else {
                routeRepository.save(route)
            }
        }
        .flatMap { savedRoute ->
            loadRateLimitInfo(savedRoute.rateLimitId)
                .map { info -> RouteResponse.from(savedRoute, info) }
                .switchIfEmpty(Mono.defer { Mono.just(RouteResponse.from(savedRoute, null)) })
        }
}
```

### Ключевые паттерны из CLAUDE.md

| Паттерн | Запрещено | Использовать |
|---------|-----------|--------------|
| Инициализация | `@PostConstruct` | `@EventListener(ApplicationReadyEvent::class)` |
| Блокирующие вызовы | `.block()`, `Thread.sleep()` | Reactive chains |
| Thread-safe state | `synchronized` | `AtomicReference` |
| ThreadLocal | Без context propagation | Reactor Context + MDC bridging |

### Примеры из проекта для документации

| Файл | Паттерн | Описание |
|------|---------|----------|
| `RouteRefreshService.kt:54-58` | `@EventListener(ApplicationReadyEvent)` | Reactive инициализация |
| `RouteRefreshService.kt:46-52` | `AtomicReference`, `AtomicBoolean` | Thread-safe state |
| `RouteRefreshService.kt:89-92` | `onErrorResume` | Fallback при ошибке Redis |
| `RouteService.kt:66-99` | `flatMap` + `switchIfEmpty` | Conditional async operations |
| `RouteService.kt:150-167` | `flatMap` + error handling | Validation chains |
| `RateLimitService.kt` | `onErrorResume` | Redis fallback to local cache |
| `ApprovalService.kt` | `flatMap` chains | Multi-step business logic |

### Тестирование reactive кода в проекте

**StepVerifier пример:**
```kotlin
// Из TokenBucketScriptTest.kt
StepVerifier.create(tokenBucketScript.checkRateLimit(key, 10, 15))
    .assertNext { result ->
        assertThat(result.allowed).isTrue()
        assertThat(result.remaining).isEqualTo(14)
    }
    .verifyComplete()
```

**WebTestClient пример:**
```kotlin
// Из RouteControllerIntegrationTest.kt
webTestClient.post()
    .uri("/api/v1/routes")
    .bodyValue(CreateRouteRequest(...))
    .exchange()
    .expectStatus().isCreated
    .expectBody<RouteResponse>()
    .returnResult()
```

**Awaitility пример (НЕ Thread.sleep!):**
```kotlin
// Правильный способ ожидания async событий
await().atMost(5.seconds).untilAsserted {
    assertThat(cacheManager.getRoutes()).isNotEmpty()
}
```

### MDC Context Propagation

```kotlin
// backend/gateway-core/src/main/kotlin/com/company/gateway/core/config/MdcContextConfig.kt
// Настройка Reactor Context для MDC (correlation ID logging)
```

### RFC 7807 Error Response

Все ошибки должны возвращаться в формате RFC 7807:
```json
{
    "type": "https://api.gateway/errors/not-found",
    "title": "Not Found",
    "status": 404,
    "detail": "Route not found",
    "correlationId": "abc-123-def"
}
```

### Project Structure Notes

- Документация создаётся в `docs/webflux-patterns.md` (рядом с rate-limiting.md, cache-sync.md)
- Файлы в `docs/` — русскоязычная документация для разработчиков
- Architecture.md содержит высокоуровневое описание — добавить ссылку на детальную документацию
- CLAUDE.md содержит правила проекта — документация должна быть согласована с ними

### References

- [Source: RouteRefreshService.kt:54-58] — @EventListener инициализация
- [Source: RouteRefreshService.kt:64-101] — Reactive subscription с error handling
- [Source: RouteService.kt:60-124] — flatMap chains и switchIfEmpty
- [Source: RateLimitService.kt:73-103] — onErrorResume fallback
- [Source: MdcContextConfig.kt] — MDC context propagation
- [Source: CLAUDE.md] — Reactive Patterns rules

### Scope Notes

**In Scope:**
- Создание docs/webflux-patterns.md
- Добавление ссылки в architecture.md
- Примеры из реального кода проекта

**Out of Scope:**
- Изменение существующего кода
- Создание дополнительных тестов
- Обновление CLAUDE.md

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

Нет debug issues.

### Completion Notes List

- Создан docs/webflux-patterns.md с 9 секциями (715 строк)
- Все 5 Acceptance Criteria покрыты:
  - AC1: Mono vs Flux, базовые операторы, Cold vs Hot publishers, примеры из проекта
  - AC2: flatMap, switchIfEmpty, defaultIfEmpty, zip/zipWith, doOn* операторы
  - AC3: onErrorResume, onErrorReturn, onErrorMap, таблица выбора паттернов, RFC 7807
  - AC4: StepVerifier, WebTestClient, Awaitility, Testcontainers с примерами кода
  - AC5: .block(), @PostConstruct, ThreadLocal, synchronized — все с правильными альтернативами
- Добавлена ссылка в architecture.md (Requirements to Structure Mapping table)
- Все ссылки на файлы проекта проверены и корректны
- Примеры кода взяты из реального кода проекта (RouteRefreshService, RouteService, TokenBucketScriptTest)

### File List

- `docs/webflux-patterns.md` — новый файл (создан)
- `_bmad-output/planning-artifacts/architecture.md` — изменён (добавлена ссылка на webflux-patterns.md)

## Senior Developer Review (AI)

**Review Date:** 2026-02-23
**Reviewer:** Claude Opus 4.5
**Outcome:** ✅ Approved with fixes applied

### Issues Found & Fixed

| Severity | Issue | Resolution |
|----------|-------|------------|
| HIGH | MdcContextConfig.kt uses @PostConstruct but docs say it's forbidden | Added exception for @Configuration classes in docs |
| MEDIUM | RateLimitService.kt example missing | Added concrete example with onErrorResume fallback |
| MEDIUM | retryWhen operator not documented | Added new section with RouteService example |
| MEDIUM | WebTestClient error testing examples missing | Added RFC 7807 error response test examples |

### Remaining LOW Issues (not blocking)

- L1: Line number references will become stale over time
- L2: Missing link to GlobalExceptionHandler.kt

### AC Verification

- ✅ AC1: Mono vs Flux basics documented with project examples
- ✅ AC2: Common operators (flatMap, switchIfEmpty, zip, retryWhen) with examples
- ✅ AC3: Error handling patterns (onErrorResume, onErrorReturn, onErrorMap) + RFC 7807
- ✅ AC4: Testing patterns (StepVerifier, WebTestClient with error tests, Awaitility, Testcontainers)
- ✅ AC5: Anti-patterns (.block(), @PostConstruct with exception, ThreadLocal, synchronized)

