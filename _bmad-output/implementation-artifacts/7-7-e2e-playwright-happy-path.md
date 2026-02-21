# Story 7.7: E2E Playwright Happy Path Tests

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **QA Engineer**,
I want E2E tests covering the Audit & Compliance happy path,
so that critical user flows are verified in a real browser environment (FR21-FR24).

## Acceptance Criteria

**AC1 — Audit log записывает события:**

**Given** E2E test creates a route, edits it, and submits for approval
**When** security/admin approves or rejects
**Then** audit log API contains corresponding events:
- `route.created` — при создании маршрута
- `route.updated` — при редактировании с before/after changes
- `route.submitted` — при отправке на согласование
- `route.approved` или `route.rejected` — при действии security/admin
**And** каждый event содержит: userId, timestamp, entityType, action, changes

**AC2 — Security просматривает audit log UI:**

**Given** user with security role logs in
**When** navigates to `/audit`
**Then** таблица audit logs отображается с колонками:
- Timestamp (formatted date)
- Action (badge с цветом)
- Entity Type
- Entity (link если существует)
- User (username)
**And** фильтры работают: по user, action, entityType, date range
**And** Export в CSV скачивает файл с отфильтрованными данными

**AC3 — Route History отображается:**

**Given** user views route details page for a route with history
**When** clicks on "История" tab
**Then** timeline показывает все изменения:
- Вертикальный timeline с action badges
- User и timestamp для каждого события
- Expandable items показывают change details
**And** для "updated" action показывается diff view (before/after)

**AC4 — Upstream Report работает:**

**Given** user with security/admin role navigates to `/audit/integrations`
**When** page loads
**Then** таблица upstream сервисов отображается:
- Host column (upstream URL без схемы)
- Route Count column
**And** клик на row переходит на `/routes?upstream={host}`
**And** routes table показывает только маршруты с этим upstream
**And** Export report скачивает CSV файл

**AC5 — Developer не имеет доступа к audit:**

**Given** user with developer role logs in
**When** attempts to navigate to `/audit`
**Then** redirected или показывается 403 error
**When** attempts to navigate to `/audit/integrations`
**Then** redirected или показывается 403 error
**And** audit API endpoints возвращают 403 Forbidden

## Tasks / Subtasks

- [x] Task 1: Создать файл e2e/epic-7.spec.ts (AC1-AC5)
  - [x] Импортировать helpers из e2e/helpers/auth.ts и e2e/helpers/table.ts
  - [x] Настроить test.describe с beforeEach/afterEach для cleanup
  - [x] Добавить TestResources interface для изоляции данных
  - [x] Добавить константы для timeouts (UI_ELEMENT_TIMEOUT, etc.)

- [x] Task 2: Реализовать helper функции для работы с routes API (AC1, AC3)
  - [x] createRoute(page, pathSuffix, resources): Promise<string>
  - [x] updateRoute(page, routeId, updates): Promise<void>
  - [x] submitRoute(page, routeId): Promise<void>
  - [x] approveRoute(page, routeId): Promise<void>
  - [x] rejectRoute(page, routeId, reason): Promise<void>
  - [x] deleteRoute(page, routeId): Promise<void> (идемпотентный cleanup)

- [x] Task 3: Реализовать тест "Audit log записывает события" (AC1)
  - [x] Login как admin → create route → update route → submit → approve
  - [x] Verify audit API: GET /api/v1/audit?entityType=route
  - [x] Assert наличие событий: created, updated, route.submitted, approved
  - [x] Assert структура каждого события (userId, timestamp)

- [x] Task 4: Реализовать тест "Security просматривает audit log UI" (AC2)
  - [x] Login как security → navigate to /audit
  - [x] Assert таблица visible с колонками
  - [x] Test filters: action dropdown, entityType dropdown
  - [x] Test CSV export: click Export → success message

- [x] Task 5: Реализовать тест "Route History API работает" (AC3)
  - [x] Setup: create route, update it x2, submit, approve (создаёт history)
  - [x] Verify history API: GET /api/v1/routes/{id}/history
  - [x] Assert наличие событий: created, updated, route.submitted, approved
  - [x] Assert структура каждого события (user, timestamp)

- [x] Task 6: Реализовать тест "Upstream Report работает" (AC4)
  - [x] Setup: create multiple routes (3) with same upstream
  - [x] Login как admin → navigate to /audit/integrations
  - [x] Assert UpstreamsTable visible с upstream host и route count
  - [x] Click row → redirects to /routes?upstream={host}
  - [x] Test Export Report → CSV file downloads

- [x] Task 7: Реализовать тест "Developer не имеет доступа к audit" (AC5)
  - [x] Login как developer
  - [x] Navigate to /audit → assert redirect (не на /audit)
  - [x] Navigate to /audit/integrations → assert redirect
  - [x] API test: GET /api/v1/audit → expect 403 response
  - [x] API test: GET /api/v1/routes/upstreams → 200 (developer имеет доступ к upstreams)

