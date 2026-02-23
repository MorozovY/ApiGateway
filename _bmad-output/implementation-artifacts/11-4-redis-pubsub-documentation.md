# Story 11.4: Redis Pub/Sub Documentation

Status: done

## Story

As a **new developer**,
I want documentation explaining the Redis Pub/Sub mechanism,
So that I can understand how route cache synchronization works.

## Feature Context

**Source:** Epic 10 Retrospective (2026-02-23) — DOC-02
**Business Value:** Новые разработчики должны понимать как работает синхронизация кэша маршрутов между gateway-admin и gateway-core. Без этого понимания сложно отлаживать проблемы типа "маршрут одобрен, но не работает".

## Acceptance Criteria

### AC1: Документация объясняет архитектуру Pub/Sub
**Given** developer opens architecture.md or dedicated docs
**When** searching for cache sync
**Then** documentation explains:
- How gateway-admin publishes route changes
- How gateway-core subscribes and updates cache
- Why this architecture was chosen (distributed cache invalidation)

### AC2: Документация описывает каналы и формат сообщений
**Given** documentation about Redis Pub/Sub
**When** developer reads it
**Then** channels are explained:
- `route-cache-invalidation` — для изменений маршрутов
- `ratelimit-cache-invalidation` — для изменений rate limit политик
- Message format (UUID в виде строки)

### AC3: Документация описывает компоненты
**Given** documentation about Redis Pub/Sub
**When** developer reads it
**Then** components are explained:
- RouteEventPublisher (gateway-admin) — издатель событий маршрутов
- RateLimitEventPublisher (gateway-admin) — издатель событий rate limit
- RouteRefreshService (gateway-core) — подписчик и обработчик событий
- RouteCacheManager (gateway-core) — менеджер кэша

### AC4: Troubleshooting cache sync issues
**Given** developer needs to debug cache sync
**When** they read documentation
**Then** they know:
- How to check Redis subscription status
- How to manually trigger cache refresh
- Common issues and solutions
- How to test Pub/Sub locally

## Tasks / Subtasks

