# Story 8.10: E2E Playwright Tests для Epic 8

Status: done

## Story

As a **QA Engineer**,
I want E2E tests covering UX improvements from Epic 8,
so that changes are verified in a real browser environment.

## Acceptance Criteria

**AC1 — Metrics Health Check:**

**Given** пользователь аутентифицирован как admin/devops
**When** пользователь переходит на /metrics
**Then** секция Health Check отображается
**And** все сервисы показывают статус-индикаторы (gateway-core, gateway-admin, PostgreSQL, Redis)

**AC2 — Users search:**

**Given** admin аутентифицирован
**When** admin переходит на /users
**And** вводит username в поле поиска
**Then** таблица фильтруется корректно по username или email

**AC3 — Routes search by Upstream:**

**Given** пользователь аутентифицирован
**When** пользователь переходит на /routes
**And** вводит upstream URL в поле поиска
**Then** маршруты фильтруются по upstream URL

**AC4 — Load Generator:**

**Given** devops/admin аутентифицирован
**When** пользователь переходит на /test
**And** выбирает published маршрут
**And** нажимает Start
**Then** генерация нагрузки запускается
**And** progress показывает sent/success/error counters
**When** пользователь нажимает Stop
**Then** генерация останавливается
**And** summary отображается с total requests, duration, success rate

## Tasks / Subtasks

- [x] Task 1: Создать файл epic-8.spec.ts (AC1-AC4)
  - [x] Subtask 1.1: Добавить импорты и константы (timeouts, helpers)
  - [x] Subtask 1.2: Добавить TestResources interface для изоляции
  - [x] Subtask 1.3: Добавить beforeEach/afterEach для setup/cleanup

- [x] Task 2: Реализовать AC1 — Metrics Health Check
  - [x] Subtask 2.1: Login как admin
  - [x] Subtask 2.2: Навигация на /metrics
  - [x] Subtask 2.3: Проверка видимости HealthCheckSection
  - [x] Subtask 2.4: Проверка статус-индикаторов для каждого сервиса

- [x] Task 3: Реализовать AC2 — Users search
  - [x] Subtask 3.1: Login как admin
  - [x] Subtask 3.2: Навигация на /users
  - [x] Subtask 3.3: Ввод username в search input
  - [x] Subtask 3.4: Проверка фильтрации таблицы
  - [x] Subtask 3.5: Проверка поиска по email

- [x] Task 4: Реализовать AC3 — Routes search by Upstream
  - [x] Subtask 4.1: Setup — создать маршрут с уникальным upstream
  - [x] Subtask 4.2: Навигация на /routes
  - [x] Subtask 4.3: Ввод upstream URL в search
  - [x] Subtask 4.4: Проверка что маршрут отображается
  - [x] Subtask 4.5: Проверка что search по path также работает

- [x] Task 5: Реализовать AC4 — Load Generator
  - [x] Subtask 5.1: Setup — создать и опубликовать маршрут
  - [x] Subtask 5.2: Навигация на /test
  - [x] Subtask 5.3: Выбор маршрута в dropdown
  - [x] Subtask 5.4: Нажатие Start и проверка progress
  - [x] Subtask 5.5: Нажатие Stop и проверка summary
  - [x] Subtask 5.6: Проверка что метрики обновились (опционально — пропущено)

- [x] Task 6: Интеграция и финализация
  - [x] Subtask 6.1: Проверить все тесты проходят локально
  - [x] Subtask 6.2: Добавить комментарии на русском языке
  - [x] Subtask 6.3: Убедиться в изоляции тестов (no shared state)

## API Dependencies Checklist

**Backend API endpoints, используемые в тестах:**

| Endpoint | Method | Параметры | Статус |
|----------|--------|-----------|--------|
| `/api/v1/auth/login` | POST | username, password | ✅ Существует |
| `/api/v1/routes` | GET | search, status | ✅ Существует |
| `/api/v1/routes` | POST | path, upstreamUrl, methods | ✅ Существует |
| `/api/v1/routes/{id}/submit` | POST | - | ✅ Существует |
| `/api/v1/routes/{id}/approve` | POST | - | ✅ Существует |
| `/api/v1/users` | GET | search | ✅ Существует |
| `/api/v1/metrics/summary` | GET | period | ✅ Существует |
| Gateway health endpoints | GET | /actuator/health | ✅ Существует |