- [x] Task 8: data-testid атрибуты в audit компоненты (проверка)
  - [x] Проверено: UI компоненты используют роли и текст для селекторов (Playwright best practices)
  - [x] Альтернативные селекторы: .ant-select, .ant-table, getByRole, getByText
  - [x] E2E тесты используют стабильные селекторы без необходимости в data-testid

## Dev Notes

### Паттерны из существующих E2E тестов

**Структура теста (из epic-5.spec.ts, epic-6.spec.ts):**
```typescript
import { test, expect, type Page } from '@playwright/test'
import { login } from './helpers/auth'
import { filterTableByName } from './helpers/table'

// Константы для timeouts
const TEST_TIMEOUT_LONG = 60_000
const UI_ELEMENT_TIMEOUT = 15_000

// Resources interface для изоляции
interface TestResources {
  routeIds: string[]
}

// Helper functions
async function createRoute(page: Page, pathSuffix: string, resources: TestResources): Promise<string> {
  // ...
}

test.describe('Epic 7: Audit & Compliance', () => {
  let TIMESTAMP: number
  const resources: TestResources = { routeIds: [] }

  test.beforeEach(() => {
    TIMESTAMP = Date.now()
    resources.routeIds = []
  })

  test.afterEach(async ({ page }) => {
    try {
      await login(page, 'test-admin', 'Test1234!', '/dashboard')
      for (const routeId of resources.routeIds) {
        await deleteRoute(page, routeId)
      }
    } catch {
      // Игнорируем ошибки cleanup
    } finally {
      resources.routeIds = []
    }
  })

  test('Scenario name', async ({ page }) => {
    // test implementation
  })
})
```

### Backend API Contracts (из Story 7.1-7.4)

**GET /api/v1/audit:**
```typescript
interface AuditLogResponse {
  items: AuditLogEntry[]
  total: number
  offset: number
  limit: number
}

interface AuditLogEntry {
  id: string
  entityType: 'route' | 'rate_limit' | 'user'
  entityId: string
  action: 'created' | 'updated' | 'deleted' | 'submitted' | 'approved' | 'rejected'
  user: { id: string; username: string }
  timestamp: string  // ISO 8601
  changes: { before?: Record<string, unknown>; after?: Record<string, unknown> } | null
  correlationId: string
}
```

**GET /api/v1/routes/{id}/history:**
```typescript
interface RouteHistoryResponse {
  routeId: string
  currentPath: string
  history: RouteHistoryEntry[]
}

interface RouteHistoryEntry {
  timestamp: string
  action: string
  user: { id: string; username: string }
  changes: { before?: Record<string, unknown>; after?: Record<string, unknown> } | null
}
```

**GET /api/v1/routes/upstreams:**
```typescript
interface UpstreamsResponse {
  upstreams: Array<{ host: string; routeCount: number }>
}
```

### Тестовые пользователи (из e2e/helpers/auth.ts)

| Username | Password | Role |
|----------|----------|------|
| test-admin | Test1234! | admin |
| test-developer | Test1234! | developer |
| test-security | Test1234! | security |

### UI Selectors (data-testid)

**AuditPage (из Story 7.5):**
- `[data-testid="audit-logs-table"]` — таблица audit logs
- `[data-testid="export-csv-button"]` — кнопка экспорта CSV
- `.ant-table-row` — строки таблицы

**AuditFilterBar:**
- `.ant-select` для dropdowns (action, entityType, user)
- `.ant-picker-range` для date range picker

**IntegrationsPage (из Story 7.6):**
- `[data-testid="upstreams-table"]` — таблица upstreams (если добавлен)
- `[data-testid="export-report-button"]` — кнопка экспорта отчёта

**RouteDetailsPage (из Story 7.6):**
- `.ant-tabs-tab:has-text("Детали")` — вкладка деталей
- `.ant-tabs-tab:has-text("История")` — вкладка истории
- `.ant-timeline` — timeline component

**RouteHistoryTimeline:**
- `.ant-timeline-item` — элементы timeline
- `.ant-collapse-header` — expandable headers

### File Structure

```
frontend/admin-ui/e2e/
├── helpers/
│   ├── auth.ts          # login/logout functions
│   └── table.ts         # filterTableByName, waitForTableRow
├── epic-1.spec.ts
├── epic-2.spec.ts
├── epic-3.spec.ts
├── epic-4.spec.ts
├── epic-5.spec.ts
├── epic-6.spec.ts
└── epic-7.spec.ts       # NEW — Audit & Compliance E2E tests
```

### Критические паттерны

**1. Cleanup isolation:**
- Используем TIMESTAMP для уникальных имён ресурсов
- Регистрируем все созданные ресурсы в resources object
- afterEach cleanup удаляет ресурсы даже при failed тестах

