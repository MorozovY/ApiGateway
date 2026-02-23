# Story 11.3: Lua + Redis Rate Limiting Documentation

Status: done

## Story

As a **new developer**,
I want documentation explaining the Lua-based rate limiting implementation,
So that I can understand and maintain the token bucket algorithm.

## Feature Context

**Source:** Epic 10 Retrospective (2026-02-23) — DOC-01
**Business Value:** Новые разработчики должны понимать как работает rate limiting без обратного инжиниринга кода. Lua скрипт — критический компонент для защиты upstream сервисов от перегрузки.

## Acceptance Criteria

### AC1: Документация объясняет почему используется Lua
**Given** developer opens architecture.md or dedicated docs
**When** searching for rate limiting
**Then** documentation explains why Lua is used (atomic operations in Redis)

### AC2: Token bucket algorithm documentation
**Given** documentation about rate limiting
**When** developer reads it
**Then** algorithm implementation is explained:
- How tokens are stored and replenished
- What parameters control the algorithm (requestsPerSecond, burstSize)
- How fractional tokens are handled

### AC3: Redis data structure documentation
**Given** documentation about rate limiting
**When** developer reads it
**Then** Redis key structure is explained:
- Key format: `ratelimit:{routeId}:{clientKey}`
- Hash fields: `tokens`, `lastRefill`
- TTL handling

### AC4: Debug and testing guide
**Given** developer needs to debug rate limiting
**When** they read documentation
**Then** they know:
- How to inspect Redis keys via CLI
- How to run integration tests
- How to test locally with Testcontainers

## Tasks / Subtasks

