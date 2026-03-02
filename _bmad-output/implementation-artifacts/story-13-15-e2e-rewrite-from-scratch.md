# Story 13.15: E2E тесты с чистого листа

Status: done

## Story

As a **разработчик**,
I want **стабильные E2E тесты, написанные CI-first с изолированными зависимостями**,
so that **CI pipeline надёжно проходит без flakiness и не требует бесконечных исправлений**.

## Контекст решения

### Проблема
- Stories 13-13, 13-14 потратили много времени на адаптацию существующих E2E тестов под GitLab CI
- Тесты продолжают падать из-за инфраструктурных проблем (Keycloak, DB таймауты)
- Каждый фикс добавляет сложность, код тестов загрязнён workarounds
- Разработчик путается в условной логике `if (isCI)`

### Решение
Написать тесты **с нуля**, изначально проектируя их для CI:
- Mock все внешние зависимости (Keycloak, API) **by design**
- Простая структура без условной логики
- 10 ключевых сценариев для 80% покрытия функционала

## Acceptance Criteria

### AC1: Архивация старых тестов
- [x] Существующие E2E тесты перемещены в `frontend/admin-ui/e2e.legacy/`
- [x] Новая директория `frontend/admin-ui/e2e/` создана с чистой структурой

### AC2: Инфраструктура тестов
- [x] `e2e/fixtures/auth.fixture.ts` — mock авторизация, всегда возвращает валидный токен admin
- [x] `e2e/fixtures/api.fixture.ts` — Playwright route handlers для мока backend API
- [x] `playwright.config.ts` — единый конфиг без условий `if CI`
- [x] Тесты не зависят от реального Keycloak или PostgreSQL

### AC3: 10 E2E тестов (приоритизированные)

| # | Приоритет | Файл | Сценарий |
|---|-----------|------|----------|
| 1 | P0 | `01-login.spec.ts` | Логин admin → редирект на Dashboard |
| 2 | P0 | `02-dashboard.spec.ts` | Dashboard отображается, виджеты загружены |
| 3 | P0 | `03-routes-list.spec.ts` | Список маршрутов, фильтры работают |
| 4 | P0 | `04-routes-create.spec.ts` | Создание маршрута → появляется в списке |
| 5 | P1 | `05-routes-edit.spec.ts` | Редактирование маршрута → изменения сохранены |
| 6 | P1 | `06-routes-details.spec.ts` | Детали маршрута с историей |
| 7 | P1 | `07-approvals.spec.ts` | Список pending, approve/reject работает |
| 8 | P2 | `08-users.spec.ts` | Список пользователей (admin) |
| 9 | P2 | `09-rate-limits.spec.ts` | CRUD rate limit политик |
| 10 | P2 | `10-audit.spec.ts` | Аудит логи с фильтрами |

### AC4: CI интеграция
- [x] Все 10 тестов проходят в GitLab CI (55 test cases, 7.3s)
- [x] Pipeline зелёный на 5 последовательных запусках (pipelines 214-218)
- [x] Время выполнения всех тестов < 3 минут (job: 166s, tests: 7.3s)

### AC5: Документация
- [x] README.md в `e2e/` с инструкцией запуска
- [x] Описание структуры fixtures

## Tasks / Subtasks

- [x] **Task 1: Архивация** (AC1)
  - [x] Переместить `e2e/` → `e2e.legacy/`
  - [x] Создать новую структуру `e2e/`

- [x] **Task 2: Fixtures** (AC2)
  - [x] Создать `fixtures/auth.fixture.ts` с mock токеном
  - [x] Создать `fixtures/api.fixture.ts` с Playwright route handlers (вместо MSW)
  - [x] Настроить `playwright.config.ts`