**Проверки перед началом разработки:**

- [x] Все endpoints существуют в backend
- [x] Тестовые пользователи (test-admin, test-developer, test-security) созданы в seed
- [x] HealthCheckSection имеет нужные data-testid
- [x] Load Generator компоненты имеют data-testid

## Dev Notes

### Архитектура E2E тестов

**Файловая структура:**
```
frontend/admin-ui/e2e/
├── epic-1.spec.ts  # Foundation
├── epic-2.spec.ts  # Auth
├── epic-3.spec.ts  # Routes
├── epic-4.spec.ts  # Approvals
├── epic-5.spec.ts  # Rate Limiting
├── epic-6.spec.ts  # Monitoring
├── epic-7.spec.ts  # Audit
├── epic-8.spec.ts  # UX Improvements ← СОЗДАТЬ
├── helpers/
│   └── auth.ts     # login/logout helpers
└── global-setup.ts
```

### Паттерны из существующих тестов

**Константы для timeouts (из epic-7.spec.ts):**
```typescript
/** Timeout для сложных тестов */
const TEST_TIMEOUT_LONG = 90_000

/** Ожидание синхронизации UI после операций */
const UI_SYNC_DELAY = 2000

/** Timeout для появления UI элементов */
const UI_ELEMENT_TIMEOUT = 15_000

/** Timeout для загрузки таблиц с данными */
const TABLE_LOAD_TIMEOUT = 30_000
```

**Helper для SPA навигации:**
```typescript
async function navigateToMenu(page: Page, menuItemText: string | RegExp): Promise<void> {
  const menuItem = page.locator('[role="menuitem"]').filter({ hasText: menuItemText })
  await menuItem.click()
  await expect(menuItem).toHaveClass(/ant-menu-item-selected/, { timeout: 5000 })
}
```

**Изоляция тестовых данных:**
```typescript
interface TestResources {
  routeIds: string[]
  userIds: string[]
}

// В beforeEach:
test.beforeEach(() => {
  TIMESTAMP = Date.now()
  resources.routeIds = []
})

// В afterEach:
test.afterEach(async ({ page }) => {
  // Cleanup созданных ресурсов
  for (const routeId of resources.routeIds) {
    await deleteRoute(page, routeId)
  }
  resources.routeIds = []
})
```

### Data-testid для тестов

**HealthCheckSection (AC1):**
Нужно проверить существующие data-testid в `HealthCheckSection.tsx`.
Ожидаемые элементы:
- Контейнер секции
- Статус-индикаторы для каждого сервиса

**Users Table (AC2):**
- `data-testid="users-search-input"` — поле поиска

**Routes Table (AC3):**
- Поле поиска (нужно добавить data-testid если отсутствует)

**Test Page (AC4):**
- `data-testid="test-page"` — контейнер страницы
- `data-testid="load-generator-form"` — форма
- `data-testid="route-selector"` — dropdown маршрутов
- `data-testid="rps-input"` — requests per second
- `data-testid="start-button"` — кнопка Start
- `data-testid="stop-button"` — кнопка Stop
- `data-testid="load-generator-progress"` — progress card
- `data-testid="stat-sent"` — счётчик sent
- `data-testid="stat-success"` — счётчик success
- `data-testid="stat-errors"` — счётчик errors
- `data-testid="load-generator-summary"` — summary card
- `data-testid="summary-total"` — total requests
- `data-testid="summary-success-rate"` — success rate

### Тестовые сценарии

**AC1 — Metrics Health Check:**
```typescript
test('Health Check отображается на Metrics', async ({ page }) => {
  // 1. Login как admin
  await login(page, 'test-admin', 'Test1234!', '/dashboard')

  // 2. Навигация на /metrics через меню
  await navigateToMenu(page, /Metrics/)

  // 3. Ожидание загрузки страницы
  await expect(page.locator('[data-testid="metrics-page"]')).toBeVisible()

  // 4. Проверка HealthCheckSection
  const healthSection = page.locator('[data-testid="health-check-section"]')
  await expect(healthSection).toBeVisible()

  // 5. Проверка статус-индикаторов
  // gateway-core, gateway-admin, PostgreSQL, Redis
  await expect(healthSection.locator('text=gateway-core')).toBeVisible()
  await expect(healthSection.locator('text=gateway-admin')).toBeVisible()
  // Статусы: UP (зелёный) или DOWN (красный)
})
```