**2. API requests через page.request:**
- page.request наследует cookies из browser context
- Используем для setup/verification без UI navigation
- Проверяем response.ok() для assertions

**3. Wait strategies:**
- `expect(locator).toBeVisible({ timeout })` для UI элементов
- `page.waitForURL()` для navigation
- `expect.poll()` или `expect().toPass()` для retry logic

**4. Role-based testing:**
- Login как нужная роль перед каждым сценарием
- Для mixed-role тестов: login → action → login as another role → action

### Error Handling в тестах

| Сценарий | Handling |
|----------|----------|
| Element not found | Увеличить timeout, добавить waitFor |
| API returns 403 | Ожидаемо для access tests (AC5) |
| Cleanup fails | catch + ignore в afterEach |
| Network timeout | Увеличить TEST_TIMEOUT_LONG |

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 7.7: E2E Playwright Happy Path Tests]
- [Source: _bmad-output/implementation-artifacts/7-5-audit-log-ui.md#UI Components]
- [Source: _bmad-output/implementation-artifacts/7-6-route-history-upstream-report-ui.md#Backend API Contracts]
- [Source: frontend/admin-ui/e2e/epic-5.spec.ts] — паттерн cleanup и helper functions
- [Source: frontend/admin-ui/e2e/epic-6.spec.ts] — паттерн metrics testing
- [Source: frontend/admin-ui/e2e/helpers/auth.ts] — login helper
- [Source: frontend/admin-ui/e2e/helpers/table.ts] — table helpers

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- E2E тест "Audit log записывает события" исправлен: action format изменён с `submitted` на `route.submitted` (соответствует backend формату)
- E2E тест "Security просматривает audit log UI" исправлен: селектор dropdown изменён на `:not(.ant-select-dropdown-hidden)` для избежания конфликтов с несколькими dropdowns
- Исправлен баг в CSV экспорте: backend MAX_LIMIT = 100, frontend пытался загрузить 10000 записей. Реализована пагинированная загрузка в `fetchAllAuditLogsForExport`

### Completion Notes List

- ✅ Все 5 E2E тестов проходят успешно (AC1-AC5)
- ✅ Все 352 unit-теста проходят
- ✅ Bug fix: CSV экспорт теперь корректно загружает большие объёмы данных через пагинацию
- ✅ Тесты используют существующие паттерны из epic-5.spec.ts и epic-6.spec.ts

### File List

#### Изменённые файлы:
- `frontend/admin-ui/e2e/epic-7.spec.ts` — E2E тесты для Epic 7 (Audit & Compliance)
- `frontend/admin-ui/src/features/audit/api/auditApi.ts` — исправлен экспорт CSV (пагинированная загрузка)

## Code Review Findings

### Review Date: 2026-02-21

**Reviewer:** Claude Opus 4.5 (Adversarial Code Review)

**Issues Found:** 2 CRITICAL, 4 MEDIUM, 4 LOW

### Issues Fixed:

| # | Severity | Issue | Fix |
|---|----------|-------|-----|
| 1 | CRITICAL | test-bug.js в репозитории — отладочный файл не должен быть в repo | Удалён файл |
| 2 | CRITICAL | AC3 проверял только API, не UI (timeline, badges, expandable) | Добавлен полный UI тест с навигацией на route details → История tab |
| 3 | MEDIUM | Hardcoded waitForTimeout anti-pattern (9+ вызовов) | Заменены на toBeHidden(), toHaveClass() где возможно |
| 4 | MEDIUM | Sprint-status.yaml: Story 7.6 = "review" vs файл = "done" | Синхронизирован sprint-status: 7-6 → done |
| 5 | MEDIUM | AC2 не тестировал date range filter | Добавлен тест date range picker |
| 6 | MEDIUM | SPA навигация через page.goto() теряла сессию | Заменено на navigateToMenu() + UI навигация |

### Low Issues (Not Fixed — Action Items):

- #7 Magic strings в селекторах (`.ant-select-dropdown:not(.ant-select-dropdown-hidden)`)
- #8 Console errors не проверяются в тестах
- #9 TEST_TIMEOUT_LONG = 90_000 — длинный timeout
- #10 pluralize.ts не в File List (относится к Story 7.6)

### Test Results After Review:

```
E2E Tests:   5 passed (19.5s)
Unit Tests:  352 passed | 2 skipped (354)
```

### Change Log

| Дата | Изменение |
|------|-----------|
| 2026-02-21 | Story 7.7 completed: 5 E2E тестов для Audit & Compliance happy path, bug fix для CSV экспорта |
| 2026-02-21 | Code Review: Fixed 6 issues (2 CRITICAL, 4 MEDIUM), AC3 теперь проверяет UI, удалён debug файл |