- [x] Task 1: Create docs/cache-sync.md documentation file (AC: #1, #2, #3, #4)
  - [x] 1.1 Section: Введение — зачем нужна синхронизация кэша
  - [x] 1.2 Section: Архитектура — Publisher/Subscriber паттерн
  - [x] 1.3 Section: Каналы и формат сообщений
  - [x] 1.4 Section: Компоненты gateway-admin (Publishers)
  - [x] 1.5 Section: Компоненты gateway-core (Subscriber, CacheManager)
  - [x] 1.6 Section: Fallback механизм (Caffeine TTL, reconnect)
  - [x] 1.7 Section: Troubleshooting & Debugging

- [x] Task 2: Add reference to new doc in architecture.md
  - [x] 2.1 Add link in appropriate section of architecture.md

- [x] Task 3: Manual verification
  - [x] 3.1 Verify documentation covers all ACs
  - [x] 3.2 Verify code references are correct (file paths, line numbers)

## Dev Notes

### Существующий код для документирования

**Publisher компоненты (gateway-admin):**

| Компонент | Путь | Назначение |
|-----------|------|------------|
| RouteEventPublisher | `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/publisher/RouteEventPublisher.kt` | Публикует события изменения маршрутов |
| RateLimitEventPublisher | `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/publisher/RateLimitEventPublisher.kt` | Публикует события изменения rate limit |

**Subscriber компоненты (gateway-core):**

| Компонент | Путь | Назначение |
|-----------|------|------------|
| RouteRefreshService | `backend/gateway-core/src/main/kotlin/com/company/gateway/core/route/RouteRefreshService.kt` | Подписывается на Redis каналы и обновляет кэш |
| RouteCacheManager | `backend/gateway-core/src/main/kotlin/com/company/gateway/core/cache/RouteCacheManager.kt` | Менеджер кэша маршрутов и rate limits |

### Каналы Redis Pub/Sub

| Канал | Назначение | Формат сообщения |
|-------|------------|------------------|
| `route-cache-invalidation` | Изменения маршрутов | `UUID` строка (routeId) |
| `ratelimit-cache-invalidation` | Изменения rate limit | `UUID` строка (rateLimitId) |

### Архитектура Pub/Sub

```
┌─────────────────────┐          Redis          ┌─────────────────────┐
│   gateway-admin     │        Pub/Sub          │   gateway-core      │
│                     │                         │                     │
│  RouteService       │                         │  RouteRefreshService│
│       │             │                         │       │             │
│       ▼             │                         │       ▼             │
│  RouteEventPublisher│──► channel: route-*  ──►│  subscribe()        │
│       │             │                         │       │             │
│       └─────────────┤                         │       ▼             │
│                     │                         │  RouteCacheManager  │
│  RateLimitService   │                         │       │             │
│       │             │                         │       ▼             │
│       ▼             │                         │  getCachedRoutes()  │
│  RateLimitPublisher │──► channel: ratelimit-►│                     │
└─────────────────────┘                         └─────────────────────┘
```

### Ключевые моменты реализации

1. **Опциональная зависимость от Redis** — Publishers используют `@Autowired(required = false)`, что позволяет работать без Redis в тестах

2. **Автоматическое переподключение** — RouteRefreshService использует `scheduleReconnect()` каждые 30 секунд при недоступности Redis

3. **Fallback на Caffeine** — При недоступности Redis используется Caffeine cache с TTL

4. **Batch загрузка rate limits** — RouteCacheManager загружает все rate limits одним запросом для оптимизации

5. **RefreshRoutesEvent** — После обновления кэша публикуется событие Spring Cloud Gateway для перезагрузки маршрутов

### Конфигурация

```yaml
# gateway-core/application.yml
gateway:
  cache:
    invalidation-channel: route-cache-invalidation
    ratelimit-invalidation-channel: ratelimit-cache-invalidation
    reconnect-delay-seconds: 30
```

### Когда публикуются события

**RouteEventPublisher вызывается при:**
- Одобрении маршрута (PENDING → PUBLISHED) — `ApprovalService.approve()`
- Отклонении маршрута — `ApprovalService.reject()`
- Откате маршрута — `ApprovalService.rollback()`

**RateLimitEventPublisher вызывается при:**
- Создании rate limit — `RateLimitService.create()`
- Обновлении rate limit — `RateLimitService.update()`
- Удалении rate limit — `RateLimitService.delete()`

### Debugging commands

```bash
# Проверить подключение к Redis
docker exec -it apigateway-redis-1 redis-cli PING

# Подписаться на канал для просмотра сообщений
docker exec -it apigateway-redis-1 redis-cli SUBSCRIBE route-cache-invalidation

# Вручную опубликовать сообщение для тестирования
docker exec -it apigateway-redis-1 redis-cli PUBLISH route-cache-invalidation "test-uuid"

# Проверить количество подписчиков
docker exec -it apigateway-redis-1 redis-cli PUBSUB NUMSUB route-cache-invalidation
```

### Project Structure Notes

- Документация создаётся в `docs/cache-sync.md` (рядом с rate-limiting.md)
- Файлы в `docs/` — русскоязычная документация для разработчиков
- Architecture.md содержит высокоуровневое описание — добавить ссылку на детальную документацию

### References

- [Source: RouteEventPublisher.kt] — Publisher маршрутов
- [Source: RateLimitEventPublisher.kt] — Publisher rate limits
- [Source: RouteRefreshService.kt:54-58] — @EventListener подписка при старте
- [Source: RouteRefreshService.kt:64-101] — startRouteSubscription()
- [Source: RouteRefreshService.kt:162-182] — scheduleReconnect()
- [Source: RouteCacheManager.kt:54-93] — refreshCache()
- [Source: RouteCacheManager.kt:145-174] — refreshRateLimitCache()

### Scope Notes

**In Scope:**
- Создание docs/cache-sync.md
- Добавление ссылки в architecture.md

**Out of Scope:**
- Изменение кода Pub/Sub
- Создание дополнительных тестов
- Обновление quick-start-guide.md

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Изучил исходный код: RouteEventPublisher.kt, RateLimitEventPublisher.kt, RouteRefreshService.kt, RouteCacheManager.kt
- Проанализировал архитектуру Redis Pub/Sub в проекте
- Проверил конфигурацию каналов и формат сообщений

### Completion Notes List

1. **Task 1 (docs/cache-sync.md):** Создана полная техническая документация Redis Pub/Sub:
   - AC1: Секция "Введение" и "Архитектура" объясняют зачем Pub/Sub и как работает
   - AC2: Секция "Каналы и формат сообщений" описывает оба канала и UUID формат
   - AC3: Секции "Компоненты gateway-admin" и "gateway-core" документируют все 4 компонента
   - AC4: Секция "Troubleshooting & Debugging" содержит Redis CLI команды, частые проблемы, логи

2. **Task 2 (architecture.md):** Добавлена строка Cache Sync в таблицу Requirements to Structure Mapping с ссылкой на docs/cache-sync.md

3. **Task 3 (Verification):** Все ACs покрыты, пути к файлам корректны

### File List

- `docs/cache-sync.md` — **NEW** — техническая документация Redis Pub/Sub
- `_bmad-output/planning-artifacts/architecture.md` — **MODIFIED** — добавлена ссылка на docs/cache-sync.md

## Senior Developer Review (AI)

**Reviewer:** Claude Opus 4.5
**Date:** 2026-02-23

### Review Outcome: ✅ APPROVED

### Issues Found and Fixed

| # | Severity | Issue | Fix Applied |
|---|----------|-------|-------------|
| M1 | MEDIUM | RouteEventPublisher пример не содержал doOnError | Добавлен doOnError в docs/cache-sync.md |
| M2 | MEDIUM | RateLimitEventPublisher показан без doOnSuccess/doOnError | Добавлена полная обработка |
| M3 | MEDIUM | startRouteSubscription не показывал cleanup логику | Добавлен AtomicReference cleanup |
| M4 | MEDIUM | Отсутствовал loadRateLimitSync fallback метод | Добавлена секция с описанием |
| L1 | LOW | sprint-status.yaml не в File List | Ожидаемое поведение (автоматическое обновление статуса) |
| L2 | LOW | Thread.sleep в примере теста | Заменён на awaitility await() |

### Verification

- ✅ Все Acceptance Criteria реализованы
- ✅ Все задачи помеченные [x] реально выполнены
- ✅ Документация соответствует исходному коду
- ✅ Все 4 компонента существуют и корректно описаны
- ✅ Примеры кода исправлены для полного соответствия реализации

