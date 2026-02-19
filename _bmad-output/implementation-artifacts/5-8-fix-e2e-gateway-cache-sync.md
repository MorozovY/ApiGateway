# Story 5.8: Fix E2E AC3 — Gateway Cache Sync

Status: backlog

## Story

As a **QA Engineer**,
I want the Gateway to see newly created rate limit policies immediately,
so that E2E test AC3 (Rate limiting применяется в Gateway) passes.

## Problem Statement

Тест AC3 из Story 5.6 падает потому что:
- Gateway-core использует Caffeine кэш для rate limit policies
- После создания политики через API, gateway-core не знает о ней
- Нет механизма инвалидации кэша между gateway-admin и gateway-core

## Acceptance Criteria

**AC1 — E2E тест AC3 проходит:**

**Given** Published маршрут с rate limit существует (создан через API)
**When** E2E тест отправляет запросы через Gateway (localhost:8080)
**Then** Gateway применяет rate limiting
**And** Заголовки X-RateLimit-* присутствуют
**And** При превышении лимита возвращается HTTP 429

## Technical Options

**Option A: Redis Pub/Sub (рекомендуется)**
- Gateway-admin публикует событие при CRUD операциях с rate limits
- Gateway-core подписан на канал и инвалидирует Caffeine кэш
- Pros: Real-time sync, production-ready
- Cons: Требует Redis pub/sub конфигурации

**Option B: Shorter Caffeine TTL для тестов**
- Уменьшить TTL кэша до 1-2 секунд в test profile
- Добавить delay в тесте перед проверкой
- Pros: Простое решение
- Cons: Не решает проблему для production

**Option C: Cache refresh endpoint**
- Добавить POST /api/v1/cache/refresh в gateway-core
- Тест вызывает endpoint перед проверкой
- Pros: Контролируемое решение
- Cons: Дополнительный endpoint

## Tasks / Subtasks

- [ ] Task 1: Выбрать и реализовать решение для cache sync
- [ ] Task 2: Обновить E2E тест AC3 если нужно (delays, API calls)
- [ ] Task 3: Запустить тест AC3, убедиться что проходит
- [ ] Task 4: Проверить что существующие тесты не сломались

## Dev Notes

### Текущая архитектура кэширования

**Gateway-core RateLimitFilter:**
- Использует `RateLimitPolicyRepository` для получения политик
- Repository может кэшировать результаты в Caffeine

**Файлы для исследования:**
- `backend/gateway-core/src/main/kotlin/.../filter/RateLimitFilter.kt`
- `backend/gateway-core/src/main/kotlin/.../repository/RateLimitPolicyRepository.kt`
- `backend/gateway-core/src/main/resources/application.yml` — настройки кэша

### E2E тест AC3 (из epic-5.spec.ts)

```typescript
test('Rate limiting применяется в Gateway', async ({ page }) => {
  // Setup: создать published маршрут с rate limit через API
  // Отправить запросы к gateway-core (localhost:8080)
  // Проверить заголовки X-RateLimit-*
  // Превысить лимит → проверить HTTP 429
})
```

## References

- [Source: 5-6-e2e-playwright-happy-path.md] — оригинальная история с AC3
- [Source: frontend/admin-ui/e2e/epic-5.spec.ts] — E2E тесты