**AC2 — Users search:**
```typescript
test('Admin ищет пользователей по username и email', async ({ page }) => {
  // 1. Login как admin
  await login(page, 'test-admin', 'Test1234!', '/dashboard')

  // 2. Навигация на /users
  await navigateToMenu(page, /Users/)

  // 3. Ожидание загрузки таблицы
  await page.waitForSelector('table tbody tr', { timeout: TABLE_LOAD_TIMEOUT })

  // 4. Поиск по username
  const searchInput = page.locator('[data-testid="users-search-input"]')
  await searchInput.fill('admin')
  await page.waitForTimeout(500) // debounce

  // 5. Проверка фильтрации
  const rows = page.locator('table tbody tr')
  await expect(rows).toHaveCount(1) // или другое ожидаемое число
  await expect(rows.first()).toContainText('admin')

  // 6. Очистка и поиск по email
  await searchInput.clear()
  await searchInput.fill('@test.com')
  await page.waitForTimeout(500)

  // 7. Проверка что результаты отфильтрованы
})
```

**AC3 — Routes search by Upstream:**
```typescript
test('Поиск маршрутов по Upstream URL', async ({ page }) => {
  // 1. Setup: создать маршрут с уникальным upstream
  await login(page, 'test-admin', 'Test1234!', '/dashboard')
  const upstreamHost = `e2e-upstream-${TIMESTAMP}.local`
  await createRoute(page, `search-${TIMESTAMP}`, resources, `http://${upstreamHost}:8080`)

  // 2. Навигация на /routes
  await navigateToMenu(page, /Routes/)

  // 3. Поиск по upstream
  const searchInput = page.locator('input[placeholder*="Поиск"]')
  await searchInput.fill(upstreamHost)
  await page.waitForTimeout(500)

  // 4. Проверка что маршрут найден
  const row = page.locator(`tr:has-text("${upstreamHost}")`)
  await expect(row).toBeVisible()
})
```

**AC4 — Load Generator:**
```typescript
test('Load Generator работает', { timeout: TEST_TIMEOUT_LONG }, async ({ page }) => {
  // 1. Setup: создать и опубликовать маршрут
  await login(page, 'test-admin', 'Test1234!', '/dashboard')
  const routePath = `load-gen-${TIMESTAMP}`
  await createPublishedRoute(page, routePath, resources)
  await page.waitForTimeout(GATEWAY_SYNC_DELAY)

  // 2. Навигация на /test
  await navigateToMenu(page, /Test/)
  await expect(page.locator('[data-testid="test-page"]')).toBeVisible()

  // 3. Выбор маршрута
  const routeSelector = page.locator('[data-testid="route-selector"]')
  await routeSelector.click()
  await page.locator(`.ant-select-item:has-text("${routePath}")`).click()

  // 4. Установка RPS (низкое значение для теста)
  const rpsInput = page.locator('[data-testid="rps-input"]')
  await rpsInput.fill('5')

  // 5. Нажатие Start
  await page.locator('[data-testid="start-button"]').click()

  // 6. Проверка progress
  const progress = page.locator('[data-testid="load-generator-progress"]')
  await expect(progress).toBeVisible()

  // Ждём несколько секунд генерации
  await page.waitForTimeout(3000)

  // Проверка что счётчики увеличиваются
  const sentCount = page.locator('[data-testid="stat-sent"] .ant-statistic-content-value')
  const sentValue = await sentCount.textContent()
  expect(parseInt(sentValue || '0')).toBeGreaterThan(0)

  // 7. Нажатие Stop
  await page.locator('[data-testid="stop-button"]').click()

  // 8. Проверка summary
  const summary = page.locator('[data-testid="load-generator-summary"]')
  await expect(summary).toBeVisible()

  const totalRequests = page.locator('[data-testid="summary-total"] .ant-statistic-content-value')
  await expect(totalRequests).toBeVisible()
  const totalValue = await totalRequests.textContent()
  expect(parseInt(totalValue || '0')).toBeGreaterThan(0)
})
```

### Project Structure Notes

| Файл | Путь | Изменение |
|------|------|-----------|
| epic-8.spec.ts | `frontend/admin-ui/e2e/` | НОВЫЙ |
| HealthCheckSection.tsx | `frontend/admin-ui/src/features/metrics/components/` | Возможно добавить data-testid |
| RoutesTable.tsx | `frontend/admin-ui/src/features/routes/components/` | Возможно добавить data-testid для search |

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 8.10]
- [Source: frontend/admin-ui/e2e/epic-7.spec.ts] — паттерн E2E тестов
- [Source: frontend/admin-ui/e2e/epic-6.spec.ts] — паттерн метрик тестов
- [Source: frontend/admin-ui/e2e/helpers/auth.ts] — login/logout helpers
- [Source: frontend/admin-ui/src/features/test/components/] — Load Generator компоненты
- [Source: _bmad-output/implementation-artifacts/8-9-test-page-load-generator.md] — предыдущая story

### Тестовые команды

```bash
# Запуск E2E тестов
cd frontend/admin-ui
npx playwright test e2e/epic-8.spec.ts

