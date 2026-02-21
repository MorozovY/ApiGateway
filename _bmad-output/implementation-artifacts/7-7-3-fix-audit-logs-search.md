# Story 7.7.3: Fix Audit Logs — Поиск не работает

Status: done

## Story

As a **Developer**,
I need to fix the search/filter functionality in Audit Logs,
so that E2E tests for Epic 7 can proceed.

## Problem Description

На странице Audit Logs (/audit):
- Поиск/фильтрация не возвращает результаты
- Возможно фильтры не применяются или API не возвращает данные

## Acceptance Criteria

**AC1 — Фильтры работают:**

**Given** пользователь на странице /audit
**When** применяет фильтры (action, entityType, user, date range)
**Then** таблица обновляется с отфильтрованными данными
**And** результаты соответствуют критериям фильтрации

**AC2 — Поиск работает:**

**Given** пользователь вводит поисковый запрос
**When** применяется поиск
**Then** результаты фильтруются по запросу
**And** релевантные записи отображаются

**AC3 — Пустой результат корректен:**

**Given** фильтры не соответствуют никаким записям
**When** применяется поиск
**Then** отображается сообщение "Нет данных" или empty state
**And** НЕ показывается ошибка

## Tasks

- [x] Task 1: Исследовать причину проблемы
  - [x] Проверить AuditPage и AuditFilterBar компоненты
  - [x] Проверить useAuditLogs hook
  - [x] Проверить API endpoint GET /api/v1/audit с параметрами фильтрации
  - [x] Проверить backend — как обрабатываются query параметры

- [x] Task 2: Исправить ошибку
  - [x] Внести необходимые изменения
  - [x] Проверить вручную

- [x] Task 3: Верифицировать исправление
  - [x] Убедиться что фильтры работают
  - [x] Убедиться что поиск возвращает результаты

## Investigation Notes

**Дата исследования:** 2026-02-21

### Корневая причина проблемы

**Проблема найдена в `AuditLogRepositoryCustomImpl.kt` строка 78-80:**

Frontend поддерживает multi-select для фильтра `action` (например, можно выбрать "created" И "updated"). При выборе нескольких значений frontend отправляет их как строку через запятую: `action=created,updated`.

Однако backend использовал простое сравнение `WHERE action = :action`, которое искало записи где action буквально равен `"created,updated"` — такие записи не существуют, так как каждая запись имеет одно значение action.

**Код до исправления:**
```kotlin
filter.action?.let {
    clauses.append(" AND action = :action")
    params["action"] = it
}
```

**Последовательность ошибки:**
1. Frontend: пользователь выбирает `action = ["created", "updated"]`
2. Frontend API: отправляет `GET /api/v1/audit?action=created,updated`
3. Backend Controller: получает `action = "created,updated"` (String)
4. Backend Repository: SQL `WHERE action = 'created,updated'`
5. Результат: 0 записей (в БД action = "created" или "updated", но не "created,updated")

### Решение

Исправлен метод `buildWhereClause()` для парсинга строки action на массив и использования `ANY()` с PostgreSQL массивом:

```kotlin
filter.action?.let { actionStr ->
    val actions = actionStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (actions.isNotEmpty()) {
        if (actions.size == 1) {
            clauses.append(" AND action = :action")
            params["action"] = actions.first()
        } else {
            clauses.append(" AND action = ANY(:actions)")
            params["actions"] = actions.toTypedArray()
        }
    }
}
```

### Проверенные компоненты

1. **Frontend AuditPage.tsx** — корректно парсит URL параметры и передаёт filter
2. **Frontend AuditFilterBar.tsx** — корректно формирует multi-select action
3. **Frontend auditApi.ts** — корректно конвертирует массив в строку через запятую
4. **Backend AuditController.kt** — получает action как String (ожидаемое поведение)
5. **Backend AuditLogRepositoryCustomImpl.kt** — **ИСПРАВЛЕН** для поддержки multi-select

## Fix Applied

**Дата исправления:** 2026-02-21

### Изменения в backend

Исправлен `AuditLogRepositoryCustomImpl.buildWhereClause()` для поддержки multi-select action фильтрации:

- При одном значении action: используется простое сравнение `action = :action`
- При нескольких значениях (через запятую): используется `action = ANY(:actions)` с PostgreSQL массивом

### Добавлены тесты

Добавлены 4 интеграционных теста в `AuditControllerIntegrationTest.kt`:
- `GET audit с multi-select action фильтром возвращает записи с любым из указанных action`
- `GET audit с multi-select action и другими фильтрами работает корректно`
- `GET audit с пустыми значениями в action строке игнорирует их` (edge case: `action=created,,approved`)
- `GET audit с trailing comma в action возвращает корректные результаты` (edge case: `action=created,`)

### Результат

- Все существующие тесты проходят (нет регрессий)
- Новые тесты для multi-select action проходят
- BUILD SUCCESSFUL

## Files Changed

- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/AuditLogRepositoryCustomImpl.kt` — исправлена обработка multi-select action фильтра
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/AuditControllerIntegrationTest.kt` — добавлены тесты для multi-select action

## Senior Developer Review (AI)

**Дата ревью:** 2026-02-21
**Reviewer:** Claude Code (Adversarial Review)

### Результат: ✅ APPROVED

Все Acceptance Criteria реализованы. Все Tasks выполнены.

### Issues найдены и исправлены

| # | Severity | Issue | Fix |
|---|----------|-------|-----|
| M1 | MEDIUM | Комментарий ссылался на "AC3" вместо Story 7.7.3 | Обновлён комментарий в AuditLogRepositoryCustomImpl.kt:78 |
| M2 | MEDIUM | Тест проверял только count, без проверки конкретных action values | Добавлены jsonPath проверки для created/approved exists, rejected/deleted doesNotExist |
| M3 | MEDIUM | Отсутствовали тесты на edge cases (пустые значения, trailing comma) | Добавлены 2 новых теста для edge cases |

### Тесты после ревью

```
BUILD SUCCESSFUL in 27s
9 actionable tasks: 5 executed, 4 up-to-date
```

### Рекомендации (LOW priority, не блокирующие)

1. **L1**: entityType не поддерживает multi-select — рассмотреть добавление если потребуется в будущем
2. **L2**: Тесты Story 7.7.3 добавлены в nested class `AC3_ФильтрацияПоAction` от Story 7.2 — логически корректно, но может вызвать путаницу

## Change Log

| Дата | Автор | Изменение |
|------|-------|-----------|
| 2026-02-21 | Dev Agent | Initial implementation — multi-select action fix |
| 2026-02-21 | Claude Code | Code Review — fixed 3 MEDIUM issues, added edge case tests |