- [x] Task 1: Create docs/rate-limiting.md documentation file (AC: #1, #2, #3, #4)
  - [x] 1.1 Section: Введение — почему Lua для atomic operations
  - [x] 1.2 Section: Token Bucket алгоритм — подробное объяснение
  - [x] 1.3 Section: Redis структура данных — ключи, поля, TTL
  - [x] 1.4 Section: Архитектура компонентов — TokenBucketScript, RateLimitService, Filter
  - [x] 1.5 Section: Fallback механизм — LocalRateLimiter при недоступности Redis
  - [x] 1.6 Section: Debugging & Testing — Redis CLI, Testcontainers

- [x] Task 2: Add reference to new doc in architecture.md
  - [x] 2.1 Add link in Rate Limiting section of architecture.md

- [x] Task 3: Manual verification
  - [x] 3.1 Verify documentation covers all ACs
  - [x] 3.2 Verify code references are correct (file paths, line numbers)

## Dev Notes

### Существующий код для документирования

**Основные файлы:**

| Компонент | Путь | Назначение |
|-----------|------|------------|
| Lua скрипт | `backend/gateway-core/src/main/resources/scripts/token-bucket.lua` | Атомарный token bucket в Redis |
| TokenBucketScript | `backend/gateway-core/src/main/kotlin/.../ratelimit/TokenBucketScript.kt` | Kotlin wrapper над Lua |
| RateLimitService | `backend/gateway-core/src/main/kotlin/.../ratelimit/RateLimitService.kt` | Координатор Redis + fallback |
| LocalRateLimiter | `backend/gateway-core/src/main/kotlin/.../ratelimit/LocalRateLimiter.kt` | In-memory fallback (Caffeine) |
| RateLimitFilter | `backend/gateway-core/src/main/kotlin/.../filter/RateLimitFilter.kt` | GlobalFilter с X-RateLimit headers |
| RateLimitResult | `backend/gateway-core/src/main/kotlin/.../ratelimit/RateLimitResult.kt` | DTO результата |
| TokenBucketScriptTest | `backend/gateway-core/src/test/kotlin/.../ratelimit/TokenBucketScriptTest.kt` | Integration tests |

### Token Bucket Algorithm (из token-bucket.lua)

```lua
-- Ключ: ratelimit:{routeId}:{clientKey}
-- ARGV[1] = requestsPerSecond (rate восполнения)
-- ARGV[2] = burstSize (максимум токенов)
-- ARGV[3] = текущее время (unix timestamp в миллисекундах)
-- ARGV[4] = TTL в секундах

-- Получаем текущее состояние bucket
local bucket = redis.call('HMGET', key, 'tokens', 'lastRefill')

-- Рассчитываем восполнение токенов
-- ВАЖНО: используем дробное значение для корректного накопления
local elapsed = (now - lastRefill) / 1000.0  -- в секундах
local refill = elapsed * rate  -- дробное восполнение

-- Добавляем токены (ограничено capacity)
tokens = math.max(0, math.min(capacity, tokens + refill))

-- Проверяем доступность токена
if tokens >= 1 then
    tokens = tokens - 1
    allowed = 1
end

-- Возвращаем: [allowed, remaining, resetTime]
```

**Ключевые моменты:**
1. **Дробные токены** — исправление бага math.floor (Story 10.1) — токены хранятся как float для точного накопления
2. **Atomic execution** — весь скрипт выполняется атомарно в Redis (EVAL)
3. **TTL auto-cleanup** — неактивные ключи автоматически удаляются (по умолчанию 120 секунд)

### Fallback механизм

При недоступности Redis:
1. `RateLimitService.handleRedisError()` переключается на `LocalRateLimiter`
2. `LocalRateLimiter` использует Caffeine cache с **консервативными лимитами (50%)**
3. Логируется WARNING при первом переключении
4. При восстановлении Redis — автоматический возврат к distributed режиму

### Redis key inspection (для debugging)

```bash
# Подключение к Redis
docker exec -it apigateway-redis-1 redis-cli

# Просмотр всех rate limit ключей
KEYS ratelimit:*

# Просмотр конкретного bucket
HGETALL ratelimit:{routeId}:{clientKey}

# Просмотр TTL
TTL ratelimit:{routeId}:{clientKey}

# Удаление ключа (сброс лимита)
DEL ratelimit:{routeId}:{clientKey}
```

### Project Structure Notes

- Документация создаётся в `docs/rate-limiting.md` (рядом с quick-start-guide.md)
- Файлы в `docs/` — русскоязычная документация для разработчиков
- Architecture.md (`_bmad-output/planning-artifacts/`) содержит краткие упоминания rate limiting — добавить ссылку

### References

- [Source: token-bucket.lua] — Lua скрипт алгоритма
- [Source: TokenBucketScript.kt:40-71] — checkRateLimit метод
- [Source: RateLimitService.kt:42-68] — координация Redis + fallback
- [Source: LocalRateLimiter.kt:48-73] — локальный fallback
- [Source: RateLimitFilter.kt:61-87] — integration в gateway
- [Source: TokenBucketScriptTest.kt] — integration тесты с Testcontainers

### Scope Notes

**In Scope:**
- Создание docs/rate-limiting.md
- Добавление ссылки в architecture.md

**Out of Scope:**
- Изменение кода rate limiting
- Создание дополнительных тестов
- Обновление quick-start-guide.md

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Изучил исходный код: token-bucket.lua, TokenBucketScript.kt, RateLimitService.kt, LocalRateLimiter.kt, RateLimitFilter.kt
- Проверил тестовый файл TokenBucketScriptTest.kt для секции тестирования
- Изучил architecture.md для корректного добавления ссылки

### Completion Notes List

1. **Task 1 (docs/rate-limiting.md):** Создана полная техническая документация rate limiting:
   - AC1: Секция "Введение" объясняет почему Lua (atomic operations, race condition prevention)
   - AC2: Секция "Token Bucket Algorithm" описывает алгоритм, параметры, дробные токены
   - AC3: Секция "Redis Data Structure" документирует формат ключей, hash fields, TTL
   - AC4: Секция "Debugging & Testing" содержит Redis CLI команды, Testcontainers примеры

2. **Task 2 (architecture.md):** Добавлена ссылка на docs/rate-limiting.md в таблицу Requirements to Structure Mapping

3. **Task 3 (Verification):** Проверено соответствие всех AC, код references корректны

### File List

- `docs/rate-limiting.md` — **NEW** — техническая документация rate limiting
- `_bmad-output/planning-artifacts/architecture.md` — **MODIFIED** — добавлена колонка Documentation и ссылка на rate-limiting.md
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — **MODIFIED** — обновлён статус story 11.3

## Senior Developer Review (AI)

**Reviewer:** Claude Opus 4.5 (claude-opus-4-5-20251101)
**Date:** 2026-02-23
**Outcome:** ✅ APPROVED (после исправлений)

### Issues Found & Fixed

| # | Severity | Issue | Status |
|---|----------|-------|--------|
| M2 | MEDIUM | Неполный пример Lua — отсутствовал расчёт resetTime | ✅ Fixed |
| M4 | MEDIUM | Placeholder пути `...` вместо полных путей в таблице компонентов | ✅ Fixed |
| M5 | MEDIUM | sprint-status.yaml не документирован в File List | ✅ Fixed |

### AC Validation

| AC | Status | Evidence |
|----|--------|----------|
| AC1 | ✅ | `docs/rate-limiting.md:17-56` — Почему Lua для Rate Limiting |
| AC2 | ✅ | `docs/rate-limiting.md:59-165` — Token Bucket Algorithm |
| AC3 | ✅ | `docs/rate-limiting.md:168-220` — Redis Data Structure |
| AC4 | ✅ | `docs/rate-limiting.md:439-567` — Debugging & Testing |

### Notes

- Документация полностью соответствует исходному коду
- Все номера строк в документации корректны
- Примеры Redis CLI и Testcontainers работоспособны
