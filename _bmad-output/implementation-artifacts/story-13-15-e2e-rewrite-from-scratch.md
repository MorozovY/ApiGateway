# Story 13.15: E2E тесты с чистого листа

Status: ready-for-dev

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
- [ ] Существующие E2E тесты перемещены в `frontend/admin-ui/e2e.legacy/`
- [ ] Новая директория `frontend/admin-ui/e2e/` создана с чистой структурой

### AC2: Инфраструктура тестов
- [ ] `e2e/fixtures/auth.fixture.ts` — mock авторизация, всегда возвращает валидный токен admin
- [ ] `e2e/fixtures/api.fixture.ts` — MSW для мока backend API
- [ ] `playwright.config.ts` — единый конфиг без условий `if CI`
- [ ] Тесты не зависят от реального Keycloak или PostgreSQL

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
- [ ] Все 10 тестов проходят в GitLab CI
- [ ] Pipeline зелёный на 5 последовательных запусках
- [ ] Время выполнения всех тестов < 3 минут

### AC5: Документация
- [ ] README.md в `e2e/` с инструкцией запуска
- [ ] Описание структуры fixtures

## Tasks / Subtasks

- [ ] **Task 1: Архивация** (AC1)
  - [ ] Переместить `e2e/` → `e2e.legacy/`
  - [ ] Создать новую структуру `e2e/`

- [ ] **Task 2: Fixtures** (AC2)
  - [ ] Создать `fixtures/auth.fixture.ts` с mock токеном
  - [ ] Создать `fixtures/api.fixture.ts` с MSW handlers
  - [ ] Настроить `playwright.config.ts`

- [ ] **Task 3: P0 тесты** (AC3 #1-4)
  - [ ] `01-login.spec.ts`
  - [ ] `02-dashboard.spec.ts`
  - [ ] `03-routes-list.spec.ts`
  - [ ] `04-routes-create.spec.ts`

- [ ] **Task 4: P1 тесты** (AC3 #5-7)
  - [ ] `05-routes-edit.spec.ts`
  - [ ] `06-routes-details.spec.ts`
  - [ ] `07-approvals.spec.ts`

- [ ] **Task 5: P2 тесты** (AC3 #8-10)
  - [ ] `08-users.spec.ts`
  - [ ] `09-rate-limits.spec.ts`
  - [ ] `10-audit.spec.ts`

- [ ] **Task 6: CI валидация** (AC4)
  - [ ] Запуск в GitLab CI
  - [ ] Проверка 5 последовательных прохождений

- [ ] **Task 7: Документация** (AC5)
  - [ ] README.md для e2e/

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

- [ ] Все 10 тестов проходят локально
- [ ] Все 10 тестов проходят в GitLab CI
- [ ] 5 последовательных зелёных pipeline
- [ ] Нет условной логики `if (isCI)` в коде тестов
- [ ] README.md документирует структуру и запуск

## Dev Agent Record

### Agent Model Used

_To be filled by dev agent_

### Completion Notes List

_To be filled during implementation_

### File List

_To be filled during implementation_