- [x] **Task 3: P0 тесты** (AC3 #1-4)
  - [x] `01-login.spec.ts`
  - [x] `02-dashboard.spec.ts`
  - [x] `03-routes-list.spec.ts`
  - [x] `04-routes-create.spec.ts`

- [x] **Task 4: P1 тесты** (AC3 #5-7)
  - [x] `05-routes-edit.spec.ts`
  - [x] `06-routes-details.spec.ts`
  - [x] `07-approvals.spec.ts`

- [x] **Task 5: P2 тесты** (AC3 #8-10)
  - [x] `08-users.spec.ts`
  - [x] `09-rate-limits.spec.ts`
  - [x] `10-audit.spec.ts`

- [x] **Task 6: CI валидация** (AC4)
  - [x] Обновлён `.gitlab-ci.yml` с новым job `e2e-test-mock`
  - [x] Запуск в GitLab CI — 55 тестов прошли за 7.3 секунды
  - [x] Проверка 5 последовательных прохождений (pipelines 214-218)

- [x] **Task 7: Документация** (AC5)
  - [x] README.md для e2e/

## Dev Notes

### Архитектурные принципы

1. **CI-first**: Тесты пишутся для CI с первого дня
2. **Изоляция**: Никаких реальных внешних зависимостей
3. **Простота**: Нет условной логики в тестах
4. **Детерминизм**: Одинаковый результат локально и в CI

### Структура проекта

```
frontend/admin-ui/
├── e2e/                        # НОВЫЕ тесты
│   ├── fixtures/
│   │   ├── auth.fixture.ts     # Mock auth
│   │   └── api.fixture.ts      # MSW handlers
│   ├── tests/
│   │   ├── 01-login.spec.ts
│   │   ├── 02-dashboard.spec.ts
│   │   ├── ...
│   │   └── 10-audit.spec.ts
│   ├── playwright.config.ts
│   └── README.md
├── e2e.legacy/                 # Архив старых тестов
│   └── ... (старые файлы)
```

### Mock Auth подход

```typescript
// fixtures/auth.fixture.ts
export const mockAdminUser = {
  id: 'test-admin-id',
  username: 'admin',
  role: 'admin',
  token: 'mock-jwt-token-for-testing'
}

// Playwright fixture
export const test = base.extend({
  authenticatedPage: async ({ page }, use) => {
    // Устанавливаем mock токен в localStorage/cookies
    await page.addInitScript(() => {
      localStorage.setItem('auth_token', 'mock-jwt-token-for-testing')
    })
    await use(page)
  }
})
```

### MSW для API

```typescript
// fixtures/api.fixture.ts
import { setupWorker, rest } from 'msw'

export const handlers = [
  rest.get('/api/v1/routes', (req, res, ctx) => {
    return res(ctx.json({ routes: [...mockRoutes], total: 10 }))
  }),
  rest.post('/api/v1/routes', (req, res, ctx) => {
    return res(ctx.json({ id: 'new-route-id', ...req.body }))
  }),
  // ... другие handlers
]
```

### References

- [Source: frontend/admin-ui/src/App.tsx] — роутинг приложения
- [Source: frontend/admin-ui/src/layouts/Sidebar.tsx] — role-based меню
- [Source: CLAUDE.md] — правила проекта, комментарии на русском

## Definition of Done

- [x] Все 10 тестов проходят локально (55 tests, ~8 секунд)
- [x] Все 10 тестов проходят в GitLab CI (55 tests, 7.3s)
- [x] 5 последовательных зелёных pipeline (214-218)
- [x] Нет условной логики `if (isCI)` в коде тестов (только в playwright.config.ts для reporter)
- [x] README.md документирует структуру и запуск

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Completion Notes List

1. **Task 1-2 (Архивация и Fixtures)**: Создана структура `e2e/` с fixtures для mock auth и API
2. **Task 3-5 (Тесты)**: Все 10 тестов созданы, 55 test cases проходят за ~8 секунд
3. **Task 7 (Документация)**: README.md создан
4. **CI конфигурация**: Добавлен job `e2e-test-mock` в `.gitlab-ci.yml`

### File List

**Новые файлы:**
- `frontend/admin-ui/e2e/fixtures/auth.fixture.ts` — mock Keycloak auth
- `frontend/admin-ui/e2e/fixtures/api.fixture.ts` — Playwright route handlers
- `frontend/admin-ui/e2e/tests/01-login.spec.ts` — Login flow (P0)
- `frontend/admin-ui/e2e/tests/02-dashboard.spec.ts` — Dashboard (P0)
- `frontend/admin-ui/e2e/tests/03-routes-list.spec.ts` — Routes list (P0)
- `frontend/admin-ui/e2e/tests/04-routes-create.spec.ts` — Create route (P0)
- `frontend/admin-ui/e2e/tests/05-routes-edit.spec.ts` — Edit route (P1)
- `frontend/admin-ui/e2e/tests/06-routes-details.spec.ts` — Route details (P1)
- `frontend/admin-ui/e2e/tests/07-approvals.spec.ts` — Approvals (P1)
- `frontend/admin-ui/e2e/tests/08-users.spec.ts` — Users list (P2)
- `frontend/admin-ui/e2e/tests/09-rate-limits.spec.ts` — Rate limits (P2)
- `frontend/admin-ui/e2e/tests/10-audit.spec.ts` — Audit logs (P2)
- `frontend/admin-ui/e2e/README.md` — Документация

**Изменённые файлы:**
- `frontend/admin-ui/playwright.config.ts` — обновлён для CI-first подхода
- `frontend/admin-ui/package.json` — добавлен `--port 3000` для preview
- `.gitlab-ci.yml` — добавлен job `e2e-test-mock`, переименован legacy job в `e2e-test-integration`

**Архивированные:**
- `frontend/admin-ui/e2e.legacy/` — старые E2E тесты
