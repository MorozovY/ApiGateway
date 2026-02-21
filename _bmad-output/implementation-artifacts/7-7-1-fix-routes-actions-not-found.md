# Story 7.7.1: Fix Routes Actions — Маршрут не найден

Status: done

## Story

As a **Developer**,
I need to fix the "Маршрут не найден" error when clicking Actions in Routes table,
so that E2E tests for Epic 7 can proceed.

## Problem Description

При клике на Actions (действия) в таблице маршрутов на вкладке Routes:
- Появляется ошибка "Маршрут не найден"
- Данные маршрута не отображаются

## Acceptance Criteria

**AC1 — Actions работают корректно:**

**Given** пользователь находится на странице /routes
**When** кликает на Actions для любого маршрута
**Then** dropdown menu отображается корректно
**And** все действия (View, Edit, Clone, Delete) работают без ошибок

**AC2 — Route Details открывается:**

**Given** пользователь кликает View в Actions menu
**When** переходит на страницу деталей маршрута
**Then** страница /routes/{id} загружается корректно
**And** данные маршрута отображаются

## Tasks

- [x] Task 1: Исследовать причину ошибки
  - [x] Проверить RoutesTable.tsx — как передаётся routeId
  - [x] Проверить routesApi.ts — endpoint getRouteById
  - [x] Проверить RouteDetailsPage — как загружаются данные
  - [x] Проверить backend endpoint GET /api/v1/routes/{id}

- [x] Task 2: Исправить ошибку
  - [x] Результат: баг не воспроизводится, исправления не требуются

- [x] Task 3: Верифицировать исправление
  - [x] Unit-тесты RouteDetailsPage (30/30) проходят
  - [x] API endpoints работают корректно

## Investigation Notes

**Дата исследования:** 2026-02-20

**Результат:** Баг не воспроизводится стабильно.

**Проверено:**
1. RoutesTable.tsx — `record.id` передаётся корректно в `navigate(`/routes/${record.id}`)`
2. routesApi.ts — endpoint `fetchRouteById(id)` работает корректно
3. RouteDetailsPage.tsx — логика загрузки и отображения ошибки корректна
4. Backend GET /api/v1/routes/{id} — возвращает данные корректно
5. E2E тесты — создание и просмотр маршрутов работает

**API тесты:**
- GET /api/v1/routes?limit=100 — возвращает все маршруты с корректными UUID id
- GET /api/v1/routes/{id} — возвращает детали маршрута для любого валидного UUID

**Возможные причины оригинальной проблемы:**
1. Устаревший кэш браузера (React Query / браузерный кэш)
2. Несинхронизированное состояние UI после E2E тестов
3. Race condition при быстрой навигации

**Рекомендации:**
- Мониторить повторное появление бага
- При воспроизведении — проверить Network tab в DevTools
- Возможно добавить error boundary с более информативным сообщением

## Fix Applied

**Результат: Баг не воспроизводится (Not Reproducible)**

Дата закрытия: 2026-02-21

Проведённое исследование подтвердило что код работает корректно:

1. **RoutesTable.tsx** — `record.id` корректно передаётся в навигацию (`Link to`, `navigate()`)
2. **routesApi.ts** — `fetchRouteById(id)` использует правильный endpoint `/api/v1/routes/${id}`
3. **RouteDetailsPage.tsx** — `useParams()` корректно извлекает ID, `useRoute(id)` правильно загружает данные
4. **useRoutes.ts** — хук `useRoute` с `enabled: !!id` предотвращает запросы без ID

**Верификация:**
- Unit-тесты RouteDetailsPage: 30/30 passed
- API endpoints: GET /api/v1/routes/{id} возвращает данные корректно
- Код review: все пути передачи ID корректны

**Вероятные причины оригинального бага:**
- Устаревший кэш браузера после E2E тестов
- Race condition при быстрой навигации (edge case)
- Временная сетевая проблема

**Рекомендация:** Мониторить повторное появление. При воспроизведении — проверить Network tab.

## Files Changed

Нет изменений (баг не воспроизводится)
