# Story 7.7.2: Fix Integration Tab — 400 Error

Status: done

## Story

As a **Developer**,
I need to fix the 400 error on Integration tab,
so that E2E tests for Epic 7 can proceed.

## Problem Description

На вкладке Integration (предположительно /audit/integrations):
- Ошибка "Request failed with status code 400"
- Данные не загружаются

## Acceptance Criteria

**AC1 — Integration tab загружается:**

**Given** пользователь с ролью security/admin
**When** переходит на /audit/integrations
**Then** страница загружается без ошибок
**And** таблица upstreams отображается

**AC2 — Данные корректны:**

**Given** Integration tab загружена
**When** отображается таблица
**Then** показываются upstream hosts и route counts
**And** клик на row переходит на /routes?upstream={host}

## Tasks

- [x] Task 1: Исследовать причину 400 ошибки
  - [x] Проверить IntegrationsPage или UpstreamsTable компонент
  - [x] Проверить API endpoint GET /api/v1/routes/upstreams
  - [x] Проверить useRouteHistory hook или соответствующий hook
  - [x] Проверить backend — какие параметры ожидает endpoint

- [x] Task 2: Исправить ошибку
  - [x] Внести необходимые изменения — Не требуется (баг не воспроизводится)
  - [x] Проверить вручную — E2E тест подтвердил корректную работу

- [x] Task 3: Верифицировать исправление
  - [x] Убедиться что страница загружается — ✓ E2E test passed
  - [x] Убедиться что данные отображаются корректно — ✓ E2E test passed

## Investigation Notes

**Дата исследования:** 2026-02-21

**Результат:** Баг не воспроизводится.

**Проверено:**

1. **Frontend компоненты:**
   - `IntegrationsPage.tsx` — страница Integrations Report, использует useUpstreams hook
   - `UpstreamsTable.tsx` — таблица upstream сервисов, корректно обрабатывает loading/error/empty states
   - `useUpstreams.ts` — React Query hook, вызывает fetchUpstreams()
   - `upstreamsApi.ts` — API клиент, GET /api/v1/routes/upstreams

2. **Backend endpoint:**
   - `RouteController.kt:106` — endpoint @GetMapping("/upstreams") существует
   - `RouteService.getUpstreams()` — простой query без дополнительной валидации
   - Требует роль DEVELOPER или выше (корректно)

3. **E2E тестирование:**
   - Тест "Upstream Report работает" (AC4) — **PASSED** (5.8s)
   - Все шаги теста выполнены успешно:
     - Login как admin
     - Создание маршрутов с upstream
     - Переход на /audit/integrations
     - Отображение таблицы upstream сервисов
     - Поиск по host
     - Навигация на /routes?upstream={host}
     - Export Report в CSV

4. **API проверка:**
   - GET /api/v1/routes/upstreams — endpoint отвечает корректно
   - Возвращает UpstreamsResponse с массивом upstream хостов

**Возможные причины оригинальной проблемы:**

1. Временная проблема с сетью или backend
2. Исправлено в коммите `53a7d31` (fix: support multi-select action filter in audit logs API)
3. Race condition при первой загрузке после деплоя
4. Кэширование некорректного состояния в браузере

**Рекомендации:**
- Мониторить повторное появление бага
- При воспроизведении — проверить Network tab в DevTools для точной идентификации failing request

## Fix Applied

Баг не воспроизводится — никаких изменений не требуется.

E2E тест подтверждает корректную работу:
- Integration tab загружается
- Таблица upstreams отображается
- Данные корректны (host + routeCount)
- Навигация на /routes?upstream={host} работает

## Files Changed

Нет изменений — баг не воспроизводится.