# С UI режимом (для отладки)
npx playwright test e2e/epic-8.spec.ts --ui

# С открытым браузером
npx playwright test e2e/epic-8.spec.ts --headed

# Конкретный тест
npx playwright test e2e/epic-8.spec.ts --grep "Health Check"

# Проверка всех E2E перед коммитом
npx playwright test
```

### Связанные stories

- Story 8.1 — Health Check на странице Metrics (HealthCheckSection)
- Story 8.3 — Поиск пользователей по username и email
- Story 8.5 — Поиск Routes по Path и Upstream URL
- Story 8.9 — Страница Test с генератором нагрузки
- Story 6.6 — E2E Playwright для Epic 6 (паттерн metrics тестов)
- Story 7.7 — E2E Playwright для Epic 7 (паттерн audit тестов)

### Git commits из предыдущих stories (контекст)

```
Latest commits from Epic 8:
- feat: implement Story 8.9 — Test page with load generator
- feat: implement Story 8.8 — unified FilterChips component
- feat: implement Story 8.7 — Approvals search by upstream
- feat: implement Story 8.5 — Routes search by path and upstream
- feat: implement Story 8.3 — Users search by username and email
- feat: implement Story 8.1 — Health Check on Metrics page
```

### Security Considerations

**Тестовые пользователи:**
- test-admin (admin роль) — для большинства тестов
- test-developer (developer роль) — для проверки ограничений
- test-security (security роль) — для approval тестов

**Изоляция:**
- Каждый тест использует уникальный TIMESTAMP для ресурсов
- Cleanup удаляет все созданные ресурсы
- global-setup удаляет e2e-* ресурсы перед запуском

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

### Completion Notes List

- **AC1 (Metrics Health Check):** Тест проверяет видимость HealthCheckSection, наличие карточек для 4 сервисов (gateway-core, gateway-admin, postgresql, redis), статус UP, и кнопку обновления.
- **AC2 (Users search):** Тест логинится как admin, переходит на /users, вводит "admin" в поле поиска и проверяет фильтрацию. Затем проверяет поиск по email.
- **AC3 (Routes search by Upstream):** Тест создаёт маршрут с уникальным upstream через API, затем проверяет поиск по upstream URL и по path.
- **AC4 (Load Generator):** Тест создаёт published маршрут, выбирает его в dropdown на странице /test, запускает генерацию, проверяет progress (sent > 0), останавливает и проверяет summary.
- **Изоляция:** Каждый тест использует уникальный TIMESTAMP, cleanup удаляет ресурсы после теста (игнорируя 409 для published маршрутов).
- **Стабильность:** GATEWAY_SYNC_DELAY увеличен до 5 секунд, используется toPass() polling для надёжного ожидания прогресса.

### Change Log

- 2026-02-21: Создан файл epic-8.spec.ts с 4 E2E тестами для Epic 8 (AC1-AC4)
- 2026-02-21: Code review — исправлено 3 issues (удалены debug файлы, оптимизирован waitForTimeout, добавлен data-testid для routes search)

### File List

| Файл | Путь | Изменение |
|------|------|-----------|
| epic-8.spec.ts | `frontend/admin-ui/e2e/` | Новый файл |
| RoutesTable.tsx | `frontend/admin-ui/src/features/routes/components/` | Добавлен data-testid для search input |
| sprint-status.yaml | `_bmad-output/implementation-artifacts/` | Обновлён статус story |
| 8-10-e2e-playwright-tests.md | `_bmad-output/implementation-artifacts/` | Обновлён статус и задачи |
