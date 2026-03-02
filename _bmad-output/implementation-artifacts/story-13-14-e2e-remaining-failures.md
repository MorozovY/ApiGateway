# Story 13.14: E2E CI — Fix Remaining Test Failures

## Metadata
- **Story ID:** 13-14
- **Epic:** 13 — GitLab CI/CD & Infrastructure Migration
- **Type:** Bug Fix
- **Priority:** P1 (blocks green pipeline)
- **Story Points:** 5 (M — multiple issues, 4-8 hours)
- **Created:** 2026-03-02
- **Status:** backlog
- **Depends on:** Story 13.13 (completed)

## Problem Statement

После Story 13.13 (FK constraint fix + login fix), E2E pipeline показывает:
- **20 passed** (улучшение с 16)
- **36 failed** (разные причины)
- **3 flaky**

Pipeline #202 результаты показывают 4 категории failures.

## Root Cause Analysis

### Category 1: API 500 Errors (~12 tests)
**Симптом:** `expect(received).toBeTruthy()` на API calls
**Вероятная причина:** Backend возвращает 500 при создании/обновлении маршрутов
**Affected tests:** Epic 3, 4, 5, 7, 8

### Category 2: Navigation/Routing Failures (~10 tests)
**Симптом:** `expect(page).toHaveURL(expected)` failed
**Вероятная причина:** SPA routing в test environment, redirect issues
**Affected tests:** Epic 3, 4, 7

### Category 3: Metrics Endpoint Issues (~8 tests)
**Симптом:** `expect(received).toContain("consumer_id")` — получает HTML
**Вероятная причина:** Prometheus metrics endpoint возвращает HTML вместо metrics format
**Affected tests:** Epic 6, 12 (AC5)

### Category 4: Remaining Login Timeouts (~6 tests)
**Симптом:** `TimeoutError: page.waitForURL: Timeout 10000ms`
**Вероятная причина:** Edge cases в login flow, network latency в CI
**Affected tests:** Epic 2, 8

## Acceptance Criteria

### AC1: API 500 Errors Fixed
- [ ] Диагностировать root cause 500 errors (FK? Validation? Auth?)
- [ ] Fix backend или test data setup
- [ ] Epic 3, 4, 5, 7, 8 тесты с API calls проходят

### AC2: Navigation/Routing Fixed
- [ ] Диагностировать SPA routing issues
- [ ] Fix navigation waits или URL patterns
- [ ] `toHaveURL` assertions проходят

### AC3: Metrics Endpoint Fixed
- [ ] Prometheus endpoint возвращает text/plain format
- [ ] `consumer_id` label присутствует в метриках
- [ ] Epic 6, 12 metrics тесты проходят

### AC4: Login Stability
- [ ] Увеличить timeout или добавить retry logic
- [ ] Все login-related тесты стабильно проходят

### AC5: Pipeline Green
- [ ] e2e-test job проходит без failures
- [ ] Или failures < 5 (acceptable flaky rate)

## Technical Context

### Текущее состояние (Pipeline #202)

```
36 failed:
  - epic-3: 4 tests (route CRUD)
  - epic-4: 5 tests (approval workflow)
  - epic-5: 4 tests (rate limiting)
  - epic-6: 3 tests (monitoring)
  - epic-7: 5 tests (audit)
  - epic-8: 3 tests (UX)
  - epic-12: 12 tests (Keycloak integration)

20 passed:
  - epic-12: Login, basic RBAC
  - diagnostic-auth: partial

3 flaky:
  - epic-12: login edge cases
```

### Релевантные файлы

| Файл | Описание |
|------|----------|
| `frontend/admin-ui/e2e/*.spec.ts` | E2E test files |
| `frontend/admin-ui/e2e/helpers/auth.ts` | Auth helper (fixed in 13.13) |
| `.gitlab-ci.yml` | CI pipeline (VITE_* fixed in 13.13) |
| `backend/gateway-admin/` | API backend для диагностики 500 errors |

## Implementation Plan

### Phase 1: Диагностика (2h)
1. Скачать artifacts из Pipeline #202
2. Проанализировать каждую категорию failures
3. Определить root causes с конкретными fix

### Phase 2: API 500 Errors (2h)
1. Проверить backend logs для 500 errors
2. Проверить audit_logs FK (возможно ещё есть edge cases)
3. Fix backend или seed data

### Phase 3: Navigation/Metrics (2h)
1. Fix routing waits в тестах
2. Проверить Traefik routing для metrics endpoint
3. Fix test assertions если нужно

### Phase 4: Stability (1h)
1. Добавить retry logic для flaky тестов
2. Увеличить timeouts где нужно
3. Финальный прогон pipeline

## Test Plan

1. После каждого fix — запустить pipeline
2. Отслеживать прогресс: failed count должен уменьшаться
3. Target: < 5 failures (acceptable flaky rate)

## Definition of Done

- [ ] e2e-test job: < 5 failures
- [ ] Все Epic 3-8 тесты проходят или помечены как skip с причиной
- [ ] Epic 12 Keycloak тесты стабильны
- [ ] Документирована причина оставшихся flaky тестов

## References

- Story 13.13: FK constraint + login fix
- Pipeline #202: https://localhost:8929/root/api-gateway/-/pipelines/202
- Job #1464: e2e-test results

## Notes

- Это follow-up к Story 13.13
- Приоритет P1 — нужно для green pipeline
- Можно разбить на под-stories если scope слишком большой
